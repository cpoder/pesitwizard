<script setup lang="ts">
import { ref, computed } from 'vue'
import { GripVertical, Info } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  direction?: 'SEND' | 'RECEIVE'
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const inputRef = ref<HTMLInputElement | null>(null)

// Available placeholders with descriptions
// Note: Vectis only transmits virtual file IDs, not physical filenames
const placeholders = computed(() => {
  const common = [
    { tag: '${partner}', label: 'Partner', desc: 'Partner ID' },
    { tag: '${virtualFile}', label: 'Virtual File', desc: 'Virtual file name (PI 12)' },
    { tag: '${server}', label: 'Server', desc: 'Server ID' },
    { tag: '${serverName}', label: 'Server Name', desc: 'Server name' },
  ]
  
  const timestamps = [
    { tag: '${timestamp}', label: 'Timestamp', desc: 'yyyyMMdd_HHmmss' },
    { tag: '${date}', label: 'Date', desc: 'yyyyMMdd' },
    { tag: '${time}', label: 'Time', desc: 'HHmmss' },
    { tag: '${year}', label: 'Year', desc: '4 digits' },
    { tag: '${month}', label: 'Month', desc: '01-12' },
    { tag: '${day}', label: 'Day', desc: '01-31' },
    { tag: '${uuid}', label: 'UUID', desc: 'Random UUID' },
  ]
  
  return { common, timestamps }
})

// Preview with sample values
const preview = computed(() => {
  let result = props.modelValue || ''
  const now = new Date()
  
  const replacements: Record<string, string> = {
    '${partner}': 'PARTNER01',
    '${partnerid}': 'PARTNER01',
    '${virtualFile}': 'DATA_FILE',
    '${virtualfile}': 'DATA_FILE',
    '${server}': 'srv-001',
    '${serverid}': 'srv-001',
    '${serverName}': 'main-server',
    '${servername}': 'main-server',
    '${timestamp}': formatDate(now, 'yyyyMMdd_HHmmss'),
    '${date}': formatDate(now, 'yyyyMMdd'),
    '${time}': formatDate(now, 'HHmmss'),
    '${year}': String(now.getFullYear()),
    '${month}': pad(now.getMonth() + 1),
    '${day}': pad(now.getDate()),
    '${hour}': pad(now.getHours()),
    '${minute}': pad(now.getMinutes()),
    '${second}': pad(now.getSeconds()),
    '${uuid}': 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    '${direction}': props.direction || 'RECEIVE',
  }
  
  for (const [key, value] of Object.entries(replacements)) {
    result = result.replace(new RegExp(escapeRegex(key), 'gi'), value)
  }
  
  return result
})

function formatDate(d: Date, format: string): string {
  const map: Record<string, string> = {
    'yyyy': String(d.getFullYear()),
    'MM': pad(d.getMonth() + 1),
    'dd': pad(d.getDate()),
    'HH': pad(d.getHours()),
    'mm': pad(d.getMinutes()),
    'ss': pad(d.getSeconds()),
  }
  let result = format
  for (const [k, v] of Object.entries(map)) {
    result = result.replace(k, v)
  }
  return result
}

function pad(n: number): string {
  return n.toString().padStart(2, '0')
}

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function insertPlaceholder(tag: string) {
  const input = inputRef.value
  if (!input) {
    emit('update:modelValue', (props.modelValue || '') + tag)
    return
  }
  
  const start = input.selectionStart || 0
  const end = input.selectionEnd || 0
  const value = props.modelValue || ''
  const newValue = value.substring(0, start) + tag + value.substring(end)
  
  emit('update:modelValue', newValue)
  
  // Restore cursor position after the inserted tag
  setTimeout(() => {
    input.focus()
    const newPos = start + tag.length
    input.setSelectionRange(newPos, newPos)
  }, 0)
}

function onDragStart(e: DragEvent, tag: string) {
  e.dataTransfer?.setData('text/plain', tag)
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  const tag = e.dataTransfer?.getData('text/plain')
  if (tag) {
    insertPlaceholder(tag)
  }
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
}

const showHelp = ref(false)
</script>

<template>
  <div class="space-y-3">
    <!-- Label -->
    <div v-if="label" class="flex items-center justify-between">
      <label class="block text-sm font-medium text-gray-700">{{ label }}</label>
      <button 
        type="button"
        @click="showHelp = !showHelp" 
        class="text-gray-400 hover:text-gray-600"
        title="Help"
      >
        <Info class="h-4 w-4" />
      </button>
    </div>

    <!-- Help panel -->
    <div v-if="showHelp" class="p-3 bg-blue-50 rounded-lg text-sm text-blue-800">
      <p class="font-medium mb-2">Path Placeholders</p>
      <p>Use placeholders like <code class="bg-blue-100 px-1 rounded">${'${partner}'}</code> to dynamically generate file paths.</p>
      <p class="mt-1">Drag tags below into the input field, or click to insert at cursor.</p>
    </div>

    <!-- Placeholder tags -->
    <div class="space-y-2">
      <div class="flex flex-wrap gap-1.5">
        <span class="text-xs text-gray-500 mr-1">Common:</span>
        <button
          v-for="p in placeholders.common"
          :key="p.tag"
          type="button"
          draggable="true"
          @dragstart="onDragStart($event, p.tag)"
          @click="insertPlaceholder(p.tag)"
          class="inline-flex items-center gap-1 px-2 py-0.5 bg-purple-100 text-purple-700 rounded text-xs font-mono cursor-grab hover:bg-purple-200 active:cursor-grabbing"
          :title="p.desc"
        >
          <GripVertical class="h-3 w-3 opacity-50" />
          {{ p.label }}
        </button>
      </div>
      <div class="flex flex-wrap gap-1.5">
        <span class="text-xs text-gray-500 mr-1">Time:</span>
        <button
          v-for="p in placeholders.timestamps"
          :key="p.tag"
          type="button"
          draggable="true"
          @dragstart="onDragStart($event, p.tag)"
          @click="insertPlaceholder(p.tag)"
          class="inline-flex items-center gap-1 px-2 py-0.5 bg-amber-100 text-amber-700 rounded text-xs font-mono cursor-grab hover:bg-amber-200 active:cursor-grabbing"
          :title="p.desc"
        >
          <GripVertical class="h-3 w-3 opacity-50" />
          {{ p.label }}
        </button>
      </div>
    </div>

    <!-- Input field -->
    <input
      ref="inputRef"
      type="text"
      :value="modelValue"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      @drop="onDrop"
      @dragover="onDragOver"
      class="input w-full font-mono text-sm"
      :placeholder="placeholder || '/path/to/files/${partner}/${file}'"
    />

    <!-- Preview -->
    <div v-if="modelValue && modelValue.includes('${')" class="text-xs">
      <span class="text-gray-500">Preview: </span>
      <span class="font-mono text-gray-700 bg-gray-100 px-2 py-0.5 rounded">{{ preview }}</span>
    </div>
  </div>
</template>
