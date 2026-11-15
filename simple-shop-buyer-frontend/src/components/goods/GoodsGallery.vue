<script setup lang="ts">
defineProps<{
  images: string[]
  active: string
  alt: string
}>()

const emit = defineEmits<{
  'update:active': [url: string]
}>()
</script>

<template>
  <div class="gallery">
    <img
      v-if="active"
      :src="active"
      :alt="alt"
      class="aspect-[4/3] w-full rounded-box bg-base-300 object-cover"
    />
    <div
      v-else
      class="flex aspect-[4/3] w-full items-center justify-center rounded-box bg-base-300 text-base-content/60"
    >
      暂无图片
    </div>

    <div v-if="images.length > 1" class="mt-2 flex flex-wrap gap-2">
      <img
        v-for="url in images"
        :key="url"
        :src="url"
        :class="[
          'size-16 cursor-pointer rounded-field border-2 object-cover',
          url === active ? 'border-primary' : 'border-transparent',
        ]"
        @click="emit('update:active', url)"
      />
    </div>
  </div>
</template>