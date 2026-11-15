package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.controller.ReportController;
import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.exception.BusinessException;
import com.fdu.cloudnative.api.exception.ErrorCode;
import com.fdu.cloudnative.api.service.ReportService;
import com.fdu.cloudnative.api.vo.ReportDetailVO;
import com.fdu.cloudnative.api.vo.ReportRunVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只加载 Web 层，ReportService 用 mock 替代，不需要数据库。
 * 验证的是控制器和 VO 的输出是否符合契约第 9 节；业务规则在服务层自己的测试里验证。
 * MockMvc 不带 context-path，所以路径是 /reports 而不是 /api/reports。
 */
@WebMvcTest(ReportController.class)
class ReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    private static ReportRun run(ReportStatus status) {
        ReportRun run = new ReportRun();
        run.setId(12L);
        run.setTriggerType(TriggerType.MANUAL);
        run.setRangeStart(LocalDate.of(2026, 11, 1));
        run.setRangeEnd(LocalDate.of(2026, 11, 14));
        run.setStatus(status);
        run.setJobName("report-12");
        run.setAttempt(status == ReportStatus.PENDING ? 0 : 1);
        run.setCreatedAt(LocalDateTime.of(2026, 11, 15, 8, 30, 0));
        if (status != ReportStatus.PENDING) {
            run.setStartedAt(LocalDateTime.of(2026, 11, 15, 8, 30, 4));
        }
        if (status == ReportStatus.SUCCESS || status == ReportStatus.FAILED) {
            run.setFinishedAt(LocalDateTime.of(2026, 11, 15, 8, 30, 6));
        }
        return run;
    }

    private static ReportResult result(long venueId, String name, int count, int minutes) {
        ReportResult r = new ReportResult();
        r.setVenueId(venueId);
        r.setVenueName(name);
        r.setReservationCount(count);
        r.setTotalMinutes(minutes);
        return r;
    }

    // ---------- POST /reports ----------

    @Test
    void create_returns202WithContractShape() throws Exception {
        when(reportService.createReport(any())).thenReturn(ReportRunVO.from(run(ReportStatus.PENDING)));

        mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rangeStart\":\"2026-11-01\",\"rangeEnd\":\"2026-11-14\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("报告任务已创建"))
                .andExpect(jsonPath("$.data.id").value("12"))                      // ID 是字符串
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.data.rangeStart").value("2026-11-01"))
                .andExpect(jsonPath("$.data.rangeEnd").value("2026-11-14"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.jobName").value("report-12"))
                .andExpect(jsonPath("$.data.attempt").value(0))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-15T08:30:00Z")) // UTC，带 Z
                // 值为 null 的字段也要出现在 JSON 里，而不是被省略
                .andExpect(jsonPath("$.data.errorMessage").value((Object) null))
                .andExpect(jsonPath("$.data.startedAt").value((Object) null))
                .andExpect(jsonPath("$.data.finishedAt").value((Object) null))
                .andExpect(jsonPath("$.data.results").doesNotExist());              // 创建的返回不含 results

        // 请求体被正确解析成日期后交给服务层
        verify(reportService).createReport(new com.fdu.cloudnative.api.dto.CreateReportRunRequest(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14)));
    }

    @Test
    void create_missingField_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rangeStart\":\"2026-11-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("rangeEnd"));
        verifyNoInteractions(reportService);
    }

    @Test
    void create_badDate_returns400PointingAtField() throws Exception {
        // 格式不对，以及格式对但日期不存在（严格模式下 2 月 30 日不会被悄悄改成 2 月 28 日）
        for (String bad : List.of("2026/11/01", "2026-02-30", "20261101")) {
            mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rangeStart\":\"" + bad + "\",\"rangeEnd\":\"2026-11-14\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.fieldErrors[0].field").value("rangeStart"));
        }
        verifyNoInteractions(reportService);
    }

    @Test
    void create_jobCreateFailed_returns503WithNullData() throws Exception {
        when(reportService.createReport(any()))
                .thenThrow(new BusinessException(ErrorCode.REPORT_JOB_CREATE_FAILED));

        mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rangeStart\":\"2026-11-01\",\"rangeEnd\":\"2026-11-14\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REPORT_JOB_CREATE_FAILED"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    // ---------- GET /reports ----------

    @Test
    void list_usesDefaultsAndReturnsPage() throws Exception {
        when(reportService.listReports(anyInt(), anyInt(), isNull(), isNull())).thenReturn(
                new PageResult<>(List.of(ReportRunVO.from(run(ReportStatus.SUCCESS))), 1, 1, 10));

        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value("12"))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].startedAt").value("2026-11-15T08:30:04Z"))
                .andExpect(jsonPath("$.data.items[0].finishedAt").value("2026-11-15T08:30:06Z"))
                .andExpect(jsonPath("$.data.items[0].results").doesNotExist());     // 列表项不含 results

        verify(reportService).listReports(1, 10, null, null);                       // 默认 page=1、pageSize=10
    }

    @Test
    void list_passesFiltersToService() throws Exception {
        when(reportService.listReports(anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 2, 5));

        mockMvc.perform(get("/reports?page=2&pageSize=5&status=FAILED&triggerType=SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        verify(reportService).listReports(2, 5, ReportStatus.FAILED, TriggerType.SCHEDULED);
    }

    @Test
    void list_invalidEnumOrNumber_returns400() throws Exception {
        mockMvc.perform(get("/reports?status=DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("status"));
        mockMvc.perform(get("/reports?triggerType=AUTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("triggerType"));
        mockMvc.perform(get("/reports?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("page"));
        verifyNoInteractions(reportService);
    }

    // ---------- GET /reports/{id} ----------

    @Test
    void detail_success_hasFlatResults() throws Exception {
        when(reportService.getReportById(12L)).thenReturn(ReportDetailVO.from(
                run(ReportStatus.SUCCESS),
                List.of(result(1, "第一会议室", 6, 540), result(5, "旧礼堂", 0, 0))));

        mockMvc.perform(get("/reports/12"))
                .andExpect(status().isOk())
                // results 与其他字段平铺在同一层
                .andExpect(jsonPath("$.data.id").value("12"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.attempt").value(1))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-15T08:30:00Z"))
                .andExpect(jsonPath("$.data.results", hasSize(2)))
                .andExpect(jsonPath("$.data.results[0].venueId").value("1"))        // 场地 ID 也是字符串
                .andExpect(jsonPath("$.data.results[0].venueName").value("第一会议室"))
                .andExpect(jsonPath("$.data.results[0].reservationCount").value(6))
                .andExpect(jsonPath("$.data.results[0].totalMinutes").value(540))
                .andExpect(jsonPath("$.data.results[1].reservationCount").value(0));
    }

    @Test
    void detail_notSuccess_resultsIsEmptyArray() throws Exception {
        // 重试途中库里可能还留着上一次的结果行：状态不是 SUCCESS 就不返回
        when(reportService.getReportById(12L)).thenReturn(ReportDetailVO.from(
                run(ReportStatus.RUNNING), List.of(result(1, "第一会议室", 6, 540))));

        mockMvc.perform(get("/reports/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.results").isArray())
                .andExpect(jsonPath("$.data.results", hasSize(0)));
    }

    @Test
    void detail_notFoundAndBadId() throws Exception {
        when(reportService.getReportById(99L)).thenThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(get("/reports/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        mockMvc.perform(get("/reports/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
