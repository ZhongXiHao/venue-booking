<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '../api/http'
import {
  getReservations,
  cancelReservation,
  type Reservation,
} from '../api/reservations'

interface Filters {
  venueId: string
  date: string
  status: '' | Reservation['status']
}

// 用户正在编辑的筛选条件。
const filters = reactive<Filters>({
  venueId: '',
  date: '',
  status: '',
})

// 最近一次查询成功使用的条件。
// 翻页和刷新时沿用这些条件。
const appliedFilters = ref<Filters>({
  venueId: '',
  date: '',
  status: '',
})

const reservations = ref<Reservation[]>([])
const page = ref(1)
const pageSize = 10
const total = ref(0)

const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
const actionError = ref('')
const cancellingId = ref<string | null>(null)
// 正在等待页面内二次确认的预约编号
const confirmingId = ref<string | null>(null)
const needsRefresh = ref(false)

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize)),
)

const busy = computed(
  () => loading.value || cancellingId.value !== null,
)

// 保存最近一次请求，失败后可重试相同条件。
let lastAttempt = {
  page: 1,
  filters: { ...appliedFilters.value },
}

async function loadReservations(
  targetPage = page.value,
  conditions: Filters = appliedFilters.value,
) {
  if (busy.value) return

  const snapshot = { ...conditions }

  lastAttempt = {
    page: targetPage,
    filters: snapshot,
  }

  loading.value = true
  errorMessage.value = ''
  confirmingId.value = null

  try {
    const params = {
      venueId: snapshot.venueId || undefined,
      reservationDate: snapshot.date || undefined,
      status: snapshot.status || undefined,
      pageSize,
    }

    let result = await getReservations({
      ...params,
      page: targetPage,
    })

    // 取消或删除记录后，原来的最后一页可能变空。
    const lastPage = Math.max(
      1,
      Math.ceil(result.total / pageSize),
    )

    if (targetPage > lastPage) {
      lastAttempt.page = lastPage

      result = await getReservations({
        ...params,
        page: lastPage,
      })
    }

    reservations.value = result.items
    total.value = result.total
    page.value = result.page
    appliedFilters.value = snapshot
    needsRefresh.value = false
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '预约记录加载失败'
  } finally {
    loading.value = false
  }
}

function searchReservations() {
  actionMessage.value = ''
  actionError.value = ''

  void loadReservations(1, {
    venueId: filters.venueId.trim(),
    date: filters.date,
    status: filters.status,
  })
}

function resetFilters() {
  filters.venueId = ''
  filters.date = ''
  filters.status = ''

  searchReservations()
}

function retryLoad() {
  void loadReservations(
    lastAttempt.page,
    lastAttempt.filters,
  )
}

// 点"取消预约"只进入确认状态，真正取消在 handleCancel
function askCancel(reservation: Reservation) {
  if (
    busy.value ||
    needsRefresh.value ||
    reservation.status !== 'ACTIVE'
  ) {
    return
  }

  actionMessage.value = ''
  actionError.value = ''
  confirmingId.value = reservation.id
}

async function handleCancel(reservation: Reservation) {
  if (
    busy.value ||
    needsRefresh.value ||
    reservation.status !== 'ACTIVE' ||
    confirmingId.value !== reservation.id
  ) {
    return
  }

  confirmingId.value = null
  cancellingId.value = reservation.id
  actionMessage.value = ''
  actionError.value = ''

  let shouldReload = false

  try {
    await cancelReservation(reservation.id)

    actionMessage.value = `预约 ${reservation.id} 已取消`

    // 重新查询，确保筛选结果和总数与后端一致。
    needsRefresh.value = true
    shouldReload = true
  } catch (error) {
    actionError.value =
      error instanceof Error ? error.message : '取消预约失败'

    if (
      error instanceof ApiError &&
      (
        error.code === 'ALREADY_CANCELLED' ||
        error.code === 'RESERVATION_NOT_FOUND'
      )
    ) {
      needsRefresh.value = true
      shouldReload = true
    } else if (
      error instanceof ApiError &&
      (
        error.status === 0 ||
        error.status >= 500 ||
        error.code === 'INVALID_RESPONSE'
      )
    ) {
      needsRefresh.value = true
      actionError.value =
        '未能确认取消结果，请刷新列表核实后再操作。'
    }
  } finally {
    cancellingId.value = null
  }

  if (shouldReload) {
    await loadReservations()
  }
}

onMounted(() => {
  void loadReservations()
})
</script>

<template>
  <section>
    <div class="toolbar">
      <h2>预约记录</h2>

      <RouterLink to="/venues">
        选择场地预约
      </RouterLink>
    </div>

    <form
      class="filter-panel"
      @submit.prevent="searchReservations"
    >
      <fieldset :disabled="busy">
        <div class="filter-fields">
          <div class="field">
            <label for="filter-venue">场地编号</label>
            <input
              id="filter-venue"
              v-model="filters.venueId"
              type="text"
              placeholder="留空表示全部场地"
            />
          </div>

          <div class="field">
            <label for="filter-date">预约日期</label>
            <input
              id="filter-date"
              v-model="filters.date"
              type="date"
            />
          </div>

          <div class="field">
            <label for="filter-status">预约状态</label>
            <select
              id="filter-status"
              v-model="filters.status"
            >
              <option value="">全部状态</option>
              <option value="ACTIVE">有效</option>
              <option value="CANCELLED">已取消</option>
            </select>
          </div>
        </div>

        <div class="actions">
          <button type="submit">查询</button>

          <button type="button" @click="resetFilters">
            重置
          </button>

          <button
            type="button"
            @click="loadReservations()"
          >
            刷新当前结果
          </button>
        </div>
      </fieldset>
    </form>

    <p v-if="actionMessage" class="success-message" role="status">
      {{ actionMessage }}
    </p>

    <p v-if="actionError" class="error-message" role="alert">
      {{ actionError }}
    </p>

    <p v-if="loading" role="status">
      正在加载预约记录……
    </p>

    <div v-else-if="errorMessage" role="alert">
      <p class="error-message">{{ errorMessage }}</p>
      <button type="button" @click="retryLoad">
        重新加载
      </button>
    </div>

    <template v-else>
      <p v-if="reservations.length === 0">
        没有符合条件的预约记录。
      </p>

      <div v-else class="table-container">
        <table>
          <thead>
            <tr>
              <th scope="col">预约编号</th>
              <th scope="col">场地</th>
              <th scope="col">预约人</th>
              <th scope="col">日期</th>
              <th scope="col">时间</th>
              <th scope="col">用途</th>
              <th scope="col">状态</th>
              <th scope="col">操作</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="reservation in reservations"
              :key="reservation.id"
            >
              <td>
                <RouterLink
                  :to="{
                    name: 'reservation-detail',
                    params: { id: reservation.id },
                  }"
                >
                  {{ reservation.id }}
                </RouterLink>
              </td>

              <td>{{ reservation.venueName }}</td>
              <td>{{ reservation.bookerName }}</td>
              <td>{{ reservation.reservationDate }}</td>

              <td>
                {{ reservation.startTime }}
                —
                {{ reservation.endTime }}
              </td>

              <td class="purpose">
                {{ reservation.purpose }}
              </td>

              <td>
                {{
                  reservation.status === 'ACTIVE'
                    ? '有效'
                    : reservation.status === 'CANCELLED'
                      ? '已取消'
                      : '未知'
                }}
              </td>

              <td>
                <template v-if="reservation.status === 'ACTIVE'">
                  <div
                    v-if="confirmingId === reservation.id"
                    class="confirm-box"
                    role="group"
                    :aria-label="`确认取消预约 ${reservation.id}`"
                  >
                    <span>确定取消？</span>

                    <button
                      type="button"
                      class="danger"
                      :disabled="busy"
                      @click="handleCancel(reservation)"
                    >
                      确认取消
                    </button>

                    <button
                      type="button"
                      :disabled="busy"
                      @click="confirmingId = null"
                    >
                      返回
                    </button>
                  </div>

                  <button
                    v-else
                    type="button"
                    :disabled="busy || needsRefresh"
                    @click="askCancel(reservation)"
                  >
                    {{
                      cancellingId === reservation.id
                        ? '取消中……'
                        : '取消预约'
                    }}
                  </button>
                </template>

                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button
          type="button"
          :disabled="busy || page <= 1"
          @click="loadReservations(page - 1)"
        >
          上一页
        </button>

        <span>
          第 {{ page }} / {{ totalPages }} 页，
          共 {{ total }} 条
        </span>

        <button
          type="button"
          :disabled="busy || page >= totalPages"
          @click="loadReservations(page + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.toolbar,
.actions,
.pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar {
  justify-content: space-between;
  margin-bottom: 20px;
}

.toolbar h2 {
  margin: 0;
}

.filter-panel {
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 8px;
  background: #f8fafc;
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.filter-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

input,
select,
button {
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font: inherit;
  background: white;
}

input,
select {
  width: 100%;
  min-width: 0;
}

button {
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.table-container {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

th {
  white-space: nowrap;
  background: #f8fafc;
}

.purpose {
  min-width: 140px;
  max-width: 260px;
  overflow-wrap: anywhere;
}

.pagination {
  margin-top: 20px;
}

.confirm-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.danger {
  color: white;
  background: #b91c1c;
  border-color: #b91c1c;
}

.success-message {
  color: #15803d;
}

.error-message {
  color: #b91c1c;
}
</style>