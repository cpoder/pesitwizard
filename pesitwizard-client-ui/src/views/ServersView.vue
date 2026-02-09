<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Plus, 
  Pencil, 
  Trash2, 
  Server,
  RefreshCw,
  Star,
  Shield,
  Upload,
  Key,
  Lock,
  CheckCircle,
  AlertCircle
} from 'lucide-vue-next'
import api from '@/api'

interface PesitServer {
  id?: string
  name: string
  host: string
  port: number
  serverId: string
  description?: string
  tlsEnabled: boolean
  truststoreConfigured?: boolean
  keystoreConfigured?: boolean
  connectionTimeout: number
  readTimeout: number
  enabled: boolean
  defaultServer: boolean
}

const servers = ref<PesitServer[]>([])
const loading = ref(true)
const showModal = ref(false)
const editingServer = ref<PesitServer | null>(null)
const saving = ref(false)
const error = ref('')

// TLS certificate upload state
const showTlsModal = ref(false)
const tlsServerId = ref<string | null>(null)
const tlsServerName = ref('')
const uploading = ref(false)
const tlsError = ref('')
const tlsSuccess = ref('')
const truststoreFile = ref<File | null>(null)
const truststorePassword = ref('')
const keystoreFile = ref<File | null>(null)
const keystorePassword = ref('')
const tlsStatus = ref({ truststoreConfigured: false, keystoreConfigured: false })

const defaultForm: PesitServer = {
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

const form = ref<PesitServer>({ ...defaultForm })

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

function openEditModal(server: PesitServer) {
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

async function deleteServer(server: PesitServer) {
  if (!confirm(`Delete server "${server.name}"?`)) return
  try {
    await api.delete(`/servers/${server.id}`)
    await loadServers()
  } catch (e) {
    console.error('Failed to delete server:', e)
  }
}

async function setDefault(server: PesitServer) {
  try {
    await api.post(`/servers/${server.id}/default`)
    await loadServers()
  } catch (e) {
    console.error('Failed to set default:', e)
  }
}

// TLS Certificate Management
async function openTlsModal(server: PesitServer) {
  tlsServerId.value = server.id || null
  tlsServerName.value = server.name
  tlsError.value = ''
  tlsSuccess.value = ''
  truststoreFile.value = null
  truststorePassword.value = ''
  keystoreFile.value = null
  keystorePassword.value = ''
  tlsStatus.value = {
    truststoreConfigured: server.truststoreConfigured || false,
    keystoreConfigured: server.keystoreConfigured || false
  }
  showTlsModal.value = true
}

function onTruststoreFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  truststoreFile.value = input.files?.[0] || null
}

function onKeystoreFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  keystoreFile.value = input.files?.[0] || null
}

async function uploadTruststore() {
  if (!truststoreFile.value) {
    tlsError.value = 'Please select a certificate file'
    return
  }
  
  // Check if password is required (for non-PEM files)
  const filename = truststoreFile.value.name.toLowerCase()
  const isPem = filename.endsWith('.pem') || filename.endsWith('.crt') || filename.endsWith('.cer')
  if (!isPem && !truststorePassword.value) {
    tlsError.value = 'Password is required for PKCS12 files'
    return
  }
  
  uploading.value = true
  tlsError.value = ''
  tlsSuccess.value = ''
  try {
    const formData = new FormData()
    formData.append('file', truststoreFile.value)
    if (truststorePassword.value) {
      formData.append('password', truststorePassword.value)
    }
    const response = await api.post(`/servers/${tlsServerId.value}/tls/truststore`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    tlsSuccess.value = response.data.message || 'CA certificate uploaded successfully'
    tlsStatus.value.truststoreConfigured = true
    truststoreFile.value = null
    truststorePassword.value = ''
    await loadServers()
  } catch (e: any) {
    tlsError.value = e.response?.data?.error || 'Failed to upload certificate'
  } finally {
    uploading.value = false
  }
}

async function uploadKeystore() {
  if (!keystoreFile.value || !keystorePassword.value) {
    tlsError.value = 'Please select a file and enter the password'
    return
  }
  uploading.value = true
  tlsError.value = ''
  tlsSuccess.value = ''
  try {
    const formData = new FormData()
    formData.append('file', keystoreFile.value)
    formData.append('password', keystorePassword.value)
    const response = await api.post(`/servers/${tlsServerId.value}/tls/keystore`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    tlsSuccess.value = response.data.message || 'Keystore uploaded successfully'
    tlsStatus.value.keystoreConfigured = true
    keystoreFile.value = null
    keystorePassword.value = ''
    await loadServers()
  } catch (e: any) {
    tlsError.value = e.response?.data?.error || 'Failed to upload keystore'
  } finally {
    uploading.value = false
  }
}

async function deleteTruststore() {
  if (!confirm('Remove the CA certificate (truststore)?')) return
  try {
    await api.delete(`/servers/${tlsServerId.value}/tls/truststore`)
    tlsStatus.value.truststoreConfigured = false
    tlsSuccess.value = 'Truststore removed'
    await loadServers()
  } catch (e: any) {
    tlsError.value = e.response?.data?.error || 'Failed to remove truststore'
  }
}

async function deleteKeystore() {
  if (!confirm('Remove the client certificate (keystore)?')) return
  try {
    await api.delete(`/servers/${tlsServerId.value}/tls/keystore`)
    tlsStatus.value.keystoreConfigured = false
    tlsSuccess.value = 'Keystore removed'
    await loadServers()
  } catch (e: any) {
    tlsError.value = e.response?.data?.error || 'Failed to remove keystore'
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">PeSIT Servers</h1>
      <div class="flex gap-3">
        <button @click="loadServers" class="btn btn-secondary flex items-center gap-2" :disabled="loading" aria-label="Refresh servers">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" aria-hidden="true" />
          Refresh
        </button>
        <button @click="openAddModal" class="btn btn-primary flex items-center gap-2" aria-label="Add server">
          <Plus class="h-4 w-4" aria-hidden="true" />
          Add Server
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" aria-hidden="true" />
    </div>

    <div v-else-if="servers.length === 0" class="card text-center py-12">
      <Server class="h-16 w-16 mx-auto mb-4 text-gray-400" aria-hidden="true" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No servers configured</h3>
      <p class="text-gray-500 mb-4">Add a PeSIT server to start transferring files</p>
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
              <Server :class="['h-6 w-6', server.enabled ? 'text-green-600' : 'text-gray-400']" aria-hidden="true" />
              <span class="sr-only">{{ server.enabled ? 'Enabled' : 'Disabled' }}</span>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-lg font-semibold text-gray-900">{{ server.name }}</h3>
                <span v-if="server.defaultServer" class="badge badge-info flex items-center gap-1">
                  <Star class="h-3 w-3" aria-hidden="true" /> Default
                </span>
                <button 
                  v-if="server.tlsEnabled" 
                  @click="openTlsModal(server)"
                  class="badge badge-success flex items-center gap-1 cursor-pointer hover:bg-green-200"
                  title="Configure TLS certificates"
                >
                  <Shield class="h-3 w-3" aria-hidden="true" /> TLS
                  <span v-if="!server.truststoreConfigured" class="text-yellow-600">!</span>
                </button>
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
              aria-label="Set as default server"
            >
              <Star class="h-5 w-5" aria-hidden="true" />
            </button>
            <button
              @click="openEditModal(server)"
              class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg"
              title="Edit"
              aria-label="Edit server"
            >
              <Pencil class="h-5 w-5" aria-hidden="true" />
            </button>
            <button
              @click="deleteServer(server)"
              class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
              title="Delete"
              aria-label="Delete server"
            >
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

        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6" role="dialog" aria-modal="true" aria-labelledby="server-modal-title">
          <h2 id="server-modal-title" class="text-xl font-bold text-gray-900 mb-6">
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
              <p class="text-xs text-gray-500 mt-1">The PeSIT server identifier to connect to</p>
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

    <!-- TLS Configuration Modal -->
    <div v-if="showTlsModal" class="fixed inset-0 z-50 overflow-y-auto" @keydown.escape="showTlsModal = false">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showTlsModal = false" />

        <div class="relative bg-white rounded-xl shadow-xl max-w-2xl w-full p-6" role="dialog" aria-modal="true" aria-labelledby="tls-modal-title">
          <h2 id="tls-modal-title" class="text-xl font-bold text-gray-900 mb-2 flex items-center gap-2">
            <Shield class="h-6 w-6 text-green-600" aria-hidden="true" />
            TLS Certificates - {{ tlsServerName }}
          </h2>
          <p class="text-sm text-gray-500 mb-6">Upload certificates to enable secure TLS connections</p>

          <div v-if="tlsError" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm flex items-center gap-2">
            <AlertCircle class="h-4 w-4" aria-hidden="true" />
            {{ tlsError }}
          </div>
          <div v-if="tlsSuccess" class="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm flex items-center gap-2">
            <CheckCircle class="h-4 w-4" aria-hidden="true" />
            {{ tlsSuccess }}
          </div>

          <div class="grid md:grid-cols-2 gap-6">
            <!-- Truststore (CA Certificate) -->
            <div class="border rounded-lg p-4">
              <div class="flex items-center gap-2 mb-3">
                <Lock class="h-5 w-5 text-blue-600" aria-hidden="true" />
                <h3 class="font-semibold">CA Certificate (Truststore)</h3>
              </div>
              <p class="text-xs text-gray-500 mb-3">Required to verify the server's certificate</p>
              
              <div v-if="tlsStatus.truststoreConfigured" class="mb-3 p-2 bg-green-50 rounded flex items-center justify-between">
                <span class="text-sm text-green-700 flex items-center gap-1">
                  <CheckCircle class="h-4 w-4" aria-hidden="true" /> Configured
                </span>
                <button @click="deleteTruststore" class="text-red-600 hover:text-red-800 text-sm">Remove</button>
              </div>

              <div class="space-y-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">Certificate File</label>
                  <input type="file" @change="onTruststoreFileChange" accept=".pem,.crt,.cer,.p12,.pfx" class="input text-sm" />
                  <p class="text-xs text-gray-500 mt-1">PEM (.pem, .crt, .cer) or PKCS12 (.p12, .pfx)</p>
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">Password <span class="text-gray-400">(PKCS12 only)</span></label>
                  <input v-model="truststorePassword" type="password" class="input" placeholder="Not required for PEM files" />
                </div>
                <button 
                  @click="uploadTruststore" 
                  class="btn btn-primary w-full flex items-center justify-center gap-2"
                  :disabled="uploading || !truststoreFile"
                >
                  <Upload class="h-4 w-4" aria-hidden="true" />
                  {{ uploading ? 'Uploading...' : 'Upload CA Certificate' }}
                </button>
              </div>
            </div>

            <!-- Keystore (Client Certificate) -->
            <div class="border rounded-lg p-4">
              <div class="flex items-center gap-2 mb-3">
                <Key class="h-5 w-5 text-purple-600" aria-hidden="true" />
                <h3 class="font-semibold">Client Certificate (Keystore)</h3>
              </div>
              <p class="text-xs text-gray-500 mb-3">Optional - for mutual TLS authentication</p>
              
              <div v-if="tlsStatus.keystoreConfigured" class="mb-3 p-2 bg-green-50 rounded flex items-center justify-between">
                <span class="text-sm text-green-700 flex items-center gap-1">
                  <CheckCircle class="h-4 w-4" aria-hidden="true" /> Configured
                </span>
                <button @click="deleteKeystore" class="text-red-600 hover:text-red-800 text-sm">Remove</button>
              </div>

              <div class="space-y-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">PKCS12 File (.p12)</label>
                  <input type="file" @change="onKeystoreFileChange" accept=".p12,.pfx" class="input text-sm" />
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">Password</label>
                  <input v-model="keystorePassword" type="password" class="input" placeholder="Keystore password" />
                </div>
                <button 
                  @click="uploadKeystore" 
                  class="btn btn-secondary w-full flex items-center justify-center gap-2"
                  :disabled="uploading || !keystoreFile"
                >
                  <Upload class="h-4 w-4" aria-hidden="true" />
                  {{ uploading ? 'Uploading...' : 'Upload Keystore' }}
                </button>
              </div>
            </div>
          </div>

          <div class="flex justify-end mt-6">
            <button @click="showTlsModal = false" class="btn btn-secondary">Close</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
