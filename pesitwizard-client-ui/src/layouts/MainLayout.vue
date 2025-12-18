<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { 
  LayoutDashboard, 
  Server, 
  Send,
  History,
  Star,
  Calendar,
  CalendarDays,
  Plug,
  Settings,
  Menu,
  X
} from 'lucide-vue-next'

const route = useRoute()
const sidebarOpen = ref(true)

const navigation = [
  { name: 'Dashboard', href: '/', icon: LayoutDashboard, exact: true },
  { name: 'Servers', href: '/servers', icon: Server, exact: false },
  { name: 'Connectors', href: '/connectors', icon: Plug, exact: false },
  { name: 'Transfer', href: '/transfer', icon: Send, exact: false },
  { name: 'History', href: '/history', icon: History, exact: false },
  { name: 'Favorites', href: '/favorites', icon: Star, exact: false },
  { name: 'Schedules', href: '/schedules', icon: Calendar, exact: false },
  { name: 'Calendars', href: '/calendars', icon: CalendarDays, exact: false },
  { name: 'Settings', href: '/settings', icon: Settings, exact: false },
]

function isActive(item: { href: string; exact: boolean }) {
  if (item.exact) {
    return route.path === item.href
  }
  return route.path === item.href || route.path.startsWith(item.href + '/')
}
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Sidebar -->
    <aside 
      :class="[
        'fixed inset-y-0 left-0 z-50 w-64 bg-gray-900 transform transition-transform duration-200 ease-in-out',
        sidebarOpen ? 'translate-x-0' : '-translate-x-full'
      ]"
    >
      <div class="flex h-16 items-center justify-between px-6 border-b border-gray-800">
        <span class="text-xl font-bold text-white">PeSIT Wizard Client</span>
        <button @click="sidebarOpen = false" class="lg:hidden text-gray-400 hover:text-white">
          <X class="h-6 w-6" />
        </button>
      </div>
      
      <nav class="mt-6 px-3">
        <RouterLink
          v-for="item in navigation"
          :key="item.name"
          :to="item.href"
          :class="[
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors mb-1',
            isActive(item) 
              ? 'bg-primary-600 text-white hover:bg-primary-700' 
              : 'text-gray-300 hover:bg-gray-800 hover:text-white'
          ]"
        >
          <component :is="item.icon" class="h-5 w-5" />
          {{ item.name }}
        </RouterLink>
      </nav>
    </aside>

    <!-- Main content -->
    <div :class="['transition-all duration-200', sidebarOpen ? 'lg:pl-64' : '']">
      <!-- Top bar -->
      <header class="sticky top-0 z-40 bg-white border-b border-gray-200">
        <div class="flex h-16 items-center gap-4 px-4">
          <button 
            @click="sidebarOpen = !sidebarOpen" 
            class="text-gray-500 hover:text-gray-700"
          >
            <Menu class="h-6 w-6" />
          </button>
          <h1 class="text-lg font-semibold text-gray-900">PeSIT Wizard Client</h1>
        </div>
      </header>

      <!-- Page content -->
      <main class="p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
