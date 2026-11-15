<script setup lang="ts">
import type { CartRow } from '../../stores/cart'
import { formatMoney, formatTime } from '../../utils/format'

defineProps<{ row: CartRow }>()

const emit = defineEmits<{
  quantity: [goodsId: string, value: number]
  step: [goodsId: string, delta: number]
  toggle: [goodsId: string, checked: boolean]
  remove: [goodsId: string]
}>()

function stockText(row: CartRow): string {
  if (!row.selectable) return '已下架'
  if (row.stock <= 0) return '缺货'
  return '库存 ' + row.stock
}

/** 数量输入用 change 而不是 input：避免每敲一个数字就夹一次，还没输完就被改掉。 */
function onQuantity(row: CartRow, event: Event): void {
  emit('quantity', row.goods_id, Number((event.target as HTMLInputElement).value))
}
</script>

<template>
  <div
    :class="['row-item', { disabled: !row.checkable, 'bg-base-200': !row.checkable }]"
    data-testid="cart-row"
  >
    <input
      class="row-check checkbox checkbox-sm"
      data-testid="cart-check"
      type="checkbox"
      :checked="row.checked"
      :disabled="!row.checkable"
      @change="emit('toggle', row.goods_id, ($event.target as HTMLInputElement).checked)"
    />

    <img
      v-if="row.main_image_url"
      :src="row.main_image_url"
      :alt="row.name"
      class="thumb size-16 rounded-field bg-base-300 object-cover"
    />
    <div
      v-else
      class="thumb flex size-16 items-center justify-center rounded-field bg-base-300 text-xs text-base-content/60"
    >
      无图
    </div>

    <div class="info min-w-0">
      <RouterLink
        v-if="row.selectable"
        :to="'/goods/' + row.goods_id"
        :class="['name block truncate', row.checkable ? 'text-base-content' : 'text-base-content/60']"
      >
        {{ row.name }}
      </RouterLink>
      <span
        v-else
        :class="['name block truncate', row.checkable ? 'text-base-content' : 'text-base-content/60']"
      >
        {{ row.name }}
      </span>

      <div class="mt-0.5 flex items-center gap-2.5">
        <span :class="['font-semibold', row.checkable ? 'text-error' : 'text-base-content/60']">
          ¥{{ formatMoney(row.price) }}
        </span>
        <span
          :class="['text-xs', !row.selectable || row.stock <= 0 ? 'text-error' : 'text-base-content/60']"
        >
          {{ stockText(row) }}
        </span>
      </div>
      <div class="muted mt-0.5 text-xs">加入时间 {{ formatTime(row.added_at) }}</div>
    </div>

    <div class="qty flex items-center gap-1">
      <button
        class="btn btn-outline btn-xs btn-square"
        :disabled="!row.checkable || row.quantity <= 1"
        @click="emit('step', row.goods_id, -1)"
      >
        −
      </button>
      <input
        class="qty-input input input-xs w-14 text-center"
        data-testid="cart-qty"
        type="number"
        :min="1"
        :max="row.stock > 0 ? row.stock : 1"
        :value="row.quantity"
        :disabled="!row.checkable"
        @change="onQuantity(row, $event)"
      />
      <button
        class="btn btn-outline btn-xs btn-square"
        :disabled="!row.checkable || row.quantity >= (row.stock > 0 ? row.stock : 1)"
        @click="emit('step', row.goods_id, 1)"
      >
        +
      </button>
    </div>

    <div class="line-total text-right font-semibold" data-testid="cart-line-total">
      ¥{{ formatMoney(row.price * row.quantity) }}
    </div>

    <button class="remove btn btn-ghost btn-xs" data-testid="cart-remove" @click="emit('remove', row.goods_id)">
      移除
    </button>
  </div>
</template>

<style scoped>
/*
 * 购物车行的网格布局（含窄屏 reflow）保留为 scoped：纯布局，不含颜色，
 * 颜色一律走模板里的语义工具类。
 */
.row-item {
  display: grid;
  grid-template-columns: 24px 64px minmax(0, 1fr) 132px 88px 56px;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-base-300);
}

.row-item:last-child {
  border-bottom: none;
}

@media (max-width: 720px) {
  .row-item {
    grid-template-columns: 24px 56px minmax(0, 1fr) 56px;
    grid-template-areas:
      'check thumb info remove'
      '. qty qty .'
      '. total total .';
  }

  .row-check {
    grid-area: check;
  }

  .thumb {
    grid-area: thumb;
    width: 56px;
    height: 56px;
  }

  .info {
    grid-area: info;
  }

  .qty {
    grid-area: qty;
  }

  .line-total {
    grid-area: total;
    text-align: left;
  }

  .remove {
    grid-area: remove;
  }
}
</style>