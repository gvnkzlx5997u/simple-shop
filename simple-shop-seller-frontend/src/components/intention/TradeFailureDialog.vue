<script setup lang="ts">
import { ref, watch } from 'vue'

import type { Disposal } from '../../api/types'
import { DISPOSAL_LABELS } from '../../constants/labels'
import { apiCodeMessage, M10 } from '../../constants/messages'
import { validateDisposal } from '../../validation'

/** 标记交易失败:选择裁决方式 VOIDED(作废,终态)或 REQUEUED(重排队尾)。 */
const props = withDefaults(
  defineProps<{
    open: boolean
    loading?: boolean
  }>(),
  { loading: false },
)

const emit = defineEmits<{
  submit: [disposal: Disposal]
  cancel: []
}>()

const disposal = ref('')
const error = ref<string | null>(null)

watch(
  () => props.open,
  (open) => {
    if (open) {
      disposal.value = ''
      error.value = null
    }
  },
)

function submit(): void {
  const result = validateDisposal(disposal.value)
  if (!result.ok) {
    error.value = apiCodeMessage(result.code)
    return
  }
  error.value = null
  emit('submit', result.value)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-mask" @click.self="emit('cancel')">
      <div class="dialog" role="dialog" aria-modal="true">
        <h3 class="dialog__title">标记交易失败</h3>
        <p class="dialog__message">{{ M10.confirmTradeFailure }}</p>

        <div class="field">
          <span class="field__label">处理方式</span>
          <label class="radio">
            <input v-model="disposal" type="radio" value="VOIDED" />
            <span>作废(终态,该意向结束)</span>
          </label>
          <label class="radio">
            <input v-model="disposal" type="radio" value="REQUEUED" />
            <span>重新排队(回到队尾,下单时间不变)</span>
          </label>
          <p v-if="error" class="field__error">{{ error }}</p>
        </div>

        <div class="dialog__actions">
          <button type="button" class="btn" :disabled="loading" @click="emit('cancel')">
            取消
          </button>
          <button type="button" class="btn btn--primary" :disabled="loading" @click="submit">
            确认
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
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  z-index: 40;
}

.dialog {
  width: 100%;
  max-width: 420px;
  margin: 16px;
  padding: 20px;
  border-radius: 8px;
  background: var(--surface);
}

.dialog__title {
  margin: 0 0 8px;
  font-size: 16px;
}

.dialog__message {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.radio {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0;
  cursor: pointer;
}
</style>
