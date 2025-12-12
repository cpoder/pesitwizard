<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Upload, 
  Download,
  FileText,
  Server,
  CheckCircle,
  XCircle,
  Loader2
} from 'lucide-vue-next'
import api from '@/api'
import PathPlaceholderInput from '@/components/PathPlaceholderInput.vue'

const servers = ref<any[]>([])
const loading = ref(true)
const transferring = ref(false)
const result = ref<any>(null)
const error = ref('')

const form = ref({
  server: '',
  partnerId: '',
  direction: 'SEND' as 'SEND' | 'RECEIVE',
  localPath: '',
  remoteFilename: ''
})


onMounted(async () => {
  await loadServers()
  loading.value = false
})

async function loadServers() {
  try {
    const response = await api.get('/servers')
    servers.value = response.data || []
    // Set default server
    const defaultServer = servers.value.find(s => s.defaultServer)
    if (defaultServer) {
      form.value.server = defaultServer.name
    } else if (servers.value.length > 0) {
      form.value.server = servers.value[0].name
    }
  } catch (e) {
    console.error('Failed to load servers:', e)
  }
}

async function startTransfer() {
  if (!form.value.server) {
    error.value = 'Please select a server'
    return
  }
  if (!form.value.partnerId) {
    error.value = 'Please enter a partner ID'
    return
  }
  if (!form.value.localPath) {
    error.value = 'Please enter a local file path'
    return
  }
  if (!form.value.remoteFilename) {
    error.value = 'Please enter a remote filename (virtual file ID)'
    return
  }

  transferring.value = true
  error.value = ''
  result.value = null

  try {
    const endpoint = form.value.direction === 'SEND' ? '/transfers/send' : '/transfers/receive'
    const response = await api.post(endpoint, {
      server: form.value.server,
      partnerId: form.value.partnerId,
      localPath: form.value.localPath,
      remoteFilename: form.value.remoteFilename
    })
    result.value = response.data
  } catch (e: any) {
    error.value = e.response?.data?.message || e.response?.data?.error || 'Transfer failed'
    console.error('Transfer failed:', e)
  } finally {
    transferring.value = false
  }
}

function resetForm() {
  result.value = null
  error.value = ''
  form.value.partnerId = ''
  form.value.localPath = ''
  form.value.remoteFilename = ''
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">File Transfer</h1>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Transfer Form -->
      <div class="card">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">New Transfer</h2>

        <div v-if="servers.length === 0 && !loading" class="text-center py-8">
          <Server class="h-12 w-12 mx-auto mb-4 text-gray-400" />
          <p class="text-gray-500 mb-4">No servers configured</p>
          <RouterLink to="/servers" class="btn btn-primary">Add Server</RouterLink>
        </div>

        <form v-else @submit.prevent="startTransfer" class="space-y-4">
          <!-- Direction -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Direction</label>
            <div class="flex gap-4">
              <label class="flex items-center gap-2 cursor-pointer">
                <input 
                  v-model="form.direction" 
                  type="radio" 
                  value="SEND" 
                  class="text-primary-600"
                />
                <Send class="h-4 w-4 text-blue-600" />
                <span>Send</span>
              </label>
              <label class="flex items-center gap-2 cursor-pointer">
                <input 
                  v-model="form.direction" 
                  type="radio" 
                  value="RECEIVE" 
                  class="text-primary-600"
                />
                <Download class="h-4 w-4 text-green-600" />
                <span>Receive</span>
              </label>
            </div>
          </div>

          <!-- Server -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Server *</label>
            <select v-model="form.server" class="select" required>
              <option value="" disabled>Select a server</option>
              <option v-for="server in servers" :key="server.id" :value="server.name">
                {{ server.name }} ({{ server.host }}:{{ server.port }})
              </option>
            </select>
          </div>

          <!-- Partner ID -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Partner ID *</label>
            <input 
              v-model="form.partnerId" 
              type="text" 
              class="input" 
              required 
              placeholder="MY_CLIENT_ID"
            />
            <p class="text-xs text-gray-500 mt-1">
              Your client identifier (must be configured as a partner on the server)
            </p>
          </div>

          <!-- Local Path -->
          <div>
            <template v-if="form.direction === 'SEND'">
              <label class="block text-sm font-medium text-gray-700 mb-1">Local File Path *</label>
              <input 
                v-model="form.localPath" 
                type="text" 
                class="input" 
                required 
                placeholder="/path/to/file.txt"
              />
              <p class="text-xs text-gray-500 mt-1">Full path to the file to send</p>
            </template>
            <template v-else>
              <PathPlaceholderInput
                v-model="form.localPath"
                label="Save To Path *"
                placeholder="/data/received/${partner}/${file}"
                direction="RECEIVE"
              />
              <p class="text-xs text-gray-500 mt-1">Use placeholders for dynamic paths</p>
            </template>
          </div>

          <!-- Remote Filename (Virtual File ID) -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Virtual File ID *</label>
            <input 
              v-model="form.remoteFilename" 
              type="text" 
              class="input" 
              required 
              placeholder="DATA_FILE"
            />
            <p class="text-xs text-gray-500 mt-1">
              The virtual file identifier configured on the server
            </p>
          </div>

          <div v-if="error" class="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <button 
            type="submit" 
            class="btn btn-primary w-full flex items-center justify-center gap-2"
            :disabled="transferring"
          >
            <Loader2 v-if="transferring" class="h-4 w-4 animate-spin" />
            <Upload v-else-if="form.direction === 'SEND'" class="h-4 w-4" />
            <Download v-else class="h-4 w-4" />
            {{ transferring ? 'Transferring...' : (form.direction === 'SEND' ? 'Send File' : 'Receive File') }}
          </button>
        </form>
      </div>

      <!-- Result -->
      <div class="card">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Transfer Result</h2>

        <div v-if="!result" class="text-center py-12 text-gray-500">
          <FileText class="h-12 w-12 mx-auto mb-4 opacity-50" />
          <p>Start a transfer to see results</p>
        </div>

        <div v-else class="space-y-4">
          <div class="flex items-center gap-3">
            <CheckCircle v-if="result.status === 'COMPLETED'" class="h-8 w-8 text-green-600" />
            <XCircle v-else-if="result.status === 'FAILED'" class="h-8 w-8 text-red-600" />
            <Loader2 v-else class="h-8 w-8 text-yellow-600 animate-spin" />
            <div>
              <p class="font-semibold text-lg" :class="{
                'text-green-600': result.status === 'COMPLETED',
                'text-red-600': result.status === 'FAILED',
                'text-yellow-600': result.status === 'IN_PROGRESS'
              }">
                {{ result.status }}
              </p>
              <p class="text-sm text-gray-500">{{ result.direction }} transfer</p>
            </div>
          </div>

          <div class="border-t pt-4 space-y-2">
            <div class="flex justify-between text-sm">
              <span class="text-gray-500">Server</span>
              <span class="font-medium">{{ result.serverName }}</span>
            </div>
            <div class="flex justify-between text-sm">
              <span class="text-gray-500">File</span>
              <span class="font-medium truncate max-w-[200px]">{{ result.remoteFilename }}</span>
            </div>
            <div v-if="result.fileSize" class="flex justify-between text-sm">
              <span class="text-gray-500">Size</span>
              <span class="font-medium">{{ formatBytes(result.fileSize) }}</span>
            </div>
            <div v-if="result.bytesTransferred" class="flex justify-between text-sm">
              <span class="text-gray-500">Transferred</span>
              <span class="font-medium">{{ formatBytes(result.bytesTransferred) }}</span>
            </div>
            <div v-if="result.durationMs" class="flex justify-between text-sm">
              <span class="text-gray-500">Duration</span>
              <span class="font-medium">{{ result.durationMs }}ms</span>
            </div>
            <div v-if="result.checksum" class="flex justify-between text-sm">
              <span class="text-gray-500">Checksum</span>
              <span class="font-mono text-xs truncate max-w-[200px]">{{ result.checksum }}</span>
            </div>
          </div>

          <div v-if="result.errorMessage" class="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ result.errorMessage }}
          </div>

          <button @click="resetForm" class="btn btn-secondary w-full">
            New Transfer
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
