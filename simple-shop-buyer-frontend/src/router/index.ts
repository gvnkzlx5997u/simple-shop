import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/goods' },
    {
      path: '/goods',
      name: 'goods-list',
      component: () => import('../views/GoodsList.vue'),
      meta: { title: '商品' },
    },
    {
      path: '/goods/:id',
      name: 'goods-detail',
      component: () => import('../views/GoodsDetail.vue'),
      meta: { title: '商品详情' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/Login.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/Register.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('../views/History.vue'),
      meta: { title: '我的历史', requiresAuth: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/goods' },
  ],
})

/**
 * 只有「我的历史」需要登录（浏览与详情免登录）。
 * 未登录时带 redirect，登录后回到原页面。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? title + ' · 在线购物' : '在线购物'
})

export default router
