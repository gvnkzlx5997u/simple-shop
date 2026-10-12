<script setup lang="ts">
import { ref, watch } from 'vue'

import type { TradeFailureRequest } from '../api/types'
import { apiCodeMessage, M10 } from '../constants/messages'
import { validateDisposal, validateFailReason } from '../validation'

const props = defineProps<{
  open: boolean
  intentionId: string
  loading?: boolean
}>()

const emit = defineEmits<{
  submit: [payload: TradeFailureRequest]
  cancel: []
}>()

const disposal = ref('')
const failReason = ref('')
const error = ref<string | null>(null)

watch(
  () => props.open,
  (open) => {
    if (open) {
      disposal.value = ''
      failReason.value = ''
      error.value = null
    }
  },
)

function submit(): void {
  const disposalResult = validateDisposal(disposal.value)
  if (!disposalResult.ok) {
    error.value = apiCodeMessage(disposalResult.code)
    return
  }
  const reasonResult = validateFailReason(failReason.value)
  if (!reasonResult.ok) {
    error.value = apiCodeMessage(reasonResult.code)
    return
  }
  error.value = null
  emit('submit', {
    intention_id: props.intentionId,
    disposal: disposalResult.value,
    fail_reason: reasonResult.value,
  })
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
            <input v-model="disposal" type="radio" value="voided" />
            <span>作废（终态，口令码失效）</span>
          </label>
          <label class="radio">
            <input v-model="disposal" type="radio" value="requeued" />
            <span>重新排队（回到队尾，口令码保持有效）</span>
          </label>
        </div>

        <div class="field">
          <label class="field__label" for="fail-reason">失败备注（可选，买家不可见）</label>
          <textarea
            id="fail-reason"
            v-model="failReason"
            class="textarea"
            maxlength="300"
            placeholder="最多 300 字符，允许换行"
          ></textarea>
        </div>

        <p v-if="error" class="field__error">{{ error }}</p>

        <div class="dialog__actions">
          <button type="button" class="btn" :disabled="loading" @click="emit('cancel')">
            取消
          </button>
          <button type="button" class="btn btn--danger" :disabled="loading" @click="submit">
            {{ loading ? '处理中…' : '确认标记失败' }}
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
  max-width: 460px;
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
  margin: 0 0 16px;
  color: var(--muted);
}

.radio {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
