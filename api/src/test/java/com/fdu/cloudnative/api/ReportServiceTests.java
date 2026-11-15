package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.dto.CreateReportRunRequest;
import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.exception.BusinessException;
import com.fdu.cloudnative.api.exception.ErrorCode;
import com.fdu.cloudnative.api.job.ReportJobLauncher;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import com.fdu.cloudnative.api.service.ReportService;
import com.fdu.cloudnative.api.vo.ReportDetailVO;
import com.fdu.cloudnative.api.vo.ReportRunVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 服务层测试：真实数据库，Job 启动器用 mock 替代（不需要集群）。
 *
 * 这个类故意不加 @Transactional：createReport 的关键点是"记录先提交，再创建 Job"，
 * 如果测试自己包一层事务，就看不出记录到底有没有提交。所以每个测试创建的记录
 * 都记在 createdIds 里，结束后手动删除。
 */
@SpringBootTest
class ReportServiceTests {

    @Autowired
    private ReportService reportService;
    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private ReportJobLauncher reportJobLauncher;

    private static final LocalDate START = LocalDate.of(2031, 5, 1);

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedRuns() throws Exception {
        // report_results 通过外键 ON DELETE CASCADE 一并删除
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM report_runs WHERE id = ?")) {
            for (Long id : createdIds) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }
        createdIds.clear();
    }

    private ReportRunVO create(LocalDate start, LocalDate end) {
        ReportRunVO vo = reportService.createReport(new CreateReportRunRequest(start, end));
        createdIds.add(Long.valueOf(vo.id()));
        return vo;
    }

    /** launch 被调用时拿到的运行 ID；用于在 createReport 抛异常、拿不到返回值时找到那条记录。 */
    private Long launchedRunId() {
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(reportJobLauncher).launch(captor.capture(), anyString());
        createdIds.add(captor.getValue());
        return captor.getValue();
    }

    private static String fieldOf(BusinessException ex) {
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals(1, ex.getFieldErrors().size());
        return ex.getFieldErrors().get(0).field();
    }

    // ---------- createReport ----------

    @Test
    void create_writesPendingRunAndLaunchesJob() {
        ReportRunVO vo = create(START, START.plusDays(13));

        // 返回值是完整记录，而不是只有请求里的那几个字段
        assertEquals(TriggerType.MANUAL, vo.triggerType());
        assertEquals(START, vo.rangeStart());
        assertEquals(START.plusDays(13), vo.rangeEnd());
        assertEquals(ReportStatus.PENDING, vo.status());
        assertEquals("report-" + vo.id(), vo.jobName());
        assertEquals(0, vo.attempt());
        assertNotNull(vo.createdAt());
        assertNull(vo.startedAt());
        assertNull(vo.finishedAt());
        assertNull(vo.errorMessage());

        // Job 启动器拿到的是同一个 ID 和 Job 名
        verify(reportJobLauncher).launch(Long.valueOf(vo.id()), "report-" + vo.id());

        // 库里的记录与返回值一致
        ReportRun saved = reportMapper.getRunById(Long.valueOf(vo.id()));
        assertEquals(ReportStatus.PENDING, saved.getStatus());
        assertEquals("report-" + vo.id(), saved.getJobName());
        assertNull(saved.getScheduleKey());           // 手动任务没有 schedule_key
    }

    @Test
    void create_launchesOnlyAfterRunIsCommitted() {
        // Job 启动后会立刻读这条记录，所以调用 launch 时写库的事务必须已经结束
        AtomicReference<Boolean> transactionActiveAtLaunch = new AtomicReference<>();
        AtomicReference<ReportRun> visibleAtLaunch = new AtomicReference<>();
        doAnswer(invocation -> {
            transactionActiveAtLaunch.set(TransactionSynchronizationManager.isActualTransactionActive());
            visibleAtLaunch.set(reportMapper.getRunById(invocation.getArgument(0)));
            return null;
        }).when(reportJobLauncher).launch(anyLong(), anyString());

        ReportRunVO vo = create(START, START);

        assertEquals(Boolean.FALSE, transactionActiveAtLaunch.get(), "launch 不应在写库事务内调用");
        assertNotNull(visibleAtLaunch.get());
        assertEquals("report-" + vo.id(), visibleAtLaunch.get().getJobName());
    }

    @Test
    void create_rejectsInvalidRange_withoutWritingOrLaunching() {
        long before = reportMapper.countRuns(null, null);

        // 起始晚于结束
        BusinessException reversed = assertThrows(BusinessException.class,
                () -> reportService.createReport(new CreateReportRunRequest(START.plusDays(1), START)));
        assertEquals("rangeEnd", fieldOf(reversed));

        // 含首尾共 367 天，超过上限
        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> reportService.createReport(new CreateReportRunRequest(START, START.plusDays(366))));
        assertEquals("rangeEnd", fieldOf(tooLong));

        assertEquals(before, reportMapper.countRuns(null, null));      // 没有写入任何记录
        verify(reportJobLauncher, never()).launch(any(), any());       // 也没有创建 Job
    }

    @Test
    void create_acceptsBoundaryRanges() {
        // 起止同一天（1 天）和含首尾恰好 366 天都合法
        assertEquals(ReportStatus.PENDING, create(START, START).status());
        assertEquals(ReportStatus.PENDING, create(START, START.plusDays(365)).status());
    }

    @Test
    void create_launchFails_marksRunFailedAndThrows503() {
        doThrow(new IllegalStateException("jobs.batch is forbidden"))
                .when(reportJobLauncher).launch(anyLong(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reportService.createReport(new CreateReportRunRequest(START, START.plusDays(6))));
        assertEquals(ErrorCode.REPORT_JOB_CREATE_FAILED, ex.getErrorCode());
        assertNull(ex.getFieldErrors());

        // 记录没有被回滚掉：仍然存在，状态为 FAILED，原因可追踪
        ReportRun failed = reportMapper.getRunById(launchedRunId());
        assertNotNull(failed);
        assertEquals(ReportStatus.FAILED, failed.getStatus());
        assertEquals("jobs.batch is forbidden", failed.getErrorMessage());
        assertNotNull(failed.getFinishedAt());
        assertEquals(0, failed.getAttempt());         // Job 从未开始执行
    }

    @Test
    void create_launchFailsWithoutMessage_stillReturns503() {
        // 没有消息的异常：不能在处理失败时自己再抛空指针，原因退回异常类名
        doThrow(new NullPointerException()).when(reportJobLauncher).launch(anyLong(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reportService.createReport(new CreateReportRunRequest(START, START)));
        assertEquals(ErrorCode.REPORT_JOB_CREATE_FAILED, ex.getErrorCode());

        ReportRun failed = reportMapper.getRunById(launchedRunId());
        assertEquals(ReportStatus.FAILED, failed.getStatus());
        assertEquals("NullPointerException", failed.getErrorMessage());
    }

    // ---------- listReports ----------

    @Test
    void list_withoutFilters_returnsNewestFirst() {
        long before = reportService.listReports(1, 1, null, null).total();
        ReportRunVO first = create(START, START);
        ReportRunVO second = create(START, START.plusDays(1));

        // 两个筛选条件都不传（默认的列表请求）
        PageResult<ReportRunVO> page = reportService.listReports(1, 2, null, null);
        assertEquals(before + 2, page.total());
        assertEquals(1, page.page());
        assertEquals(2, page.pageSize());
        assertEquals(List.of(second.id(), first.id()), page.items().stream().map(ReportRunVO::id).toList());

        // 第 2 页，每页 1 条
        PageResult<ReportRunVO> page2 = reportService.listReports(2, 1, null, null);
        assertEquals(List.of(first.id()), page2.items().stream().map(ReportRunVO::id).toList());
    }

    @Test
    void list_filtersByStatusAndTriggerType() {
        long failedBefore = reportService.listReports(1, 1, ReportStatus.FAILED, null).total();
        long scheduledBefore = reportService.listReports(1, 1, null, TriggerType.SCHEDULED).total();
        long failedManualBefore = reportService.listReports(1, 1, ReportStatus.FAILED, TriggerType.MANUAL).total();

        ReportRunVO pending = create(START, START);
        ReportRunVO failed = create(START, START);
        reportMapper.markFailed(Long.valueOf(failed.id()), "boom");

        // 只按状态
        PageResult<ReportRunVO> failedPage = reportService.listReports(1, 1, ReportStatus.FAILED, null);
        assertEquals(failedBefore + 1, failedPage.total());
        assertEquals(failed.id(), failedPage.items().get(0).id());
        assertEquals("boom", failedPage.items().get(0).errorMessage());

        // 只按触发方式：新建的两条都是 MANUAL，SCHEDULED 的数量不变
        assertEquals(scheduledBefore, reportService.listReports(1, 1, null, TriggerType.SCHEDULED).total());
        assertEquals(pending.id(), reportService.listReports(1, 1, ReportStatus.PENDING, TriggerType.MANUAL)
                .items().get(0).id());

        // 两个条件叠加
        assertEquals(failedManualBefore + 1,
                reportService.listReports(1, 1, ReportStatus.FAILED, TriggerType.MANUAL).total());
    }

    @Test
    void list_rejectsBadPaging() {
        assertEquals("page", fieldOf(assertThrows(BusinessException.class,
                () -> reportService.listReports(0, 10, null, null))));
        assertEquals("pageSize", fieldOf(assertThrows(BusinessException.class,
                () -> reportService.listReports(1, 101, null, null))));
    }

    // ---------- getReportById ----------

    @Test
    void detail_notFound() {
        BusinessException ex = assertThrows(BusinessException.class, () -> reportService.getReportById(-1L));
        assertEquals(ErrorCode.REPORT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void detail_resultsOnlyWhenSuccess() {
        ReportRunVO created = create(START, START.plusDays(6));
        Long id = Long.valueOf(created.id());

        // PENDING：没有结果
        ReportDetailVO pending = reportService.getReportById(id);
        assertEquals(ReportStatus.PENDING, pending.status());
        assertEquals("report-" + id, pending.jobName());
        assertTrue(pending.results().isEmpty());

        // 模拟报告程序执行：RUNNING 时已经写入了结果行，但状态还没改成 SUCCESS
        reportMapper.markRunning(id);
        reportMapper.insertResults(id, List.of(result(2L, "学术报告厅", 1, 30), result(1L, "第一会议室", 2, 150)));
        ReportDetailVO running = reportService.getReportById(id);
        assertEquals(ReportStatus.RUNNING, running.status());
        assertEquals(1, running.attempt());
        assertTrue(running.results().isEmpty());       // 未成功时不返回结果

        // SUCCESS：返回结果，按场地 ID 升序
        reportMapper.markSuccess(id);
        ReportDetailVO success = reportService.getReportById(id);
        assertEquals(ReportStatus.SUCCESS, success.status());
        assertNotNull(success.startedAt());
        assertNotNull(success.finishedAt());
        assertEquals(2, success.results().size());
        assertEquals("1", success.results().get(0).venueId());
        assertEquals("第一会议室", success.results().get(0).venueName());
        assertEquals(2, success.results().get(0).reservationCount());
        assertEquals(150, success.results().get(0).totalMinutes());
        assertEquals("2", success.results().get(1).venueId());
    }

    private static ReportResult result(Long venueId, String name, int count, int minutes) {
        ReportResult r = new ReportResult();
        r.setVenueId(venueId);
        r.setVenueName(name);
        r.setReservationCount(count);
        r.setTotalMinutes(minutes);
        return r;
    }
}
