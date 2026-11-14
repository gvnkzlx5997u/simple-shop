<script setup lang="ts">
import { computed } from 'vue'

import type { CategoryNodeData } from '../../api/types'
import AppIcon from '../common/AppIcon.vue'
import InlineNameEditor from './InlineNameEditor.vue'
import type { CategoryEditTarget } from './categoryEdit'

/**
 * 一级类别卡片：一级名称 + 其下二级类别列表。
 * 编辑态由页面持有（activeEdit，全局同一时刻只有一个），卡片只据此渲染编辑表单并转发事件；
 * 校验与请求都在页面，成功后页面清空 activeEdit 即收起编辑。
 */
const props = withDefaults(
  defineProps<{
    l1: CategoryNodeData
    activeEdit: CategoryEditTarget | null
    busy?: boolean
  }>(),
  { busy: false },
)

type Level = 'l1' | 'l2'

const emit = defineEmits<{
  edit: [target: CategoryEditTarget]
  'cancel-edit': []
  rename: [target: { level: Level; id: string }, name: string]
  create: [l1Id: string, name: string]
  remove: [target: { level: Level; id: string; name: string }]
}>()

const editingL1 = computed(
  () =>
    props.activeEdit?.kind === 'rename' &&
    props.activeEdit.level === 'l1' &&
    props.activeEdit.id === props.l1.id,
)

const addingL2 = computed(
  () => props.activeEdit?.kind === 'add' && props.activeEdit.l1Id === props.l1.id,
)

function isEditingL2(id: string): boolean {
  return (
    props.activeEdit?.kind === 'rename' &&
    props.activeEdit.level === 'l2' &&
    props.activeEdit.id === id
  )
}

function startRename(level: Level, id: string): void {
  emit('edit', { kind: 'rename', level, id })
}

function startAdd(): void {
  emit('edit', { kind: 'add', l1Id: props.l1.id })
}

function submitRename(level: Level, id: string, name: string): void {
  emit('rename', { level, id }, name)
}

function submitAdd(name: string): void {
  emit('create', props.l1.id, name)
}
</script>

<template>
  <div class="card flex flex-col border border-base-300 bg-base-100">
    <div class="flex items-center gap-1 border-b border-base-300 px-2.5 py-2">
      <InlineNameEditor
        v-if="editingL1"
        :initial-value="l1.name"
        label="一级类别名称"
        :busy="busy"
        @submit="submitRename('l1', l1.id, $event)"
        @cancel="emit('cancel-edit')"
      />

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
          @click="startRename('l1', l1.id)"
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

    <div class="flex flex-col gap-0.5 p-2">
      <div
        v-for="l2 in l1.children"
        :key="l2.id"
        class="flex items-center gap-1 rounded-md px-1 py-0.5 hover:bg-base-200"
      >
        <InlineNameEditor
          v-if="isEditingL2(l2.id)"
          :initial-value="l2.name"
          label="二级类别名称"
          :busy="busy"
          @submit="submitRename('l2', l2.id, $event)"
          @cancel="emit('cancel-edit')"
        />

        <template v-else>
          <span class="min-w-0 flex-1 truncate text-sm" :title="l2.name">{{ l2.name }}</span>
          <button
            type="button"
            class="btn btn-square btn-xs btn-ghost text-base-content/50 hover:text-primary"
            :disabled="busy"
            title="改名"
            aria-label="重命名二级类别"
            @click="startRename('l2', l2.id)"
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

      <div class="pt-1.5">
        <InlineNameEditor
          v-if="addingL2"
          placeholder="二级类别名称"
          label="新二级类别名称"
          :busy="busy"
          @submit="submitAdd"
          @cancel="emit('cancel-edit')"
        />

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