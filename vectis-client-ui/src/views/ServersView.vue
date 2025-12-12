<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Plus, 
  Pencil, 
  Trash2, 
  Server,
  RefreshCw,
  Star,
  Shield
} from 'lucide-vue-next'
import api from '@/api'

interface VectisServer {
  id?: string
  name: string
  host: string
  port: number
  serverId: string
  description?: string
  tlsEnabled: boolean
  connectionTimeout: number
  readTimeout: number
  enabled: boolean
  defaultServer: boolean
}

const servers = ref<VectisServer[]>([])
const loading = ref(true)
const showModal = ref(false)
const editingServer = ref<VectisServer | null>(null)
const saving = ref(false)
const error = ref('')

const defaultForm: VectisServer = {
  name: '',
  host: 'localhost',
  port: 5000,
  serverId: '',
  description: '',
  tlsEnabled: false,
  connectionTimeout: 30000,
  readTimeout: 60000,
  enabled: true,
  defaultServer: false
}

const form = ref<VectisServer>({ ...defaultForm })

onMounted(() => loadServers())

async function loadServers() {
  loading.value = true
  try {
    const response = await api.get('/servers')
    servers.value = response.data || []
  } catch (e) {
    console.error('Failed to load servers:', e)
  } finally {
    loading.value = false
  }
}

function openAddModal() {
  editingServer.value = null
  form.value = { ...defaultForm }
  error.value = ''
  showModal.value = true
}

function openEditModal(server: VectisServer) {
  editingServer.value = server
  form.value = { ...server }
  error.value = ''
  showModal.value = true
}

async function saveServer() {
  saving.value = true
  error.value = ''
  try {
    if (editingServer.value?.id) {
      await api.put(`/servers/${editingServer.value.id}`, form.value)
    } else {
      await api.post('/servers', form.value)
    }
    showModal.value = false
    await loadServers()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to save server'
  } finally {
    saving.value = false
  }
}

async function deleteServer(server: VectisServer) {
  if (!confirm(`Delete server "${server.name}"?`)) return
  try {
    await api.delete(`/servers/${server.id}`)
    await loadServers()
  } catch (e) {
    console.error('Failed to delete server:', e)
  }
}

async function setDefault(server: VectisServer) {
  try {
    await api.post(`/servers/${server.id}/default`)
    await loadServers()
  } catch (e) {
    console.error('Failed to set default:', e)
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Vectis Servers</h1>
      <div class="flex gap-3">
        <button @click="loadServers" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
          Refresh
        </button>
        <button @click="openAddModal" class="btn btn-primary flex items-center gap-2">
          <Plus class="h-4 w-4" />
          Add Server
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" />
    </div>

    <div v-else-if="servers.length === 0" class="card text-center py-12">
      <Server class="h-16 w-16 mx-auto mb-4 text-gray-400" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No servers configured</h3>
      <p class="text-gray-500 mb-4">Add a Vectis server to start transferring files</p>
      <button @click="openAddModal" class="btn btn-primary">Add Server</button>
    </div>

    <div v-else class="grid gap-4">
      <div 
        v-for="server in servers" 
        :key="server.id"
        class="card hover:shadow-md transition-shadow"
      >
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div :class="['p-3 rounded-lg', server.enabled ? 'bg-green-100' : 'bg-gray-100']">
              <Server :class="['h-6 w-6', server.enabled ? 'text-green-600' : 'text-gray-400']" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-lg font-semibold text-gray-900">{{ server.name }}</h3>
                <span v-if="server.defaultServer" class="badge badge-info flex items-center gap-1">
                  <Star class="h-3 w-3" /> Default
                </span>
                <span v-if="server.tlsEnabled" class="badge badge-success flex items-center gap-1">
                  <Shield class="h-3 w-3" /> TLS
                </span>
              </div>
              <p class="text-gray-600">{{ server.host }}:{{ server.port }}</p>
              <p class="text-sm text-gray-500 mt-1">Server ID: {{ server.serverId }}</p>
              <p v-if="server.description" class="text-sm text-gray-500">{{ server.description }}</p>
            </div>
          </div>
          
          <div class="flex items-center gap-2">
            <button 
              v-if="!server.defaultServer"
              @click="setDefault(server)" 
              class="p-2 text-gray-400 hover:text-yellow-600 hover:bg-yellow-50 rounded-lg"
              title="Set as default"
            >
              <Star class="h-5 w-5" />
            </button>
            <button 
              @click="openEditModal(server)" 
              class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg"
              title="Edit"
            >
              <Pencil class="h-5 w-5" />
            </button>
            <button 
              @click="deleteServer(server)" 
              class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
              title="Delete"
            >
              <Trash2 class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6">
          <h2 class="text-xl font-bold text-gray-900 mb-6">
            {{ editingServer ? 'Edit Server' : 'Add Server' }}
          </h2>

          <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <form @submit.prevent="saveServer" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
              <input v-model="form.name" type="text" class="input" required placeholder="My Server" />
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Host *</label>
                <input v-model="form.host" type="text" class="input" required placeholder="localhost" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Port *</label>
                <input v-model.number="form.port" type="number" class="input" required min="1" max="65535" />
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Server ID *</label>
              <input v-model="form.serverId" type="text" class="input" required placeholder="PESIT-SERVER" />
              <p class="text-xs text-gray-500 mt-1">The Vectis server identifier to connect to</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="form.description" type="text" class="input" placeholder="Optional description" />
            </div>

            <div class="flex flex-wrap gap-6">
              <label class="flex items-center gap-2">
                <input v-model="form.tlsEnabled" type="checkbox" class="rounded border-gray-300 text-primary-600" />
                <span class="text-sm text-gray-700">Enable TLS</span>
              </label>
              <label class="flex items-center gap-2">
                <input v-model="form.enabled" type="checkbox" class="rounded border-gray-300 text-primary-600" />
                <span class="text-sm text-gray-700">Enabled</span>
              </label>
              <label class="flex items-center gap-2">
                <input v-model="form.defaultServer" type="checkbox" class="rounded border-gray-300 text-primary-600" />
                <span class="text-sm text-gray-700">Default Server</span>
              </label>
            </div>

            <div class="flex justify-end gap-3 pt-4">
              <button type="button" @click="showModal = false" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                {{ saving ? 'Saving...' : 'Save' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
