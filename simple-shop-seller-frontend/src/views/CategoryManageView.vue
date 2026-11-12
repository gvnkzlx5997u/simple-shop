<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  createCategoryL1,
  createCategoryL2,
  deleteCategoryL1,
  deleteCategoryL2,
  listCategoryTree,
  renameCategoryL1,
  renameCategoryL2,
} from '../api/category'
import type { CategoryNodeData } from '../api/types'
import CategoryCard from '../components/category/CategoryCard.vue'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import { M10, apiCodeMessage } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { validateCategoryName } from '../validation'

/** 类别为固定两级；商品只能挂二级类别。被引用或有子级时删除会被服务端拒绝。 */

type Level = 'l1' | 'l2'

interface CategoryTarget {
  level: Level
  id: string
  name: string
}

const toast = useToast()

const tree = ref<CategoryNodeData[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

const newL1Name = ref('')

const pendingDelete = ref<CategoryTarget | null>(null)

const deleteMessage = computed(() => {
  const target = pendingDelete.value
  if (!target) {
    return ''
  }
  return target.level === 'l1'
    ? `确定删除一级类别「${target.name}」？删除后不可恢复。`
    : `确定删除二级类别「${target.name}」？仍被商品引用时会删除失败。`
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    tree.value = await listCategoryTree()
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function run(action: () => Promise<unknown>, successText: string): Promise<boolean> {
  busy.value = true
  try {
    await action()
    await load()
    toast.success(successText)
    return true
  } catch (caught) {
    toast.error(errorMessage(caught))
    return false
  } finally {
    busy.value = false
  }
}

function submitNewL1(): void {
  const result = validateCategoryName(newL1Name.value)
  if (!result.ok) {
    toast.error(apiCodeMessage(result.code))
    return
  }
  void run(() => createCategoryL1(result.value), '已创建一级类别').then((ok) => {
    if (ok) {
      newL1Name.value = ''
    }
  })
}

/** 卡片只负责收集输入，校验与请求由页面统一处理，成功才让卡片收起编辑态。 */
function onRename(target: CategoryTarget, done: (ok: boolean) => void): void {
  const result = validateCategoryName(target.name)
  if (!result.ok) {
    toast.error(apiCodeMessage(result.code))
    done(false)
    return
  }
  const action =
    target.level === 'l1'
      ? () => renameCategoryL1(target.id, result.value)
      : () => renameCategoryL2(target.id, result.value)
  void run(action, '已改名').then(done)
}

function onCreate(payload: { l1Id: string; name: string }, done: (ok: boolean) => void): void {
  const result = validateCategoryName(payload.name)
  if (!result.ok) {
    toast.error(apiCodeMessage(result.code))
    done(false)
    return
  }
  void run(() => createCategoryL2(payload.l1Id, result.value), '已创建二级类别').then(done)
}

function onRemove(target: CategoryTarget): void {
  // 卡片已在有子级时隐藏一级删除入口，这里再兜一次，防止状态过期。
  if (target.level === 'l1') {
    const node = tree.value.find((item) => item.id === target.id)
    if (node && node.children.length > 0) {
      toast.error('请先删除该类别下的二级类别')
      return
    }
  }
  pendingDelete.value = target
}

function confirmDelete(): void {
  const target = pendingDelete.value
  if (!target) {
    return
  }
  const action =
    target.level === 'l1' ? () => deleteCategoryL1(target.id) : () => deleteCategoryL2(target.id)
  void run(action, '已删除').then((ok) => {
    if (ok) {
      pendingDelete.value = null
    }
  })
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header>
      <h1 class="text-lg font-semibold">类别管理</h1>
      <p class="text-xs text-base-content/60">
        类别为固定两级，商品必须挂在二级类别下；删除被商品引用的类别会被拒绝。
      </p>
    </header>

    <form class="panel flex flex-wrap items-center gap-2" @submit.prevent="submitNewL1">
      <input
        v-model="newL1Name"
        class="input input-sm min-w-[200px] flex-1"
        type="text"
        maxlength="60"
        placeholder="新一级类别名称"
        aria-label="新一级类别名称"
        :disabled="busy"
      />
      <button type="submit" class="btn btn-sm btn-primary" :disabled="busy">
        <AppIcon name="plus" />
        添加一级类别
      </button>
    </form>

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
    </div>

    <p v-if="loading && tree.length === 0" class="text-sm text-base-content/60">加载中…</p>

    <div v-else-if="tree.length === 0" class="panel py-14 text-center text-sm text-base-content/60">
      {{ M10.emptyCategories }}
    </div>

    <div
      v-else
      class="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] items-stretch gap-4"
      :class="{ 'pointer-events-none opacity-60': busy }"
    >
      <CategoryCard
        v-for="l1 in tree"
        :key="l1.id"
        :l1="l1"
        :busy="busy"
        @rename="onRename"
        @remove="onRemove"
        @create="onCreate"
      />
    </div>

    <ConfirmDialog
      :open="pendingDelete !== null"
      :message="deleteMessage"
      :danger="true"
      confirm-text="删除"
      :loading="busy"
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />
  </div>
</template>
