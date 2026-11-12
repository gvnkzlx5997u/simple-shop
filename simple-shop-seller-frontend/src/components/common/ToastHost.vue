<script setup lang="ts">
import { useToast } from '../../composables/useToast'
import type { ToastTone } from '../../composables/useToast'

const { items, remove } = useToast()

const TONE_CLASS: Record<ToastTone, string> = {
  success: 'alert-success',
  error: 'alert-error',
  info: 'alert-info',
}
</script>

<template>
  <Teleport to="body">
    <div class="toast toast-top toast-center z-50">
      <TransitionGroup name="toast">
        <div
          v-for="item in items"
          :key="item.id"
          class="alert max-w-[420px] cursor-pointer shadow-lg"
          :class="TONE_CLASS[item.tone]"
          role="status"
          data-testid="toast-item"
          @click="remove(item.id)"
        >
          {{ item.message }}
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
