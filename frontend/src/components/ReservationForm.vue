<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import type { Venue } from '../api/venues'
import {
  createReservation,
  type Reservation,
} from '../api/reservations'
import { ApiError } from '../api/http'

const props = defineProps<{
  venue: Venue
}>()

const form = reactive({
  bookerName: '',
  reservationDate: '',
  startTime: '',
  endTime: '',
  purpose: '',
})

const submitting = ref(false)
const errorMessage = ref('')
const createdReservation = ref<Reservation | null>(null)
const resultUncertain = ref(false)

function timeToSeconds(value: string): number {
  if (!/^\d{2}:\d{2}(:\d{2})?$/.test(value)) {
    return NaN
  }

  const [hour = -1, minute = -1, second = 0] =
    value.split(':').map(Number)

  if (
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59 ||
    second < 0 ||
    second > 59
  ) {
    return NaN
  }

  return hour * 3600 + minute * 60 + second
}

function validateForm(): string {
  if (props.venue.status !== 'OPEN') {
    return '该场地当前不可预约'
  }

  const name = form.bookerName.trim()
  const purpose = form.purpose.trim()

  if (!name) return '请输入预约人或组织名称'
  if (name.length > 100) return '预约人或组织名称不能超过100个字符'

  if (!form.reservationDate) return '请选择预约日期'
  if (!form.startTime || !form.endTime) {
    return '请选择开始时间和结束时间'
  }

  const start = timeToSeconds(form.startTime)
  const end = timeToSeconds(form.endTime)
  const open = timeToSeconds(props.venue.openTime)
  const close = timeToSeconds(props.venue.closeTime)

  if (![start, end, open, close].every(Number.isFinite)) {
    return '时间格式异常，请检查输入或重新加载场地'
  }

  if (start >= end) {
    return '结束时间必须晚于开始时间，本系统暂不支持跨天预约'
  }

  if (start < open || end > close) {
    return `预约时间必须在 ${props.venue.openTime} 至 ${props.venue.closeTime} 内`
  }

  // 按已约定的 Asia/Shanghai 业务时间检查。
  const startTimestamp = Date.parse(
    `${form.reservationDate}T${toApiTime(form.startTime)}+08:00`,
  )

  if (!Number.isFinite(startTimestamp)) {
    return '预约日期或时间不合法'
  }

  if (startTimestamp <= Date.now()) {
    return '请选择尚未开始的预约时间'
  }

  if (!purpose) return '请输入使用目的'
  if (purpose.length > 500) return '使用目的不能超过500个字符'

  return ''
}

function toApiTime(value: string): string {
  return value.length === 5 ? `${value}:00` : value
}

async function submitReservation() {
  if (
    submitting.value ||
    createdReservation.value ||
    resultUncertain.value
  ) {
    return
  }

  errorMessage.value = validateForm()

  if (errorMessage.value) {
    return
  }

  submitting.value = true

  try {
    const result = await createReservation({
      venueId: props.venue.id,
      bookerName: form.bookerName.trim(),
      reservationDate: form.reservationDate,
      startTime: toApiTime(form.startTime),
      endTime: toApiTime(form.endTime),
      purpose: form.purpose.trim(),
    })

    createdReservation.value = result
  } catch (error) {
    if (
      error instanceof ApiError &&
      (
        error.status === 0 ||
        error.status >= 500 ||
        error.code === 'INVALID_RESPONSE'
      )
    ) {
      resultUncertain.value = true
      errorMessage.value =
        '未能确认预约结果，请先查询预约记录或联系后端同学核实，避免重复提交。'
    } else {
      errorMessage.value =
        error instanceof Error ? error.message : '预约提交失败'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="reservation-form">
    <h3>填写预约信息</h3>
    <p class="hint">日期和时间均按北京时间填写。</p>

    <!-- 创建成功后隐藏表单，避免再次点击提交 -->
    <div
      v-if="createdReservation"
      class="success-message"
      role="status"
    >
      <h3>预约成功</h3>

      <p>预约编号：{{ createdReservation.id }}</p>
      <p>场地：{{ venue.name }}</p>
      <p>日期：{{ createdReservation.reservationDate }}</p>

      <p>
        时间：
        {{ createdReservation.startTime }}
        —
        {{ createdReservation.endTime }}
      </p>

      <p>
        <RouterLink
          :to="{
            name: 'reservation-detail',
            params: { id: createdReservation.id },
          }"
        >
          查看本次预约详情
        </RouterLink>
      </p>

      <p>
        <RouterLink to="/reservations">
          查看预约记录
        </RouterLink>
      </p>

      <p>
        <RouterLink to="/venues">
          返回场地列表
        </RouterLink>
      </p>
    </div>

    <form v-else @submit.prevent="submitReservation">
      <fieldset :disabled="submitting || resultUncertain">
        <div class="form-field">
          <label for="bookerName">预约人或组织名称</label>
          <input
            id="bookerName"
            v-model="form.bookerName"
            type="text"
            maxlength="100"
            required
            placeholder="请输入预约人或组织名称"
          />
        </div>

        <div class="form-field">
          <label for="reservationDate">预约日期</label>
          <input
            id="reservationDate"
            v-model="form.reservationDate"
            type="date"
            required
          />
        </div>

        <div class="time-row">
          <div class="form-field">
            <label for="startTime">开始时间</label>
            <input
              id="startTime"
              v-model="form.startTime"
              type="time"
              step="60"
              required
            />
          </div>

          <div class="form-field">
            <label for="endTime">结束时间</label>
            <input
              id="endTime"
              v-model="form.endTime"
              type="time"
              step="60"
              required
            />
          </div>
        </div>

        <div class="form-field">
          <label for="purpose">使用目的</label>
          <textarea
            id="purpose"
            v-model="form.purpose"
            rows="4"
            maxlength="500"
            required
            placeholder="例如：课程项目讨论"
          />
        </div>

        <button type="submit">
          {{ submitting ? '正在提交……' : '提交预约' }}
        </button>
      </fieldset>

      <p
        v-if="errorMessage"
        class="error-message"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <p v-if="resultUncertain" class="hint">
        请核实本次预约是否已创建，当前页面已暂停再次提交。
      </p>
    </form>
  </section>
</template>

<style scoped>
.reservation-form {
  margin-top: 24px;
  max-width: 600px;
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 18px;
}

input,
textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font: inherit;
}

textarea {
  resize: vertical;
}

.time-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

button {
  padding: 10px 20px;
  color: white;
  background: #2563eb;
  border: 0;
  border-radius: 6px;
}

button:disabled {
  opacity: 0.6;
}

.hint {
  color: #64748b;
}

.error-message {
  color: #b91c1c;
}

.success-message {
  padding: 20px;
  background: #f0fdf4;
  border: 1px solid #86efac;
  border-radius: 8px;
}
</style>
