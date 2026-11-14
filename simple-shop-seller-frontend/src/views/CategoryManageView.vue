<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  createCategoryL1,
  createCategoryL2,
  deleteCategoryL1,
  deleteCategoryL2,
  renameCategoryL1,
  renameCategoryL2,
} from '../api/category'
import CategoryCard from '../components/category/CategoryCard.vue'
import type { CategoryEditTarget } from '../components/category/categoryEdit'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import { M10, apiCodeMessage } from '../constants/messages'
import { useCategoryTree } from '../composables/useCategoryTree'
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

const { tree, loading, error, refresh } = useCategoryTree()
const busy = ref(false)

const newL1Name = ref('')

/** 全局同一时刻只允许一个行内编辑；开始新编辑自动收起上一个（草稿丢弃）。 */
const activeEdit = ref<CategoryEditTarget | null>(null)

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

// 类别管理是增删改入口：每次进入强制刷新，保证看到的是最新树，并更新共享缓存。
onMounted(() => void refresh())

async function run(action: () => Promise<unknown>, successText: string): Promise<boolean> {
  busy.value = true
  try {
    await action()
    await refresh()
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

function startEdit(target: CategoryEditTarget): void {
  activeEdit.value = target
}

function cancelEdit(): void {
  activeEdit.value = null
}

/** 卡片只负责收集输入，校验与请求由页面统一处理，成功才收起编辑态；失败保留输入可重试。 */
function onRename(target: { level: Level; id: string }, name: string): void {
  const result = validateCategoryName(name)
  if (!result.ok) {
    toast.error(apiCodeMessage(result.code))
    return
  }
  const action =
    target.level === 'l1'
      ? () => renameCategoryL1(target.id, result.value)
      : () => renameCategoryL2(target.id, result.value)
  void run(action, '已改名').then((ok) => {
    if (ok) {
      activeEdit.value = null
    }
  })
}

function onCreate(l1Id: string, name: string): void {
  const result = validateCategoryName(name)
  if (!result.ok) {
    toast.error(apiCodeMessage(result.code))
    return
  }
  void run(() => createCategoryL2(l1Id, result.value), '已创建二级类别').then((ok) => {
    if (ok) {
      activeEdit.value = null
    }
  })
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

    <!-- 多列瀑布流：卡片按内容自然高度排列，不再被同行最高卡片撑高；列优先顺序对无序类别可接受。 -->
    <div
      v-else
      class="columns-[240px] gap-4 [&>*]:mb-4 [&>*]:break-inside-avoid"
      :class="{ 'pointer-events-none opacity-60': busy }"
    >
      <CategoryCard
        v-for="l1 in tree"
        :key="l1.id"
        :l1="l1"
        :active-edit="activeEdit"
        :busy="busy"
        @edit="startEdit"
        @cancel-edit="cancelEdit"
        @rename="onRename"
        @create="onCreate"
        @remove="onRemove"
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
