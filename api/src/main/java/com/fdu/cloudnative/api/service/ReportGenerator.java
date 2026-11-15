package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ReportGenerator {
    private final ReportMapper reportMapper;
    private final ReportResultWriter reportResultWriter;

    public ReportGenerator(ReportMapper reportMapper, ReportResultWriter reportResultWriter) {
        this.reportMapper = reportMapper;
        this.reportResultWriter = reportResultWriter;
    }

    public void generateReport(Long runId) {
        ReportRun run = reportMapper.getRunById(runId);
        if (run == null) {
            throw new IllegalArgumentException("Report run not found for ID: " + runId);
        }
        if (run.getStatus() == ReportStatus.SUCCESS) {
            log.info("Report run {} is already SUCCESS, skipping.", runId);
            return;
        }
        reportMapper.markRunning(runId);
        log.info("Generating report: runId={}, range={}..{}, attempt={}",
                runId, run.getRangeStart(), run.getRangeEnd(), run.getAttempt() + 1);
        try {
            List<ReportResult> results = reportMapper.aggregateUsage(run.getRangeStart(), run.getRangeEnd());
            reportResultWriter.write(runId, results);
            log.info("Report generated: runId={}, rows={}", runId, results.size());
        } catch (Exception e) {
            markFailedQuietly(runId, e);
            throw e;
        }
    }

    private void markFailedQuietly(Long runId, Exception cause) {
        String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        try {
            reportMapper.markFailed(runId, reason);
        } catch (Exception markError) {
            cause.addSuppressed(markError);   // 原始异常仍然向外抛，这个附在它后面
        }
    }
}
