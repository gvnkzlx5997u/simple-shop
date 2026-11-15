<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="panel py-10 text-center" data-testid="empty-state">
    <p class="mb-2 text-base font-semibold">{{ title }}</p>
    <p v-if="description" class="muted">{{ description }}</p>
    <div v-if="$slots.default" class="mt-3">
      <slot />
    </div>
  </div>
</template>