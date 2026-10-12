<script setup lang="ts">
import { useToast } from '../composables/useToast'

const { items, remove } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="toast-host">
      <TransitionGroup name="toast">
        <div
          v-for="item in items"
          :key="item.id"
          class="toast"
          :class="'toast--' + item.tone"
          role="status"
          @click="remove(item.id)"
        >
          {{ item.message }}
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 20px;
  left: 50%;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transform: translateX(-50%);
  pointer-events: none;
}

.toast {
  min-width: 220px;
  max-width: 420px;
  padding: 10px 16px;
  border-radius: 6px;
  color: #fff;
  background: #334155;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.2);
  pointer-events: auto;
  cursor: pointer;
}

.toast--success {
  background: var(--success);
}

.toast--error {
  background: var(--danger);
}

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
