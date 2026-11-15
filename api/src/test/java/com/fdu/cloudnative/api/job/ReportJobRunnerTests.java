package com.fdu.cloudnative.api.job;

import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import com.fdu.cloudnative.api.service.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 独立入口的单元测试：不启动 Spring，Mapper 和 Generator 都用 mock。
 * 测的是 execute()（返回退出码），run() 里的 System.exit 不在测试范围内。
 * 放在 job 包下是为了能调用包内可见的 execute() 和 buildScheduledRun()。
 */
class ReportJobRunnerTests {

    private final ReportGenerator generator = mock(ReportGenerator.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);

    private ReportJobRunner runner(String runId, String jobName, boolean demo) {
        // context 只在 run() 里用到，这里不需要
        return new ReportJobRunner(generator, reportMapper, null, runId, jobName, demo);
    }

    /** 模拟数据库生成主键 */
    private void insertAssignsId(long id) {
        doAnswer(inv -> {
            inv.getArgument(0, ReportRun.class).setId(id);
            return 1;
        }).when(reportMapper).insertRun(any());
    }

    private static ReportRun existing(long id) {
        ReportRun run = new ReportRun();
        run.setId(id);
        return run;
    }

    // ---------- 手动触发：带 REPORT_RUN_ID ----------

    @Test
    void manual_usesGivenRunIdAndCreatesNoRecord() {
        assertEquals(0, runner("42", "report-42", false).execute());

        verify(generator).generateReport(42L);
        verify(reportMapper, never()).insertRun(any());
    }

    @Test
    void manual_runIdWithSpaces_isTrimmed() {
        assertEquals(0, runner(" 42 ", "", false).execute());
        verify(generator).generateReport(42L);
    }

    @Test
    void manual_invalidRunId_exitsWithOneWithoutGenerating() {
        assertEquals(1, runner("abc", "", false).execute());

        verify(generator, never()).generateReport(anyLong());
        verify(reportMapper, never()).insertRun(any());
    }

    @Test
    void generatorFails_exitsWithOne() {
        doThrow(new IllegalStateException("boom")).when(generator).generateReport(42L);
        assertEquals(1, runner("42", "", false).execute());
    }

    // ---------- 定时触发：不带 REPORT_RUN_ID ----------

    @Test
    void scheduled_insertsRecordAndGeneratesIt() {
        insertAssignsId(7L);

        assertEquals(0, runner("", "report-cron-29000000", false).execute());

        ArgumentCaptor<ReportRun> captor = ArgumentCaptor.forClass(ReportRun.class);
        verify(reportMapper).insertRun(captor.capture());
        ReportRun run = captor.getValue();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertEquals(TriggerType.SCHEDULED, run.getTriggerType());
        assertEquals("report-cron-29000000", run.getJobName());
        // 测试恰好跨零点时 today 可能差一天，所以只断言窗口长度和 key 的形状
        assertEquals(6, run.getRangeEnd().toEpochDay() - run.getRangeStart().toEpochDay());
        assertFalse(run.getRangeEnd().isAfter(today));
        assertTrue(run.getScheduleKey().matches("SCHEDULED:\\d{8}"));
        verify(generator).generateReport(7L);
    }

    @Test
    void scheduled_blankJobName_isStoredAsNull() {
        // 回归：空串会撞 job_name 的唯一键（多个 NULL 可以并存，多个 '' 不行）
        insertAssignsId(7L);

        assertEquals(0, runner("", "", false).execute());

        ArgumentCaptor<ReportRun> captor = ArgumentCaptor.forClass(ReportRun.class);
        verify(reportMapper).insertRun(captor.capture());
        assertNull(captor.getValue().getJobName());
    }

    @Test
    void scheduled_duplicateScheduleKey_reusesExistingRecord() {
        when(reportMapper.insertRun(any())).thenThrow(new DuplicateKeyException("uk_report_schedule_key"));
        when(reportMapper.selectByScheduleKey(anyString())).thenReturn(existing(9L));

        assertEquals(0, runner("", "report-cron-1", false).execute());

        verify(generator).generateReport(9L);
        verify(reportMapper, never()).selectByJobName(anyString());
    }

    @Test
    void scheduled_duplicateJobName_reusesRecordOfSameJob() {
        // 同一个 Job 重试时已经跨到下一分钟：schedule_key 查不到，按 Job 名找回原记录
        when(reportMapper.insertRun(any())).thenThrow(new DuplicateKeyException("uk_report_job_name"));
        when(reportMapper.selectByScheduleKey(anyString())).thenReturn(null);
        when(reportMapper.selectByJobName("report-cron-1")).thenReturn(existing(11L));

        assertEquals(0, runner("", "report-cron-1", true).execute());

        verify(generator).generateReport(11L);
    }

    @Test
    void scheduled_duplicateButNothingFound_exitsWithOne() {
        when(reportMapper.insertRun(any())).thenThrow(new DuplicateKeyException("other"));

        assertEquals(1, runner("", "", false).execute());

        // 没有 Job 名时不按 Job 名查（否则会查 job_name = NULL）
        verify(reportMapper, never()).selectByJobName(any());
        verify(generator, never()).generateReport(anyLong());
    }

    // ---------- 统计窗口与 schedule_key ----------

    @Test
    void buildScheduledRun_dailyKeyAndSevenDayWindow() {
        ReportRun run = runner("", "", false).buildScheduledRun(LocalDateTime.of(2026, 11, 15, 8, 30, 59));

        assertEquals(LocalDate.of(2026, 11, 9), run.getRangeStart());    // 含当天共 7 天
        assertEquals(LocalDate.of(2026, 11, 15), run.getRangeEnd());
        assertEquals("SCHEDULED:20261115", run.getScheduleKey());
    }

    @Test
    void buildScheduledRun_demoKeyIsMinuteLevel() {
        ReportRun run = runner("", "", true).buildScheduledRun(LocalDateTime.of(2026, 11, 15, 8, 30, 59));

        assertEquals("SCHEDULED:202611150830", run.getScheduleKey());    // 秒被舍去
        assertEquals(LocalDate.of(2026, 11, 15), run.getRangeEnd());
    }

    @Test
    void buildScheduledRun_windowCrossesMonthBoundary() {
        ReportRun run = runner("", "", false).buildScheduledRun(LocalDateTime.of(2026, 12, 3, 0, 0));

        assertEquals(LocalDate.of(2026, 11, 27), run.getRangeStart());
        assertEquals("SCHEDULED:20261203", run.getScheduleKey());
    }
}
