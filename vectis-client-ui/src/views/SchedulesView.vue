<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Calendar, 
  Play,
  Pause,
  Trash2,
  RefreshCw,
  Upload,
  Download,
  CheckCircle,
  XCircle,
  Clock,
  PlayCircle
} from 'lucide-vue-next'
import api from '@/api'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { useToast } from '@/composables/useToast'

const toast = useToast()

interface Schedule {
  id: string
  name: string
  description?: string
  favoriteId?: string
  serverId: string
  serverName?: string
  partnerId?: string
  direction: 'SEND' | 'RECEIVE'
  localPath?: string
  remoteFilename?: string
  scheduleType: 'ONCE' | 'INTERVAL' | 'CRON' | 'DAILY' | 'HOURLY'
  intervalMinutes?: number
  scheduledAt?: string
  nextRunAt?: string
  lastRunAt?: string
  lastRunStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING'
  lastRunError?: string
  successCount: number
  failureCount: number
  enabled: boolean
}

const schedules = ref<Schedule[]>([])
const loading = ref(true)
const deleting = ref<string | null>(null)
const toggling = ref<string | null>(null)
const running = ref<string | null>(null)

onMounted(() => loadSchedules())

async function loadSchedules() {
  loading.value = true
  try {
    const response = await api.get('/schedules')
    schedules.value = response.data || []
  } catch (e) {
    console.error('Failed to load schedules:', e)
  } finally {
    loading.value = false
  }
}

async function toggleSchedule(schedule: Schedule) {
  toggling.value = schedule.id
  try {
    const response = await api.post(`/schedules/${schedule.id}/toggle`)
    const index = schedules.value.findIndex(s => s.id === schedule.id)
    if (index !== -1) {
      schedules.value[index] = response.data
    }
    toast.success(response.data.enabled ? 'Schedule enabled' : 'Schedule disabled')
  } catch (e: any) {
    toast.error('Failed to toggle: ' + (e.response?.data?.message || e.message))
  } finally {
    toggling.value = null
  }
}

async function runNow(schedule: Schedule) {
  running.value = schedule.id
  try {
    const response = await api.post(`/schedules/${schedule.id}/run`)
    const index = schedules.value.findIndex(s => s.id === schedule.id)
    if (index !== -1) {
      schedules.value[index] = response.data
    }
    toast.success('Schedule executed')
  } catch (e: any) {
    toast.error('Failed to run: ' + (e.response?.data?.message || e.message))
  } finally {
    running.value = null
  }
}

const showDeleteModal = ref(false)
const scheduleToDelete = ref<Schedule | null>(null)

function confirmDelete(schedule: Schedule) {
  scheduleToDelete.value = schedule
  showDeleteModal.value = true
}

async function deleteSchedule() {
  if (!scheduleToDelete.value) return
  deleting.value = scheduleToDelete.value.id
  try {
    await api.delete(`/schedules/${scheduleToDelete.value.id}`)
    schedules.value = schedules.value.filter(s => s.id !== scheduleToDelete.value!.id)
    toast.success(`Schedule "${scheduleToDelete.value.name}" deleted`)
  } catch (e: any) {
    toast.error('Failed to delete: ' + (e.response?.data?.message || e.message))
  } finally {
    deleting.value = null
    showDeleteModal.value = false
    scheduleToDelete.value = null
  }
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

function formatScheduleType(schedule: Schedule) {
  switch (schedule.scheduleType) {
    case 'ONCE': return 'Once'
    case 'INTERVAL': return `Every ${schedule.intervalMinutes} min`
    case 'HOURLY': return 'Hourly'
    case 'DAILY': return 'Daily'
    case 'CRON': return 'Cron'
    default: return schedule.scheduleType
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <Calendar class="h-8 w-8 text-purple-500" />
        <h1 class="text-2xl font-bold text-gray-900">Scheduled Transfers</h1>
      </div>
      <button @click="loadSchedules" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
        <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        Refresh
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" />
    </div>

    <div v-else-if="schedules.length === 0" class="card text-center py-12">
      <Calendar class="h-16 w-16 mx-auto mb-4 text-gray-400" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No scheduled transfers</h3>
      <p class="text-gray-500 mb-4">Schedule transfers from Favorites or the Transfer page</p>
      <RouterLink to="/favorites" class="btn btn-primary">View Favorites</RouterLink>
    </div>

    <div v-else class="space-y-4">
      <div 
        v-for="schedule in schedules" 
        :key="schedule.id" 
        :class="[
          'card',
          !schedule.enabled && 'opacity-60'
        ]"
      >
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-4">
            <div :class="[
              'p-3 rounded-lg',
              schedule.direction === 'SEND' ? 'bg-blue-100' : 'bg-green-100'
            ]">
              <Upload v-if="schedule.direction === 'SEND'" class="h-6 w-6 text-blue-600" />
              <Download v-else class="h-6 w-6 text-green-600" />
            </div>
            
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-1">
                <h3 class="font-semibold text-gray-900">{{ schedule.name }}</h3>
                <span :class="[
                  'px-2 py-0.5 text-xs rounded-full',
                  schedule.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'
                ]">
                  {{ schedule.enabled ? 'Active' : 'Paused' }}
                </span>
              </div>
              
              <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm mt-3">
                <div>
                  <span class="text-gray-500 block">Server</span>
                  <span class="font-medium">{{ schedule.serverName || schedule.serverId }}</span>
                </div>
                <div>
                  <span class="text-gray-500 block">Schedule</span>
                  <span class="font-medium">{{ formatScheduleType(schedule) }}</span>
                </div>
                <div>
                  <span class="text-gray-500 block">Next Run</span>
                  <span class="font-medium">{{ schedule.enabled ? formatDate(schedule.nextRunAt) : '-' }}</span>
                </div>
                <div>
                  <span class="text-gray-500 block">Last Run</span>
                  <div class="flex items-center gap-1">
                    <CheckCircle v-if="schedule.lastRunStatus === 'SUCCESS'" class="h-4 w-4 text-green-500" />
                    <XCircle v-else-if="schedule.lastRunStatus === 'FAILED'" class="h-4 w-4 text-red-500" />
                    <Clock v-else-if="schedule.lastRunStatus === 'RUNNING'" class="h-4 w-4 text-blue-500 animate-spin" />
                    <span class="font-medium">{{ formatDate(schedule.lastRunAt) }}</span>
                  </div>
                </div>
              </div>

              <div v-if="schedule.lastRunError" class="mt-2 p-2 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
                {{ schedule.lastRunError }}
              </div>

              <div class="flex items-center gap-4 mt-3 text-sm text-gray-500">
                <span>✓ {{ schedule.successCount }} success</span>
                <span>✗ {{ schedule.failureCount }} failed</span>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button 
              @click="runNow(schedule)"
              :disabled="running === schedule.id"
              class="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"
              title="Run now"
            >
              <RefreshCw v-if="running === schedule.id" class="h-5 w-5 animate-spin" />
              <PlayCircle v-else class="h-5 w-5" />
            </button>
            <button 
              @click="toggleSchedule(schedule)"
              :disabled="toggling === schedule.id"
              :class="[
                'p-2 rounded-lg',
                schedule.enabled 
                  ? 'text-gray-400 hover:text-orange-600 hover:bg-orange-50' 
                  : 'text-gray-400 hover:text-green-600 hover:bg-green-50'
              ]"
              :title="schedule.enabled ? 'Pause' : 'Resume'"
            >
              <RefreshCw v-if="toggling === schedule.id" class="h-5 w-5 animate-spin" />
              <Pause v-else-if="schedule.enabled" class="h-5 w-5" />
              <Play v-else class="h-5 w-5" />
            </button>
            <button 
              @click="confirmDelete(schedule)"
              :disabled="deleting === schedule.id"
              class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
              title="Delete"
            >
              <RefreshCw v-if="deleting === schedule.id" class="h-5 w-5 animate-spin" />
              <Trash2 v-else class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      :show="showDeleteModal"
      title="Delete Schedule"
      :message="`Are you sure you want to delete '${scheduleToDelete?.name}'? This action cannot be undone.`"
      confirm-text="Delete"
      @confirm="deleteSchedule"
      @cancel="showDeleteModal = false"
    />
  </div>
</template>
