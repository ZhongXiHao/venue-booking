package com.fdu.cloudnative.api.service.impl;

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
import com.fdu.cloudnative.api.service.ReportRunWriter;
import com.fdu.cloudnative.api.service.ReportService;
import com.fdu.cloudnative.api.vo.ReportDetailVO;
import com.fdu.cloudnative.api.vo.ReportRunVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReportServiceImpl implements ReportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportJobLauncher reportJobLauncher;
    private final ReportMapper reportMapper;
    private final ReportRunWriter reportRunWriter;

    public ReportServiceImpl(ReportJobLauncher reportJobLauncher, ReportMapper reportMapper, ReportRunWriter reportRunWriter) {
        this.reportJobLauncher = reportJobLauncher;
        this.reportMapper = reportMapper;
        this.reportRunWriter = reportRunWriter;
    }

    @Override
    public ReportRunVO createReport(CreateReportRunRequest request) {
        LocalDate rangeStart = request.rangeStart();
        LocalDate rangeEnd = request.rangeEnd();
        if (rangeEnd.isBefore(rangeStart)) {
            throw BusinessException.validation("rangeEnd", "报告结束日期不能早于开始日期");
        }
        // 报告跨度不能超过 366 天
        if (rangeEnd.toEpochDay() - rangeStart.toEpochDay() > 365) {
            throw BusinessException.validation("rangeEnd", "报告跨度不能超过 366 天");
        }
        ReportRun written = reportRunWriter.write(request);
        try {
            reportJobLauncher.launch(written.getId(), written.getJobName());
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            reportMapper.markFailed(written.getId(), reason);
            logger.warn("创建报告 Job 失败，runId={}", written.getId(), e);
            throw new BusinessException(ErrorCode.REPORT_JOB_CREATE_FAILED);
        }
        return ReportRunVO.from(written);
    }

    @Override
    public PageResult<ReportRunVO> listReports(int page, int pageSize, ReportStatus status, TriggerType triggerType) {
        PageResult.checkParams(page, pageSize);
        // 两个筛选条件都是可选的，没传时为 null
        String statusName = status == null ? null : status.name();
        String triggerTypeName = triggerType == null ? null : triggerType.name();
        long total = reportMapper.countRuns(statusName, triggerTypeName);
        long offset = (long) (page - 1) * pageSize;
        List<ReportRun> reportRuns = reportMapper.selectRunPage(statusName, triggerTypeName, offset, pageSize);
        return new PageResult<>(reportRuns.stream().map(ReportRunVO::from).toList(), total, page, pageSize);
    }

    @Override
    public ReportDetailVO getReportById(Long id) {
        ReportRun reportRun = reportMapper.getRunById(id);
        if (reportRun == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        List<ReportResult> reportResults = reportMapper.selectResultsByRunId(id);
        return ReportDetailVO.from(reportRun, reportResults);
    }
}
