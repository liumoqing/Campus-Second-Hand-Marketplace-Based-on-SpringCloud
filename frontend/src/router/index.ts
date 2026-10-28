import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/api/http'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layout/DefaultLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
      {
        path: 'product/:id',
        name: 'product',
        component: () => import('@/views/ProductDetailView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'publish',
        name: 'publish',
        component: () => import('@/views/PublishView.vue'),
        meta: { requiresAuth: true, requiresPublish: true }
      },
      {
        path: 'publish/:id',
        name: 'publish-edit',
        component: () => import('@/views/PublishView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'purchase/:id',
        name: 'purchase',
        component: () => import('@/views/PurchaseView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/OrdersView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'mine/:tab?',
        name: 'mine',
        component: () => import('@/views/MineView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'seller/:id',
        name: 'seller',
        component: () => import('@/views/SellerView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/ChatView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'admin',
        component: () => import('@/views/admin/AdminLayout.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN', 'SUPER_ADMIN'] },
        children: [
          { path: '', redirect: '/admin/users' },
          { path: 'users', name: 'admin-users', component: () => import('@/views/admin/AdminUsers.vue') },
          { path: 'products', name: 'admin-products', component: () => import('@/views/admin/AdminProducts.vue') },
          { path: 'categories', name: 'admin-categories', component: () => import('@/views/admin/AdminCategories.vue') },
          { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/AdminOrders.vue') }
        ]
      }
    ]
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/reset',
    name: 'reset',
    component: () => import('@/views/auth/ResetRequestView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/reset/confirm',
    name: 'reset-confirm',
    component: () => import('@/views/auth/ResetConfirmView.vue'),
    meta: { guestOnly: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const logged = !!getToken()

  if (to.meta.guestOnly && logged) return { path: '/' }

  if (to.meta.requiresAuth && !logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.roles && logged) {
    const raw = localStorage.getItem('mall_user')
    const role = raw ? JSON.parse(raw).role : 'USER'
    const roles = to.meta.roles as string[]
    if (!roles.includes(role)) return { path: '/' }
  }

  return true
})

export default router
