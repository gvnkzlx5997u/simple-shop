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
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import { M10, apiCodeMessage } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { validateCategoryName } from '../validation'

/** 类别为固定两级；商品只能挂二级类别。被引用或有子级时删除会被服务端拒绝。 */

const toast = useToast()

const tree = ref<CategoryNodeData[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

const newL1Name = ref('')
const newL2Names = ref<Record<string, string>>({})

type RenameTarget = { level: 'l1' | 'l2'; id: string }
const renaming = ref<RenameTarget | null>(null)
const renameInput = ref('')

type DeleteTarget = { level: 'l1' | 'l2'; id: string; name: string }
const pendingDelete = ref<DeleteTarget | null>(null)

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
    for (const l1 of tree.value) {
      if (!(l1.id in newL2Names.value)) {
        newL2Names.value[l1.id] = ''
      }
    }
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

function submitNewL2(l1: CategoryNodeData): void {
  const result = validateCategoryName(newL2Names.value[l1.id] ?? '')
  if (!result.ok) {
    toast.error(apiCodeMessage(result.code))
    return
  }
  void run(() => createCategoryL2(l1.id, result.value), '已创建二级类别').then((ok) => {
    if (ok) {
      newL2Names.value[l1.id] = ''
    }
  })
}

function startRename(level: 'l1' | 'l2', node: CategoryNodeData): void {
  renaming.value = { level, id: node.id }
  renameInput.value = node.name
}

function cancelRename(): void {
  renaming.value = null
  renameInput.value = ''
}

function submitRename(): void {
  const target = renaming.value
  if (!target) {
    return
  }
  const result = validateCategoryName(renameInput.value)
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
      cancelRename()
    }
  })
}

function askDelete(level: 'l1' | 'l2', node: CategoryNodeData): void {
  if (level === 'l1' && node.children.length > 0) {
    toast.error('请先删除该类别下的二级类别')
    return
  }
  pendingDelete.value = { level, id: node.id, name: node.name }
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
  <div class="page">
    <h1 class="page__title">类别管理</h1>
    <p class="muted page__hint">
      类别为固定两级，商品必须挂在二级类别下；删除被商品引用的类别会被拒绝。
    </p>

    <p v-if="error" class="page__error">{{ error }}</p>

    <form class="card new-l1" @submit.prevent="submitNewL1">
      <input
        v-model="newL1Name"
        class="input"
        type="text"
        maxlength="60"
        placeholder="新一级类别名称"
        :disabled="busy"
      />
      <button type="submit" class="btn btn--primary" :disabled="busy">添加一级类别</button>
    </form>

    <div v-if="loading && tree.length === 0" class="card muted">加载中…</div>
    <div v-else-if="tree.length === 0" class="card muted empty">{{ M10.emptyCategories }}</div>

    <section v-for="l1 in tree" :key="l1.id" class="card l1">
      <div class="l1__header">
        <template v-if="renaming && renaming.level === 'l1' && renaming.id === l1.id">
          <input
            v-model="renameInput"
            class="input l1__rename-input"
            type="text"
            maxlength="60"
            @keyup.enter="submitRename"
            @keyup.esc="cancelRename"
          />
          <button
            type="button"
            class="btn btn--small btn--primary"
            :disabled="busy"
            @click="submitRename"
          >
            保存
          </button>
          <button type="button" class="btn btn--small" :disabled="busy" @click="cancelRename">
            取消
          </button>
        </template>
        <template v-else>
          <span class="l1__name">{{ l1.name }}</span>
          <div class="row-actions">
            <button
              type="button"
              class="btn btn--small"
              :disabled="busy"
              @click="startRename('l1', l1)"
            >
              改名
            </button>
            <button
              type="button"
              class="btn btn--small btn--danger"
              :disabled="busy"
              @click="askDelete('l1', l1)"
            >
              删除
            </button>
          </div>
        </template>
      </div>

      <ul class="l1__children">
        <li v-for="l2 in l1.children" :key="l2.id" class="l2">
          <template v-if="renaming && renaming.level === 'l2' && renaming.id === l2.id">
            <input
              v-model="renameInput"
              class="input l1__rename-input"
              type="text"
              maxlength="60"
              @keyup.enter="submitRename"
              @keyup.esc="cancelRename"
            />
            <button
              type="button"
              class="btn btn--small btn--primary"
              :disabled="busy"
              @click="submitRename"
            >
              保存
            </button>
            <button type="button" class="btn btn--small" :disabled="busy" @click="cancelRename">
              取消
            </button>
          </template>
          <template v-else>
            <span class="l2__name">{{ l2.name }}</span>
            <div class="row-actions">
              <button
                type="button"
                class="btn btn--small"
                :disabled="busy"
                @click="startRename('l2', l2)"
              >
                改名
              </button>
              <button
                type="button"
                class="btn btn--small btn--danger"
                :disabled="busy"
                @click="askDelete('l2', l2)"
              >
                删除
              </button>
            </div>
          </template>
        </li>
        <li v-if="l1.children.length === 0" class="muted l2-empty">
          {{ M10.emptyCategoryChildren }}
        </li>
      </ul>

      <form class="l1__add" @submit.prevent="submitNewL2(l1)">
        <input
          v-model="newL2Names[l1.id]"
          class="input"
          type="text"
          maxlength="60"
          placeholder="新二级类别名称"
          :disabled="busy"
        />
        <button type="submit" class="btn" :disabled="busy">添加二级类别</button>
      </form>
    </section>

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

<style scoped>
.page__title {
  margin-bottom: 8px;
}

.page__hint {
  margin: 0 0 16px;
  font-size: 13px;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.new-l1 {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.new-l1 .input {
  flex: 1;
}

.l1 {
  margin-bottom: 16px;
  padding: 12px 16px;
}

.l1__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.l1__name {
  font-weight: 600;
}

.l1__rename-input {
  flex: 1;
}

.row-actions {
  display: flex;
  gap: 8px;
}

.l1__children {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.l2 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0 6px 16px;
  border-left: 2px solid var(--border);
}

.l2__name {
  flex: 1;
}

.l2-empty {
  padding: 6px 0 6px 16px;
  border-left: 2px solid var(--border);
  font-size: 13px;
}

.l1__add {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.l1__add .input {
  flex: 1;
}

.empty {
  padding: 48px;
  text-align: center;
}
</style>
