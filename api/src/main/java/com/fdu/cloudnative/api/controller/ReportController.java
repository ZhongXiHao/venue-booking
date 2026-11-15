package com.fdu.cloudnative.api.controller;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.common.Result;
import com.fdu.cloudnative.api.dto.CreateReportRunRequest;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.service.ReportService;
import com.fdu.cloudnative.api.vo.ReportDetailVO;
import com.fdu.cloudnative.api.vo.ReportRunVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 202：任务已受理，尚未完成
    @PostMapping
    public ResponseEntity<Result<ReportRunVO>> createReport(@Valid @RequestBody CreateReportRunRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.success("报告任务已创建", reportService.createReport(request)));
    }

    // status、triggerType 传了不存在的枚举值时，由全局异常处理返回 400 VALIDATION_ERROR
    @GetMapping
    public ResponseEntity<Result<PageResult<ReportRunVO>>> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) TriggerType triggerType
    ) {
        return ResponseEntity.ok(Result.success(reportService.listReports(page, pageSize, status, triggerType)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<ReportDetailVO>> getReportById(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(reportService.getReportById(id)));
    }
}
