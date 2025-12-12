<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Calendar, Trash2, RefreshCw, Plus, Edit, X } from 'lucide-vue-next'
import api from '@/api'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { useToast } from '@/composables/useToast'

const toast = useToast()

interface BusinessCalendar {
  id?: string
  name: string
  description?: string
  timezone: string
  workingDays: number[]
  holidays: string[]
  defaultCalendar: boolean
}

const calendars = ref<BusinessCalendar[]>([])
const loading = ref(true)
const saving = ref(false)
const deleting = ref<string | null>(null)

// Modal state
const showModal = ref(false)
const editingCalendar = ref<BusinessCalendar | null>(null)
const form = ref<BusinessCalendar>({
  name: '',
  description: '',
  timezone: 'Europe/Paris',
  workingDays: [1, 2, 3, 4, 5],
  holidays: [],
  defaultCalendar: false
})
const newHoliday = ref('')

const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const timezones = [
  'Europe/Paris',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Zurich',
  'America/New_York',
  'America/Los_Angeles',
  'Asia/Tokyo',
  'UTC'
]

const isEditing = computed(() => editingCalendar.value !== null)
const modalTitle = computed(() => isEditing.value ? 'Edit Calendar' : 'New Calendar')

onMounted(() => loadCalendars())

async function loadCalendars() {
  loading.value = true
  try {
    const response = await api.get('/calendars')
    calendars.value = response.data || []
  } catch (e) {
    console.error('Failed to load calendars:', e)
  } finally {
    loading.value = false
  }
}

const showDeleteModal = ref(false)
const calendarToDelete = ref<BusinessCalendar | null>(null)

function confirmDelete(cal: BusinessCalendar) {
  calendarToDelete.value = cal
  showDeleteModal.value = true
}

async function deleteCalendar() {
  if (!calendarToDelete.value) return
  deleting.value = calendarToDelete.value.id!
  try {
    await api.delete(`/calendars/${calendarToDelete.value.id}`)
    toast.success(`Calendar "${calendarToDelete.value.name}" deleted`)
    await loadCalendars()
  } catch (e: any) {
    toast.error('Failed to delete: ' + (e.response?.data?.message || e.message))
  } finally {
    deleting.value = null
    showDeleteModal.value = false
    calendarToDelete.value = null
  }
}

function formatWorkingDays(days: number[]) {
  return days?.map(d => dayNames[d - 1]).join(', ') || 'Mon-Fri'
}

function openCreateModal() {
  editingCalendar.value = null
  form.value = {
    name: '',
    description: '',
    timezone: 'Europe/Paris',
    workingDays: [1, 2, 3, 4, 5],
    holidays: [],
    defaultCalendar: false
  }
  newHoliday.value = ''
  showModal.value = true
}

function openEditModal(cal: BusinessCalendar) {
  editingCalendar.value = cal
  form.value = {
    ...cal,
    holidays: [...(cal.holidays || [])],
    workingDays: [...(cal.workingDays || [1, 2, 3, 4, 5])]
  }
  newHoliday.value = ''
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  editingCalendar.value = null
}

function toggleWorkingDay(day: number) {
  const idx = form.value.workingDays.indexOf(day)
  if (idx >= 0) {
    form.value.workingDays.splice(idx, 1)
  } else {
    form.value.workingDays.push(day)
    form.value.workingDays.sort((a, b) => a - b)
  }
}

function addHoliday() {
  if (!newHoliday.value) return
  if (!form.value.holidays.includes(newHoliday.value)) {
    form.value.holidays.push(newHoliday.value)
    form.value.holidays.sort()
  }
  newHoliday.value = ''
}

function removeHoliday(date: string) {
  const idx = form.value.holidays.indexOf(date)
  if (idx >= 0) {
    form.value.holidays.splice(idx, 1)
  }
}

async function saveCalendar() {
  if (!form.value.name.trim()) {
    toast.warning('Name is required')
    return
  }
  saving.value = true
  try {
    if (isEditing.value && editingCalendar.value?.id) {
      await api.put(`/calendars/${editingCalendar.value.id}`, form.value)
      toast.success('Calendar updated')
    } else {
      await api.post('/calendars', form.value)
      toast.success('Calendar created')
    }
    closeModal()
    await loadCalendars()
  } catch (e: any) {
    toast.error('Failed to save: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <Calendar class="h-8 w-8 text-orange-500" />
        <h1 class="text-2xl font-bold text-gray-900">Business Calendars</h1>
      </div>
      <div class="flex gap-2">
        <button @click="loadCalendars" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
          Refresh
        </button>
        <button @click="openCreateModal" class="btn btn-primary flex items-center gap-2">
          <Plus class="h-4 w-4" />
          New Calendar
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" />
    </div>

    <div v-else-if="calendars.length === 0" class="card text-center py-12">
      <Calendar class="h-16 w-16 mx-auto mb-4 text-gray-400" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No calendars configured</h3>
      <p class="text-gray-500">Calendars define working days and holidays for scheduled transfers</p>
    </div>

    <div v-else class="grid gap-4 md:grid-cols-2">
      <div v-for="cal in calendars" :key="cal.id" class="card">
        <div class="flex items-start justify-between">
          <div>
            <div class="flex items-center gap-2">
              <h3 class="font-semibold text-gray-900">{{ cal.name }}</h3>
              <span v-if="cal.defaultCalendar" class="px-2 py-0.5 text-xs bg-blue-100 text-blue-700 rounded-full">Default</span>
            </div>
            <p v-if="cal.description" class="text-sm text-gray-500 mt-1">{{ cal.description }}</p>
          </div>
          <div class="flex gap-1">
            <button @click="openEditModal(cal)" class="p-2 text-gray-400 hover:text-blue-600 rounded-lg" title="Edit">
              <Edit class="h-4 w-4" />
            </button>
            <button @click="confirmDelete(cal)" :disabled="deleting === cal.id" class="p-2 text-gray-400 hover:text-red-600 rounded-lg" title="Delete">
              <RefreshCw v-if="deleting === cal.id" class="h-4 w-4 animate-spin" />
              <Trash2 v-else class="h-4 w-4" />
            </button>
          </div>
        </div>
        <div class="mt-4 space-y-2 text-sm">
          <div class="flex justify-between">
            <span class="text-gray-500">Working days</span>
            <span class="font-medium">{{ formatWorkingDays(cal.workingDays) }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">Holidays</span>
            <span class="font-medium">{{ cal.holidays?.length || 0 }} configured</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">Timezone</span>
            <span class="font-medium">{{ cal.timezone }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="closeModal"></div>
        <div class="relative bg-white rounded-xl shadow-xl w-full max-w-lg">
          <div class="flex items-center justify-between p-4 border-b">
            <h3 class="text-lg font-semibold">{{ modalTitle }}</h3>
            <button @click="closeModal" class="p-2 text-gray-400 hover:text-gray-600 rounded-lg">
              <X class="h-5 w-5" />
            </button>
          </div>
          
          <form @submit.prevent="saveCalendar" class="p-4 space-y-4">
            <!-- Name -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
              <input v-model="form.name" type="text" class="input w-full" placeholder="e.g. France Holidays" required />
            </div>
            
            <!-- Description -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="form.description" type="text" class="input w-full" placeholder="Optional description" />
            </div>
            
            <!-- Timezone -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
              <select v-model="form.timezone" class="input w-full">
                <option v-for="tz in timezones" :key="tz" :value="tz">{{ tz }}</option>
              </select>
            </div>
            
            <!-- Working Days -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">Working Days</label>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="(name, idx) in dayNames"
                  :key="idx"
                  type="button"
                  @click="toggleWorkingDay(idx + 1)"
                  :class="[
                    'px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
                    form.workingDays.includes(idx + 1)
                      ? 'bg-primary-600 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  ]"
                >
                  {{ name }}
                </button>
              </div>
            </div>
            
            <!-- Holidays -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">Holidays</label>
              <div class="flex gap-2 mb-2">
                <input 
                  v-model="newHoliday" 
                  type="date" 
                  class="input flex-1"
                  @keyup.enter="addHoliday"
                />
                <button type="button" @click="addHoliday" class="btn btn-secondary">
                  <Plus class="h-4 w-4" />
                </button>
              </div>
              <div v-if="form.holidays.length > 0" class="flex flex-wrap gap-2">
                <span 
                  v-for="date in form.holidays" 
                  :key="date"
                  class="inline-flex items-center gap-1 px-2 py-1 bg-red-100 text-red-700 rounded-lg text-sm"
                >
                  {{ date }}
                  <button type="button" @click="removeHoliday(date)" class="hover:text-red-900">
                    <X class="h-3 w-3" />
                  </button>
                </span>
              </div>
              <p v-else class="text-sm text-gray-500">No holidays configured</p>
            </div>
            
            <!-- Default Calendar -->
            <div class="flex items-center gap-2">
              <input v-model="form.defaultCalendar" type="checkbox" id="defaultCalendar" class="rounded text-primary-600" />
              <label for="defaultCalendar" class="text-sm text-gray-700">Set as default calendar</label>
            </div>
            
            <!-- Actions -->
            <div class="flex justify-end gap-2 pt-4 border-t">
              <button type="button" @click="closeModal" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <RefreshCw v-if="saving" class="h-4 w-4 animate-spin mr-2" />
                {{ isEditing ? 'Save Changes' : 'Create Calendar' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      :show="showDeleteModal"
      title="Delete Calendar"
      :message="`Are you sure you want to delete '${calendarToDelete?.name}'? This action cannot be undone.`"
      confirm-text="Delete"
      @confirm="deleteCalendar"
      @cancel="showDeleteModal = false"
    />
  </div>
</template>
