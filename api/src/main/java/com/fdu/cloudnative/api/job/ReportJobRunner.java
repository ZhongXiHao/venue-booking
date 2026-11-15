package com.fdu.cloudnative.api.job;

import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.TriggerType;
import com.fdu.cloudnative.api.mapper.ReportMapper;
import com.fdu.cloudnative.api.service.ReportGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 报告任务的独立入口：只在 report profile 下生效，跑完一次就退出。
 * 成功退出码为 0，失败为 1，Kubernetes 据此判断 Job 是否成功、是否重试。
 *
 * 配置都来自环境变量（Spring 把 report.run-id 对应到 REPORT_RUN_ID，其余同理）：
 *   REPORT_RUN_ID         手动触发时由 API 创建 Job 时传入；没有则按定时触发处理
 *   REPORT_JOB_NAME       运行本任务的 Job 名，定时触发时写进记录
 *   REPORT_SCHEDULE_DEMO  true 时 schedule_key 精确到分钟，配合演示用的分钟级 CronJob
 */
@Component
@Profile("report")
@Slf4j
public class ReportJobRunner implements ApplicationRunner {
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MINUTE_KEY = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ReportGenerator generator;
    private final ReportMapper reportMapper;
    private final ConfigurableApplicationContext context;
    private final String runIdText;
    private final String jobName;
    private final boolean demo;

    public ReportJobRunner(ReportGenerator generator,
                           ReportMapper reportMapper,
                           ConfigurableApplicationContext context,
                           @Value("${report.run-id:}") String runIdText,
                           @Value("${report.job-name:}") String jobName,
                           @Value("${report.schedule.demo:false}") boolean demo) {
        this.generator = generator;
        this.reportMapper = reportMapper;
        this.context = context;
        this.runIdText = runIdText;
        // 没配置时注入的是空串。job_name 有唯一键，多个 NULL 可以并存，多个 '' 不行，所以统一转成 null
        this.jobName = StringUtils.hasText(jobName) ? jobName.trim() : null;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute();
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** 执行一次报告任务，返回进程退出码。和 System.exit 分开，方便单元测试。 */
    int execute() {
        Long runId = null;
        try {
            runId = resolveRunId();
            generator.generateReport(runId);
            return 0;
        } catch (Exception e) {
            log.error("Report generation failed: runId={}", runId, e);
            return 1;
        }
    }

    /** 手动触发：用传进来的 ID；定时触发：自己建 SCHEDULED 记录，已存在就复用。 */
    private Long resolveRunId() {
        if (StringUtils.hasText(runIdText)) {
            return Long.valueOf(runIdText.trim());
        }
        ReportRun run = buildScheduledRun(LocalDateTime.now(BIZ_ZONE));
        try {
            reportMapper.insertRun(run);
            return run.getId();
        } catch (DuplicateKeyException e) {
            ReportRun existing = findExisting(run);
            if (existing == null) {
                throw e;   // 不是这两个唯一键引起的，照常抛出
            }
            log.info("Reusing existing report run: runId={}, scheduleKey={}, jobName={}",
                    existing.getId(), existing.getScheduleKey(), existing.getJobName());
            return existing.getId();
        }
    }

    /**
     * 撞唯一键有两种情况：
     * 1. schedule_key 相同：同一个计划时刻已经有人建过记录；
     * 2. job_name 相同：同一个 Job 失败后重试，新 Pod 启动时已经跨到下一分钟（或下一天），
     *    schedule_key 变了但 Job 名没变，应该接着用原来那条记录。
     */
    private ReportRun findExisting(ReportRun run) {
        ReportRun existing = reportMapper.selectByScheduleKey(run.getScheduleKey());
        if (existing == null && run.getJobName() != null) {
            existing = reportMapper.selectByJobName(run.getJobName());
        }
        return existing;
    }

    /** 统计窗口：最近 7 天（含当天）；schedule_key：SCHEDULED: 加上取整到天（演示时到分钟）的启动时刻。 */
    ReportRun buildScheduledRun(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        ReportRun run = new ReportRun();
        run.setTriggerType(TriggerType.SCHEDULED);
        run.setRangeStart(today.minusDays(6));
        run.setRangeEnd(today);
        run.setScheduleKey("SCHEDULED:" + now.format(demo ? MINUTE_KEY : DAY_KEY));
        run.setJobName(jobName);
        return run;
    }
}
