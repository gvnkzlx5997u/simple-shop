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
    { path: '/:pathMatch(.*)*', redirect: '/goods' },
  ],
})

/**
 * 登录守卫：`meta.requiresAuth` 为真的页面未登录时跳登录，并带 redirect 回原页。
 * 本期只有浏览与账号页（都不需要登录），守卫留给购物车、收藏与订单页接入时使用。
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
