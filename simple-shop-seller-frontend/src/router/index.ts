import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '../layouts/AppLayout.vue'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'goods-list', component: () => import('../views/GoodsListView.vue') },
        { path: 'publish', name: 'publish', component: () => import('../views/PublishView.vue') },
        {
          path: 'goods/:id',
          name: 'goods-detail',
          component: () => import('../views/GoodsDetailView.vue'),
        },
        {
          path: 'customers',
          name: 'customers',
          component: () => import('../views/CustomerListView.vue'),
        },
        {
          path: 'customers/:id',
          name: 'customer-detail',
          component: () => import('../views/CustomerDetailView.vue'),
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('../views/HistoryListView.vue'),
        },
        {
          path: 'history/:id',
          name: 'history-detail',
          component: () => import('../views/HistoryDetailView.vue'),
        },
        {
          path: 'categories',
          name: 'categories',
          component: () => import('../views/CategoryManageView.vue'),
        },
        {
          path: 'password',
          name: 'password',
          component: () => import('../views/ChangePasswordView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'goods-list' }
  }
  return true
})

export default router
