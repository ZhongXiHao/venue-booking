<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError, parseApiError } from '../api/http'
import { createReport, getReports } from '../api/reports'
import type { ReportRun, ReportStatus, TriggerType } from '../types/report'
import {
  formatReportTime,
  recentReportRange,
  reportStatusText,
  validateReportRange,
} from '../utils/report'

const router = useRouter()
const range = reactive(recentReportRange())
const fields = ref<Record<string, string>>({})
const submitting = ref(false)
const submitError = ref('')
const filters = reactive<{ status: ReportStatus | ''; triggerType: TriggerType | '' }>({
  status: '',
  triggerType: '',
})
const applied = ref({ ...filters })
const rows = ref<ReportRun[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const listError = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 10)))

async function load(targetPage = page.value, conditions = applied.value) {
  if (loading.value) return
  loading.value = true
  listError.value = ''
  const snapshot = { ...conditions }
  try {
    const result = await getReports({ ...snapshot, page: targetPage, pageSize: 10 })
    rows.value = result.items
    page.value = result.page
    total.value = result.total
    applied.value = snapshot
  } catch (error) {
    listError.value = error instanceof Error ? error.message : '报告列表加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value) return
  submitError.value = ''
  fields.value = validateReportRange(range)
  if (Object.keys(fields.value).length) return
  submitting.value = true
  try {
    const report = await createReport({ ...range })
    await router.push({ name: 'report-detail', params: { id: report.id } })
  } catch (error) {
    const parsed = parseApiError(error, '报告创建失败', ['rangeStart', 'rangeEnd'])
    fields.value = parsed.fieldErrors
    submitError.value = parsed.message
    if (error instanceof ApiError && error.code === 'REPORT_JOB_CREATE_FAILED') {
      submitError.value += '；失败记录已保存，请在报告列表查看。'
      await load(1, { status: '', triggerType: '' })
    } else if (
      error instanceof ApiError &&
      (error.status === 0 || error.status >= 500 || error.code === 'INVALID_RESPONSE')
    ) {
      submitError.value += '；暂时无法确认是否已创建，请刷新列表核实后再提交。'
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="report-page">
    <h2>场地利用率报告</h2>
    <p class="muted">统计有效预约次数与累计时长，包含未来预约、停用场地及零预约场地。</p>
    <form class="panel" @submit.prevent="submit">
      <h3>生成报告</h3>
      <fieldset :disabled="submitting">
        <div class="fields">
          <div>
            <label for="range-start">起始日期</label
            ><input
              id="range-start"
              v-model="range.rangeStart"
              type="date"
              required
              :aria-invalid="!!fields.rangeStart"
            />
            <p v-if="fields.rangeStart" class="error-message">{{ fields.rangeStart }}</p>
          </div>
          <div>
            <label for="range-end">结束日期</label
            ><input
              id="range-end"
              v-model="range.rangeEnd"
              type="date"
              required
              :aria-invalid="!!fields.rangeEnd"
            />
            <p v-if="fields.rangeEnd" class="error-message">{{ fields.rangeEnd }}</p>
          </div>
          <button type="submit">{{ submitting ? '正在提交……' : '生成报告' }}</button>
        </div>
      </fieldset>
      <p class="muted">日期含首尾，最多 366 天。提交后自动查看任务进度。</p>
      <p v-if="submitError" role="alert" class="error-message">{{ submitError }}</p>
    </form>
    <h3>报告记录</h3>
    <form @submit.prevent="load(1, filters)">
      <fieldset :disabled="loading">
        <div class="fields">
          <div>
            <label for="report-status">状态</label
            ><select id="report-status" v-model="filters.status">
              <option value="">全部状态</option>
              <option value="PENDING">处理中（等待开始）</option>
              <option value="RUNNING">处理中（执行中）</option>
              <option value="SUCCESS">成功</option>
              <option value="FAILED">失败</option>
            </select>
          </div>
          <div>
            <label for="report-trigger">触发方式</label
            ><select id="report-trigger" v-model="filters.triggerType">
              <option value="">全部方式</option>
              <option value="MANUAL">手动</option>
              <option value="SCHEDULED">定时</option>
            </select>
          </div>
          <button type="submit">查询</button><button type="button" @click="load()">刷新列表</button>
        </div>
      </fieldset>
    </form>
    <p v-if="loading" role="status">正在加载报告记录……</p>
    <p v-if="listError" role="alert" class="error-message">{{ listError }}（可点击刷新列表重试）</p>
    <template v-if="!loading && !listError">
      <p v-if="!rows.length">没有符合条件的报告。</p>
      <div v-else class="table-container">
        <table>
          <thead>
            <tr>
              <th>报告编号</th>
              <th>统计日期</th>
              <th>触发方式</th>
              <th>状态</th>
              <th>创建时间（上海）</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.rangeStart }} 至 {{ row.rangeEnd }}</td>
              <td>{{ row.triggerType === 'MANUAL' ? '手动' : '定时' }}</td>
              <td>
                <span :class="['status', row.status]">{{ reportStatusText[row.status] }}</span>
                <p v-if="row.status === 'FAILED'" class="error-message">
                  {{ row.errorMessage || '未提供失败原因' }}
                </p>
              </td>
              <td>{{ formatReportTime(row.createdAt) }}</td>
              <td>
                <RouterLink :to="{ name: 'report-detail', params: { id: row.id } }"
                  >查看详情</RouterLink
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="actions">
        <button :disabled="page <= 1" @click="load(page - 1)">上一页</button
        ><span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span
        ><button :disabled="page >= totalPages" @click="load(page + 1)">下一页</button>
      </div>
    </template>
  </section>
</template>

<style src="../assets/reports.css"></style>
