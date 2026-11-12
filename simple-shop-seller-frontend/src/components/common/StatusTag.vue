<script setup lang="ts">
import type { TagTone } from '../../constants/labels'

withDefaults(
  defineProps<{
    label: string
    tone?: TagTone
  }>(),
  { tone: 'default' },
)

/** 只负责把 TagTone 翻成 daisyUI 的 badge 色；具体状态用哪个 tone 仍由 constants/labels.ts 决定。 */
const TONE_CLASS: Record<TagTone, string> = {
  // 中性态用 ghost：底色与文字都跟着 base-* 走，深浅主题都读得出来。
  default: 'badge-ghost',
  success: 'badge-soft badge-success',
  warning: 'badge-soft badge-warning',
  danger: 'badge-soft badge-error',
  info: 'badge-soft badge-info',
}
</script>

<template>
  <span class="badge badge-sm" :class="TONE_CLASS[tone]" data-testid="status-tag">
    {{ label }}
  </span>
</template>
