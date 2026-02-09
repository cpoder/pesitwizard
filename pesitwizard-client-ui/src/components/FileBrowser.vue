<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Folder, File, ChevronRight, ChevronUp, Loader2, X, Check } from 'lucide-vue-next'
import api from '@/api'

interface FileMetadata {
  name: string
  path: string
  size: number
  lastModified: string
  directory: boolean
}

const props = defineProps<{
  connectionId: string  // Empty string = local filesystem
  mode: 'file' | 'directory'
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'close': []
}>()

const loading = ref(false)
const error = ref('')
const currentPath = ref('.')
const files = ref<FileMetadata[]>([])
const selectedPath = ref(props.modelValue || '')

const pathParts = computed(() => {
  if (currentPath.value === '.' || currentPath.value === '') return []
  return currentPath.value.split('/').filter(p => p && p !== '.')
})

watch(() => props.connectionId, () => {
  currentPath.value = '.'
  browse('.')
}, { immediate: true })

async function browse(path: string) {
  loading.value = true
  error.value = ''
  
  try {
    // Use connection-specific endpoint or local filesystem endpoint
    const endpoint = props.connectionId 
      ? `/connectors/connections/${props.connectionId}/browse`
      : '/connectors/local/browse'
    const response = await api.get(endpoint, {
      params: { path }
    })
    files.value = response.data || []
    currentPath.value = path
    
    // Sort: directories first, then files, alphabetically
    files.value.sort((a, b) => {
      if (a.directory && !b.directory) return -1
      if (!a.directory && b.directory) return 1
      return a.name.localeCompare(b.name)
    })
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to browse'
    console.error('Browse failed:', e)
  } finally {
    loading.value = false
  }
}

function navigateTo(file: FileMetadata) {
  if (file.directory) {
    browse(file.path)
  } else if (props.mode === 'file') {
    selectedPath.value = file.path
  }
}

function navigateUp() {
  const parts = currentPath.value.split('/').filter(p => p && p !== '.')
  if (parts.length > 0) {
    parts.pop()
    browse(parts.length > 0 ? parts.join('/') : '.')
  }
}

function navigateToIndex(index: number) {
  const parts = pathParts.value.slice(0, index + 1)
  browse(parts.join('/'))
}

function selectCurrentDirectory() {
  selectedPath.value = currentPath.value === '.' ? '' : currentPath.value
}

function confirm() {
  emit('update:modelValue', selectedPath.value)
  emit('close')
}

function formatSize(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div class="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[80vh] flex flex-col">
      <!-- Header -->
      <div class="flex items-center justify-between px-4 py-3 border-b">
        <h3 class="font-semibold text-gray-900">
          {{ mode === 'file' ? 'Select File' : 'Select Directory' }}
        </h3>
        <button @click="$emit('close')" class="p-1 hover:bg-gray-100 rounded" aria-label="Close file browser">
          <X class="h-5 w-5 text-gray-500" aria-hidden="true" />
        </button>
      </div>

      <!-- Breadcrumb -->
      <nav aria-label="Breadcrumb" class="flex items-center gap-1 px-4 py-2 bg-gray-50 border-b text-sm overflow-x-auto">
        <button 
          @click="browse('.')" 
          class="hover:text-blue-600 font-medium flex-shrink-0"
        >
          Root
        </button>
        <template v-for="(part, index) in pathParts" :key="index">
          <ChevronRight class="h-4 w-4 text-gray-400 flex-shrink-0" aria-hidden="true" />
          <button 
            @click="navigateToIndex(index)"
            class="hover:text-blue-600 truncate max-w-[150px]"
            :title="part"
          >
            {{ part }}
          </button>
        </template>
      </nav>

      <!-- File List -->
      <div class="flex-1 overflow-y-auto min-h-[300px]">
        <div v-if="loading" class="flex items-center justify-center h-full">
          <Loader2 class="h-8 w-8 animate-spin text-gray-400" aria-hidden="true" />
        </div>

        <div v-else-if="error" class="p-4 text-red-600 text-center">
          {{ error }}
        </div>

        <div v-else-if="files.length === 0" class="p-8 text-center text-gray-500">
          Empty directory
        </div>

        <table v-else class="w-full text-sm">
          <thead class="bg-gray-50 sticky top-0">
            <tr>
              <th scope="col" class="text-left px-4 py-2 font-medium text-gray-600">Name</th>
              <th scope="col" class="text-right px-4 py-2 font-medium text-gray-600 w-24">Size</th>
              <th scope="col" class="text-right px-4 py-2 font-medium text-gray-600 w-40">Modified</th>
            </tr>
          </thead>
          <tbody>
            <!-- Parent directory -->
            <tr
              v-if="currentPath !== '.'"
              @click="navigateUp"
              @keydown.enter="navigateUp"
              tabindex="0"
              class="hover:bg-gray-50 cursor-pointer border-b"
            >
              <td class="px-4 py-2 flex items-center gap-2">
                <ChevronUp class="h-4 w-4 text-gray-400" aria-hidden="true" />
                <span class="text-gray-600">..</span>
              </td>
              <td></td>
              <td></td>
            </tr>
            <!-- Files and directories -->
            <tr
              v-for="file in files"
              :key="file.path"
              @click="navigateTo(file)"
              @keydown.enter="navigateTo(file)"
              tabindex="0"
              :class="[
                'hover:bg-gray-50 cursor-pointer border-b',
                selectedPath === file.path ? 'bg-blue-50' : ''
              ]"
            >
              <td class="px-4 py-2">
                <div class="flex items-center gap-2">
                  <Folder v-if="file.directory" class="h-4 w-4 text-yellow-500 flex-shrink-0" aria-hidden="true" />
                  <File v-else class="h-4 w-4 text-gray-400 flex-shrink-0" aria-hidden="true" />
                  <span class="truncate" :title="file.name">{{ file.name }}</span>
                </div>
              </td>
              <td class="px-4 py-2 text-right text-gray-500">
                {{ file.directory ? '-' : formatSize(file.size) }}
              </td>
              <td class="px-4 py-2 text-right text-gray-500 text-xs">
                {{ formatDate(file.lastModified) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Selected path -->
      <div class="px-4 py-2 border-t bg-gray-50">
        <div class="flex items-center gap-2">
          <span class="text-sm text-gray-600">Selected:</span>
          <input 
            v-model="selectedPath"
            type="text"
            class="flex-1 px-2 py-1 text-sm border rounded bg-white"
            :placeholder="mode === 'file' ? 'Select a file above' : 'Current directory or enter path'"
          />
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-between px-4 py-3 border-t">
        <button 
          v-if="mode === 'directory'"
          @click="selectCurrentDirectory"
          class="text-sm text-blue-600 hover:underline"
        >
          Use current directory
        </button>
        <div v-else></div>
        <div class="flex gap-2">
          <button @click="$emit('close')" class="btn btn-secondary">
            Cancel
          </button>
          <button 
            @click="confirm"
            class="btn btn-primary flex items-center gap-1"
            :disabled="!selectedPath && mode === 'file'"
          >
            <Check class="h-4 w-4" aria-hidden="true" />
            Select
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
