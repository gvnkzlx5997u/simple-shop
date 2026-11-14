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
      path: '/cart',
      name: 'cart',
      component: () => import('../views/Cart.vue'),
      meta: { title: '购物车', requiresAuth: true },
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('../views/Favorites.vue'),
      meta: { title: '我的收藏', requiresAuth: true },
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('../views/Orders.vue'),
      meta: { title: '我的订单', requiresAuth: true },
    },
    {
      path: '/orders/:id',
      name: 'order-detail',
      component: () => import('../views/OrderDetail.vue'),
      meta: { title: '订单详情', requiresAuth: true },
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
 * 需要登录的是购物车、收藏、订单列表与订单详情四处，都靠同一个 meta 标记。
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
