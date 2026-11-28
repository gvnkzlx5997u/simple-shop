<script setup lang="ts">
import type { OrderItemData } from '../../api/types'
import { formatMoney } from '../../utils/format'

withDefaults(
  defineProps<{
    items: OrderItemData[]
    /** 只有交易完成的订单才给售后入口。 */
    applyEnabled?: boolean
  }>(),
  { applyEnabled: false },
)

const emit = defineEmits<{ apply: [orderItemId: string] }>()
</script>

<template>
  <div class="overflow-x-auto">
    <table class="items table table-sm" data-testid="order-items">
      <thead>
        <tr>
          <th class="text-left">商品</th>
          <th class="text-right">单价</th>
          <th class="text-right">数量</th>
          <th class="text-right">小计</th>
          <th v-if="applyEnabled" class="text-right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.order_item_id">
          <td class="text-left">{{ item.goods_name }}</td>
          <td class="text-right whitespace-nowrap">¥{{ formatMoney(item.unit_price) }}</td>
          <td class="text-right">{{ item.quantity }}</td>
          <td class="text-right whitespace-nowrap">¥{{ formatMoney(item.amount) }}</td>
          <td v-if="applyEnabled" class="text-right">
            <button
              class="btn btn-outline btn-xs"
              data-testid="aftersale-apply-entry"
              @click="emit('apply', item.order_item_id)"
            >
              申请售后
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>