<script setup lang="ts">
import { onUnmounted, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    message: string
    title?: string
    confirmText?: string
    cancelText?: string
    danger?: boolean
    loading?: boolean
  }>(),
  {
    title: '请确认',
    confirmText: '确认',
    cancelText: '取消',
    danger: false,
    loading: false,
  },
)

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

/** 弹窗是自绘的 modal-open，没有原生 dialog 的 Esc 行为，这里补上；提交中不允许关闭。 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open && !props.loading) {
    emit('cancel')
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown)
    } else {
      window.removeEventListener('keydown', onKeydown)
    }
  },
  { immediate: true },
)

onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="modal modal-open"
      role="dialog"
      aria-modal="true"
      @click.self="emit('cancel')"
    >
      <!-- 弹窗底色走 --color-surface-solid（见 base.css 的 @utility modal-box），实色就不再需要模糊。 -->
      <div class="modal-box max-w-[420px] shadow-xl">
        <h3 class="text-base font-semibold">{{ title }}</h3>
        <p class="mt-3 mb-6 whitespace-pre-line text-sm leading-relaxed">{{ message }}</p>
        <div class="modal-action">
          <button type="button" class="btn btn-sm btn-outline" :disabled="loading" @click="emit('cancel')">
            {{ cancelText }}
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :class="danger ? 'btn-error' : 'btn-primary'"
            :disabled="loading"
            @click="emit('confirm')"
          >
            {{ loading ? '处理中…' : confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
