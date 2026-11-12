<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { ComponentPublicInstance } from 'vue'

import type { CategoryNodeData } from '../../api/types'
import AppIcon from '../common/AppIcon.vue'

/**
 * 一级类别卡片：一级名称 + 其下二级类别列表。
 * 改名 / 删除 / 新增都在卡片内就地编辑，异步与校验交给父级（emit 带 done 回调，成功才收起编辑态）。
 */
const props = withDefaults(
  defineProps<{
    l1: CategoryNodeData
    busy?: boolean
  }>(),
  { busy: false },
)

type Level = 'l1' | 'l2'

interface RenameTarget {
  level: Level
  id: string
  name: string
}

const emit = defineEmits<{
  rename: [target: RenameTarget, done: (ok: boolean) => void]
  remove: [target: { level: Level; id: string; name: string }]
  create: [payload: { l1Id: string; name: string }, done: (ok: boolean) => void]
}>()

const editing = ref<{ level: Level; id: string } | null>(null)
const adding = ref(false)
const draft = ref('')
const inputEl = ref<HTMLInputElement | null>(null)

/** 列表里的输入框在 v-for 内，用字符串 ref 会被收集成数组，这里统一用函数 ref。 */
function setInputEl(el: Element | ComponentPublicInstance | null): void {
  inputEl.value = el instanceof HTMLInputElement ? el : null
}

function isEditing(level: Level, id: string): boolean {
  return editing.value?.level === level && editing.value.id === id
}

async function focusInput(): Promise<void> {
  await nextTick()
  inputEl.value?.focus()
  inputEl.value?.select()
}

async function startRename(level: Level, node: { id: string; name: string }): Promise<void> {
  adding.value = false
  editing.value = { level, id: node.id }
  draft.value = node.name
  await focusInput()
}

async function startAdd(): Promise<void> {
  editing.value = null
  adding.value = true
  draft.value = ''
  await focusInput()
}

function cancel(): void {
  editing.value = null
  adding.value = false
  draft.value = ''
}

function submitRename(): void {
  const target = editing.value
  if (!target) {
    return
  }
  emit(
    'rename',
    { level: target.level, id: target.id, name: draft.value.trim() },
    (ok) => {
      if (ok) {
        cancel()
      }
    },
  )
}

function submitAdd(): void {
  emit('create', { l1Id: props.l1.id, name: draft.value.trim() }, (ok) => {
    if (ok) {
      cancel()
    }
  })
}
</script>

<template>
  <div class="card flex h-full flex-col border border-base-300 bg-base-100">
    <div class="flex items-center gap-1 border-b border-base-300 px-2.5 py-2">
      <form
        v-if="isEditing('l1', l1.id)"
        class="flex flex-1 items-center gap-1"
        @submit.prevent="submitRename"
      >
        <input
          :ref="setInputEl"
          v-model="draft"
          class="input input-xs min-w-0 flex-1"
          type="text"
          maxlength="60"
          aria-label="一级类别名称"
          @keydown.esc="cancel"
        />
        <button
          type="submit"
          class="btn btn-square btn-xs btn-primary"
          :disabled="busy"
          title="保存"
          aria-label="保存一级类别名称"
        >
          <AppIcon name="confirm" class="text-[12px]" />
        </button>
        <button
          type="button"
          class="btn btn-square btn-xs btn-ghost"
          :disabled="busy"
          title="取消（Esc）"
          aria-label="取消改名"
          @click="cancel"
        >
          <AppIcon name="close" class="text-[12px]" />
        </button>
      </form>

      <template v-else>
        <span class="min-w-0 flex-1 truncate text-sm font-semibold" :title="l1.name">
          {{ l1.name }}
        </span>
        <button
          type="button"
          class="btn btn-square btn-xs btn-ghost text-base-content/50 hover:text-primary"
          :disabled="busy"
          title="改名"
          aria-label="重命名一级类别"
          @click="startRename('l1', l1)"
        >
          <AppIcon name="edit" class="text-[13px]" />
        </button>
        <!-- 有二级类别时不给删除入口，避免点了才被告知「请先删除子级」 -->
        <button
          v-if="l1.children.length === 0"
          type="button"
          class="btn btn-square btn-xs btn-ghost text-base-content/50 hover:text-error"
          :disabled="busy"
          title="删除"
          aria-label="删除一级类别"
          @click="emit('remove', { level: 'l1', id: l1.id, name: l1.name })"
        >
          <AppIcon name="delete" class="text-[13px]" />
        </button>
      </template>
    </div>

    <div class="flex flex-1 flex-col gap-0.5 p-2">
      <div
        v-for="l2 in l1.children"
        :key="l2.id"
        class="flex items-center gap-1 rounded-md px-1 py-0.5 hover:bg-base-200"
      >
        <form
          v-if="isEditing('l2', l2.id)"
          class="flex flex-1 items-center gap-1"
          @submit.prevent="submitRename"
        >
          <input
            :ref="setInputEl"
            v-model="draft"
            class="input input-xs min-w-0 flex-1"
            type="text"
            maxlength="60"
            aria-label="二级类别名称"
            @keydown.esc="cancel"
          />
          <button
            type="submit"
            class="btn btn-square btn-xs btn-primary"
            :disabled="busy"
            title="保存"
            aria-label="保存二级类别名称"
          >
            <AppIcon name="confirm" class="text-[12px]" />
          </button>
          <button
            type="button"
            class="btn btn-square btn-xs btn-ghost"
            :disabled="busy"
            title="取消（Esc）"
            aria-label="取消改名"
            @click="cancel"
          >
            <AppIcon name="close" class="text-[12px]" />
          </button>
        </form>

        <template v-else>
          <span class="min-w-0 flex-1 truncate text-sm" :title="l2.name">{{ l2.name }}</span>
          <button
            type="button"
            class="btn btn-square btn-xs btn-ghost text-base-content/50 hover:text-primary"
            :disabled="busy"
            title="改名"
            aria-label="重命名二级类别"
            @click="startRename('l2', l2)"
          >
            <AppIcon name="edit" class="text-[13px]" />
          </button>
          <button
            type="button"
            class="btn btn-square btn-xs btn-ghost text-base-content/50 hover:text-error"
            :disabled="busy"
            title="删除"
            aria-label="删除二级类别"
            @click="emit('remove', { level: 'l2', id: l2.id, name: l2.name })"
          >
            <AppIcon name="delete" class="text-[13px]" />
          </button>
        </template>
      </div>

      <p v-if="l1.children.length === 0" class="px-1 py-1 text-xs text-base-content/50">
        暂无二级类别
      </p>

      <div class="mt-auto pt-1.5">
        <form v-if="adding" class="flex items-center gap-1" @submit.prevent="submitAdd">
          <input
            :ref="setInputEl"
            v-model="draft"
            class="input input-xs min-w-0 flex-1"
            type="text"
            maxlength="60"
            placeholder="二级类别名称"
            aria-label="新二级类别名称"
            @keydown.esc="cancel"
          />
          <button
            type="submit"
            class="btn btn-square btn-xs btn-primary"
            :disabled="busy"
            title="确认添加"
            aria-label="确认添加二级类别"
          >
            <AppIcon name="confirm" class="text-[12px]" />
          </button>
          <button
            type="button"
            class="btn btn-square btn-xs btn-ghost"
            :disabled="busy"
            title="取消（Esc）"
            aria-label="取消添加"
            @click="cancel"
          >
            <AppIcon name="close" class="text-[12px]" />
          </button>
        </form>

        <button
          v-else
          type="button"
          class="btn btn-xs btn-ghost w-full justify-center border border-dashed border-base-300 text-base-content/40 hover:border-primary hover:text-primary"
          :disabled="busy"
          title="添加二级类别"
          aria-label="添加二级类别"
          @click="startAdd"
        >
          <AppIcon name="plus" class="text-[12px]" />
        </button>
      </div>
    </div>
  </div>
</template>
