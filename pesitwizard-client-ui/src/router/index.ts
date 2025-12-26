import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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
          path: 'certificates',
          name: 'certificates',
          component: () => import('../views/CertificatesView.vue')
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

export default router
