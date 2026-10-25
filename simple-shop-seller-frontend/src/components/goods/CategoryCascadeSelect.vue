<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { listCategoryTree } from '../../api/category'
import type { CategoryNodeData } from '../../api/types'
import { M10 } from '../../constants/messages'
import { useToast } from '../../composables/useToast'
import { errorMessage } from '../../utils/error'

/**
 * 两级类别级联选择。
 *
 * l1Id/l2Id 双向绑定;一级变化时二级自动清空。后端列表筛选接受一级或二级 id
 * (一级匹配其下全部二级),因此两种粒度都可以直接作为筛选值。
 * mode=filter 提供「全部」选项;mode=required 用「请选择」占位(发布表单)。
 */
const props = withDefaults(
  defineProps<{
    mode?: 'filter' | 'required'
    disabled?: boolean
  }>(),
  { mode: 'required', disabled: false },
)

const l1Id = defineModel<string | null>('l1Id', { required: true })
const l2Id = defineModel<string | null>('l2Id', { required: true })

const toast = useToast()

const tree = ref<CategoryNodeData[]>([])
const loading = ref(false)
const loadFailed = ref(false)

const l2Options = computed(() => tree.value.find((l1) => l1.id === l1Id.value)?.children ?? [])

const l1Placeholder = computed(() => (props.mode === 'filter' ? '全部分类' : '请选择一级类别'))
const l2Placeholder = computed(() => (props.mode === 'filter' ? '全部二级' : '请选择二级类别'))

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    tree.value = await listCategoryTree()
  } catch (caught) {
    loadFailed.value = true
    toast.error(errorMessage(caught))
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 一级切换后,原二级不再属于新一级,清空由本组件负责。
watch(l1Id, () => {
  l2Id.value = null
})

watch(tree, (next) => {
  // 树重载后若当前选中项已不存在(如类别被删),同步清空避免提交失效 id。
  if (l1Id.value && !next.some((l1) => l1.id === l1Id.value)) {
    l1Id.value = null
    l2Id.value = null
  }
})
</script>

<template>
  <div class="category-cascade">
    <select
      v-model="l1Id"
      class="input category-cascade__select"
      :disabled="disabled || loading || loadFailed || tree.length === 0"
      :aria-label="l1Placeholder"
    >
      <option :value="null">{{ l1Placeholder }}</option>
      <option v-for="l1 in tree" :key="l1.id" :value="l1.id">{{ l1.name }}</option>
    </select>
    <select
      v-model="l2Id"
      class="input category-cascade__select"
      :disabled="disabled || !l1Id || l2Options.length === 0"
      :aria-label="l2Placeholder"
    >
      <option :value="null">{{ l2Placeholder }}</option>
      <option v-for="l2 in l2Options" :key="l2.id" :value="l2.id">{{ l2.name }}</option>
    </select>

    <p v-if="loadFailed" class="category-cascade__hint">
      {{ M10.categoryLoadFailed }}
      <button type="button" class="btn btn--small" @click="load">重试</button>
    </p>
    <p v-else-if="!loading && tree.length === 0" class="category-cascade__hint">
      {{ M10.categoryRequiredFirst }}
    </p>
  </div>
</template>

<style scoped>
.category-cascade {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.category-cascade__select {
  flex: 1 1 160px;
  min-width: 0;
}

.category-cascade__hint {
  flex-basis: 100%;
  margin: 0;
  font-size: 13px;
  color: var(--warning);
}
</style>
