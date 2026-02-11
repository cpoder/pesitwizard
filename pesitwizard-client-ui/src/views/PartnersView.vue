<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Plus,
  Pencil,
  Trash2,
  Users,
  RefreshCw,
  KeyRound,
  CheckCircle
} from 'lucide-vue-next'
import api from '@/api'
import { ConfirmModal } from '@pesitwizard/frontend-common/components'
import { useToast } from '@pesitwizard/frontend-common/composables'

const toast = useToast()

interface Partner {
  id?: string
  partnerId: string
  description?: string
  password?: string
  passwordConfigured?: boolean
  createdAt?: string
  updatedAt?: string
}

const partners = ref<Partner[]>([])
const loading = ref(true)
const showModal = ref(false)
const editingPartner = ref<Partner | null>(null)
const saving = ref(false)
const error = ref('')

const defaultForm: Partner = {
  partnerId: '',
  description: '',
  password: ''
}

const form = ref<Partner>({ ...defaultForm })

onMounted(() => loadPartners())

async function loadPartners() {
  loading.value = true
  try {
    const response = await api.get('/partners')
    partners.value = response.data || []
  } catch (e) {
    console.error('Failed to load partners:', e)
  } finally {
    loading.value = false
  }
}

function openAddModal() {
  editingPartner.value = null
  form.value = { ...defaultForm }
  error.value = ''
  showModal.value = true
}

function openEditModal(partner: Partner) {
  editingPartner.value = partner
  form.value = { ...partner, password: '' }
  error.value = ''
  showModal.value = true
}

async function savePartner() {
  saving.value = true
  error.value = ''
  try {
    const payload: Record<string, any> = {
      partnerId: form.value.partnerId,
      description: form.value.description || undefined
    }
    // Only send password if user entered one
    if (form.value.password) {
      payload.password = form.value.password
    }

    if (editingPartner.value?.id) {
      await api.put(`/partners/${editingPartner.value.id}`, payload)
    } else {
      await api.post('/partners', payload)
    }
    showModal.value = false
    await loadPartners()
    toast.success(editingPartner.value ? 'Partner updated' : 'Partner created')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to save partner'
  } finally {
    saving.value = false
  }
}

const showDeleteModal = ref(false)
const partnerToDelete = ref<Partner | null>(null)

function confirmDelete(partner: Partner) {
  partnerToDelete.value = partner
  showDeleteModal.value = true
}

async function deletePartner() {
  if (!partnerToDelete.value) return
  try {
    await api.delete(`/partners/${partnerToDelete.value.id}`)
    partners.value = partners.value.filter(p => p.id !== partnerToDelete.value!.id)
    toast.success(`Partner "${partnerToDelete.value.partnerId}" deleted`)
  } catch (e: any) {
    toast.error('Failed to delete: ' + (e.response?.data?.message || e.message))
  } finally {
    showDeleteModal.value = false
    partnerToDelete.value = null
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
        <Users class="h-8 w-8 text-indigo-500" aria-hidden="true" />
        <h1 class="text-2xl font-bold text-gray-900">Partners</h1>
      </div>
      <div class="flex gap-3">
        <button @click="loadPartners" class="btn btn-secondary flex items-center gap-2" :disabled="loading" aria-label="Refresh partners">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" aria-hidden="true" />
          Refresh
        </button>
        <button @click="openAddModal" class="btn btn-primary flex items-center gap-2" aria-label="Add partner">
          <Plus class="h-4 w-4" aria-hidden="true" />
          Add Partner
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center h-64">
      <RefreshCw class="h-8 w-8 animate-spin text-primary-600" aria-hidden="true" />
    </div>

    <div v-else-if="partners.length === 0" class="card text-center py-12">
      <Users class="h-16 w-16 mx-auto mb-4 text-gray-400" aria-hidden="true" />
      <h3 class="text-lg font-medium text-gray-900 mb-2">No partners configured</h3>
      <p class="text-gray-500 mb-4">Add a PeSIT partner to store credentials for quick selection in the transfer form</p>
      <button @click="openAddModal" class="btn btn-primary">Add Partner</button>
    </div>

    <div v-else class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Partner ID</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Description</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Password</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
            <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="partner in partners" :key="partner.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 whitespace-nowrap">
              <span class="font-medium text-gray-900 font-mono">{{ partner.partnerId }}</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ partner.description || '-' }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span v-if="partner.passwordConfigured" class="badge badge-success flex items-center gap-1 w-fit">
                <KeyRound class="h-3 w-3" aria-hidden="true" />
                Configured
              </span>
              <span v-else class="badge bg-gray-100 text-gray-500 w-fit">
                None
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ formatDate(partner.createdAt) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-right">
              <div class="flex items-center justify-end gap-2">
                <button
                  @click="openEditModal(partner)"
                  class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg"
                  title="Edit"
                  aria-label="Edit partner"
                >
                  <Pencil class="h-4 w-4" aria-hidden="true" />
                </button>
                <button
                  @click="confirmDelete(partner)"
                  class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                  title="Delete"
                  aria-label="Delete partner"
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

        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6" role="dialog" aria-modal="true" aria-labelledby="partner-modal-title">
          <h2 id="partner-modal-title" class="text-xl font-bold text-gray-900 mb-6">
            {{ editingPartner ? 'Edit Partner' : 'Add Partner' }}
          </h2>

          <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <form @submit.prevent="savePartner" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Partner ID *</label>
              <input v-model="form.partnerId" type="text" class="input" required placeholder="MY_CLIENT_ID" />
              <p class="text-xs text-gray-500 mt-1">The PeSIT identifier (PI 3) used to authenticate with the server</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="form.description" type="text" class="input" placeholder="Optional description" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                Password
                <span v-if="editingPartner?.passwordConfigured" class="text-green-600 text-xs font-normal ml-2">
                  <CheckCircle class="h-3 w-3 inline" aria-hidden="true" /> Currently set
                </span>
              </label>
              <input v-model="form.password" type="password" class="input"
                :placeholder="editingPartner?.passwordConfigured ? 'Leave empty to keep current' : 'Partner password (optional)'" />
              <p class="text-xs text-gray-500 mt-1">
                Stored encrypted. Used automatically when this partner is selected in the transfer form.
              </p>
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
      title="Delete Partner"
      :message="`Are you sure you want to delete partner '${partnerToDelete?.partnerId}'? This action cannot be undone.`"
      confirm-text="Delete"
      @confirm="deletePartner"
      @cancel="showDeleteModal = false"
    />
  </div>
</template>
