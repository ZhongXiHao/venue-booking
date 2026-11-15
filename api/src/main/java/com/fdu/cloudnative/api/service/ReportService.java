package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.dto.CreateReportRunRequest;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.vo.ReportDetailVO;
import com.fdu.cloudnative.api.vo.ReportRunVO;

public interface ReportService {
    // 范围不合法（起始晚于结束、跨度超过 366 天）抛 400 VALIDATION_ERROR；
    // 记录已写入但创建 Job 失败时，把记录改为 FAILED 并抛 503 REPORT_JOB_CREATE_FAILED
    ReportRunVO createReport(CreateReportRunRequest request);

    // status、triggerType 为可选筛选条件；按创建时间倒序；page、pageSize 不合法时抛 400 VALIDATION_ERROR
    PageResult<ReportRunVO> listReports(int page, int pageSize, ReportStatus status, TriggerType triggerType);

    // 记录不存在时抛 404 REPORT_NOT_FOUND；状态不是 SUCCESS 时 results 为空数组
    ReportDetailVO getReportById(Long id);
}
