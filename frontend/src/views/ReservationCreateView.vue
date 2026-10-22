<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { getVenue, type Venue } from '../api/venues'
import ReservationForm from '../components/ReservationForm.vue'

const route = useRoute()

const venueId = computed(() => {
  const value = route.query.venueId
  return typeof value === 'string' ? value.trim() : ''
})

const venue = ref<Venue | null>(null)
const loading = ref(false)
const errorMessage = ref('')

let latestRequest = 0

async function loadVenue() {
  const requestId = ++latestRequest
  const id = venueId.value

  venue.value = null
  errorMessage.value = ''
  loading.value = false

  if (!id) {
    return
  }

  loading.value = true

  try {
    const result = await getVenue(id)

    if (requestId !== latestRequest) {
      return
    }

    venue.value = result
  } catch (error) {
    if (requestId !== latestRequest) {
      return
    }

    errorMessage.value =
      error instanceof Error
        ? error.message
        : '场地信息加载失败'
  } finally {
    if (requestId === latestRequest) {
      loading.value = false
    }
  }
}

// 首次进入以及场地 ID 改变时加载详情。
watch(
  venueId,
  () => {
    void loadVenue()
  },
  { immediate: true },
)
</script>

<template>
  <section>
    <h2>创建预约</h2>

    <!-- 未选择场地 -->
    <div v-if="!venueId" class="notice">
      <p>请先选择场地。</p>
      <RouterLink to="/venues">
        返回场地列表
      </RouterLink>
    </div>

    <!-- 正在加载 -->
    <p v-else-if="loading" role="status">
      正在加载场地信息……
    </p>

    <!-- 加载失败 -->
    <div
      v-else-if="errorMessage"
      class="notice"
      role="alert"
    >
      <p class="error-message">
        {{ errorMessage }}
      </p>

      <div class="actions">
        <button type="button" @click="loadVenue">
          重新加载
        </button>

        <RouterLink to="/venues">
          返回场地列表
        </RouterLink>
      </div>
    </div>

    <!-- 加载成功 -->
    <div v-else-if="venue">
      <article class="venue-info">
        <h3>{{ venue.name }}</h3>

        <p>位置：{{ venue.location }}</p>
        <p>可容纳人数：{{ venue.capacity }} 人</p>

        <p>
          开放时间：
          {{ venue.openTime }} — {{ venue.closeTime }}
        </p>

        <p>
          状态：
          {{
            venue.status === 'OPEN'
              ? '开放'
              : venue.status === 'DISABLED'
                ? '停用'
                : '未知'
          }}
        </p>
      </article>

      <!-- 停用：不显示预约表单 -->
      <div
        v-if="venue.status === 'DISABLED'"
        class="notice"
        role="alert"
      >
        <p class="error-message">
          该场地已停用，暂时无法预约。
        </p>

        <RouterLink to="/venues">
          选择其他场地
        </RouterLink>
      </div>

      <!-- 开放：显示预约表单组件 -->
      <ReservationForm
        v-else-if="venue.status === 'OPEN'"
        :key="venue.id"
        :venue="venue"
      />

      <!-- 未知状态：不允许预约 -->
      <p v-else class="error-message" role="alert">
        场地状态异常，暂时无法预约。
      </p>
    </div>

    <p v-else class="error-message">
      未获取到场地信息，请返回场地列表重新选择。
    </p>
  </section>
</template>

<style scoped>
.venue-info {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.venue-info h3 {
  margin-top: 0;
}

.notice {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.error-message {
  color: #b91c1c;
}
</style>