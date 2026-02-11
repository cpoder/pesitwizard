<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Plus,
  Pencil,
  Trash2,
  FileText,
  RefreshCw
} from 'lucide-vue-next'
import api from '@/api'
import { ConfirmModal } from '@pesitwizard/frontend-common/components'
import { useToast } from '@pesitwizard/frontend-common/composables'

const toast = useToast()

interface VirtualFile {
  id?: string
  name: string
  description?: string
  direction: 'SEND' | 'RECEIVE' | 'BOTH'
  recordLength?: number | null
  createdAt?: string
  updatedAt?: string
}

const virtualFiles = ref<VirtualFile[]>([])
const loading = ref(true)
const showModal = ref(false)
const editingFile = ref<VirtualFile | null>(null)
const saving = ref(false)
const error = ref('')

const defaultForm: VirtualFile = {
  name: '',
  description: '',
  direction: 'BOTH',
  recordLength: null
}

const form = ref<VirtualFile>({ ...defaultForm })

onMounted(() => loadVirtualFiles())

async function loadVirtualFiles() {
  loading.value = true
  try {
    const response = await api.get('/virtual-files')
    virtualFiles.value = response.data || []
  } catch (e) {
    console.error('Failed to load virtual files:', e)
  } finally {
    loading.value = false
  }
}

function openAddModal() {
  editingFile.value = null
  form.value = { ...defaultForm }
  error.value = ''
  showModal.value = true
}

function openEditModal(vf: VirtualFile) {
  editingFile.value = vf
  form.value = { ...vf }
  error.value = ''
  showModal.value = true
}

async function saveVirtualFile() {
  saving.value = true
  error.value = ''
  try {
    const payload = {
      name: form.value.name,
      description: form.value.description || undefined,
      direction: form.value.direction,
      recordLength: form.value.recordLength || undefined
    }

    if (editingFile.value?.id) {
      await api.put(`/virtual-files/${editingFile.value.id}`, payload)
    } else {
      await api.post('/virtual-files', payload)
    }
    showModal.value = false
    await loadVirtualFiles()
    toast.success(editingFile.value ? 'Virtual file updated' : 'Virtual file created')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to save virtual file'
  } finally {
    saving.value = false
  }
}

const showDeleteModal = ref(false)
const fileToDelete = ref<VirtualFile | null>(null)

function confirmDelete(vf: VirtualFile) {
  fileToDelete.value = vf
  showDeleteModal.value = true
}

async function deleteVirtualFile() {
  if (!fileToDelete.value) return
  try {
    await api.delete(`/virtual-files/${fileToDelete.value.id}`)
    virtualFiles.value = virtualFiles.value.filter(v => v.id !== fileToDelete.value!.id)
    toast.success(`Virtual file "${fileToDelete.value.name}" deleted`)
  } catch (e: any) {
    toast.error('Failed to delete: ' + (e.response?.data?.message || e.message))
  } finally {
    showDeleteModal.value = false
    fileToDelete.value = null
  }
}

function directionBadgeClass(direction: string) {
  switch (direction) {
    case 'SEND': return 'badge badge-info'
    case 'RECEIVE': return 'badge badge-success'
    case 'BOTH': return 'badge bg-purple-100 text-purple-800'
    default: return 'badge'
  }
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <FileText class="h-8 w-8 text-teal-500" aria-hidden="true" />
        <h1 class="text-2xl font-bold text-gray-900">Virtual Files</h1>
      </div>
      <div class="flex gap-3">
        <button @click="loadVirtualFiles" class="btn btn-secondary flex items-center gap-2" :disabled="loading" aria-label="Refresh virtual files">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" aria-hidden="true" />
          Refresh
        </button>
        <button @click="openAddModal" class="btn btn-primary flex items-center gap-2" aria-label="Add virtual file">
          <Plus class="h-4 w-4" aria-hidden="true" />
          Add Virtual File
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" aria-hidden="true" />
    </div>

    <div v-else-if="virtualFiles.length === 0" class="card text-center py-12">
      <FileText class="h-16 w-16 mx-auto mb-4 text-gray-400" aria-hidden="true" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No virtual files configured</h3>
      <p class="text-gray-500 mb-4">Add virtual file definitions to enable dropdown selection in the transfer form</p>
      <button @click="openAddModal" class="btn btn-primary">Add Virtual File</button>
    </div>

    <div v-else class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Description</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Direction</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Record Length</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
            <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="vf in virtualFiles" :key="vf.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 whitespace-nowrap">
              <span class="font-medium text-gray-900 font-mono">{{ vf.name }}</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ vf.description || '-' }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span :class="directionBadgeClass(vf.direction)">{{ vf.direction }}</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
              {{ vf.recordLength ? vf.recordLength + ' B' : 'Default' }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ formatDate(vf.createdAt) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-right">
              <div class="flex items-center justify-end gap-2">
                <button
                  @click="openEditModal(vf)"
                  class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg"
                  title="Edit"
                  aria-label="Edit virtual file"
                >
                  <Pencil class="h-4 w-4" aria-hidden="true" />
                </button>
                <button
                  @click="confirmDelete(vf)"
                  class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                  title="Delete"
                  aria-label="Delete virtual file"
                >
                  <Trash2 class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 overflow-y-auto" @keydown.escape="showModal = false">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showModal = false" />

        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6" role="dialog" aria-modal="true" aria-labelledby="vf-modal-title">
          <h2 id="vf-modal-title" class="text-xl font-bold text-gray-900 mb-6">
            {{ editingFile ? 'Edit Virtual File' : 'Add Virtual File' }}
          </h2>

          <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <form @submit.prevent="saveVirtualFile" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
              <input v-model="form.name" type="text" class="input" required placeholder="DATA_FILE" />
              <p class="text-xs text-gray-500 mt-1">The PeSIT virtual file identifier (PI 12) configured on the server</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="form.description" type="text" class="input" placeholder="Optional description" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Direction *</label>
              <select v-model="form.direction" class="select" required>
                <option value="BOTH">Both (Send & Receive)</option>
                <option value="SEND">Send only</option>
                <option value="RECEIVE">Receive only</option>
              </select>
              <p class="text-xs text-gray-500 mt-1">Controls when this virtual file appears in the transfer form dropdown</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Record Length (PI 32)</label>
              <select v-model="form.recordLength" class="select">
                <option :value="null">Default (from transfer config)</option>
                <option :value="128">128 bytes</option>
                <option :value="256">256 bytes</option>
                <option :value="506">506 bytes</option>
                <option :value="1018">1018 bytes</option>
                <option :value="1024">1024 bytes</option>
                <option :value="2042">2042 bytes</option>
                <option :value="4044">4044 bytes (SIT max)</option>
              </select>
              <p class="text-xs text-gray-500 mt-1">Auto-fills the record length field when selecting this virtual file</p>
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

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      :show="showDeleteModal"
      title="Delete Virtual File"
      :message="`Are you sure you want to delete virtual file '${fileToDelete?.name}'? This action cannot be undone.`"
      confirm-text="Delete"
      @confirm="deleteVirtualFile"
      @cancel="showDeleteModal = false"
    />
  </div>
</template>
