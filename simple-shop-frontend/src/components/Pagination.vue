<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  pageSize: number
  total: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:page': [page: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const pages = computed(() => {
  const total = totalPages.value
  const start = Math.max(1, Math.min(props.page - 2, total - 4))
  const end = Math.min(total, start + 4)
  const list: number[] = []
  for (let current = start; current <= end; current += 1) {
    list.push(current)
  }
  return list
})

function go(page: number): void {
  if (props.disabled || page < 1 || page > totalPages.value || page === props.page) {
    return
  }
  emit('update:page', page)
}
</script>

<template>
  <div class="pagination">
    <span class="pagination__total muted">共 {{ total }} 条</span>
    <button
      type="button"
      class="btn btn--small"
      :disabled="disabled || page <= 1"
      @click="go(page - 1)"
    >
      上一页
    </button>
    <button
      v-for="item in pages"
      :key="item"
      type="button"
      class="btn btn--small"
      :class="{ 'btn--primary': item === page }"
      :disabled="disabled"
      @click="go(item)"
    >
      {{ item }}
    </button>
    <button
      type="button"
      class="btn btn--small"
      :disabled="disabled || page >= totalPages"
      @click="go(page + 1)"
    >
      下一页
    </button>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pagination__total {
  margin-right: auto;
  font-size: 13px;
}
</style>
