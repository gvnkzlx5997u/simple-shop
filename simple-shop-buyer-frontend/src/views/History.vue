<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { fetchHistory } from '../api/buyer'
import type { BuyerHistoryEntry } from '../api/types'
import { describeError } from '../stores/auth'

const pageSize = 10
const items = ref<BuyerHistoryEntry[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errorText = ref('')

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await fetchHistory(page.value, pageSize)
    items.value = data.items
    total.value = data.total
  } catch (err) {
    errorText.value = describeError(err)
    items.value = []
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

/** 后端 Time 为 UTC ISO-8601 字符串，按本地时区渲染。 */
function formatTime(iso: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function resultText(result: string): string {
  return result === 'succeeded' ? '交易成功' : '交易失败'
}

onMounted(load)
</script>

<template>
  <div class="page stack">
    <div class="card">
      <h1 class="title">我的历史</h1>
      <p class="muted">
        只显示已经有交易结果的下单记录；排队中、交易中的意向不在这里。
      </p>
    </div>

    <p v-if="errorText" class="error">{{ errorText }}</p>
    <p v-else-if="loading" class="muted">加载中…</p>
    <p v-else-if="!items.length" class="muted">还没有已结束的下单记录</p>

    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>商品名称</th>
            <th>下单时间</th>
            <th>交易结果</th>
            <th>交易时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in items" :key="idx">
            <td>{{ row.goods_name }}</td>
            <td>{{ formatTime(row.create_at) }}</td>
            <td :class="{ success: row.result === 'succeeded' }">
              {{ resultText(row.result) }}
            </td>
            <td>{{ formatTime(row.finish_at) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > pageSize" class="row pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span class="muted">第 {{ page }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页</span>
      <button :disabled="page * pageSize >= total" @click="changePage(page + 1)">
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.title {
  font-size: 18px;
  margin: 0 0 6px;
}

.table-wrap {
  padding: 0;
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  text-align: left;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.table th {
  color: var(--color-text-weak);
  font-weight: 500;
  background: #fafbfc;
}

.table tr:last-child td {
  border-bottom: none;
}

.success {
  color: #15803d;
}

.pager {
  justify-content: center;
}
</style>
