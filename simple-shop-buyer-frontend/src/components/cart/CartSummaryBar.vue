<script setup lang="ts">
import { formatMoney } from '../../utils/format'

defineProps<{
  allChecked: boolean
  lineCount: number
  totalQuantity: number
  totalAmount: number
  movingFav: boolean
}>()

const emit = defineEmits<{
  'toggle-all': [checked: boolean]
  'move-fav': []
  checkout: []
}>()
</script>

<template>
  <div class="bar panel sticky bottom-0 flex items-center gap-4">
    <label class="check-all flex cursor-pointer items-center gap-1.5">
      <input
        type="checkbox"
        class="checkbox checkbox-sm"
        data-testid="cart-check-all"
        :checked="allChecked"
        @change="emit('toggle-all', ($event.target as HTMLInputElement).checked)"
      />
      <span>全选</span>
    </label>

    <div class="ml-auto flex items-center gap-4">
      <span class="muted">已选 {{ lineCount }} 件，共 {{ totalQuantity }} 个</span>
      <span class="total">
        合计 <strong class="text-lg text-error" data-testid="cart-total">¥{{ formatMoney(totalAmount) }}</strong>
      </span>
    </div>

    <button
      class="to-fav btn btn-outline btn-sm"
      data-testid="cart-to-fav"
      :disabled="lineCount === 0 || movingFav"
      @click="emit('move-fav')"
    >
      {{ movingFav ? '转移中…' : '转收藏' }}
    </button>

    <button
      class="checkout btn btn-primary btn-sm"
      data-testid="cart-checkout"
      :disabled="lineCount === 0 || movingFav"
      @click="emit('checkout')"
    >
      去结算
    </button>
  </div>
</template>