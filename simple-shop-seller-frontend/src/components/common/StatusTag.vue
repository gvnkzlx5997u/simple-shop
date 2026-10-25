<script setup lang="ts">
import type { TagTone } from '../../constants/labels'

withDefaults(
  defineProps<{
    label: string
    tone?: TagTone
  }>(),
  { tone: 'default' },
)
</script>

<template>
  <span class="tag" :class="'tag--' + tone">{{ label }}</span>
</template>

<style scoped>
.tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  border: 1px solid var(--border);
  background: #f3f4f6;
  color: var(--muted);
}

.tag--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: var(--success);
}

.tag--warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: var(--warning);
}

.tag--danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: var(--danger);
}

.tag--info {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: var(--primary);
}
</style>
