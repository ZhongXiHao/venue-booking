<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getReservation,
  type Reservation,
} from '../api/reservations'

const route = useRoute()

const reservationId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
})

const reservation = ref<Reservation | null>(null)
const loading = ref(false)
const errorMessage = ref('')

let latestRequest = 0

async function loadReservation() {
  const requestId = ++latestRequest
  const id = reservationId.value

  reservation.value = null
  errorMessage.value = ''
  loading.value = false

  if (!id) {
    errorMessage.value = '缺少预约编号'
    return
  }

  loading.value = true

  try {
    const result = await getReservation(id)

    if (requestId === latestRequest) {
      reservation.value = result
    }
  } catch (error) {
    if (requestId === latestRequest) {
      errorMessage.value =
        error instanceof Error ? error.message : '预约详情加载失败'
    }
  } finally {
    if (requestId === latestRequest) {
      loading.value = false
    }
  }
}

watch(
  reservationId,
  () => {
    void loadReservation()
  },
  { immediate: true },
)
</script>

<template>
  <section>
    <div class="toolbar">
      <h2>预约详情</h2>

      <button
        type="button"
        :disabled="loading"
        @click="loadReservation"
      >
        刷新
      </button>
    </div>

    <p v-if="loading" role="status">
      正在加载预约信息……
    </p>

    <div v-else-if="errorMessage" role="alert">
      <p class="error-message">{{ errorMessage }}</p>

      <button type="button" @click="loadReservation">
        重新加载
      </button>
    </div>

    <article v-else-if="reservation" class="detail-card">
      <dl>
        <dt>预约编号</dt>
        <dd>{{ reservation.id }}</dd>

        <dt>预约场地</dt>
        <dd>
          <RouterLink
            :to="{
              name: 'venue-detail',
              params: { id: reservation.venueId },
            }"
          >
            {{ reservation.venueName }}
          </RouterLink>
        </dd>

        <dt>预约人</dt>
        <dd>{{ reservation.bookerName }}</dd>

        <dt>预约日期</dt>
        <dd>{{ reservation.reservationDate }}</dd>

        <dt>预约时间</dt>
        <dd>
          {{ reservation.startTime }}
          —
          {{ reservation.endTime }}
        </dd>

        <dt>使用目的</dt>
        <dd class="purpose">{{ reservation.purpose }}</dd>

        <dt>预约状态</dt>
        <dd>
          {{
            reservation.status === 'ACTIVE'
              ? '有效'
              : reservation.status === 'CANCELLED'
                ? '已取消'
                : '未知'
          }}
        </dd>
      </dl>

      <p v-if="reservation.status === 'ACTIVE'">
        如需取消，请返回预约记录列表操作。
      </p>
    </article>

    <p>
      <RouterLink to="/reservations">
        返回预约记录
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.detail-card {
  padding: 24px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

dl {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 16px;
}

dt {
  color: #64748b;
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.purpose {
  white-space: pre-wrap;
}

.error-message {
  color: #b91c1c;
}
</style>