package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.dto.CreateReportRunRequest;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.job.ReportJobLauncher;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportRunWriter {
    private final ReportMapper reportMapper;

    ReportRunWriter(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }


    /**
     * 防止 @Transactional 注解在 this 内部调用时失效，使用 ReportRunWriter 来包装 ReportService 的方法
     */
    @Transactional
    public ReportRun write(CreateReportRunRequest request) {

        ReportRun run = toReportRun(request);
        int inserted = reportMapper.insertRun(run);
        if (inserted != 1) {
            throw new RuntimeException("Failed to insert report run");
        }
        // Job 名形如 report-<id>，要先插入拿到 id 才能确定，所以单独更新
        reportMapper.updateJobName(run.getId(), ReportJobLauncher.jobNameFor(run.getId()));
        return reportMapper.getRunById(run.getId());
    }

    private ReportRun toReportRun(CreateReportRunRequest request) {
        ReportRun run = new ReportRun();
        run.setTriggerType(TriggerType.MANUAL);
        run.setRangeStart(request.rangeStart());
        run.setRangeEnd(request.rangeEnd());
        run.setAttempt(0);
        return run;
    }
}
