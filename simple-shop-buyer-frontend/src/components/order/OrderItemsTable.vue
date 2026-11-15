<script setup lang="ts">
import type { OrderItemData } from '../../api/types'
import { formatMoney } from '../../utils/format'

defineProps<{ items: OrderItemData[] }>()
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
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, idx) in items" :key="item.goods_id + '-' + idx">
          <td class="text-left">{{ item.goods_name }}</td>
          <td class="text-right whitespace-nowrap">¥{{ formatMoney(item.unit_price) }}</td>
          <td class="text-right">{{ item.quantity }}</td>
          <td class="text-right whitespace-nowrap">¥{{ formatMoney(item.amount) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>