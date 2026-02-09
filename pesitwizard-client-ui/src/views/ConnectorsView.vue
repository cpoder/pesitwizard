<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { 
  Plus, Pencil, Trash2, RefreshCw, Upload,
  TestTube, FolderOpen, CheckCircle, XCircle, Plug
} from 'lucide-vue-next'
import api from '@/api'

interface ConfigParameter {
  name: string
  description: string
  type: string
  required: boolean
  defaultValue?: string
}

interface ConnectorType {
  type: string
  name: string
  version: string
  description: string
  requiredParameters: ConfigParameter[]
  optionalParameters: ConfigParameter[]
}

interface StorageConnection {
  id?: string
  name: string
  description?: string
  connectorType: string
  configJson: string
  enabled: boolean
  lastTestedAt?: string
  lastTestSuccess?: boolean
  lastTestError?: string
}

const connectorTypes = ref<ConnectorType[]>([])
const connections = ref<StorageConnection[]>([])
const loading = ref(true)
const showModal = ref(false)
const showImportModal = ref(false)
const showBrowseModal = ref(false)
const editingConnection = ref<StorageConnection | null>(null)
const saving = ref(false)
const testing = ref<string | null>(null)
const error = ref('')
const browseFiles = ref<any[]>([])
const browsePath = ref('.')
const browseConnectionId = ref<string | null>(null)

const form = ref({
  name: '',
  description: '',
  connectorType: '',
  config: {} as Record<string, string>,
  enabled: true
})

const selectedType = computed(() => 
  connectorTypes.value.find(t => t.type === form.value.connectorType)
)

onMounted(() => {
  loadConnectorTypes()
  loadConnections()
})

async function loadConnectorTypes() {
  try {
    const response = await api.get('/connectors/types')
    connectorTypes.value = response.data || []
  } catch (e) {
    console.error('Failed to load connector types:', e)
  }
}

async function loadConnections() {
  loading.value = true
  try {
    const response = await api.get('/connectors/connections')
    connections.value = response.data || []
  } catch (e) {
    console.error('Failed to load connections:', e)
  } finally {
    loading.value = false
  }
}

async function reloadConnectors() {
  try {
    await api.post('/connectors/types/reload')
    await loadConnectorTypes()
  } catch (e) {
    console.error('Failed to reload connectors:', e)
  }
}

function openAddModal() {
  editingConnection.value = null
  form.value = { name: '', description: '', connectorType: '', config: {}, enabled: true }
  error.value = ''
  showModal.value = true
}

function openEditModal(conn: StorageConnection) {
  editingConnection.value = conn
  form.value = {
    name: conn.name,
    description: conn.description || '',
    connectorType: conn.connectorType,
    config: JSON.parse(conn.configJson || '{}'),
    enabled: conn.enabled
  }
  error.value = ''
  showModal.value = true
}

async function saveConnection() {
  saving.value = true
  error.value = ''
  try {
    const payload = {
      name: form.value.name,
      description: form.value.description,
      connectorType: form.value.connectorType,
      config: form.value.config,
      enabled: form.value.enabled
    }
    if (editingConnection.value?.id) {
      await api.put(`/connectors/connections/${editingConnection.value.id}`, payload)
    } else {
      await api.post('/connectors/connections', payload)
    }
    showModal.value = false
    await loadConnections()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to save connection'
  } finally {
    saving.value = false
  }
}

async function deleteConnection(conn: StorageConnection) {
  if (!confirm(`Delete connection "${conn.name}"?`)) return
  try {
    await api.delete(`/connectors/connections/${conn.id}`)
    await loadConnections()
  } catch (e) {
    console.error('Failed to delete connection:', e)
  }
}

async function testConnection(conn: StorageConnection) {
  testing.value = conn.id!
  try {
    await api.post(`/connectors/connections/${conn.id}/test`)
    await loadConnections()
  } catch (e) {
    console.error('Failed to test connection:', e)
  } finally {
    testing.value = null
  }
}

async function browseConnection(conn: StorageConnection) {
  browseConnectionId.value = conn.id!
  browsePath.value = '.'
  showBrowseModal.value = true
  await loadBrowseFiles()
}

async function loadBrowseFiles() {
  try {
    const response = await api.get(`/connectors/connections/${browseConnectionId.value}/browse`, {
      params: { path: browsePath.value }
    })
    browseFiles.value = response.data || []
  } catch (e) {
    console.error('Failed to browse:', e)
    browseFiles.value = []
  }
}

function navigateTo(file: any) {
  if (file.directory) {
    browsePath.value = file.path
    loadBrowseFiles()
  }
}

function navigateUp() {
  const parts = browsePath.value.split('/')
  parts.pop()
  browsePath.value = parts.length ? parts.join('/') : '.'
  loadBrowseFiles()
}

async function importConnector(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  
  const formData = new FormData()
  formData.append('file', file)
  
  try {
    await api.post('/connectors/types/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    showImportModal.value = false
    await loadConnectorTypes()
  } catch (e: any) {
    alert(e.response?.data?.error || 'Failed to import connector')
  }
}

function getTypeIcon(type: string) {
  const icons: Record<string, string> = {
    local: '💾', sftp: '🔐', s3: '☁️', ftp: '📁'
  }
  return icons[type] || '🔌'
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Storage Connectors</h1>
      <div class="flex gap-3">
        <button @click="reloadConnectors" class="btn btn-secondary flex items-center gap-2" aria-label="Refresh connector plugins">
          <RefreshCw class="h-4 w-4" aria-hidden="true" />
          Reload Plugins
        </button>
        <button @click="showImportModal = true" class="btn btn-secondary flex items-center gap-2" aria-label="Import connector plugin">
          <Upload class="h-4 w-4" aria-hidden="true" />
          Import Plugin
        </button>
        <button @click="openAddModal" class="btn btn-primary flex items-center gap-2" aria-label="New connection">
          <Plus class="h-4 w-4" aria-hidden="true" />
          New Connection
        </button>
      </div>
    </div>

    <!-- Connector Types -->
    <div class="mb-8">
      <h2 class="text-lg font-semibold text-gray-800 mb-3">Available Connector Types</h2>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div v-for="ct in connectorTypes" :key="ct.type" class="card p-4">
          <div class="flex items-center gap-3">
            <span class="text-2xl">{{ getTypeIcon(ct.type) }}</span>
            <div>
              <h3 class="font-semibold text-gray-900">{{ ct.name }}</h3>
              <p class="text-sm text-gray-500">v{{ ct.version }}</p>
            </div>
          </div>
          <p class="text-sm text-gray-600 mt-2">{{ ct.description }}</p>
        </div>
      </div>
    </div>

    <!-- Connections -->
    <h2 class="text-lg font-semibold text-gray-800 mb-3">Configured Connections</h2>
    
    <div v-if="loading" class="flex items-center justify-center h-32">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" aria-hidden="true" />
    </div>

    <div v-else-if="connections.length === 0" class="card text-center py-12">
      <Plug class="h-16 w-16 mx-auto mb-4 text-gray-400" aria-hidden="true" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No connections configured</h3>
      <p class="text-gray-500 mb-4">Create a storage connection to access remote files</p>
      <button @click="openAddModal" class="btn btn-primary">New Connection</button>
    </div>

    <div v-else class="grid gap-4">
      <div v-for="conn in connections" :key="conn.id" class="card hover:shadow-md transition-shadow">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div :class="['p-3 rounded-lg text-2xl', conn.enabled ? 'bg-green-100' : 'bg-gray-100']">
              {{ getTypeIcon(conn.connectorType) }}
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-lg font-semibold text-gray-900">{{ conn.name }}</h3>
                <span class="badge" :class="conn.enabled ? 'badge-success' : 'badge-secondary'">
                  {{ conn.connectorType }}
                </span>
                <span v-if="conn.lastTestSuccess === true" class="text-green-600">
                  <CheckCircle class="h-4 w-4 inline" aria-hidden="true" /> OK
                </span>
                <span v-else-if="conn.lastTestSuccess === false" class="text-red-600">
                  <XCircle class="h-4 w-4 inline" aria-hidden="true" /> Failed
                </span>
              </div>
              <p v-if="conn.description" class="text-gray-600">{{ conn.description }}</p>
              <p v-if="conn.lastTestError" class="text-sm text-red-500 mt-1">{{ conn.lastTestError }}</p>
            </div>
          </div>
          
          <div class="flex items-center gap-2">
            <button @click="testConnection(conn)"
              class="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"
              :disabled="testing === conn.id" title="Test" aria-label="Test connection">
              <TestTube class="h-5 w-5" :class="{ 'animate-pulse': testing === conn.id }" aria-hidden="true" />
            </button>
            <button @click="browseConnection(conn)"
              class="p-2 text-gray-400 hover:text-green-600 hover:bg-green-50 rounded-lg" title="Browse" aria-label="Browse files">
              <FolderOpen class="h-5 w-5" aria-hidden="true" />
            </button>
            <button @click="openEditModal(conn)"
              class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg" title="Edit" aria-label="Edit connection">
              <Pencil class="h-5 w-5" aria-hidden="true" />
            </button>
            <button @click="deleteConnection(conn)"
              class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg" title="Delete" aria-label="Delete connection">
              <Trash2 class="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 overflow-y-auto" @keydown.escape="showModal = false">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showModal = false" />

        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6 max-h-[90vh] overflow-y-auto" role="dialog" aria-modal="true" aria-labelledby="connection-modal-title">
          <h2 id="connection-modal-title" class="text-xl font-bold text-gray-900 mb-6">
            {{ editingConnection ? 'Edit Connection' : 'New Connection' }}
          </h2>

          <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <form @submit.prevent="saveConnection" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
              <input v-model="form.name" type="text" class="input" required placeholder="My SFTP Server" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="form.description" type="text" class="input" placeholder="Optional description" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Connector Type *</label>
              <select v-model="form.connectorType" class="input" required :disabled="!!editingConnection">
                <option value="">Select a type...</option>
                <option v-for="ct in connectorTypes" :key="ct.type" :value="ct.type">
                  {{ ct.name }} ({{ ct.type }})
                </option>
              </select>
            </div>

            <!-- Dynamic config fields -->
            <div v-if="selectedType" class="space-y-4 border-t pt-4">
              <h3 class="font-medium text-gray-900">Configuration</h3>
              
              <div v-for="param in selectedType.requiredParameters" :key="param.name">
                <label class="block text-sm font-medium text-gray-700 mb-1">
                  {{ param.description }} *
                </label>
                <input 
                  v-model="form.config[param.name]" 
                  :type="param.type === 'PASSWORD' ? 'password' : 'text'" 
                  class="input" 
                  required 
                  :placeholder="param.name"
                />
              </div>
              
              <div v-for="param in selectedType.optionalParameters" :key="param.name">
                <label class="block text-sm font-medium text-gray-700 mb-1">
                  {{ param.description }}
                </label>
                <input 
                  v-model="form.config[param.name]" 
                  :type="param.type === 'PASSWORD' ? 'password' : 'text'" 
                  class="input" 
                  :placeholder="param.defaultValue || param.name"
                />
              </div>
            </div>

            <label class="flex items-center gap-2">
              <input v-model="form.enabled" type="checkbox" class="rounded border-gray-300 text-primary-600" />
              <span class="text-sm text-gray-700">Enabled</span>
            </label>

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

    <!-- Import Modal -->
    <div v-if="showImportModal" class="fixed inset-0 z-50 overflow-y-auto" @keydown.escape="showImportModal = false">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showImportModal = false" />

        <div class="relative bg-white rounded-xl shadow-xl max-w-md w-full p-6" role="dialog" aria-modal="true" aria-labelledby="import-modal-title">
          <h2 id="import-modal-title" class="text-xl font-bold text-gray-900 mb-4">Import Connector Plugin</h2>
          <p class="text-gray-600 mb-4">Select a connector JAR file to import.</p>
          
          <input type="file" accept=".jar" @change="importConnector" class="input" />
          
          <div class="flex justify-end gap-3 mt-6">
            <button @click="showImportModal = false" class="btn btn-secondary">Cancel</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Browse Modal -->
    <div v-if="showBrowseModal" class="fixed inset-0 z-50 overflow-y-auto" @keydown.escape="showBrowseModal = false">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showBrowseModal = false" />

        <div class="relative bg-white rounded-xl shadow-xl max-w-2xl w-full p-6 max-h-[80vh] overflow-hidden flex flex-col" role="dialog" aria-modal="true" aria-labelledby="browse-modal-title">
          <h2 id="browse-modal-title" class="text-xl font-bold text-gray-900 mb-2">Browse Files</h2>
          <div class="flex items-center gap-2 mb-4">
            <button @click="navigateUp" class="btn btn-secondary btn-sm">⬆️ Up</button>
            <span class="text-gray-600 text-sm">{{ browsePath }}</span>
          </div>
          
          <div class="flex-1 overflow-y-auto border rounded-lg">
            <table class="w-full text-sm">
              <thead class="bg-gray-50 sticky top-0">
                <tr>
                  <th scope="col" class="px-4 py-2 text-left">Name</th>
                  <th scope="col" class="px-4 py-2 text-right">Size</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="file in browseFiles" :key="file.path" 
                    class="border-t hover:bg-gray-50 cursor-pointer"
                    @click="navigateTo(file)">
                  <td class="px-4 py-2">
                    <span v-if="file.directory">📁</span>
                    <span v-else>📄</span>
                    {{ file.name }}
                  </td>
                  <td class="px-4 py-2 text-right text-gray-500">
                    {{ file.directory ? '-' : file.size }}
                  </td>
                </tr>
                <tr v-if="browseFiles.length === 0">
                  <td colspan="2" class="px-4 py-8 text-center text-gray-500">No files</td>
                </tr>
              </tbody>
            </table>
          </div>
          
          <div class="flex justify-end mt-4">
            <button @click="showBrowseModal = false" class="btn btn-secondary">Close</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
