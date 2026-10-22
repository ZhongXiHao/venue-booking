<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getVenue, type Venue } from '../api/venues'

const route = useRoute()

const venueId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
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
    errorMessage.value = '缺少场地编号'
    return
  }

  loading.value = true

  try {
    const result = await getVenue(id)

    if (requestId === latestRequest) {
      venue.value = result
    }
  } catch (error) {
    if (requestId === latestRequest) {
      errorMessage.value =
        error instanceof Error ? error.message : '场地详情加载失败'
    }
  } finally {
    if (requestId === latestRequest) {
      loading.value = false
    }
  }
}

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
    <h2>场地详情</h2>

    <p v-if="loading" role="status">
      正在加载场地信息……
    </p>

    <div v-else-if="errorMessage" role="alert">
      <p class="error-message">{{ errorMessage }}</p>
      <button type="button" @click="loadVenue">
        重新加载
      </button>
    </div>

    <article v-else-if="venue" class="detail-card">
      <h3>{{ venue.name }}</h3>

      <dl>
        <dt>场地编号</dt>
        <dd>{{ venue.id }}</dd>

        <dt>所在位置</dt>
        <dd>{{ venue.location }}</dd>

        <dt>容纳人数</dt>
        <dd>{{ venue.capacity }} 人</dd>

        <dt>开放时间</dt>
        <dd>{{ venue.openTime }} — {{ venue.closeTime }}</dd>

        <dt>场地状态</dt>
        <dd>
          {{
            venue.status === 'OPEN'
              ? '开放'
              : venue.status === 'DISABLED'
                ? '停用'
                : '未知'
          }}
        </dd>
      </dl>

      <RouterLink
        v-if="venue.status === 'OPEN'"
        :to="{
          name: 'reservation-create',
          query: { venueId: venue.id },
        }"
      >
        预约此场地
      </RouterLink>

      <p v-else class="error-message">
        当前场地暂不可预约。
      </p>
    </article>

    <p>
      <RouterLink to="/venues">
        返回场地列表
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.detail-card {
  padding: 24px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.detail-card h3 {
  margin-top: 0;
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

.error-message {
  color: #b91c1c;
}
</style>