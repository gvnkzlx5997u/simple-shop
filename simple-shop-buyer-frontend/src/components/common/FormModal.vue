<script setup lang="ts">
/**
 * 表单弹窗外壳：只管遮罩、居中容器与标题，字段和按钮由调用方放进插槽。
 *
 * 点遮罩即关闭——daisyUI 把 .modal-backdrop 放在 modal-box 之下并铺满整屏，
 * 所以「点弹窗外」等价于点它，不用自己算坐标。关闭的另一条路是表单自己的取消按钮。
 */
defineProps<{
  open: boolean
  title: string
}>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div
    v-if="open"
    class="modal modal-open modal-middle"
    role="dialog"
    aria-modal="true"
    :aria-label="title"
  >
    <div class="modal-box max-w-[440px]">
      <h2 class="mb-4 text-[15px]">{{ title }}</h2>
      <slot />
    </div>
    <div class="modal-backdrop" aria-hidden="true" @click="emit('close')"></div>
  </div>
</template>