<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Upload, 
  Download, 
  CheckCircle, 
  XCircle, 
  Clock,
  Server,
  ArrowRight
} from 'lucide-vue-next'
import api from '@/api'

const stats = ref({
  totalTransfers: 0,
  completedTransfers: 0,
  failedTransfers: 0,
  inProgressTransfers: 0,
  totalBytesTransferred: 0
})

const recentTransfers = ref<any[]>([])
const servers = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  await Promise.all([loadStats(), loadServers(), loadRecentTransfers()])
  loading.value = false
})

async function loadStats() {
  try {
    const response = await api.get('/transfers/stats')
    stats.value = response.data
  } catch (e) {
    console.error('Failed to load stats:', e)
  }
}

async function loadServers() {
  try {
    const response = await api.get('/servers')
    servers.value = response.data || []
  } catch (e) {
    console.error('Failed to load servers:', e)
  }
}

async function loadRecentTransfers() {
  try {
    const response = await api.get('/transfers/history?size=5')
    recentTransfers.value = response.data.content || []
  } catch (e) {
    console.error('Failed to load transfers:', e)
  }
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Dashboard</h1>

    <!-- Stats Cards -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      <div class="card">
        <div class="flex items-center gap-4">
          <div class="p-3 bg-blue-100 rounded-lg">
            <Send class="h-6 w-6 text-blue-600" />
          </div>
          <div>
            <p class="text-sm text-gray-500">Total Transfers</p>
            <p class="text-2xl font-bold text-gray-900">{{ stats.totalTransfers }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-4">
          <div class="p-3 bg-green-100 rounded-lg">
            <CheckCircle class="h-6 w-6 text-green-600" />
          </div>
          <div>
            <p class="text-sm text-gray-500">Completed</p>
            <p class="text-2xl font-bold text-gray-900">{{ stats.completedTransfers }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-4">
          <div class="p-3 bg-red-100 rounded-lg">
            <XCircle class="h-6 w-6 text-red-600" />
          </div>
          <div>
            <p class="text-sm text-gray-500">Failed</p>
            <p class="text-2xl font-bold text-gray-900">{{ stats.failedTransfers }}</p>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center gap-4">
          <div class="p-3 bg-purple-100 rounded-lg">
            <Download class="h-6 w-6 text-purple-600" />
          </div>
          <div>
            <p class="text-sm text-gray-500">Data Transferred</p>
            <p class="text-2xl font-bold text-gray-900">{{ formatBytes(stats.totalBytesTransferred) }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Configured Servers -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900">Configured Servers</h2>
          <RouterLink to="/servers" class="text-primary-600 hover:text-primary-700 text-sm flex items-center gap-1">
            Manage <ArrowRight class="h-4 w-4" />
          </RouterLink>
        </div>

        <div v-if="servers.length === 0" class="text-center py-8 text-gray-500">
          <Server class="h-12 w-12 mx-auto mb-2 opacity-50" />
          <p>No servers configured</p>
          <RouterLink to="/servers" class="btn btn-primary mt-4 inline-block">Add Server</RouterLink>
        </div>

        <div v-else class="space-y-3">
          <div 
            v-for="server in servers.slice(0, 5)" 
            :key="server.id"
            class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
          >
            <div class="flex items-center gap-3">
              <div :class="['w-2 h-2 rounded-full', server.enabled ? 'bg-green-500' : 'bg-gray-400']" />
              <div>
                <p class="font-medium text-gray-900">{{ server.name }}</p>
                <p class="text-sm text-gray-500">{{ server.host }}:{{ server.port }}</p>
              </div>
            </div>
            <span v-if="server.defaultServer" class="badge badge-info">Default</span>
          </div>
        </div>
      </div>

      <!-- Recent Transfers -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900">Recent Transfers</h2>
          <RouterLink to="/history" class="text-primary-600 hover:text-primary-700 text-sm flex items-center gap-1">
            View All <ArrowRight class="h-4 w-4" />
          </RouterLink>
        </div>

        <div v-if="recentTransfers.length === 0" class="text-center py-8 text-gray-500">
          <Clock class="h-12 w-12 mx-auto mb-2 opacity-50" />
          <p>No transfers yet</p>
          <RouterLink to="/transfer" class="btn btn-primary mt-4 inline-block">Start Transfer</RouterLink>
        </div>

        <div v-else class="space-y-3">
          <div 
            v-for="transfer in recentTransfers" 
            :key="transfer.id"
            class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
          >
            <div class="flex items-center gap-3">
              <Upload v-if="transfer.direction === 'SEND'" class="h-5 w-5 text-blue-600" />
              <Download v-else class="h-5 w-5 text-green-600" />
              <div>
                <p class="font-medium text-gray-900 truncate max-w-[200px]">{{ transfer.remoteFilename || 'Unknown' }}</p>
                <p class="text-sm text-gray-500">{{ formatDate(transfer.startedAt) }}</p>
              </div>
            </div>
            <span :class="[
              'badge',
              transfer.status === 'COMPLETED' ? 'badge-success' : 
              transfer.status === 'FAILED' ? 'badge-danger' : 'badge-warning'
            ]">
              {{ transfer.status }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Actions -->
    <div class="card mt-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
      <div class="flex flex-wrap gap-4">
        <RouterLink to="/transfer" class="btn btn-primary flex items-center gap-2">
          <Send class="h-4 w-4" /> Send File
        </RouterLink>
        <RouterLink to="/servers" class="btn btn-secondary flex items-center gap-2">
          <Server class="h-4 w-4" /> Manage Servers
        </RouterLink>
      </div>
    </div>
  </div>
</template>
