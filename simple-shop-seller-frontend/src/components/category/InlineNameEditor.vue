<script setup lang="ts">
import { onMounted, ref } from 'vue'

import AppIcon from '../common/AppIcon.vue'

/**
 * 行内名称编辑表单：输入框 + 确认 + 取消，挂载即聚焦并全选。
 * 只收集输入；校验与请求由宿主页面处理，提交成功由宿主卸载本组件。
 */
const props = withDefaults(
  defineProps<{
    initialValue?: string
    placeholder?: string
    /** 输入框的无障碍名称；不用 ariaLabel 命名，避免模板里 aria-label 被当作 HTML 属性。 */
    label: string
    busy?: boolean
  }>(),
  { initialValue: '', placeholder: undefined, busy: false },
)

const emit = defineEmits<{
  submit: [name: string]
  cancel: []
}>()

const draft = ref(props.initialValue)
const inputEl = ref<HTMLInputElement | null>(null)

onMounted(() => {
  inputEl.value?.focus()
  inputEl.value?.select()
})

function onSubmit(): void {
  emit('submit', draft.value.trim())
}
</script>

<template>
  <form class="flex flex-1 items-center gap-1" @submit.prevent="onSubmit">
    <input
      ref="inputEl"
      v-model="draft"
      class="input input-xs min-w-0 flex-1"
      type="text"
      maxlength="60"
      :placeholder="placeholder"
      :aria-label="label"
      :disabled="busy"
      @keydown.esc="emit('cancel')"
    />
    <button
      type="submit"
      class="btn btn-square btn-xs btn-primary"
      :disabled="busy"
      title="保存"
      :aria-label="`保存：${label}`"
    >
      <AppIcon name="confirm" class="text-[12px]" />
    </button>
    <button
      type="button"
      class="btn btn-square btn-xs btn-ghost"
      :disabled="busy"
      title="取消（Esc）"
      aria-label="取消编辑"
      @click="emit('cancel')"
    >
      <AppIcon name="close" class="text-[12px]" />
    </button>
  </form>
</template>