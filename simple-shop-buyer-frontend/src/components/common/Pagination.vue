<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    page: number
    pageSize: number
    total: number
    /** 总数单位：订单用「笔」、商品/收藏用「件」。 */
    unit?: string
    disabled?: boolean
  }>(),
  { unit: '条', disabled: false },
)

const emit = defineEmits<{
  'update:page': [page: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
/** 只有一页时不画按钮：两个灰按钮摆在那儿像坏了。页码文字仍保留。 */
const hasPager = computed(() => props.total > props.pageSize)

function go(page: number): void {
  if (props.disabled || page < 1 || page > totalPages.value || page === props.page) return
  emit('update:page', page)
}
</script>

<template>
  <div class="flex flex-wrap items-center justify-center gap-3">
    <span class="muted text-xs">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} {{ unit }}</span>
    <div v-if="hasPager" class="join">
      <button
        type="button"
        class="btn join-item btn-sm"
        :disabled="disabled || page <= 1"
        @click="go(page - 1)"
      >
        上一页
      </button>
      <button
        type="button"
        class="btn join-item btn-sm"
        :disabled="disabled || page >= totalPages"
        @click="go(page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>