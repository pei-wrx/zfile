/**
 * Vue Router 配置
 */
import { createRouter, createWebHistory } from 'vue-router'
import { setLogoutCallback } from '@/api/request'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ============ 公开页面 ============
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { guest: true },
    },
    {
      path: '/share/:shareCode',
      name: 'PublicShare',
      component: () => import('@/views/share/PublicShareView.vue'),
    },

    // ============ 需要登录的主区域 ============
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: { name: 'Files' },
      meta: { requiresAuth: true },
      children: [
        {
          path: 'files',
          name: 'Files',
          component: () => import('@/views/files/FilesView.vue'),
        },
        {
          path: 'shares',
          name: 'MyShares',
          component: () => import('@/views/share/MySharesView.vue'),
        },
        {
          path: 'share-square',
          name: 'ShareSquare',
          component: () => import('@/views/share/ShareSquareView.vue'),
        },
        {
          path: 'trash',
          name: 'Trash',
          component: () => import('@/views/trash/TrashView.vue'),
        },
        {
          path: 'admin/users',
          name: 'AdminUsers',
          component: () => import('@/views/admin/AdminUsersView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/profile/ProfileView.vue'),
        },
      ],
    },

    // ============ 404 ============
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/error/NotFoundView.vue'),
    },
  ],
})

// ============ 401 刷新失败回调：清除认证并跳转登录 ============
setLogoutCallback(() => {
  const authStore = useAuthStore()
  authStore.clearAuth()
  router.push({ name: 'Login' })
})

// ============ 路由守卫 ============
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  // 等待 init 完成，确保 role/status 等字段已从后端刷新
  await authStore.init()

  // 需要登录但未登录
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  // 需要管理员权限（大小写不敏感，兼容后端返回格式差异）
  if (to.meta.requiresAdmin && authStore.user?.role?.toUpperCase() !== 'ADMIN') {
    return { name: 'Files' }
  }

  // 已登录用户不能访问登录/注册页
  if (to.meta.guest && authStore.isLoggedIn) {
    return { name: 'Files' }
  }

  return true
})

export default router
