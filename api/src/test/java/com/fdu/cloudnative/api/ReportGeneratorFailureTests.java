package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import com.fdu.cloudnative.api.service.ReportGenerator;
import com.fdu.cloudnative.api.service.ReportResultWriter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 失败路径的单元测试：Mapper 和 Writer 都用 mock，不启动 Spring，也不需要数据库。
 * 真实数据库里很难稳定地制造"统计到一半出错"，所以这部分用 mock 注入异常。
 */
class ReportGeneratorFailureTests {

    private static final Long RUN_ID = 7L;
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 14);

    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final ReportResultWriter reportResultWriter = mock(ReportResultWriter.class);
    private final ReportGenerator reportGenerator = new ReportGenerator(reportMapper, reportResultWriter);

    private void givenRun(ReportStatus status) {
        ReportRun run = new ReportRun();
        run.setId(RUN_ID);
        run.setRangeStart(START);
        run.setRangeEnd(END);
        run.setStatus(status);
        run.setAttempt(0);
        when(reportMapper.getRunById(RUN_ID)).thenReturn(run);
    }

    @Test
    void happyPath_callsStepsInOrder() {
        givenRun(ReportStatus.PENDING);
        List<ReportResult> rows = List.of(new ReportResult());
        when(reportMapper.aggregateUsage(START, END)).thenReturn(rows);

        reportGenerator.generateReport(RUN_ID);

        // 先改 RUNNING，再按记录里的范围统计，最后把统计结果原样交给 Writer
        InOrder order = inOrder(reportMapper, reportResultWriter);
        order.verify(reportMapper).markRunning(RUN_ID);
        order.verify(reportMapper).aggregateUsage(START, END);
        order.verify(reportResultWriter).write(RUN_ID, rows);
        verify(reportMapper, never()).markFailed(anyLong(), any());
    }

    @Test
    void aggregateFails_marksFailedWithReasonAndRethrows() {
        givenRun(ReportStatus.PENDING);
        RuntimeException boom = new IllegalStateException("connection reset");
        when(reportMapper.aggregateUsage(START, END)).thenThrow(boom);

        // 原异常原样向外抛，入口据此以非 0 退出码结束
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> reportGenerator.generateReport(RUN_ID));
        assertSame(boom, thrown);

        verify(reportMapper).markFailed(RUN_ID, "connection reset");
        verify(reportResultWriter, never()).write(anyLong(), any());
    }

    @Test
    void writeFails_marksFailedAndRethrows() {
        givenRun(ReportStatus.FAILED);                // 重试中的记录同样适用
        when(reportMapper.aggregateUsage(START, END)).thenReturn(List.of());
        RuntimeException boom = new IllegalStateException("deadlock");
        doThrow(boom).when(reportResultWriter).write(anyLong(), any());

        assertSame(boom, assertThrows(RuntimeException.class, () -> reportGenerator.generateReport(RUN_ID)));
        verify(reportMapper).markFailed(RUN_ID, "deadlock");
    }

    @Test
    void failureWithoutMessage_usesExceptionClassName() {
        givenRun(ReportStatus.PENDING);
        when(reportMapper.aggregateUsage(START, END)).thenThrow(new NullPointerException());

        assertThrows(NullPointerException.class, () -> reportGenerator.generateReport(RUN_ID));
        verify(reportMapper).markFailed(RUN_ID, "NullPointerException");
    }

    @Test
    void markFailedAlsoFails_originalExceptionSurvives() {
        // 数据库整个断开时，"写入失败状态"这一步自己也会失败：不能让它盖掉最初的原因
        givenRun(ReportStatus.PENDING);
        RuntimeException original = new IllegalStateException("aggregate failed");
        RuntimeException markError = new IllegalStateException("cannot write FAILED");
        when(reportMapper.aggregateUsage(START, END)).thenThrow(original);
        when(reportMapper.markFailed(anyLong(), anyString())).thenThrow(markError);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> reportGenerator.generateReport(RUN_ID));
        assertSame(original, thrown);
        assertArrayEquals(new Throwable[]{markError}, thrown.getSuppressed());
    }

    @Test
    void missingRun_throwsBeforeTouchingAnything() {
        when(reportMapper.getRunById(RUN_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> reportGenerator.generateReport(RUN_ID));
        // 没有记录可以标记：既不改 RUNNING，也不写 FAILED
        verify(reportMapper, never()).markRunning(anyLong());
        verify(reportMapper, never()).markFailed(anyLong(), any());
    }

    @Test
    void successRun_isSkipped() {
        givenRun(ReportStatus.SUCCESS);

        reportGenerator.generateReport(RUN_ID);

        verify(reportMapper, never()).markRunning(anyLong());
        verify(reportMapper, never()).aggregateUsage(any(), any());
        verify(reportResultWriter, never()).write(anyLong(), any());
    }
}
