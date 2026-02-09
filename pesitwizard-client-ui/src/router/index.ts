import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue')
        },
        {
          path: 'servers',
          name: 'servers',
          component: () => import('../views/ServersView.vue')
        },
        {
          path: 'transfer',
          name: 'transfer',
          component: () => import('../views/TransferView.vue')
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('../views/HistoryView.vue')
        },
        {
          path: 'favorites',
          name: 'favorites',
          component: () => import('../views/FavoritesView.vue')
        },
        {
          path: 'schedules',
          name: 'schedules',
          component: () => import('../views/SchedulesView.vue')
        },
        {
          path: 'calendars',
          name: 'calendars',
          component: () => import('../views/CalendarsView.vue')
        },
        {
          path: 'connectors',
          name: 'connectors',
          component: () => import('../views/ConnectorsView.vue')
        },
        {
          path: 'tls',
          name: 'tls',
          redirect: { name: 'servers' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/SettingsView.vue')
        },
      ]
    },
  ]
})

// Auth guard
router.beforeEach(async (to) => {
  const { authRequired, authenticated, initialized, checkAuthStatus } = useAuth()

  if (!initialized.value) {
    await checkAuthStatus()
  }

  // Public routes don't need auth
  if (to.meta.public) return true

  // If auth is required and user is not authenticated, redirect to login
  if (authRequired.value && !authenticated.value) {
    return { name: 'login' }
  }

  // If user is authenticated and going to login, redirect to dashboard
  if (to.name === 'login' && authenticated.value) {
    return { name: 'dashboard' }
  }

  return true
})

export default router
