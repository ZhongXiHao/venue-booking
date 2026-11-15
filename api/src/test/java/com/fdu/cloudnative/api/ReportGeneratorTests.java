package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.Reservation;
import com.fdu.cloudnative.api.entity.ReservationStatus;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import com.fdu.cloudnative.api.mapper.ReservationMapper;
import com.fdu.cloudnative.api.mapper.VenueMapper;
import com.fdu.cloudnative.api.service.ReportGenerator;
import com.fdu.cloudnative.api.service.ReportResultWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报告核心逻辑的集成测试：真实数据库，不涉及 Web 和 Kubernetes。
 *
 * 不加 @Transactional：要验证的正是 ReportResultWriter 自己的事务（结果与 SUCCESS 一起提交、
 * 出错一起回滚），测试再包一层事务就看不出来了。测试数据在 @AfterEach 里手动清理。
 */
@SpringBootTest
class ReportGeneratorTests {

    @Autowired
    private ReportGenerator reportGenerator;
    @Autowired
    private ReportResultWriter reportResultWriter;
    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private VenueMapper venueMapper;
    @Autowired
    private DataSource dataSource;

    // 用很远的日期和专用的预约人，把测试预约和库里已有的数据隔离开，也方便清理
    private static final LocalDate DAY1 = LocalDate.of(2032, 6, 1);
    private static final LocalDate DAY2 = DAY1.plusDays(1);
    private static final String BOOKER = "报告生成测试";

    private final List<Long> runIds = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // report_results 通过外键 ON DELETE CASCADE 一并删除
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM report_runs WHERE id = ?")) {
                for (Long id : runIds) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM reservations WHERE booker_name = ?")) {
                ps.setString(1, BOOKER);
                ps.executeUpdate();
            }
        }
        runIds.clear();
    }

    private Long newRun(LocalDate start, LocalDate end) {
        ReportRun run = new ReportRun();
        run.setTriggerType(TriggerType.MANUAL);
        run.setRangeStart(start);
        run.setRangeEnd(end);
        reportMapper.insertRun(run);
        runIds.add(run.getId());
        return run.getId();
    }

    private Reservation reserve(Long venueId, LocalDate date, String start, String end) {
        Reservation r = new Reservation();
        r.setVenueId(venueId);
        r.setBookerName(BOOKER);
        r.setReservationDate(date);
        r.setStartTime(LocalTime.parse(start));
        r.setEndTime(LocalTime.parse(end));
        r.setPurpose("单元测试");
        r.setStatus(ReservationStatus.ACTIVE);
        reservationMapper.insertReservation(r);
        return r;
    }

    private Map<Long, ReportResult> resultsByVenue(Long runId) {
        return reportMapper.selectResultsByRunId(runId).stream()
                .collect(Collectors.toMap(ReportResult::getVenueId, Function.identity()));
    }

    private static ReportResult row(Long venueId, String name, int count, int minutes) {
        ReportResult r = new ReportResult();
        r.setVenueId(venueId);
        r.setVenueName(name);
        r.setReservationCount(count);
        r.setTotalMinutes(minutes);
        return r;
    }

    // ---------- 正常执行 ----------

    @Test
    void pendingRun_becomesSuccessWithResults() {
        reserve(1L, DAY1, "09:00:00", "10:00:00");
        reserve(1L, DAY2, "10:00:00", "11:30:00");
        Reservation cancelled = reserve(1L, DAY1, "13:00:00", "14:00:00");
        reservationMapper.cancelReservation(cancelled.getId());
        reserve(2L, DAY2, "09:00:00", "09:30:00");
        reserve(2L, DAY2.plusDays(1), "09:00:00", "10:00:00");      // 范围外
        Long runId = newRun(DAY1, DAY2);

        reportGenerator.generateReport(runId);

        ReportRun run = reportMapper.getRunById(runId);
        assertEquals(ReportStatus.SUCCESS, run.getStatus());
        assertEquals(1, run.getAttempt());
        assertNotNull(run.getStartedAt());
        assertNotNull(run.getFinishedAt());
        assertNull(run.getErrorMessage());

        // 每个场地一行；数字与预约数据对得上
        Map<Long, ReportResult> results = resultsByVenue(runId);
        assertEquals(venueMapper.countVenues(), results.size());
        assertEquals(2, results.get(1L).getReservationCount());     // 已取消的不计入
        assertEquals(150, results.get(1L).getTotalMinutes());
        assertEquals(1, results.get(2L).getReservationCount());     // 范围外的不计入
        assertEquals(30, results.get(2L).getTotalMinutes());
        // 其余场地在这个日期范围内没有预约
        results.values().stream()
                .filter(r -> r.getVenueId() != 1L && r.getVenueId() != 2L)
                .forEach(r -> {
                    assertEquals(0, r.getReservationCount());
                    assertEquals(0, r.getTotalMinutes());
                });
    }

    // ---------- 重试 ----------

    @Test
    void failedRun_retryUpdatesSameRunAndReplacesResults() {
        Long runId = newRun(DAY1, DAY2);
        // 模拟上一次执行：算到一半失败，留下了过期的结果行
        reportMapper.markRunning(runId);
        reportMapper.insertResults(runId, List.of(row(1L, "过期数据", 99, 9999)));
        reportMapper.markFailed(runId, "上一次失败");
        reserve(1L, DAY1, "09:00:00", "10:00:00");

        reportGenerator.generateReport(runId);

        ReportRun run = reportMapper.getRunById(runId);
        assertEquals(ReportStatus.SUCCESS, run.getStatus());
        assertEquals(2, run.getAttempt());                          // 同一条记录，次数累加
        assertNull(run.getErrorMessage());                          // 上次的错误信息被清掉
        // 旧结果被整体替换，而不是叠加
        Map<Long, ReportResult> results = resultsByVenue(runId);
        assertEquals(venueMapper.countVenues(), results.size());
        assertEquals(1, results.get(1L).getReservationCount());
        assertEquals(60, results.get(1L).getTotalMinutes());
        assertNotEquals("过期数据", results.get(1L).getVenueName());
    }

    @Test
    void runLeftInRunning_canBeRerun() {
        // 上一个 Pod 执行到一半被杀掉，状态停在 RUNNING；重试必须能接着跑
        Long runId = newRun(DAY1, DAY1);
        reportMapper.markRunning(runId);

        reportGenerator.generateReport(runId);

        ReportRun run = reportMapper.getRunById(runId);
        assertEquals(ReportStatus.SUCCESS, run.getStatus());
        assertEquals(2, run.getAttempt());
    }

    @Test
    void successRun_isNotRecomputed() {
        Long runId = newRun(DAY1, DAY1);
        reportGenerator.generateReport(runId);
        ReportRun first = reportMapper.getRunById(runId);
        assertEquals(0, resultsByVenue(runId).get(1L).getReservationCount());

        // 成功之后预约数据变了，再对同一个 ID 执行一次：应当直接跳过
        reserve(1L, DAY1, "09:00:00", "10:00:00");
        reportGenerator.generateReport(runId);

        ReportRun second = reportMapper.getRunById(runId);
        assertEquals(ReportStatus.SUCCESS, second.getStatus());
        assertEquals(1, second.getAttempt());                       // 次数没有增加
        assertEquals(first.getStartedAt(), second.getStartedAt());
        assertEquals(first.getFinishedAt(), second.getFinishedAt());
        assertEquals(0, resultsByVenue(runId).get(1L).getReservationCount());   // 结果没有被重算
    }

    // ---------- 记录不存在 ----------

    @Test
    void missingRun_throwsAndWritesNothing() {
        long before = reportMapper.countRuns(null, null);
        assertThrows(IllegalArgumentException.class, () -> reportGenerator.generateReport(-1L));
        assertEquals(before, reportMapper.countRuns(null, null));
    }

    // ---------- 写结果的事务 ----------

    @Test
    void write_isAtomic_failureKeepsOldResultsAndStatus() {
        Long runId = newRun(DAY1, DAY1);
        reportMapper.markRunning(runId);
        reportMapper.insertResults(runId, List.of(row(1L, "旧结果", 5, 300)));

        // 同一个场地出现两次：删除旧结果之后，插入时撞唯一键
        List<ReportResult> broken = List.of(row(1L, "新结果", 1, 60), row(1L, "新结果", 2, 120));
        assertThrows(DuplicateKeyException.class, () -> reportResultWriter.write(runId, broken));

        // 整个事务回滚：旧结果还在（删除被撤销），状态也没有变成 SUCCESS
        List<ReportResult> remaining = reportMapper.selectResultsByRunId(runId);
        assertEquals(1, remaining.size());
        assertEquals("旧结果", remaining.get(0).getVenueName());
        assertEquals(300, remaining.get(0).getTotalMinutes());
        assertEquals(ReportStatus.RUNNING, reportMapper.getRunById(runId).getStatus());
    }

    @Test
    void write_emptyResults_stillMarksSuccess() {
        // 系统里一个场地都没有时统计结果为空：不应生成非法 SQL，仍然算成功
        Long runId = newRun(DAY1, DAY1);
        reportMapper.markRunning(runId);
        reportMapper.insertResults(runId, List.of(row(1L, "旧结果", 5, 300)));

        reportResultWriter.write(runId, List.of());

        assertTrue(reportMapper.selectResultsByRunId(runId).isEmpty());
        assertEquals(ReportStatus.SUCCESS, reportMapper.getRunById(runId).getStatus());
    }
}
