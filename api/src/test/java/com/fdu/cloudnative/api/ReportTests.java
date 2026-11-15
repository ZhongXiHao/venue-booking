package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.Reservation;
import com.fdu.cloudnative.api.entity.ReservationStatus;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.entity.VenueStatus;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import com.fdu.cloudnative.api.mapper.ReservationMapper;
import com.fdu.cloudnative.api.mapper.VenueMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional   // 每个测试结束后自动回滚，不会污染数据库
class ReportTests {

    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private VenueMapper venueMapper;

    // 用很远的日期，把测试预约和库里已有的预约隔离开
    private static final LocalDate DAY1 = LocalDate.of(2031, 3, 10);
    private static final LocalDate DAY2 = DAY1.plusDays(1);

    private ReportRun newRun(TriggerType type, String scheduleKey) {
        ReportRun run = new ReportRun();
        run.setTriggerType(type);
        run.setRangeStart(DAY1);
        run.setRangeEnd(DAY2);
        run.setScheduleKey(scheduleKey);
        reportMapper.insertRun(run);
        return run;
    }

    private Reservation reserve(Long venueId, LocalDate date, String start, String end) {
        Reservation r = new Reservation();
        r.setVenueId(venueId);
        r.setBookerName("报告测试");
        r.setReservationDate(date);
        r.setStartTime(LocalTime.parse(start));
        r.setEndTime(LocalTime.parse(end));
        r.setPurpose("单元测试");
        r.setStatus(ReservationStatus.ACTIVE);
        reservationMapper.insertReservation(r);
        return r;
    }

    private ReportResult result(Long venueId, String name, int count, int minutes) {
        ReportResult r = new ReportResult();
        r.setVenueId(venueId);
        r.setVenueName(name);
        r.setReservationCount(count);
        r.setTotalMinutes(minutes);
        return r;
    }

    // ---------- 运行记录 ----------

    @Test
    void insertRun_startsPendingAndCanSetJobName() {
        ReportRun run = newRun(TriggerType.MANUAL, null);
        assertNotNull(run.getId());                       // useGeneratedKeys 回填了 id

        ReportRun saved = reportMapper.getRunById(run.getId());
        assertEquals(TriggerType.MANUAL, saved.getTriggerType());
        assertEquals(DAY1, saved.getRangeStart());
        assertEquals(DAY2, saved.getRangeEnd());
        assertEquals(ReportStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttempt());
        assertNull(saved.getJobName());
        assertNull(saved.getScheduleKey());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getStartedAt());
        assertNull(saved.getFinishedAt());

        assertEquals(1, reportMapper.updateJobName(run.getId(), "report-" + run.getId()));
        assertEquals("report-" + run.getId(), reportMapper.getRunById(run.getId()).getJobName());

        assertNull(reportMapper.getRunById(-1L));         // 不存在
    }

    @Test
    void statusTransitions_failThenRetryThenSucceed() {
        Long id = newRun(TriggerType.MANUAL, null).getId();

        // 第一次执行
        assertEquals(1, reportMapper.markRunning(id));
        ReportRun running = reportMapper.getRunById(id);
        assertEquals(ReportStatus.RUNNING, running.getStatus());
        assertEquals(1, running.getAttempt());
        assertNotNull(running.getStartedAt());
        assertNull(running.getFinishedAt());

        // 失败：记下原因和结束时间
        assertEquals(1, reportMapper.markFailed(id, "数据库连接超时"));
        ReportRun failed = reportMapper.getRunById(id);
        assertEquals(ReportStatus.FAILED, failed.getStatus());
        assertEquals("数据库连接超时", failed.getErrorMessage());
        assertNotNull(failed.getFinishedAt());

        // 重试：同一条记录，attempt 加 1，上次的错误信息和结束时间被清掉
        reportMapper.markRunning(id);
        ReportRun retried = reportMapper.getRunById(id);
        assertEquals(ReportStatus.RUNNING, retried.getStatus());
        assertEquals(2, retried.getAttempt());
        assertNull(retried.getErrorMessage());
        assertNull(retried.getFinishedAt());

        // 成功
        assertEquals(1, reportMapper.markSuccess(id));
        ReportRun done = reportMapper.getRunById(id);
        assertEquals(ReportStatus.SUCCESS, done.getStatus());
        assertNotNull(done.getFinishedAt());
        assertNull(done.getErrorMessage());

        // 不存在的记录：影响行数为 0
        assertEquals(0, reportMapper.markRunning(-1L));
    }

    @Test
    void markFailed_truncatesLongErrorMessage() {
        Long id = newRun(TriggerType.MANUAL, null).getId();
        reportMapper.markFailed(id, "x".repeat(1500));    // 列宽 1000
        assertEquals(1000, reportMapper.getRunById(id).getErrorMessage().length());
    }

    @Test
    void scheduleKey_isUnique_butManualRunsAreNotAffected() {
        newRun(TriggerType.SCHEDULED, "SCHEDULED:20310310");
        // 同一计划时刻的第二次插入撞唯一键
        assertThrows(DuplicateKeyException.class,
                () -> newRun(TriggerType.SCHEDULED, "SCHEDULED:20310310"));
        // 不同周期可以插入；手动任务 schedule_key 为 NULL，多条互不冲突
        newRun(TriggerType.SCHEDULED, "SCHEDULED:20310311");
        newRun(TriggerType.MANUAL, null);
        newRun(TriggerType.MANUAL, null);
    }

    @Test
    void selectRunPageAndCount_filtersOrderAndPaging() {
        // 库里可能已有别的记录，所以数量都和插入前的基线比较
        long allBefore = reportMapper.countRuns(null, null);
        long manualBefore = reportMapper.countRuns(null, "MANUAL");
        long failedBefore = reportMapper.countRuns("FAILED", null);
        long scheduledFailedBefore = reportMapper.countRuns("FAILED", "SCHEDULED");

        ReportRun r1 = newRun(TriggerType.MANUAL, null);
        ReportRun r2 = newRun(TriggerType.SCHEDULED, "SCHEDULED:test-page");
        ReportRun r3 = newRun(TriggerType.MANUAL, null);
        reportMapper.markFailed(r2.getId(), "boom");

        assertEquals(allBefore + 3, reportMapper.countRuns(null, null));
        assertEquals(manualBefore + 2, reportMapper.countRuns(null, "MANUAL"));
        assertEquals(failedBefore + 1, reportMapper.countRuns("FAILED", null));
        assertEquals(scheduledFailedBefore + 1, reportMapper.countRuns("FAILED", "SCHEDULED"));
        // 空字符串等同于不筛选
        assertEquals(allBefore + 3, reportMapper.countRuns("", ""));

        // 倒序：刚插入的三条最新，后插入的在前
        List<Long> newest = reportMapper.selectRunPage(null, null, 0, 3)
                .stream().map(ReportRun::getId).toList();
        assertEquals(List.of(r3.getId(), r2.getId(), r1.getId()), newest);

        // 分页：每页 1 条，第 2 页是倒序后的第 2 条
        List<ReportRun> page2 = reportMapper.selectRunPage(null, null, 1, 1);
        assertEquals(1, page2.size());
        assertEquals(r2.getId(), page2.get(0).getId());

        // 筛选后的列表
        assertEquals(r2.getId(), reportMapper.selectRunPage("FAILED", "SCHEDULED", 0, 1).get(0).getId());
        assertEquals(r3.getId(), reportMapper.selectRunPage("PENDING", "MANUAL", 0, 1).get(0).getId());
    }

    // ---------- 统计 ----------

    @Test
    void aggregateUsage_countsAndMinutes() {
        // 一个没有任何预约的停用场地：也必须出现在结果里
        Venue disabled = new Venue();
        disabled.setName("报告测试-停用场地");
        disabled.setLocation("测试楼");
        disabled.setCapacity(10);
        disabled.setOpenTime(LocalTime.parse("08:00:00"));
        disabled.setCloseTime(LocalTime.parse("22:00:00"));
        disabled.setStatus(VenueStatus.DISABLED);
        venueMapper.insertVenue(disabled);

        // 场地 1：范围内 2 条有效（60 + 90 分钟），1 条已取消，1 条在范围外
        reserve(1L, DAY1, "09:00:00", "10:00:00");
        reserve(1L, DAY1, "10:00:00", "11:30:00");
        Reservation cancelled = reserve(1L, DAY1, "13:00:00", "14:00:00");
        reservationMapper.cancelReservation(cancelled.getId());
        reserve(1L, DAY2.plusDays(1), "09:00:00", "10:00:00");     // 结束日期的后一天
        reserve(1L, DAY1.minusDays(1), "09:00:00", "10:00:00");    // 起始日期的前一天
        // 场地 2：只有结束日期当天的一条（30 分钟），验证范围含尾
        reserve(2L, DAY2, "09:00:00", "09:30:00");

        List<ReportResult> rows = reportMapper.aggregateUsage(DAY1, DAY2);

        // 每个场地恰好一行，按场地 id 升序
        assertEquals(venueMapper.countVenues(), rows.size());
        List<Long> ids = rows.stream().map(ReportResult::getVenueId).toList();
        assertEquals(ids.stream().sorted().toList(), ids);

        Map<Long, ReportResult> byVenue = rows.stream()
                .collect(Collectors.toMap(ReportResult::getVenueId, Function.identity()));

        ReportResult v1 = byVenue.get(1L);
        assertEquals("第一会议室", v1.getVenueName());               // 取决于种子数据
        assertEquals(2, v1.getReservationCount());                  // 已取消和范围外的不计入
        assertEquals(150, v1.getTotalMinutes());

        ReportResult v2 = byVenue.get(2L);
        assertEquals(1, v2.getReservationCount());
        assertEquals(30, v2.getTotalMinutes());

        ReportResult none = byVenue.get(disabled.getId());
        assertNotNull(none);                                        // 没有预约的场地也有一行
        assertEquals("报告测试-停用场地", none.getVenueName());
        assertEquals(0, none.getReservationCount());
        assertEquals(0, none.getTotalMinutes());

        // 单日范围：起止为同一天，只算 DAY1
        Map<Long, ReportResult> day1Only = reportMapper.aggregateUsage(DAY1, DAY1).stream()
                .collect(Collectors.toMap(ReportResult::getVenueId, Function.identity()));
        assertEquals(2, day1Only.get(1L).getReservationCount());
        assertEquals(0, day1Only.get(2L).getReservationCount());
    }

    // ---------- 结果 ----------

    @Test
    void results_insertSelectAndReplace() {
        Long runId = newRun(TriggerType.MANUAL, null).getId();
        Long otherRunId = newRun(TriggerType.MANUAL, null).getId();

        assertEquals(2, reportMapper.insertResults(runId, List.of(
                result(2L, "学术报告厅", 1, 30),
                result(1L, "第一会议室", 2, 150))));
        reportMapper.insertResults(otherRunId, List.of(result(1L, "第一会议室", 9, 900)));

        // 按场地 id 升序，且只返回本次运行的结果
        List<ReportResult> saved = reportMapper.selectResultsByRunId(runId);
        assertEquals(2, saved.size());
        assertEquals(1L, saved.get(0).getVenueId());
        assertEquals(runId, saved.get(0).getRunId());
        assertEquals("第一会议室", saved.get(0).getVenueName());
        assertEquals(2, saved.get(0).getReservationCount());
        assertEquals(150, saved.get(0).getTotalMinutes());
        assertEquals(2L, saved.get(1).getVenueId());

        // 同一次运行里同一场地不能有两行
        assertThrows(DuplicateKeyException.class,
                () -> reportMapper.insertResults(runId, List.of(result(1L, "第一会议室", 0, 0))));

        // 重试时"先清除再写入"：旧结果被替换，别的运行不受影响
        assertEquals(2, reportMapper.deleteResultsByRunId(runId));
        reportMapper.insertResults(runId, List.of(result(1L, "第一会议室", 3, 180)));
        List<ReportResult> replaced = reportMapper.selectResultsByRunId(runId);
        assertEquals(1, replaced.size());
        assertEquals(180, replaced.get(0).getTotalMinutes());
        assertEquals(1, reportMapper.selectResultsByRunId(otherRunId).size());
    }

    @Test
    void scheduledRuns_lookupByKeyAndJobName_nullJobNamesCoexist() {
        // 两条都没有 Job 名的定时记录可以并存（唯一键允许多个 NULL）
        ReportRun first = newRun(TriggerType.SCHEDULED, "SCHEDULED:TEST-LOOKUP-1");
        ReportRun second = newRun(TriggerType.SCHEDULED, "SCHEDULED:TEST-LOOKUP-2");
        assertNull(reportMapper.getRunById(first.getId()).getJobName());

        assertEquals(second.getId(), reportMapper.selectByScheduleKey("SCHEDULED:TEST-LOOKUP-2").getId());
        assertNull(reportMapper.selectByScheduleKey("SCHEDULED:TEST-NOT-EXIST"));

        reportMapper.updateJobName(first.getId(), "report-cron-test-lookup");
        assertEquals(first.getId(), reportMapper.selectByJobName("report-cron-test-lookup").getId());
        assertNull(reportMapper.selectByJobName("report-cron-test-none"));

        // Job 名重复会撞唯一键：同一个 Job 重试时入口靠它找回原记录
        assertThrows(DuplicateKeyException.class,
                () -> reportMapper.updateJobName(second.getId(), "report-cron-test-lookup"));
    }

    @Test
    void aggregateThenStore_roundTrip() {
        // 统计出来的行可以直接交给 insertResults 保存
        reserve(1L, DAY1, "09:00:00", "10:00:00");
        Long runId = newRun(TriggerType.MANUAL, null).getId();

        List<ReportResult> rows = reportMapper.aggregateUsage(DAY1, DAY2);
        assertEquals(rows.size(), reportMapper.insertResults(runId, rows));

        List<ReportResult> saved = reportMapper.selectResultsByRunId(runId);
        assertEquals(rows.size(), saved.size());
        assertEquals(60, saved.stream().filter(r -> r.getVenueId() == 1L).findFirst().orElseThrow().getTotalMinutes());
    }
}
