<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { 
  Upload, 
  Download,
  FileText,
  Server,
  CheckCircle,
  XCircle,
  Loader2,
  Plug,
  FolderOpen,
  ChevronDown,
  RotateCcw,
  Play,
  StopCircle,
  RefreshCw
} from 'lucide-vue-next'
import api from '@/api'
import PathPlaceholderInput from '@/components/PathPlaceholderInput.vue'
import FileBrowser from '@/components/FileBrowser.vue'
import { useTransferProgress } from '@/composables/useTransferProgress'

interface StorageConnection {
  id: string
  name: string
  connectorType: string
  enabled: boolean
}

const servers = ref<any[]>([])
const connections = ref<StorageConnection[]>([])
const loading = ref(true)
const transferring = ref(false)
const result = ref<any>(null)
const error = ref('')
const currentTransferId = ref<string | null>(null)
const progress = ref({ bytesTransferred: 0, fileSize: 0, percentage: 0 })
const resumableTransfers = ref<any[]>([])

// WebSocket for real-time progress updates
const { 
  progress: wsProgress, 
  subscribeToTransfer,
  unsubscribe: wsUnsubscribe,
  disconnect: wsDisconnect,
  reset: wsReset
} = useTransferProgress()

// Watch WebSocket progress and update local progress
watch(wsProgress, (newProgress) => {
  if (newProgress) {
    progress.value = {
      bytesTransferred: newProgress.bytesTransferred,
      fileSize: newProgress.fileSize,
      percentage: newProgress.percentage
    }
    // Check if transfer is complete
    if (newProgress.status === 'COMPLETED' || newProgress.status === 'FAILED') {
      wsUnsubscribe()
      
      // Immediately update UI with WebSocket status in case API call fails
      if (newProgress.status === 'FAILED') {
        error.value = newProgress.errorMessage || 'Transfer failed'
        transferring.value = false
        // Update result status immediately
        if (result.value) {
          result.value = { ...result.value, status: 'FAILED', errorMessage: newProgress.errorMessage }
        }
      }
      
      // Fetch final result from API
      if (currentTransferId.value) {
        api.get(`/transfers/${currentTransferId.value}`).then(response => {
          result.value = response.data
          transferring.value = false
        }).catch(e => {
          console.error('Failed to fetch transfer result:', e)
          transferring.value = false
        })
      }
    }
  }
})

const form = ref({
  server: '',
  partnerId: '',
  password: '',
  direction: 'SEND' as 'SEND' | 'RECEIVE',
  sourceConnectionId: '' as string,  // '' = local filesystem
  destinationConnectionId: '' as string,  // '' = local filesystem
  filename: '',
  remoteFilename: '',
  // Advanced options
  syncPointsEnabled: false,
  syncPointIntervalBytes: null as number | null,  // null = auto
  resyncEnabled: false,
  recordLength: null as number | null  // PI 32 - configured per virtual file on server
})

const showAdvancedOptions = ref(false)

// File browser state
const showFileBrowser = ref(false)
const fileBrowserMode = ref<'file' | 'directory'>('file')
const fileBrowserConnectionId = ref('')

function openFileBrowser(mode: 'file' | 'directory', connectionId: string) {
  fileBrowserMode.value = mode
  fileBrowserConnectionId.value = connectionId
  showFileBrowser.value = true
}

const activeConnectionLabel = computed(() => 
  form.value.direction === 'SEND' ? 'Source Storage' : 'Destination Storage'
)


onMounted(async () => {
  await Promise.all([loadServers(), loadConnections(), loadResumableTransfers()])
  loading.value = false
})

async function loadResumableTransfers() {
  try {
    const response = await api.get('/transfers/resumable?size=5')
    resumableTransfers.value = response.data?.content || []
  } catch (e) {
    console.error('Failed to load resumable transfers:', e)
  }
}

let pollingInterval: ReturnType<typeof setInterval> | null = null

function startProgressTracking(transferId: string) {
  // Connect to WebSocket and subscribe to transfer progress
  wsReset()
  subscribeToTransfer(transferId)
  
  // Also poll API as fallback in case WebSocket misses the message
  pollingInterval = setInterval(async () => {
    try {
      const response = await api.get(`/transfers/${transferId}`)
      const status = response.data?.status
      console.log('Polling transfer status:', status)
      if (status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED') {
        stopProgressTracking()
        result.value = response.data
        transferring.value = false
        if (status === 'FAILED') {
          error.value = response.data?.errorMessage || 'Transfer failed'
        }
      }
    } catch (e) {
      console.error('Polling error:', e)
    }
  }, 2000) // Poll every 2 seconds
}

function stopProgressTracking() {
  wsUnsubscribe()
  if (pollingInterval) {
    clearInterval(pollingInterval)
    pollingInterval = null
  }
}

// Cleanup on unmount
onUnmounted(() => {
  wsDisconnect()
  if (pollingInterval) {
    clearInterval(pollingInterval)
    pollingInterval = null
  }
})

async function cancelCurrentTransfer() {
  if (!currentTransferId.value) return
  
  try {
    const response = await api.post(`/transfers/${currentTransferId.value}/cancel`)
    result.value = response.data
    stopProgressTracking()
    transferring.value = false
    await loadResumableTransfers() // Refresh resumable list
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to cancel transfer'
    console.error('Cancel failed:', e)
  }
}

async function loadServers() {
  try {
    const response = await api.get('/servers')
    servers.value = response.data || []
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

async function loadConnections() {
  try {
    const response = await api.get('/connectors/connections')
    connections.value = (response.data || []).filter((c: StorageConnection) => c.enabled)
  } catch (e) {
    console.error('Failed to load connections:', e)
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
  if (!form.value.filename) {
    error.value = 'Please enter a filename'
    return
  }
  if (!form.value.remoteFilename) {
    error.value = 'Please enter a remote filename (virtual file ID)'
    return
  }

  transferring.value = true
  error.value = ''
  result.value = null
  currentTransferId.value = null
  progress.value = { bytesTransferred: 0, fileSize: 0, percentage: 0 }

  try {
    const endpoint = form.value.direction === 'SEND' ? '/transfers/send' : '/transfers/receive'
    const payload: Record<string, any> = {
      server: form.value.server,
      partnerId: form.value.partnerId,
      password: form.value.password || undefined,
      filename: form.value.filename,
      remoteFilename: form.value.remoteFilename,
      // Advanced options
      syncPointsEnabled: form.value.syncPointsEnabled || undefined,
      syncPointIntervalBytes: form.value.syncPointIntervalBytes || undefined,
      resyncEnabled: form.value.resyncEnabled || undefined,
      recordLength: form.value.recordLength || undefined
    }
    
    // Add connection IDs if selected
    if (form.value.direction === 'SEND' && form.value.sourceConnectionId) {
      payload.sourceConnectionId = form.value.sourceConnectionId
    }
    if (form.value.direction === 'RECEIVE' && form.value.destinationConnectionId) {
      payload.destinationConnectionId = form.value.destinationConnectionId
    }
    
    const response = await api.post(endpoint, payload)
    result.value = response.data
    console.log('Transfer response:', response.data)
    
    // Start progress polling if transfer is in progress
    if (response.data?.transferId && response.data?.status === 'IN_PROGRESS') {
      console.log('Starting WebSocket tracking for transfer:', response.data.transferId)
      currentTransferId.value = response.data.transferId
      startProgressTracking(response.data.transferId)
    } else {
      console.log('Transfer not IN_PROGRESS, status:', response.data?.status)
      transferring.value = false
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || e.response?.data?.error || 'Transfer failed'
    console.error('Transfer failed:', e)
    transferring.value = false
  }
}

function resetForm() {
  result.value = null
  error.value = ''
  form.value.partnerId = ''
  form.value.password = ''
  form.value.filename = ''
  form.value.remoteFilename = ''
  form.value.sourceConnectionId = ''
  form.value.destinationConnectionId = ''
  form.value.syncPointsEnabled = false
  form.value.syncPointIntervalBytes = null
  form.value.resyncEnabled = false
  form.value.recordLength = null
  showAdvancedOptions.value = false
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function replayTransfer(transferId: string) {
  transferring.value = true
  error.value = ''
  try {
    const response = await api.post(`/transfers/${transferId}/replay`)
    result.value = response.data
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Replay failed'
    console.error('Replay failed:', e)
  } finally {
    transferring.value = false
  }
}

async function resumeTransfer(transferId: string) {
  transferring.value = true
  error.value = ''
  try {
    const response = await api.post(`/transfers/${transferId}/resume`)
    result.value = response.data
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Resume failed'
    console.error('Resume failed:', e)
  } finally {
    transferring.value = false
  }
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

          <!-- Password -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <input 
              v-model="form.password" 
              type="password" 
              class="input" 
              placeholder="Partner password (if required)"
            />
            <p class="text-xs text-gray-500 mt-1">
              Leave empty if the partner doesn't require authentication
            </p>
          </div>

          <!-- Storage Connection -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">
              <Plug class="h-4 w-4 inline mr-1" />
              {{ activeConnectionLabel }}
            </label>
            <select 
              v-if="form.direction === 'SEND'"
              v-model="form.sourceConnectionId" 
              class="select"
            >
              <option value="">Local Filesystem</option>
              <option v-for="conn in connections" :key="conn.id" :value="conn.id">
                {{ conn.name }} ({{ conn.connectorType }})
              </option>
            </select>
            <select 
              v-else
              v-model="form.destinationConnectionId" 
              class="select"
            >
              <option value="">Local Filesystem</option>
              <option v-for="conn in connections" :key="conn.id" :value="conn.id">
                {{ conn.name }} ({{ conn.connectorType }})
              </option>
            </select>
            <p class="text-xs text-gray-500 mt-1">
              {{ form.direction === 'SEND' ? 'Where to read the file from' : 'Where to save the received file' }}
            </p>
          </div>

          <!-- Filename -->
          <div>
            <template v-if="form.direction === 'SEND'">
              <label class="block text-sm font-medium text-gray-700 mb-1">Filename *</label>
              <div class="flex gap-2">
                <input 
                  v-model="form.filename" 
                  type="text" 
                  class="input flex-1" 
                  required 
                  :placeholder="form.sourceConnectionId ? 'path/to/file.txt' : '/path/to/file.txt'"
                />
                <button 
                  type="button"
                  @click="openFileBrowser('file', form.sourceConnectionId)"
                  class="btn btn-secondary flex items-center gap-1"
                  title="Browse files"
                >
                  <FolderOpen class="h-4 w-4" />
                </button>
              </div>
              <p class="text-xs text-gray-500 mt-1">
                {{ form.sourceConnectionId ? 'Select file from storage connection' : 'Select file from local filesystem' }}
              </p>
            </template>
            <template v-else>
              <label class="block text-sm font-medium text-gray-700 mb-1">Destination *</label>
              <div class="flex gap-2">
                <div class="flex-1">
                  <PathPlaceholderInput
                    v-model="form.filename"
                    label=""
                    :placeholder="form.destinationConnectionId ? 'output/${file}' : '/data/received/${file}'"
                    direction="RECEIVE"
                  />
                </div>
                <button 
                  type="button"
                  @click="openFileBrowser('directory', form.destinationConnectionId)"
                  class="btn btn-secondary flex items-center gap-1 self-start"
                  title="Browse directories"
                >
                  <FolderOpen class="h-4 w-4" />
                </button>
              </div>
              <p class="text-xs text-gray-500 mt-1">
                Select directory + filename pattern (use ${file}, ${date}, etc.)
              </p>
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

          <!-- Advanced Options Toggle -->
          <div class="border-t pt-4">
            <button 
              type="button"
              @click="showAdvancedOptions = !showAdvancedOptions"
              class="flex items-center gap-2 text-sm text-gray-600 hover:text-gray-900"
            >
              <ChevronDown 
                class="h-4 w-4 transition-transform" 
                :class="{ 'rotate-180': showAdvancedOptions }"
              />
              Advanced Options
            </button>
            
            <div v-if="showAdvancedOptions" class="mt-4 space-y-4 p-4 bg-gray-50 rounded-lg">
              <!-- Sync Points -->
              <div>
                <label class="flex items-center gap-2">
                  <input 
                    v-model="form.syncPointsEnabled" 
                    type="checkbox" 
                    class="rounded border-gray-300 text-primary-600"
                  />
                  <span class="text-sm font-medium text-gray-700">Enable Sync Points</span>
                </label>
                <p class="text-xs text-gray-500 mt-1 ml-6">
                  Create checkpoints during transfer for resume capability
                </p>
              </div>

              <!-- Sync Point Interval -->
              <div v-if="form.syncPointsEnabled" class="ml-6">
                <label class="block text-sm font-medium text-gray-700 mb-1">
                  Sync Point Interval
                </label>
                <select v-model="form.syncPointIntervalBytes" class="select">
                  <option :value="null">Auto (based on file size)</option>
                  <option :value="262144">256 KB</option>
                  <option :value="524288">512 KB</option>
                  <option :value="1048576">1 MB</option>
                  <option :value="5242880">5 MB</option>
                  <option :value="10485760">10 MB</option>
                </select>
                <p class="text-xs text-gray-500 mt-1">
                  Auto: &lt;1MB=none, 1-10MB=256KB, 10-100MB=1MB, &gt;100MB=5MB
                </p>
              </div>

              <!-- Resync -->
              <div v-if="form.syncPointsEnabled">
                <label class="flex items-center gap-2">
                  <input 
                    v-model="form.resyncEnabled" 
                    type="checkbox" 
                    class="rounded border-gray-300 text-primary-600"
                  />
                  <span class="text-sm font-medium text-gray-700">Enable Resynchronization</span>
                </label>
                <p class="text-xs text-gray-500 mt-1 ml-6">
                  Allow resuming from last checkpoint if transfer fails
                </p>
              </div>

              <!-- Record Length (PI 32) -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">
                  Record Length (PI 32)
                </label>
                <select v-model="form.recordLength" class="select">
                  <option :value="null">Default (506 bytes)</option>
                  <option :value="128">128 bytes</option>
                  <option :value="256">256 bytes</option>
                  <option :value="506">506 bytes</option>
                  <option :value="1018">1018 bytes</option>
                  <option :value="2042">2042 bytes</option>
                  <option :value="4044">4044 bytes (SIT max)</option>
                </select>
                <p class="text-xs text-gray-500 mt-1">
                  Max article size - configured per virtual file on server. Must match server config.
                </p>
              </div>
            </div>
          </div>

          <div v-if="error" class="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <!-- Progress Bar (visible during transfer) -->
          <div v-if="transferring" class="space-y-2">
            <div class="flex justify-between text-sm text-gray-600">
              <span>Progress</span>
              <span>{{ formatBytes(progress.bytesTransferred) }} / {{ progress.fileSize > 0 ? formatBytes(progress.fileSize) : '...' }}</span>
            </div>
            <div class="w-full bg-gray-200 rounded-full h-3">
              <div 
                class="bg-primary-600 h-3 rounded-full transition-all duration-300"
                :style="{ width: progress.percentage + '%' }"
              ></div>
            </div>
            <p class="text-center text-sm text-gray-500">{{ progress.percentage }}%</p>
          </div>

          <!-- Transfer / Cancel Buttons -->
          <div class="flex gap-2">
            <button 
              v-if="!transferring"
              type="submit" 
              class="btn btn-primary flex-1 flex items-center justify-center gap-2"
            >
              <Upload v-if="form.direction === 'SEND'" class="h-4 w-4" />
              <Download v-else class="h-4 w-4" />
              {{ form.direction === 'SEND' ? 'Send File' : 'Receive File' }}
            </button>
            
            <button 
              v-if="transferring"
              type="button"
              @click="cancelCurrentTransfer"
              class="btn btn-danger flex-1 flex items-center justify-center gap-2"
            >
              <StopCircle class="h-4 w-4" />
              Cancel Transfer
            </button>
          </div>
        </form>

        <!-- Resumable Transfers Section -->
        <div v-if="resumableTransfers.length > 0" class="mt-6 border-t pt-4">
          <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
            <RefreshCw class="h-4 w-4" />
            Resumable Transfers
          </h3>
          <div class="space-y-2">
            <div 
              v-for="transfer in resumableTransfers" 
              :key="transfer.id"
              class="flex items-center justify-between p-3 bg-yellow-50 border border-yellow-200 rounded-lg"
            >
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-gray-900 truncate">{{ transfer.remoteFilename }}</p>
                <p class="text-xs text-gray-500">
                  {{ transfer.direction }} - {{ formatBytes(transfer.bytesAtLastSyncPoint || 0) }} / {{ formatBytes(transfer.fileSize || 0) }}
                  <span v-if="transfer.lastSyncPoint">(checkpoint #{{ transfer.lastSyncPoint }})</span>
                </p>
              </div>
              <button 
                @click="resumeTransfer(transfer.id)"
                class="btn btn-success btn-sm flex items-center gap-1"
                :disabled="transferring"
              >
                <Play class="h-3 w-3" />
                Resume
              </button>
            </div>
          </div>
        </div>
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

          <!-- Action buttons -->
          <div class="flex gap-2">
            <button @click="resetForm" class="btn btn-secondary flex-1">
              New Transfer
            </button>
            
            <!-- Replay button for failed/cancelled transfers -->
            <button 
              v-if="result.status === 'FAILED' || result.status === 'CANCELLED'"
              @click="replayTransfer(result.transferId)"
              class="btn btn-primary flex items-center justify-center gap-2"
              :disabled="transferring"
              title="Replay transfer from start"
            >
              <RotateCcw class="h-4 w-4" />
              Replay
            </button>

            <!-- Resume button for resumable transfers -->
            <button 
              v-if="(result.status === 'FAILED' || result.status === 'CANCELLED') && result.syncPointsEnabled && result.lastSyncPoint > 0"
              @click="resumeTransfer(result.transferId)"
              class="btn btn-success flex items-center justify-center gap-2"
              :disabled="transferring"
              title="Resume from last checkpoint"
            >
              <Play class="h-4 w-4" />
              Resume
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- File Browser Modal -->
    <FileBrowser
      v-if="showFileBrowser"
      :connection-id="fileBrowserConnectionId"
      :mode="fileBrowserMode"
      v-model="form.filename"
      @close="showFileBrowser = false"
    />
  </div>
</template>
