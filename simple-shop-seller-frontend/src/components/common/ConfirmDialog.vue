<script setup lang="ts">
withDefaults(
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
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-mask" @click.self="emit('cancel')">
      <div class="dialog" role="dialog" aria-modal="true">
        <h3 class="dialog__title">{{ title }}</h3>
        <p class="dialog__message">{{ message }}</p>
        <div class="dialog__actions">
          <button type="button" class="btn" :disabled="loading" @click="emit('cancel')">
            {{ cancelText }}
          </button>
          <button
            type="button"
            class="btn"
            :class="danger ? 'btn--danger' : 'btn--primary'"
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

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.45);
}

.dialog {
  width: 100%;
  max-width: 420px;
  padding: 20px;
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.24);
}

.dialog__title {
  margin-bottom: 10px;
  font-size: 16px;
}

.dialog__message {
  margin: 0 0 18px;
  color: var(--text);
  white-space: pre-line;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
