<script setup lang="ts">
import ToastHost from './components/common/ToastHost.vue'
</script>

<template>
  <!-- 登录页与后台之间只做淡入淡出；后台内部的页面切换在 AppLayout 里做。 -->
  <RouterView v-slot="{ Component }">
    <Transition name="fade" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
  <ToastHost />
</template>
