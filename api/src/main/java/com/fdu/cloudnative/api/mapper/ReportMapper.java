package com.fdu.cloudnative.api.mapper;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReportMapper {

    // ---------- 运行记录 report_runs ----------

    // 状态固定从 PENDING 开始；created_at 显式写 UTC，不依赖 MySQL 会话时区。
    // schedule_key 重复时会抛 DuplicateKeyException，定时任务靠它防重复。
    @Insert("INSERT INTO report_runs (trigger_type, range_start, range_end, status, job_name, schedule_key, created_at) " +
            "VALUES (#{triggerType}, #{rangeStart}, #{rangeEnd}, 'PENDING', #{jobName}, #{scheduleKey}, UTC_TIMESTAMP())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRun(ReportRun run);

    // Job 名形如 report-<id>，要先插入拿到 id 才能确定，所以单独更新
    @Update("UPDATE report_runs SET job_name = #{jobName} WHERE id = #{id}")
    int updateJobName(@Param("id") Long id, @Param("jobName") String jobName);

    @Select("SELECT * FROM report_runs WHERE id = #{id}")
    ReportRun getRunById(@Param("id") Long id);

    List<ReportRun> selectRunPage(@Param("status") String status,
                                  @Param("triggerType") String triggerType,
                                  @Param("offset") long offset,
                                  @Param("pageSize") int pageSize);

    long countRuns(@Param("status") String status,
                   @Param("triggerType") String triggerType);

    // 开始执行（含重试）：attempt 加 1，并清掉上一次失败留下的结束时间和错误信息
    @Update("UPDATE report_runs SET status = 'RUNNING', attempt = attempt + 1, " +
            "started_at = UTC_TIMESTAMP(), finished_at = NULL, error_message = NULL WHERE id = #{id}")
    int markRunning(@Param("id") Long id);

    @Update("UPDATE report_runs SET status = 'SUCCESS', finished_at = UTC_TIMESTAMP(), error_message = NULL " +
            "WHERE id = #{id}")
    int markSuccess(@Param("id") Long id);

    // 列宽 1000，超长的错误信息在这里截断，避免"写失败状态"本身再失败
    @Update("UPDATE report_runs SET status = 'FAILED', finished_at = UTC_TIMESTAMP(), " +
            "error_message = LEFT(#{errorMessage}, 1000) WHERE id = #{id}")
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    // ---------- 统计结果 report_results ----------

    @Delete("DELETE FROM report_results WHERE run_id = #{runId}")
    int deleteResultsByRunId(@Param("runId") Long runId);

    /**
     * 批量写入结果行。results 不能为空（空列表会生成非法 SQL），调用方先判断。
     */
    int insertResults(@Param("runId") Long runId, @Param("results") List<ReportResult> results);

    @Select("SELECT * FROM report_results WHERE run_id = #{runId} ORDER BY venue_id")
    List<ReportResult> selectResultsByRunId(@Param("runId") Long runId);

    // ---------- 统计 ----------

    /**
     * 统计 [rangeStart, rangeEnd]（含首尾）内各场地的预约次数和累计分钟数。
     * 只统计 ACTIVE 预约；所有场地都会出现，没有预约的两项为 0。
     * 返回的 ReportResult 只填了 venueId、venueName、reservationCount、totalMinutes。
     */
    List<ReportResult> aggregateUsage(@Param("rangeStart") LocalDate rangeStart,
                                      @Param("rangeEnd") LocalDate rangeEnd);
}
