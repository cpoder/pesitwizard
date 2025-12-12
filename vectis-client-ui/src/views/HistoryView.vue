<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Upload, 
  Download,
  RefreshCw,
  CheckCircle,
  XCircle,
  Clock,
  Eye,
  Play,
  Star
} from 'lucide-vue-next'
import api from '@/api'
import { useToast } from '@/composables/useToast'

const toast = useToast()

const transfers = ref<any[]>([])
const loading = ref(true)
const replaying = ref<string | null>(null)
const addingFavorite = ref<string | null>(null)
const favoriteName = ref('')
const showFavoriteModal = ref(false)
const selectedForFavorite = ref<any>(null)
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const selectedTransfer = ref<any>(null)
const showModal = ref(false)

onMounted(() => loadTransfers())

async function loadTransfers(page = 0) {
  loading.value = true
  try {
    const response = await api.get(`/transfers/history?page=${page}&size=20`)
    transfers.value = response.data.content || []
    totalPages.value = response.data.totalPages || 0
    totalElements.value = response.data.totalElements || 0
    currentPage.value = page
  } catch (e) {
    console.error('Failed to load transfers:', e)
  } finally {
    loading.value = false
  }
}

function showDetails(transfer: any) {
  selectedTransfer.value = transfer
  showModal.value = true
}

function formatBytes(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

async function replayTransfer(transfer: any) {
  if (transfer.direction === 'MESSAGE') {
    toast.warning('MESSAGE transfers cannot be replayed')
    return
  }
  replaying.value = transfer.id
  try {
    await api.post(`/transfers/${transfer.id}/replay`)
    toast.success('Transfer replay started')
    await loadTransfers(currentPage.value)
  } catch (e: any) {
    toast.error('Replay failed: ' + (e.response?.data?.message || e.message))
  } finally {
    replaying.value = null
  }
}

function openFavoriteModal(transfer: any) {
  if (transfer.direction === 'MESSAGE') {
    toast.warning('MESSAGE transfers cannot be added to favorites')
    return
  }
  selectedForFavorite.value = transfer
  
  // Generate default name based on direction
  if (transfer.direction === 'SEND') {
    const filePath = transfer.localFilename || 'file'
    const virtualFile = transfer.remoteFilename || 'remote'
    const partnerId = transfer.partnerId || 'unknown'
    favoriteName.value = `send ${filePath} to ${virtualFile} as ${partnerId}`
  } else {
    const virtualFile = transfer.remoteFilename || 'remote'
    const partnerId = transfer.partnerId || 'unknown'
    favoriteName.value = `receive ${virtualFile} as ${partnerId}`
  }
  
  showFavoriteModal.value = true
}

async function addToFavorites() {
  if (!selectedForFavorite.value || !favoriteName.value.trim()) return
  addingFavorite.value = selectedForFavorite.value.id
  try {
    await api.post(`/favorites/from-history/${selectedForFavorite.value.id}?name=${encodeURIComponent(favoriteName.value)}`)
    showFavoriteModal.value = false
    toast.success('Added to favorites!')
  } catch (e: any) {
    toast.error('Failed to add to favorites: ' + (e.response?.data?.message || e.message))
  } finally {
    addingFavorite.value = null
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Transfer History</h1>
      <button @click="loadTransfers(currentPage)" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
        <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        Refresh
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" />
    </div>

    <div v-else-if="transfers.length === 0" class="card text-center py-12">
      <Clock class="h-16 w-16 mx-auto mb-4 text-gray-400" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No transfers yet</h3>
      <p class="text-gray-500 mb-4">Start a transfer to see history</p>
      <RouterLink to="/transfer" class="btn btn-primary">Start Transfer</RouterLink>
    </div>

    <div v-else class="card overflow-hidden p-0">
      <table class="w-full">
        <thead class="bg-gray-50 border-b">
          <tr>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Direction</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Server</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Partner</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">File</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Size</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Duration</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-200">
          <tr v-for="transfer in transfers" :key="transfer.id" class="hover:bg-gray-50">
            <td class="px-4 py-3">
              <Upload v-if="transfer.direction === 'SEND'" class="h-5 w-5 text-blue-600" />
              <Download v-else class="h-5 w-5 text-green-600" />
            </td>
            <td class="px-4 py-3 font-medium text-gray-900">{{ transfer.serverName }}</td>
            <td class="px-4 py-3 text-gray-600">{{ transfer.partnerId || '-' }}</td>
            <td class="px-4 py-3 text-gray-600 truncate max-w-[200px]" :title="transfer.remoteFilename">
              {{ transfer.remoteFilename || transfer.localFilename || '-' }}
            </td>
            <td class="px-4 py-3 text-gray-600">{{ formatBytes(transfer.fileSize) }}</td>
            <td class="px-4 py-3">
              <span :class="[
                'badge',
                transfer.status === 'COMPLETED' ? 'badge-success' : 
                transfer.status === 'FAILED' ? 'badge-danger' : 'badge-warning'
              ]">
                <CheckCircle v-if="transfer.status === 'COMPLETED'" class="h-3 w-3 mr-1" />
                <XCircle v-else-if="transfer.status === 'FAILED'" class="h-3 w-3 mr-1" />
                <Clock v-else class="h-3 w-3 mr-1" />
                {{ transfer.status }}
              </span>
            </td>
            <td class="px-4 py-3 text-sm text-gray-600">{{ formatDate(transfer.startedAt) }}</td>
            <td class="px-4 py-3 text-sm text-gray-600">{{ transfer.durationMs ? transfer.durationMs + 'ms' : '-' }}</td>
            <td class="px-4 py-3 flex gap-1">
              <button 
                @click="replayTransfer(transfer)" 
                :disabled="replaying === transfer.id || transfer.direction === 'MESSAGE'"
                class="p-2 text-gray-400 hover:text-green-600 hover:bg-green-50 rounded-lg disabled:opacity-50"
                title="Replay transfer"
              >
                <RefreshCw v-if="replaying === transfer.id" class="h-4 w-4 animate-spin" />
                <Play v-else class="h-4 w-4" />
              </button>
              <button 
                @click="openFavoriteModal(transfer)" 
                :disabled="transfer.direction === 'MESSAGE'"
                class="p-2 text-gray-400 hover:text-yellow-600 hover:bg-yellow-50 rounded-lg disabled:opacity-50"
                title="Add to favorites"
              >
                <Star class="h-4 w-4" />
              </button>
              <button @click="showDetails(transfer)" class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg" title="View details">
                <Eye class="h-4 w-4" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="px-4 py-3 border-t bg-gray-50 flex items-center justify-between">
        <span class="text-sm text-gray-600">
          Showing {{ currentPage * 20 + 1 }} - {{ Math.min((currentPage + 1) * 20, totalElements) }} of {{ totalElements }}
        </span>
        <div class="flex gap-2">
          <button 
            @click="loadTransfers(currentPage - 1)" 
            :disabled="currentPage === 0"
            class="btn btn-secondary text-sm"
          >
            Previous
          </button>
          <button 
            @click="loadTransfers(currentPage + 1)" 
            :disabled="currentPage >= totalPages - 1"
            class="btn btn-secondary text-sm"
          >
            Next
          </button>
        </div>
      </div>
    </div>

    <!-- Details Modal -->
    <div v-if="showModal && selectedTransfer" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6">
          <div class="flex items-center gap-3 mb-6">
            <Upload v-if="selectedTransfer.direction === 'SEND'" class="h-6 w-6 text-blue-600" />
            <Download v-else class="h-6 w-6 text-green-600" />
            <h2 class="text-xl font-bold text-gray-900">Transfer Details</h2>
          </div>

          <div class="space-y-3">
            <div class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Status</span>
              <span :class="[
                'badge',
                selectedTransfer.status === 'COMPLETED' ? 'badge-success' : 
                selectedTransfer.status === 'FAILED' ? 'badge-danger' : 'badge-warning'
              ]">
                {{ selectedTransfer.status }}
              </span>
            </div>
            <div class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Direction</span>
              <span class="font-medium">{{ selectedTransfer.direction }}</span>
            </div>
            <div class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Server</span>
              <span class="font-medium">{{ selectedTransfer.serverName }}</span>
            </div>
            <div v-if="selectedTransfer.localFilename" class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Local File</span>
              <span class="font-medium text-sm truncate max-w-[250px]">{{ selectedTransfer.localFilename }}</span>
            </div>
            <div v-if="selectedTransfer.remoteFilename" class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Remote File</span>
              <span class="font-medium">{{ selectedTransfer.remoteFilename }}</span>
            </div>
            <div v-if="selectedTransfer.fileSize" class="flex justify-between py-2 border-b">
              <span class="text-gray-500">File Size</span>
              <span class="font-medium">{{ formatBytes(selectedTransfer.fileSize) }}</span>
            </div>
            <div v-if="selectedTransfer.bytesTransferred" class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Transferred</span>
              <span class="font-medium">{{ formatBytes(selectedTransfer.bytesTransferred) }}</span>
            </div>
            <div class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Started</span>
              <span class="font-medium">{{ formatDate(selectedTransfer.startedAt) }}</span>
            </div>
            <div v-if="selectedTransfer.completedAt" class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Completed</span>
              <span class="font-medium">{{ formatDate(selectedTransfer.completedAt) }}</span>
            </div>
            <div v-if="selectedTransfer.durationMs" class="flex justify-between py-2 border-b">
              <span class="text-gray-500">Duration</span>
              <span class="font-medium">{{ selectedTransfer.durationMs }}ms</span>
            </div>
            <div v-if="selectedTransfer.checksum" class="py-2 border-b">
              <span class="text-gray-500 block mb-1">Checksum</span>
              <span class="font-mono text-xs break-all">{{ selectedTransfer.checksum }}</span>
            </div>
          </div>

          <div v-if="selectedTransfer.errorMessage" class="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            <strong>Error:</strong> {{ selectedTransfer.errorMessage }}
          </div>

          <div class="flex justify-end mt-6">
            <button @click="showModal = false" class="btn btn-secondary">Close</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Add to Favorites Modal -->
    <div v-if="showFavoriteModal && selectedForFavorite" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showFavoriteModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-md w-full p-6">
          <div class="flex items-center gap-3 mb-6">
            <Star class="h-6 w-6 text-yellow-500" />
            <h2 class="text-xl font-bold text-gray-900">Add to Favorites</h2>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
              <input 
                v-model="favoriteName" 
                type="text" 
                class="input w-full"
                placeholder="Enter a name for this favorite"
              />
            </div>
            <div class="text-sm text-gray-500">
              <p><strong>Direction:</strong> {{ selectedForFavorite.direction }}</p>
              <p><strong>Server:</strong> {{ selectedForFavorite.serverName }}</p>
              <p><strong>File:</strong> {{ selectedForFavorite.remoteFilename || selectedForFavorite.localFilename }}</p>
            </div>
          </div>

          <div class="flex justify-end gap-3 mt-6">
            <button @click="showFavoriteModal = false" class="btn btn-secondary">Cancel</button>
            <button 
              @click="addToFavorites" 
              :disabled="!favoriteName.trim() || !!addingFavorite"
              class="btn btn-primary flex items-center gap-2"
            >
              <RefreshCw v-if="addingFavorite" class="h-4 w-4 animate-spin" />
              <Star v-else class="h-4 w-4" />
              Add to Favorites
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
