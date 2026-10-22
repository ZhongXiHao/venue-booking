<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getVenues, type Venue } from '../api/venues'

const venues = ref<Venue[]>([])

const page = ref(1)
const pageSize = ref(10)
const selectedPageSize = ref(10)
const total = ref(0)

const loading = ref(false)
const errorMessage = ref('')

// 请求失败后，重试相同的页码和每页条数。
const lastRequest = ref({
  page: 1,
  pageSize: 10,
})

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize.value)),
)

async function loadVenues(
  targetPage = page.value,
  targetPageSize = pageSize.value,
) {
  if (loading.value) return

  lastRequest.value = {
    page: targetPage,
    pageSize: targetPageSize,
  }

  loading.value = true
  errorMessage.value = ''

  try {
    let result = await getVenues(targetPage, targetPageSize)

    // 数据减少后，当前页可能超出范围，回到最后一个有效页。
    const lastPage = Math.max(
      1,
      Math.ceil(result.total / result.pageSize),
    )

    if (targetPage > lastPage) {
      lastRequest.value = {
        page: lastPage,
        pageSize: result.pageSize,
      }

      result = await getVenues(lastPage, result.pageSize)
    }

    venues.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
    selectedPageSize.value = result.pageSize
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '场地列表加载失败'
  } finally {
    loading.value = false
  }
}

function retryLoad() {
  void loadVenues(
    lastRequest.value.page,
    lastRequest.value.pageSize,
  )
}

function changePageSize() {
  // 修改每页条数后，从第一页重新查询。
  void loadVenues(1, selectedPageSize.value)
}

function getStatusText(status: Venue['status']) {
  if (status === 'OPEN') return '开放'
  if (status === 'DISABLED') return '停用'
  return '未知'
}

onMounted(() => {
  void loadVenues()
})
</script>

<template>
  <section>
    <div class="page-toolbar">
      <div>
        <h2>场地列表</h2>
        <p class="hint">查看场地信息，选择合适的场地进行预约。</p>
      </div>

      <div class="toolbar-actions">
        <button
          type="button"
          :disabled="loading"
          @click="loadVenues()"
        >
          刷新
        </button>

        <RouterLink to="/venues/new" class="primary-link">
          创建场地
        </RouterLink>
      </div>
    </div>

    <p v-if="loading" role="status">
      正在加载场地……
    </p>

    <div
      v-else-if="errorMessage"
      class="notice"
      role="alert"
    >
      <p class="error-message">{{ errorMessage }}</p>

      <button type="button" @click="retryLoad">
        重新加载
      </button>
    </div>

    <template v-else>
      <div v-if="venues.length === 0" class="notice">
        <p>暂无场地。</p>
        <RouterLink to="/venues/new">
          创建一个场地
        </RouterLink>
      </div>

      <div v-else class="venue-grid">
        <article
          v-for="venue in venues"
          :key="venue.id"
          class="venue-card"
        >
          <div class="card-heading">
            <h3>
              <RouterLink
                :to="{
                  name: 'venue-detail',
                  params: { id: venue.id },
                }"
              >
                {{ venue.name }}
              </RouterLink>
            </h3>

            <span
              class="status"
              :class="{
                'status-open': venue.status === 'OPEN',
                'status-disabled': venue.status !== 'OPEN',
              }"
            >
              {{ getStatusText(venue.status) }}
            </span>
          </div>

          <p>位置：{{ venue.location }}</p>
          <p>容量：{{ venue.capacity }} 人</p>
          <p>
            开放时间：
            {{ venue.openTime }} — {{ venue.closeTime }}
          </p>

          <div class="card-actions">
            <RouterLink
              :to="{
                name: 'venue-detail',
                params: { id: venue.id },
              }"
            >
              查看详情
            </RouterLink>

            <RouterLink
              v-if="venue.status === 'OPEN'"
              :to="{
                name: 'reservation-create',
                query: { venueId: venue.id },
              }"
            >
              预约此场地
            </RouterLink>

            <span v-else class="hint">
              暂不可预约
            </span>
          </div>
        </article>
      </div>

      <div class="pagination">
        <label class="page-size">
          每页条数

          <select
            v-model.number="selectedPageSize"
            :disabled="loading"
            @change="changePageSize"
          >
            <option :value="5">5</option>
            <option :value="10">10</option>
            <option :value="20">20</option>
          </select>
        </label>

        <span>
          共 {{ total }} 条，
          第 {{ page }} / {{ totalPages }} 页
        </span>

        <div class="pagination-buttons">
          <button
            type="button"
            :disabled="loading || page <= 1"
            @click="loadVenues(page - 1)"
          >
            上一页
          </button>

          <button
            type="button"
            :disabled="loading || page >= totalPages"
            @click="loadVenues(page + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.page-toolbar,
.toolbar-actions,
.card-heading,
.card-actions,
.pagination,
.pagination-buttons,
.page-size {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-toolbar,
.pagination {
  justify-content: space-between;
  flex-wrap: wrap;
}

.page-toolbar {
  margin-bottom: 24px;
}

.page-toolbar h2 {
  margin: 0;
}

.hint {
  color: #64748b;
}

.toolbar-actions,
.card-actions {
  flex-wrap: wrap;
}

.venue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.venue-card {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.card-heading {
  justify-content: space-between;
  align-items: flex-start;
}

.card-heading h3 {
  margin: 0;
  overflow-wrap: anywhere;
}

.card-heading h3 a {
  color: #1f2937;
  text-decoration: none;
}

.card-actions {
  margin-top: 20px;
}

.status {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 13px;
}

.status-open {
  color: #166534;
  background: #dcfce7;
}

.status-disabled {
  color: #64748b;
  background: #f1f5f9;
}

button,
select {
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: white;
  font: inherit;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.primary-link {
  padding: 8px 12px;
  border-radius: 6px;
  background: #2563eb;
  color: white;
  text-decoration: none;
}

.pagination {
  margin-top: 24px;
}

.notice {
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
}

.error-message {
  color: #b91c1c;
}
</style>