<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { 
  Star, 
  Play,
  Trash2,
  RefreshCw,
  Upload,
  Download,
  CheckCircle,
  XCircle,
  Calendar,
  Edit,
  X
} from 'lucide-vue-next'
import api from '@/api'
import PathPlaceholderInput from '@/components/PathPlaceholderInput.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { useToast } from '@/composables/useToast'

const toast = useToast()

interface Favorite {
  id: string
  name: string
  description?: string
  serverId: string
  serverName?: string
  partnerId?: string
  direction: 'SEND' | 'RECEIVE'
  localPath?: string
  remoteFilename?: string
  usageCount: number
  lastUsedAt?: string
  createdAt: string
}

interface ExecutionState {
  status: 'idle' | 'executing' | 'success' | 'error'
  message?: string
}

const favorites = ref<Favorite[]>([])
const loading = ref(true)
const deleting = ref<string | null>(null)
const executionStates = reactive<Record<string, ExecutionState>>({})

// Schedule modal state
const showScheduleModal = ref(false)
const selectedFavoriteForSchedule = ref<Favorite | null>(null)
const scheduleType = ref<'ONCE' | 'INTERVAL' | 'HOURLY' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CRON'>('DAILY')
const scheduledDateTime = ref('')
const dailyTime = ref('09:00')
const dayOfWeek = ref(1) // 1=Monday
const dayOfMonth = ref(1)
const intervalMinutes = ref(60)
const workingDaysOnly = ref(true)
const cronExpression = ref('')
const creatingSchedule = ref(false)

interface Calendar {
  id: string
  name: string
}
const calendars = ref<Calendar[]>([])
const selectedCalendarId = ref('')

// Edit modal state
const showEditModal = ref(false)
const editingFavorite = ref<Favorite | null>(null)
const editForm = ref({
  name: '',
  description: '',
  serverId: '',
  partnerId: '',
  localPath: '',
  remoteFilename: '',
  direction: 'SEND' as 'SEND' | 'RECEIVE'
})
const saving = ref(false)
const servers = ref<any[]>([])

onMounted(() => {
  loadFavorites()
  loadServers()
})

function getExecutionState(id: string): ExecutionState {
  return executionStates[id] || { status: 'idle' }
}

async function loadFavorites() {
  loading.value = true
  try {
    const response = await api.get('/favorites?sortBy=usage')
    favorites.value = response.data || []
    // Initialize execution states
    favorites.value.forEach(f => {
      if (!executionStates[f.id]) {
        executionStates[f.id] = { status: 'idle' }
      }
    })
  } catch (e) {
    console.error('Failed to load favorites:', e)
  } finally {
    loading.value = false
  }
}

async function executeFavorite(favorite: Favorite) {
  executionStates[favorite.id] = { status: 'executing' }
  try {
    await api.post(`/favorites/${favorite.id}/execute`)
    executionStates[favorite.id] = { status: 'success', message: 'Transfer completed!' }
    // Update usage count locally
    favorite.usageCount++
    favorite.lastUsedAt = new Date().toISOString()
    // Clear success status after 5 seconds
    setTimeout(() => {
      if (executionStates[favorite.id]?.status === 'success') {
        executionStates[favorite.id] = { status: 'idle' }
      }
    }, 5000)
  } catch (e: any) {
    const errorMsg = e.response?.data?.message || e.message || 'Transfer failed'
    executionStates[favorite.id] = { status: 'error', message: errorMsg }
    // Clear error status after 10 seconds
    setTimeout(() => {
      if (executionStates[favorite.id]?.status === 'error') {
        executionStates[favorite.id] = { status: 'idle' }
      }
    }, 10000)
  }
}

const showDeleteModal = ref(false)
const favoriteToDelete = ref<Favorite | null>(null)

function confirmDelete(favorite: Favorite) {
  favoriteToDelete.value = favorite
  showDeleteModal.value = true
}

async function deleteFavorite() {
  if (!favoriteToDelete.value) return
  deleting.value = favoriteToDelete.value.id
  try {
    await api.delete(`/favorites/${favoriteToDelete.value.id}`)
    favorites.value = favorites.value.filter(f => f.id !== favoriteToDelete.value!.id)
    delete executionStates[favoriteToDelete.value.id]
    toast.success(`Favorite "${favoriteToDelete.value.name}" deleted`)
  } catch (e: any) {
    toast.error('Failed to delete: ' + (e.response?.data?.message || e.message))
  } finally {
    deleting.value = null
    showDeleteModal.value = false
    favoriteToDelete.value = null
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return 'Never'
  return new Date(dateStr).toLocaleString()
}

async function loadCalendars() {
  try {
    const response = await api.get('/calendars')
    calendars.value = response.data || []
  } catch (e) {
    console.error('Failed to load calendars:', e)
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

function openEditModal(favorite: Favorite) {
  editingFavorite.value = favorite
  editForm.value = {
    name: favorite.name,
    description: favorite.description || '',
    serverId: favorite.serverId,
    partnerId: favorite.partnerId || '',
    localPath: favorite.localPath || '',
    remoteFilename: favorite.remoteFilename || '',
    direction: favorite.direction
  }
  showEditModal.value = true
}

async function saveFavorite() {
  if (!editingFavorite.value) return
  
  saving.value = true
  try {
    const server = servers.value.find(s => s.id === editForm.value.serverId)
    const payload = {
      ...editForm.value,
      serverName: server?.name || editForm.value.serverId
    }
    const response = await api.put(`/favorites/${editingFavorite.value.id}`, payload)
    
    // Update in local list
    const index = favorites.value.findIndex(f => f.id === editingFavorite.value!.id)
    if (index >= 0) {
      favorites.value[index] = response.data
    }
    
    showEditModal.value = false
    toast.success('Favorite updated')
  } catch (e: any) {
    toast.error('Failed to save: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

function openScheduleModal(favorite: Favorite) {
  selectedFavoriteForSchedule.value = favorite
  scheduleType.value = 'DAILY'
  dailyTime.value = '09:00'
  dayOfWeek.value = 1 // Monday
  dayOfMonth.value = 1
  workingDaysOnly.value = true
  cronExpression.value = ''
  selectedCalendarId.value = ''
  // Default to 1 hour from now for ONCE
  const defaultDate = new Date(Date.now() + 60 * 60 * 1000)
  scheduledDateTime.value = defaultDate.toISOString().slice(0, 16)
  intervalMinutes.value = 60
  loadCalendars()
  showScheduleModal.value = true
}

async function createSchedule() {
  if (!selectedFavoriteForSchedule.value) return
  
  creatingSchedule.value = true
  try {
    // Build schedule object
    const schedule = {
      name: `Schedule: ${selectedFavoriteForSchedule.value.name}`,
      favoriteId: selectedFavoriteForSchedule.value.id,
      serverId: selectedFavoriteForSchedule.value.serverId,
      serverName: selectedFavoriteForSchedule.value.serverName,
      partnerId: selectedFavoriteForSchedule.value.partnerId,
      direction: selectedFavoriteForSchedule.value.direction,
      localPath: selectedFavoriteForSchedule.value.localPath,
      remoteFilename: selectedFavoriteForSchedule.value.remoteFilename,
      scheduleType: scheduleType.value,
      workingDaysOnly: workingDaysOnly.value,
      calendarId: selectedCalendarId.value || null,
      dailyTime: ['DAILY', 'WEEKLY', 'MONTHLY'].includes(scheduleType.value) ? dailyTime.value : null,
      dayOfWeek: scheduleType.value === 'WEEKLY' ? dayOfWeek.value : null,
      dayOfMonth: scheduleType.value === 'MONTHLY' ? dayOfMonth.value : null,
      scheduledAt: scheduleType.value === 'ONCE' ? new Date(scheduledDateTime.value).toISOString() : null,
      intervalMinutes: scheduleType.value === 'INTERVAL' ? intervalMinutes.value : null,
      cronExpression: scheduleType.value === 'CRON' ? cronExpression.value : null,
    }
    
    await api.post('/schedules', schedule)
    showScheduleModal.value = false
    toast.success('Schedule created! View it in the Schedules page.')
  } catch (e: any) {
    toast.error('Failed to create schedule: ' + (e.response?.data?.message || e.message))
  } finally {
    creatingSchedule.value = false
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <Star class="h-8 w-8 text-yellow-500" />
        <h1 class="text-2xl font-bold text-gray-900">Favorites</h1>
      </div>
      <button @click="loadFavorites" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
        <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        Refresh
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" />
    </div>

    <div v-else-if="favorites.length === 0" class="card text-center py-12">
      <Star class="h-16 w-16 mx-auto mb-4 text-gray-400" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No favorites yet</h3>
      <p class="text-gray-500 mb-4">Add transfers to favorites from the History page</p>
      <RouterLink to="/history" class="btn btn-primary">View History</RouterLink>
    </div>

    <div v-else class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <div 
        v-for="favorite in favorites" 
        :key="favorite.id" 
        class="card hover:shadow-lg transition-shadow"
      >
        <div class="flex items-start justify-between mb-4">
          <div class="flex items-center gap-3">
            <div :class="[
              'p-2 rounded-lg',
              favorite.direction === 'SEND' ? 'bg-blue-100' : 'bg-green-100'
            ]">
              <Upload v-if="favorite.direction === 'SEND'" class="h-5 w-5 text-blue-600" />
              <Download v-else class="h-5 w-5 text-green-600" />
            </div>
            <div>
              <h3 class="font-semibold text-gray-900">{{ favorite.name }}</h3>
              <p class="text-sm text-gray-500">{{ favorite.direction }}</p>
            </div>
          </div>
          <div class="flex gap-1">
            <button 
              @click="openEditModal(favorite)"
              class="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"
              title="Edit"
            >
              <Edit class="h-4 w-4" />
            </button>
            <button 
              @click="confirmDelete(favorite)"
              :disabled="deleting === favorite.id"
              class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
              title="Delete"
            >
              <RefreshCw v-if="deleting === favorite.id" class="h-4 w-4 animate-spin" />
              <Trash2 v-else class="h-4 w-4" />
            </button>
          </div>
        </div>

        <div class="space-y-2 text-sm mb-4">
          <div class="flex justify-between">
            <span class="text-gray-500">Server</span>
            <span class="font-medium">{{ favorite.serverName || favorite.serverId }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">Partner</span>
            <span class="font-medium">{{ favorite.partnerId || '-' }}</span>
          </div>
          <div v-if="favorite.remoteFilename" class="flex justify-between">
            <span class="text-gray-500">Virtual File</span>
            <span class="font-medium truncate max-w-[150px]" :title="favorite.remoteFilename">
              {{ favorite.remoteFilename }}
            </span>
          </div>
          <div v-if="favorite.localPath" class="flex justify-between">
            <span class="text-gray-500">{{ favorite.direction === 'SEND' ? 'Source' : 'Destination' }}</span>
            <span class="font-medium truncate max-w-[150px] font-mono text-xs" :title="favorite.localPath">
              {{ favorite.localPath }}
            </span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">Used</span>
            <span class="font-medium">{{ favorite.usageCount }} times</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">Last used</span>
            <span class="font-medium">{{ formatDate(favorite.lastUsedAt || '') }}</span>
          </div>
        </div>

        <!-- Execution status area -->
        <div class="mb-3">
          <!-- Progress bar when executing -->
          <div v-if="getExecutionState(favorite.id).status === 'executing'" class="space-y-2">
            <div class="flex items-center gap-2 text-sm text-blue-600">
              <RefreshCw class="h-4 w-4 animate-spin" />
              <span>Transferring...</span>
            </div>
            <div class="w-full bg-gray-200 rounded-full h-2">
              <div class="bg-blue-600 h-2 rounded-full animate-pulse" style="width: 100%"></div>
            </div>
          </div>
          
          <!-- Success indicator -->
          <div v-else-if="getExecutionState(favorite.id).status === 'success'" 
               class="flex items-center gap-2 p-2 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm">
            <CheckCircle class="h-5 w-5 flex-shrink-0" />
            <span>{{ getExecutionState(favorite.id).message }}</span>
          </div>
          
          <!-- Error indicator -->
          <div v-else-if="getExecutionState(favorite.id).status === 'error'" 
               class="flex items-start gap-2 p-2 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            <XCircle class="h-5 w-5 flex-shrink-0 mt-0.5" />
            <span class="break-words">{{ getExecutionState(favorite.id).message }}</span>
          </div>
        </div>

        <div class="flex gap-2">
          <button 
            @click="executeFavorite(favorite)"
            :disabled="getExecutionState(favorite.id).status === 'executing'"
            :class="[
              'flex-1 flex items-center justify-center gap-2 transition-colors',
              getExecutionState(favorite.id).status === 'executing' 
                ? 'btn bg-gray-300 text-gray-500 cursor-not-allowed' 
                : 'btn btn-primary'
            ]"
          >
            <Play class="h-4 w-4" />
            {{ getExecutionState(favorite.id).status === 'executing' ? 'Executing...' : 'Execute' }}
          </button>
          <button 
            @click="openScheduleModal(favorite)"
            class="btn btn-secondary flex items-center gap-2"
            title="Schedule"
          >
            <Calendar class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>

    <!-- Schedule Modal -->
    <div v-if="showScheduleModal && selectedFavoriteForSchedule" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showScheduleModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-md w-full p-6">
          <div class="flex items-center gap-3 mb-6">
            <Calendar class="h-6 w-6 text-purple-500" />
            <h2 class="text-xl font-bold text-gray-900">Schedule Transfer</h2>
          </div>

          <div class="space-y-4 max-h-[60vh] overflow-y-auto">
            <div class="p-3 bg-gray-50 rounded-lg text-sm">
              <p class="font-medium text-gray-900">{{ selectedFavoriteForSchedule.name }}</p>
              <p class="text-gray-500">{{ selectedFavoriteForSchedule.direction }} • {{ selectedFavoriteForSchedule.serverName || selectedFavoriteForSchedule.serverId }}</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">Schedule Type</label>
              <select v-model="scheduleType" class="input w-full">
                <option value="DAILY">Daily (at specific time)</option>
                <option value="HOURLY">Hourly</option>
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
                <option value="INTERVAL">Interval (every N minutes)</option>
                <option value="ONCE">Once (at specific date/time)</option>
                <option value="CRON">Cron expression</option>
              </select>
            </div>

            <!-- Day of week for WEEKLY -->
            <div v-if="scheduleType === 'WEEKLY'">
              <label class="block text-sm font-medium text-gray-700 mb-2">Day of week</label>
              <select v-model.number="dayOfWeek" class="input w-full">
                <option :value="1">Monday</option>
                <option :value="2">Tuesday</option>
                <option :value="3">Wednesday</option>
                <option :value="4">Thursday</option>
                <option :value="5">Friday</option>
                <option :value="6">Saturday</option>
                <option :value="7">Sunday</option>
              </select>
            </div>

            <!-- Day of month for MONTHLY -->
            <div v-if="scheduleType === 'MONTHLY'">
              <label class="block text-sm font-medium text-gray-700 mb-2">Day of month</label>
              <select v-model.number="dayOfMonth" class="input w-full">
                <option v-for="d in 31" :key="d" :value="d">{{ d }}</option>
              </select>
            </div>

            <!-- Time for DAILY, WEEKLY, MONTHLY -->
            <div v-if="['DAILY', 'WEEKLY', 'MONTHLY'].includes(scheduleType)">
              <label class="block text-sm font-medium text-gray-700 mb-2">Time of day</label>
              <input 
                v-model="dailyTime" 
                type="time" 
                class="input w-full"
              />
            </div>

            <!-- Date/Time for ONCE -->
            <div v-if="scheduleType === 'ONCE'">
              <label class="block text-sm font-medium text-gray-700 mb-2">Date & Time</label>
              <input 
                v-model="scheduledDateTime" 
                type="datetime-local" 
                class="input w-full"
              />
            </div>

            <!-- Interval -->
            <div v-if="scheduleType === 'INTERVAL'">
              <label class="block text-sm font-medium text-gray-700 mb-2">Interval (minutes)</label>
              <input 
                v-model.number="intervalMinutes" 
                type="number" 
                min="1"
                class="input w-full"
              />
            </div>

            <!-- Cron expression -->
            <div v-if="scheduleType === 'CRON'">
              <label class="block text-sm font-medium text-gray-700 mb-2">Cron Expression</label>
              <input 
                v-model="cronExpression" 
                type="text" 
                placeholder="0 0 9 * * MON-FRI"
                class="input w-full font-mono"
              />
              <p class="text-xs text-gray-500 mt-1">Format: sec min hour day month weekday</p>
            </div>

            <!-- Working days only -->
            <div class="flex items-center gap-3 p-3 bg-blue-50 rounded-lg">
              <input 
                v-model="workingDaysOnly" 
                type="checkbox" 
                id="workingDaysOnly"
                class="h-4 w-4 text-blue-600 rounded"
              />
              <label for="workingDaysOnly" class="text-sm text-gray-700">
                <span class="font-medium">Working days only</span>
                <span class="block text-gray-500">Skip weekends and holidays</span>
              </label>
            </div>

            <!-- Calendar selection -->
            <div v-if="workingDaysOnly && calendars.length > 0">
              <label class="block text-sm font-medium text-gray-700 mb-2">Business Calendar</label>
              <select v-model="selectedCalendarId" class="input w-full">
                <option value="">Default (Mon-Fri)</option>
                <option v-for="cal in calendars" :key="cal.id" :value="cal.id">
                  {{ cal.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="flex justify-end gap-3 mt-6">
            <button @click="showScheduleModal = false" class="btn btn-secondary">Cancel</button>
            <button 
              @click="createSchedule" 
              :disabled="creatingSchedule"
              class="btn btn-primary flex items-center gap-2"
            >
              <RefreshCw v-if="creatingSchedule" class="h-4 w-4 animate-spin" />
              <Calendar v-else class="h-4 w-4" />
              Create Schedule
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Edit Modal -->
    <div v-if="showEditModal && editingFavorite" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showEditModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6">
          <div class="flex items-center justify-between mb-6">
            <div class="flex items-center gap-3">
              <Edit class="h-6 w-6 text-blue-500" />
              <h2 class="text-xl font-bold text-gray-900">Edit Favorite</h2>
            </div>
            <button @click="showEditModal = false" class="p-2 hover:bg-gray-100 rounded-lg">
              <X class="h-5 w-5 text-gray-500" />
            </button>
          </div>

          <div class="space-y-4 max-h-[60vh] overflow-y-auto">
            <!-- Name -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
              <input v-model="editForm.name" type="text" class="input w-full" required />
            </div>

            <!-- Description -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="editForm.description" type="text" class="input w-full" />
            </div>

            <!-- Direction -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Direction</label>
              <select v-model="editForm.direction" class="input w-full">
                <option value="SEND">SEND (Upload)</option>
                <option value="RECEIVE">RECEIVE (Download)</option>
              </select>
            </div>

            <!-- Server -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Server *</label>
              <select v-model="editForm.serverId" class="input w-full" required>
                <option v-for="server in servers" :key="server.id" :value="server.id">
                  {{ server.name || server.id }}
                </option>
              </select>
            </div>

            <!-- Partner ID -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Partner ID</label>
              <input v-model="editForm.partnerId" type="text" class="input w-full" placeholder="MY_CLIENT_ID" />
            </div>

            <!-- Virtual File -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Virtual File *</label>
              <input v-model="editForm.remoteFilename" type="text" class="input w-full" placeholder="DATA_FILE" required />
            </div>

            <!-- Local Path -->
            <div>
              <template v-if="editForm.direction === 'SEND'">
                <label class="block text-sm font-medium text-gray-700 mb-1">Source File Path *</label>
                <input v-model="editForm.localPath" type="text" class="input w-full font-mono" placeholder="/path/to/file.txt" required />
                <p class="text-xs text-gray-500 mt-1">Full path to the file to send</p>
              </template>
              <template v-else>
                <PathPlaceholderInput
                  v-model="editForm.localPath"
                  label="Destination Path *"
                  placeholder="/data/received/${partner}/${virtualFile}_${timestamp}"
                  direction="RECEIVE"
                />
                <p class="text-xs text-gray-500 mt-1">Use placeholders for dynamic paths</p>
              </template>
            </div>
          </div>

          <div class="flex justify-end gap-3 mt-6">
            <button @click="showEditModal = false" class="btn btn-secondary">Cancel</button>
            <button 
              @click="saveFavorite" 
              :disabled="saving || !editForm.name || !editForm.serverId"
              class="btn btn-primary flex items-center gap-2"
            >
              <RefreshCw v-if="saving" class="h-4 w-4 animate-spin" />
              Save Changes
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      :show="showDeleteModal"
      title="Delete Favorite"
      :message="`Are you sure you want to delete '${favoriteToDelete?.name}'? This action cannot be undone.`"
      confirm-text="Delete"
      @confirm="deleteFavorite"
      @cancel="showDeleteModal = false"
    />
  </div>
</template>
