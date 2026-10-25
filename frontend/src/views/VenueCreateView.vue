<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import {
  createVenue,
  type Venue,
  type CreateVenueRequest,
} from '../api/venues'
import { ApiError, parseApiError } from '../api/http'

const form = reactive({
  name: '',
  location: '',
  capacity: '20',
  openTime: '08:00',
  closeTime: '22:00',
  status: 'OPEN' as CreateVenueRequest['status'],
})

const submitting = ref(false)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})
const createdVenue = ref<Venue | null>(null)
const resultUncertain = ref(false)

function timeToMinutes(value: string): number {
  if (!/^\d{2}:\d{2}$/.test(value)) {
    return NaN
  }

  const [hour = -1, minute = -1] = value.split(':').map(Number)

  if (
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59
  ) {
    return NaN
  }

  return hour * 60 + minute
}

function validateForm(): string {
  const name = form.name.trim()
  const location = form.location.trim()
  const capacity = Number(form.capacity)

  if (!name) return '请输入场地名称'
  if (name.length > 100) return '场地名称不能超过100个字符'

  if (!location) return '请输入场地位置'
  if (location.length > 200) return '场地位置不能超过200个字符'

  if (!Number.isSafeInteger(capacity) || capacity <= 0) {
    return '容纳人数必须为有效的正整数'
  }

  const open = timeToMinutes(form.openTime)
  const close = timeToMinutes(form.closeTime)

  if (!Number.isFinite(open) || !Number.isFinite(close)) {
    return '请选择合法的开放时间和关闭时间'
  }

  if (open >= close) {
    return '关闭时间必须晚于开放时间，暂不支持跨天开放'
  }

  if (form.status !== 'OPEN' && form.status !== 'DISABLED') {
    return '请选择合法的场地状态'
  }

  return ''
}

async function submitVenue() {
  if (
    submitting.value ||
    createdVenue.value ||
    resultUncertain.value
  ) {
    return
  }

  fieldErrors.value = {}
  errorMessage.value = validateForm()

  if (errorMessage.value) {
    return
  }

  submitting.value = true

  try {
    createdVenue.value = await createVenue({
      name: form.name.trim(),
      location: form.location.trim(),
      capacity: Number(form.capacity),
      openTime: `${form.openTime}:00`,
      closeTime: `${form.closeTime}:00`,
      status: form.status,
    })
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
        '未能确认创建结果，请先查看场地列表或联系后端同学核实，避免重复创建。'
    } else {
      const parsed = parseApiError(error, '创建场地失败', [
        'name',
        'location',
        'capacity',
        'openTime',
        'closeTime',
        'status',
      ])

      errorMessage.value = parsed.message
      fieldErrors.value = parsed.fieldErrors
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="create-page">
    <h2>创建场地</h2>
    <p class="hint">开放时间按北京时间填写，暂不支持跨天。</p>

    <!-- 创建成功 -->
    <div
      v-if="createdVenue"
      class="success-panel"
      role="status"
    >
      <h3>场地创建成功</h3>

      <p>场地编号：{{ createdVenue.id }}</p>
      <p>场地名称：{{ createdVenue.name }}</p>
      <p>位置：{{ createdVenue.location }}</p>
      <p>容纳人数：{{ createdVenue.capacity }} 人</p>

      <div class="actions">
        <RouterLink to="/venues">
          查看场地列表
        </RouterLink>

        <RouterLink
          v-if="createdVenue.status === 'OPEN'"
          :to="{
            name: 'reservation-create',
            query: { venueId: createdVenue.id },
          }"
        >
          预约此场地
        </RouterLink>
      </div>
    </div>

    <!-- 创建表单 -->
    <form v-else @submit.prevent="submitVenue">
      <fieldset :disabled="submitting || resultUncertain">
        <div class="form-field">
          <label for="venue-name">场地名称</label>
          <input
            id="venue-name"
            :aria-invalid="!!fieldErrors.name"
            :aria-describedby="fieldErrors.name ? 'error-name' : undefined"
            v-model="form.name"
            type="text"
            maxlength="100"
            placeholder="例如：第一会议室"
            required
          />
          <p
            v-if="fieldErrors.name"
            id="error-name"
            class="field-error"
          >
            {{ fieldErrors.name }}
          </p>
        </div>

        <div class="form-field">
          <label for="venue-location">场地位置</label>
          <input
            id="venue-location"
            :aria-invalid="!!fieldErrors.location"
            :aria-describedby="fieldErrors.location ? 'error-location' : undefined"
            v-model="form.location"
            type="text"
            maxlength="200"
            placeholder="例如：A栋201"
            required
          />
          <p
            v-if="fieldErrors.location"
            id="error-location"
            class="field-error"
          >
            {{ fieldErrors.location }}
          </p>
        </div>

        <div class="form-field">
          <label for="venue-capacity">容纳人数</label>
          <input
            id="venue-capacity"
            :aria-invalid="!!fieldErrors.capacity"
            :aria-describedby="fieldErrors.capacity ? 'error-capacity' : undefined"
            v-model="form.capacity"
            type="text"
            inputmode="numeric"
            pattern="[0-9]+"
            required
          />
          <p
            v-if="fieldErrors.capacity"
            id="error-capacity"
            class="field-error"
          >
            {{ fieldErrors.capacity }}
          </p>
        </div>

        <div class="time-row">
          <div class="form-field">
            <label for="venue-open-time">开放时间</label>
            <input
              id="venue-open-time"
              :aria-invalid="!!fieldErrors.openTime"
              :aria-describedby="fieldErrors.openTime ? 'error-openTime' : undefined"
              v-model="form.openTime"
              type="time"
              step="60"
              required
            />
            <p
              v-if="fieldErrors.openTime"
              id="error-openTime"
              class="field-error"
            >
              {{ fieldErrors.openTime }}
            </p>
          </div>

          <div class="form-field">
            <label for="venue-close-time">关闭时间</label>
            <input
              id="venue-close-time"
              :aria-invalid="!!fieldErrors.closeTime"
              :aria-describedby="fieldErrors.closeTime ? 'error-closeTime' : undefined"
              v-model="form.closeTime"
              type="time"
              step="60"
              required
            />
            <p
              v-if="fieldErrors.closeTime"
              id="error-closeTime"
              class="field-error"
            >
              {{ fieldErrors.closeTime }}
            </p>
          </div>
        </div>

        <div class="form-field">
          <label for="venue-status">场地状态</label>
          <select
            id="venue-status"
            :aria-invalid="!!fieldErrors.status"
            :aria-describedby="fieldErrors.status ? 'error-status' : undefined"
            v-model="form.status"
          >
            <option value="OPEN">开放</option>
            <option value="DISABLED">停用</option>
          </select>
          <p
            v-if="fieldErrors.status"
            id="error-status"
            class="field-error"
          >
            {{ fieldErrors.status }}
          </p>
        </div>

        <button type="submit">
          {{ submitting ? '正在创建……' : '创建场地' }}
        </button>
      </fieldset>

      <p
        v-if="errorMessage"
        class="error-message"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <p v-if="resultUncertain">
        <RouterLink to="/venues">
          前往场地列表核实
        </RouterLink>
      </p>
    </form>

    <p>
      <RouterLink to="/venues">
        返回场地列表
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.create-page {
  max-width: 640px;
}

.hint {
  color: #64748b;
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
select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font: inherit;
}

.time-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

button {
  padding: 10px 20px;
  border: 0;
  border-radius: 6px;
  background: #2563eb;
  color: white;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.success-panel {
  padding: 20px;
  border: 1px solid #86efac;
  border-radius: 8px;
  background: #f0fdf4;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.error-message,
.field-error {
  color: #b91c1c;
}

.field-error {
  margin: 0;
  font-size: 14px;
}
</style>