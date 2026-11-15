package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportResultWriter {
    private final ReportMapper reportMapper;

    ReportResultWriter(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @Transactional
    public void write(Long runId, List<ReportResult> results) {
        reportMapper.deleteResultsByRunId(runId);
        if (!results.isEmpty()) {
            reportMapper.insertResults(runId, results);
        }
        reportMapper.markSuccess(runId);

    }
}
