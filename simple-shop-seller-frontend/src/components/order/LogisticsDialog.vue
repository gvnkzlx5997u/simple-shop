<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'

import type { ShipLogisticsRequest } from '../../api/types'
import { CARRIER_OTHER, CARRIER_PRESETS, resolveCarrierSelection } from '../../constants/carriers'
import { M10 } from '../../constants/messages'
import { validateLogisticsForm } from '../../validation/logistics'
import type { LogisticsFormErrors } from '../../validation/logistics'
import AppIcon from '../common/AppIcon.vue'

/**
 * 发货与改物流共用一个弹窗：两者请求体完全一样，差别只有标题、说明与提交后的状态变化。
 * 分开写会得到两份几乎相同的表单，改校验时必然漏掉一份。
 *
 * 承运商按需求做成「固定清单 + 其他」：清单见 constants/carriers.ts，
 * 选「其他」时再出现一个名称输入框（文档要求"选择其他时填写名称"）。
 *
 * 校验在本地先做（必填、长度、控制字符），通过后由页面负责发请求；
 * 弹窗自己不发请求，提交中的禁用与结果提示交给页面，避免两处都管 loading。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    mode: 'ship' | 'edit'
    carrier?: string | null
    trackingNo?: string | null
    remark?: string | null
    loading?: boolean
  }>(),
  { carrier: '', trackingNo: '', remark: '', loading: false },
)

const emit = defineEmits<{
  submit: [payload: ShipLogisticsRequest]
  cancel: []
}>()

/** '' 表示还没选；预设值表示选了某家；CARRIER_OTHER 表示选「其他」。 */
const carrierChoice = ref('')
const carrierOther = ref('')
const trackingNo = ref('')
const remark = ref('')
const errors = ref<LogisticsFormErrors>({})

const isOther = computed(() => carrierChoice.value === CARRIER_OTHER)

/** 真正要提交的承运商名称：选「其他」时取手填的那个框。 */
const carrierValue = computed(() =>
  isOther.value ? carrierOther.value.trim() : carrierChoice.value,
)

function reset(): void {
  const selection = resolveCarrierSelection(props.carrier)
  carrierChoice.value = selection.choice
  carrierOther.value = selection.other
  trackingNo.value = props.trackingNo ?? ''
  remark.value = props.remark ?? ''
  errors.value = {}
}

// 每次打开都按传入值重置：改物流时要看到当前承运商与单号，而不是上一次填的内容。
watch(
  () => props.open,
  (open) => {
    if (open) {
      reset()
    }
  },
  { immediate: true },
)

/**
 * 用户一动输入就清掉上一次的报错：留着旧的「请选择承运商」贴在已选好的框下面，
 * 看起来像刚选的还是错的。真正的判定仍在提交时做。
 */
watch([carrierChoice, carrierOther, trackingNo, remark], () => {
  if (Object.values(errors.value).some(Boolean)) {
    errors.value = {}
  }
})

function onSubmit(): void {
  if (props.loading) {
    return
  }
  const result = validateLogisticsForm({
    carrier: carrierValue.value,
    trackingNo: trackingNo.value,
    remark: remark.value,
  })
  // 纯校验器只知道「承运商是空串」，分不清是没选还是选了「其他」却没填名字，
  // 这里按控件状态把文案补精确。
  const fieldErrors: LogisticsFormErrors = { ...result.errors }
  if (carrierChoice.value === '') {
    fieldErrors.carrier = M10.carrierRequired
  } else if (isOther.value && carrierOther.value.trim().length === 0) {
    fieldErrors.carrier = M10.carrierOtherRequired
  }
  errors.value = fieldErrors
  if (result.values === null || fieldErrors.carrier) {
    return
  }
  emit('submit', { ...result.values, carrier: carrierValue.value })
}

/** 自绘 modal 没有原生 dialog 的 Esc 行为，这里补上；提交中不允许关闭。 */
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
      :aria-label="mode === 'ship' ? M10.shipTitle : M10.logisticsTitle"
      data-testid="logistics-dialog"
      @click.self="loading ? undefined : emit('cancel')"
    >
      <div class="modal-box max-w-[480px] shadow-xl">
        <h3 class="text-base font-semibold">
          {{ mode === 'ship' ? M10.shipTitle : M10.logisticsTitle }}
        </h3>
        <p class="mt-2 text-xs leading-relaxed text-base-content/60">
          {{ mode === 'ship' ? M10.shipHint : M10.logisticsHint }}
        </p>

        <form class="mt-4 flex flex-col gap-3" @submit.prevent="onSubmit">
          <div class="form-control w-full">
            <label class="mb-1 text-xs text-base-content/60" for="logistics-carrier">承运商</label>
            <select
              id="logistics-carrier"
              v-model="carrierChoice"
              class="select select-sm w-full"
              :disabled="loading"
              data-testid="logistics-carrier"
            >
              <option value="">{{ M10.carrierSelectPlaceholder }}</option>
              <option v-for="preset in CARRIER_PRESETS" :key="preset.value" :value="preset.value">
                {{ preset.label }}
              </option>
              <option :value="CARRIER_OTHER">其他（手动填写）</option>
            </select>
            <!-- 只在选「其他」时出现：文档要求"选其他时填写名称" -->
            <input
              v-if="isOther"
              v-model="carrierOther"
              class="input input-sm mt-2 w-full"
              type="text"
              :placeholder="M10.carrierOtherPlaceholder"
              :disabled="loading"
              data-testid="logistics-carrier-other"
            />
            <span v-if="errors.carrier" class="mt-1 text-xs text-error">{{ errors.carrier }}</span>
          </div>

          <label class="form-control w-full">
            <span class="mb-1 text-xs text-base-content/60">运单号</span>
            <textarea
              v-model="trackingNo"
              class="textarea textarea-sm w-full font-mono"
              rows="2"
              :placeholder="M10.trackingNoPlaceholder"
              :disabled="loading"
              data-testid="logistics-tracking-no"
            ></textarea>
            <span v-if="errors.trackingNo" class="mt-1 text-xs text-error">
              {{ errors.trackingNo }}
            </span>
            <span v-else class="mt-1 text-xs text-base-content/50">
              一个订单分多个包裹时，一行写一个单号。
            </span>
          </label>

          <label class="form-control w-full">
            <span class="mb-1 text-xs text-base-content/60">备注（可选）</span>
            <textarea
              v-model="remark"
              class="textarea textarea-sm w-full"
              rows="2"
              :placeholder="M10.logisticsRemarkPlaceholder"
              :disabled="loading"
              data-testid="logistics-remark"
            ></textarea>
            <span v-if="errors.remark" class="mt-1 text-xs text-error">{{ errors.remark }}</span>
          </label>

          <div class="modal-action">
            <button
              type="button"
              class="btn btn-sm btn-outline"
              :disabled="loading"
              @click="emit('cancel')"
            >
              取消
            </button>
            <button
              type="submit"
              class="btn btn-sm btn-primary"
              :disabled="loading"
              data-testid="logistics-submit"
            >
              <AppIcon v-if="loading" name="refresh" class="animate-spin" />
              {{ loading ? '提交中…' : mode === 'ship' ? '确认发货' : '保存' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
