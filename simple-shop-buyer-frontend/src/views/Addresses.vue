<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import type { AddressData } from '../api/types'
import AddressForm from '../components/address/AddressForm.vue'
import EmptyState from '../components/common/EmptyState.vue'
import FormModal from '../components/common/FormModal.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { MAX_ADDRESSES, useAddressStore } from '../stores/address'
import { describeError } from '../utils/errorText'

const addresses = useAddressStore()

const notice = ref('')
const actionError = ref('')
const busyId = ref('')
const confirmingId = ref('')

const formOpen = ref(false)
/** null 表示新建，否则是被编辑的那条。 */
const editing = ref<AddressData | null>(null)

const formTitle = computed(() => (editing.value ? '编辑地址' : '新建地址'))

function clearMessages(): void {
  notice.value = ''
  actionError.value = ''
}

function openCreate(): void {
  if (addresses.full) return
  clearMessages()
  confirmingId.value = ''
  editing.value = null
  formOpen.value = true
}

function openEdit(item: AddressData): void {
  clearMessages()
  confirmingId.value = ''
  editing.value = item
  formOpen.value = true
}

function closeForm(): void {
  formOpen.value = false
}

function onSaved(created: boolean): void {
  notice.value = created ? '已新增地址' : '地址已保存'
  closeForm()
}

async function onSetDefault(item: AddressData): Promise<void> {
  if (busyId.value) return
  busyId.value = item.id
  clearMessages()
  try {
    await addresses.setDefault(item.id)
    notice.value = '已设为默认地址'
  } catch (err) {
    actionError.value = describeError(err)
    // 地址可能已在别处被删掉，重拉一次让列表回到真实状态。
    await addresses.load()
  } finally {
    busyId.value = ''
  }
}

function askRemove(item: AddressData): void {
  clearMessages()
  confirmingId.value = item.id
}

async function doRemove(item: AddressData): Promise<void> {
  if (busyId.value) return
  busyId.value = item.id
  clearMessages()
  try {
    await addresses.remove(item.id)
    notice.value = '已删除地址'
    confirmingId.value = ''
    if (editing.value?.id === item.id) closeForm()
  } catch (err) {
    actionError.value = describeError(err)
    await addresses.load()
  } finally {
    busyId.value = ''
  }
}

onMounted(() => {
  void addresses.load()
})
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex flex-wrap items-baseline gap-3">
      <h1 class="text-lg">收货地址</h1>
      <span class="muted text-xs">共 {{ addresses.items.length }} / {{ MAX_ADDRESSES }} 条</span>
      <button
        class="btn btn-primary btn-sm ml-auto"
        data-testid="address-new"
        :disabled="addresses.full || addresses.loading"
        @click="openCreate"
      >
        新建地址
      </button>
    </div>

    <p v-if="notice" class="notice text-success" data-testid="notice">{{ notice }}</p>
    <p v-if="actionError" class="text-error">{{ actionError }}</p>
    <p v-if="addresses.full" class="muted text-xs">
      地址已达上限，删掉不用的才能再新建。
    </p>

    <p v-if="addresses.loading && !addresses.items.length" class="muted">加载中…</p>
    <div v-else-if="addresses.errorText" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ addresses.errorText }}</p>
      <button class="btn btn-outline btn-sm" @click="addresses.load()">重试</button>
    </div>

    <EmptyState
      v-else-if="!addresses.items.length"
      title="还没有收货地址"
      description="下单必须先选一个已有地址，先新建一条吧。"
    >
      <button class="btn btn-primary btn-sm" @click="openCreate">新建地址</button>
    </EmptyState>

    <div v-else class="space-y-3">
      <div
        v-for="item in addresses.items"
        :key="item.id"
        class="panel flex flex-wrap items-start gap-3"
        data-testid="address-row"
      >
        <div class="min-w-[220px] flex-1 space-y-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-semibold">{{ item.receiver_name }}</span>
            <span class="muted text-sm">{{ item.receiver_phone }}</span>
            <StatusTag v-if="item.is_default" label="默认" tone="info" />
          </div>
          <p class="text-sm">{{ item.detail_address }}</p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <template v-if="confirmingId === item.id">
            <span class="muted text-sm">删除这条地址？</span>
            <button
              class="btn btn-error btn-sm"
              :disabled="busyId === item.id"
              @click="doRemove(item)"
            >
              {{ busyId === item.id ? '删除中…' : '确认删除' }}
            </button>
            <button
              class="btn btn-ghost btn-sm"
              :disabled="busyId === item.id"
              @click="confirmingId = ''"
            >
              再想想
            </button>
          </template>
          <template v-else>
            <button
              v-if="!item.is_default"
              class="btn btn-outline btn-sm"
              data-testid="address-set-default"
              :disabled="!!busyId"
              @click="onSetDefault(item)"
            >
              设为默认
            </button>
            <button
              class="btn btn-ghost btn-sm"
              data-testid="address-edit"
              :disabled="!!busyId"
              @click="openEdit(item)"
            >
              编辑
            </button>
            <button
              class="btn btn-ghost btn-sm text-error"
              data-testid="address-remove"
              :disabled="!!busyId"
              @click="askRemove(item)"
            >
              删除
            </button>
          </template>
        </div>
      </div>
    </div>

    <FormModal :open="formOpen" :title="formTitle" @close="closeForm">
      <AddressForm :address="editing" @saved="onSaved" @cancel="closeForm" />
    </FormModal>
  </div>
</template>