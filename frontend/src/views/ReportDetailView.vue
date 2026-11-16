<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '../api/http'
import { getReport } from '../api/reports'
import type { ReportDetail } from '../types/report'
import { formatReportTime, isReportProcessing, reportStatusText } from '../utils/report'

const route = useRoute()
const report = ref<ReportDetail | null>(null)
const errorMessage = ref('')
const loading = ref(false)
const polling = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let controller: AbortController | undefined
let generation = 0

function stop() {
  generation++
  clearTimeout(timer)
  controller?.abort()
  polling.value = false
}

async function load(id: string, current: number) {
  loading.value = true
  controller = new AbortController()
  let again = false
  try {
    const result = await getReport(id, controller.signal)
    if (current !== generation) return
    report.value = result
    errorMessage.value = ''
    again = isReportProcessing(result.status)
  } catch (error) {
    if (current !== generation) return
    errorMessage.value = error instanceof Error ? error.message : '报告详情加载失败'
    // 查询失败不等于任务失败；404 停止重试，网络/服务故障继续查询。
    again =
      !(error instanceof ApiError && error.status === 404) &&
      (!report.value || isReportProcessing(report.value.status))
  } finally {
    if (current === generation) {
      loading.value = false
      polling.value = again
      if (again)
        timer = setTimeout(() => {
          void load(id, current)
        }, 2000)
    }
  }
}

function refresh() {
  stop()
  void load(String(route.params.id), generation)
}

watch(
  () => route.params.id,
  () => {
    report.value = null
    errorMessage.value = ''
    refresh()
  },
  { immediate: true },
)
onBeforeUnmount(stop)
</script>

<template>
  <section class="report-page">
    <div class="actions">
      <RouterLink to="/reports">返回报告列表</RouterLink
      ><button :disabled="loading" @click="refresh">刷新状态</button>
    </div>
    <h2>报告详情</h2>
    <p v-if="loading && !report" role="status">正在加载报告……</p>
    <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
    <p v-if="polling" class="muted" role="status">
      每 2 秒自动查询任务状态；查询失败不会更改任务状态。
    </p>
    <template v-if="report">
      <div class="panel">
        <h3>
          报告 {{ report.id }}
          <span :class="['status', report.status]">{{ reportStatusText[report.status] }}</span>
        </h3>
        <dl>
          <dt>统计范围</dt>
          <dd>{{ report.rangeStart }} 至 {{ report.rangeEnd }}（含首尾）</dd>
          <dt>触发方式</dt>
          <dd>{{ report.triggerType === 'MANUAL' ? '手动' : '定时' }}</dd>
          <dt>任务名称</dt>
          <dd>{{ report.jobName || '尚未创建' }}</dd>
          <dt>执行次数</dt>
          <dd>{{ report.attempt }}</dd>
          <dt>创建时间（上海）</dt>
          <dd>{{ formatReportTime(report.createdAt) }}</dd>
          <dt>开始时间（上海）</dt>
          <dd>{{ formatReportTime(report.startedAt) }}</dd>
          <dt>结束时间（上海）</dt>
          <dd>{{ formatReportTime(report.finishedAt) }}</dd>
        </dl>
        <p v-if="report.status === 'FAILED'" class="error-message" role="alert">
          失败原因：{{ report.errorMessage || '未提供失败原因' }}
        </p>
        <p v-if="isReportProcessing(report.status)">报告正在处理，请稍候。</p>
      </div>
      <template v-if="report.status === 'SUCCESS'">
        <h3>各场地统计结果</h3>
        <p class="muted">场地名称为生成时快照；累计时长以分钟为准。</p>
        <p v-if="!report.results.length">本次报告没有场地数据。</p>
        <div v-else class="table-container">
          <table>
            <thead>
              <tr>
                <th>场地编号</th>
                <th>场地名称</th>
                <th>预约次数</th>
                <th>累计时长（分钟）</th>
                <th>折合小时</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="result in report.results" :key="result.venueId">
                <td>{{ result.venueId }}</td>
                <td>{{ result.venueName }}</td>
                <td>{{ result.reservationCount }}</td>
                <td>{{ result.totalMinutes }}</td>
                <td>{{ (result.totalMinutes / 60).toFixed(2) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>
  </section>
</template>

<style src="../assets/reports.css"></style>
