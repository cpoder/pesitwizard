<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Upload, 
  Download, 
  CheckCircle, 
  XCircle, 
  Clock,
  Server,
  ArrowRight,
  Lock,
  AlertTriangle,
  Activity,
  RefreshCw,
  Cpu,
  HardDrive
} from 'lucide-vue-next'
import api from '@/api'

interface DashboardData {
  transfers: {
    total: number
    completed: number
    failed: number
    inProgress: number
    bytesTransferred: number
  }
  activeTransfers: any[]
  recentTransfers: any[]
  servers: {
    total: number
    enabled: number
    disabled: number
    list: any[]
  }
  security: {
    encryptionEnabled: boolean
    encryptionMode: string
    message: string
    vaultAvailable: boolean
  }
  system: {
    javaVersion: string
    memoryUsed: number
    memoryMax: number
    processors: number
  }
}

const dashboard = ref<DashboardData | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  await loadDashboard()
})

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/dashboard')
    dashboard.value = response.data
  } catch (e: any) {
    console.error('Failed to load dashboard:', e)
    error.value = 'Failed to load dashboard'
    // Fallback to individual calls
    await Promise.all([loadStats(), loadServers(), loadRecentTransfers()])
  } finally {
    loading.value = false
  }
}

// Fallback loaders
const stats = ref({ total: 0, completed: 0, failed: 0, inProgress: 0, bytesTransferred: 0 })
const recentTransfers = ref<any[]>([])
const servers = ref<any[]>([])

async function loadStats() {
  try {
    const response = await api.get('/transfers/stats')
    stats.value = {
      total: response.data.totalTransfers,
      completed: response.data.completedTransfers,
      failed: response.data.failedTransfers,
      inProgress: response.data.inProgressTransfers,
      bytesTransferred: response.data.totalBytesTransferred
    }
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

// Computed helpers
const transferStats = () => dashboard.value?.transfers || stats.value
const serverList = () => dashboard.value?.servers?.list || servers.value
const recentList = () => dashboard.value?.recentTransfers || recentTransfers.value

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
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Dashboard</h1>
      <button @click="loadDashboard" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
        <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        Refresh
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading && !dashboard" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" />
    </div>

    <template v-else>
      <!-- Stats Cards - Row 1: Transfers -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
        <div class="card">
          <div class="flex items-center gap-4">
            <div class="p-3 bg-blue-100 rounded-lg">
              <Activity class="h-6 w-6 text-blue-600" />
            </div>
            <div>
              <p class="text-sm text-gray-500">Total Transfers</p>
              <p class="text-2xl font-bold text-gray-900">{{ transferStats().total }}</p>
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
              <p class="text-2xl font-bold text-green-600">{{ transferStats().completed }}</p>
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
              <p class="text-2xl font-bold" :class="transferStats().failed > 0 ? 'text-red-600' : 'text-gray-900'">
                {{ transferStats().failed }}
              </p>
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
              <p class="text-2xl font-bold text-gray-900">{{ formatBytes(transferStats().bytesTransferred) }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Stats Cards - Row 2: Infrastructure -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <!-- In Progress -->
        <div class="card">
          <div class="flex items-center gap-4">
            <div class="p-3 rounded-lg" :class="transferStats().inProgress > 0 ? 'bg-yellow-100' : 'bg-gray-100'">
              <Clock :class="['h-6 w-6', transferStats().inProgress > 0 ? 'text-yellow-600' : 'text-gray-500']" />
            </div>
            <div>
              <p class="text-sm text-gray-500">In Progress</p>
              <p class="text-2xl font-bold" :class="transferStats().inProgress > 0 ? 'text-yellow-600' : 'text-gray-500'">
                {{ transferStats().inProgress }}
              </p>
            </div>
          </div>
        </div>

        <!-- Servers -->
        <div class="card">
          <div class="flex items-center gap-4">
            <div class="p-3 bg-indigo-100 rounded-lg">
              <Server class="h-6 w-6 text-indigo-600" />
            </div>
            <div>
              <p class="text-sm text-gray-500">PeSIT Servers</p>
              <p class="text-2xl font-bold">
                <span class="text-green-600">{{ dashboard?.servers?.enabled || serverList().filter((s: any) => s.enabled).length }}</span>
                <span class="text-gray-400 text-lg">/{{ dashboard?.servers?.total || serverList().length }}</span>
              </p>
            </div>
          </div>
        </div>

        <!-- Security -->
        <div class="card">
          <div class="flex items-center gap-4">
            <div class="p-3 rounded-lg" :class="dashboard?.security?.encryptionEnabled ? 'bg-green-100' : 'bg-yellow-100'">
              <Lock :class="['h-6 w-6', dashboard?.security?.encryptionEnabled ? 'text-green-600' : 'text-yellow-600']" />
            </div>
            <div>
              <p class="text-sm text-gray-500">Encryption</p>
              <p class="text-lg font-bold" :class="dashboard?.security?.encryptionEnabled ? 'text-green-600' : 'text-yellow-600'">
                {{ dashboard?.security?.encryptionMode || 'Not configured' }}
              </p>
            </div>
          </div>
        </div>

        <!-- System -->
        <div class="card">
          <div class="flex items-center gap-4">
            <div class="p-3 bg-gray-100 rounded-lg">
              <Cpu class="h-6 w-6 text-gray-600" />
            </div>
            <div>
              <p class="text-sm text-gray-500">Memory</p>
              <p class="text-lg font-bold text-gray-700">
                {{ dashboard?.system?.memoryUsed || '?' }} / {{ dashboard?.system?.memoryMax || '?' }} MB
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Active Transfers Alert -->
      <div v-if="dashboard?.activeTransfers?.length" class="mb-6 bg-yellow-50 border border-yellow-200 rounded-lg p-4">
        <div class="flex items-center gap-3 mb-3">
          <Activity class="h-5 w-5 text-yellow-600 animate-pulse" />
          <h3 class="font-semibold text-yellow-700">{{ dashboard.activeTransfers.length }} Transfer(s) In Progress</h3>
        </div>
        <div class="space-y-2">
          <div v-for="transfer in dashboard.activeTransfers.slice(0, 3)" :key="transfer.id"
               class="flex items-center justify-between text-sm bg-white p-2 rounded">
            <div class="flex items-center gap-2">
              <Upload v-if="transfer.direction === 'SEND'" class="h-4 w-4 text-blue-500" />
              <Download v-else class="h-4 w-4 text-green-500" />
              <span class="font-medium">{{ transfer.filename }}</span>
              <span class="text-gray-500">→ {{ transfer.serverName }}</span>
            </div>
            <span class="text-yellow-600">{{ formatBytes(transfer.progress) }}</span>
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

        <div v-if="serverList().length === 0" class="text-center py-8 text-gray-500">
          <Server class="h-12 w-12 mx-auto mb-2 opacity-50" />
          <p>No servers configured</p>
          <RouterLink to="/servers" class="btn btn-primary mt-4 inline-block">Add Server</RouterLink>
        </div>

        <div v-else class="space-y-3">
          <div 
            v-for="server in serverList().slice(0, 5)" 
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

        <div v-if="recentList().length === 0" class="text-center py-8 text-gray-500">
          <Clock class="h-12 w-12 mx-auto mb-2 opacity-50" />
          <p>No transfers yet</p>
          <RouterLink to="/transfer" class="btn btn-primary mt-4 inline-block">Start Transfer</RouterLink>
        </div>

        <div v-else class="space-y-3">
          <div 
            v-for="transfer in recentList()" 
            :key="transfer.id"
            class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
          >
            <div class="flex items-center gap-3">
              <Upload v-if="transfer.direction === 'SEND'" class="h-5 w-5 text-blue-600" />
              <Download v-else class="h-5 w-5 text-green-600" />
              <div>
                <p class="font-medium text-gray-900 truncate max-w-[200px]">{{ transfer.filename || transfer.remoteFilename || 'Unknown' }}</p>
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
          <Upload class="h-4 w-4" /> Send File
        </RouterLink>
        <RouterLink to="/servers" class="btn btn-secondary flex items-center gap-2">
          <Server class="h-4 w-4" /> Manage Servers
        </RouterLink>
      </div>
    </div>
    </template>
  </div>
</template>
