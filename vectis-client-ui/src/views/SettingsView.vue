<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Zap, Info, Check } from 'lucide-vue-next'
import api from '@/api'

// OTLP settings
const otlpEndpoint = ref('')
const otlpMetricsEnabled = ref(false)
const otlpTracingEnabled = ref(false)
const savingOtlp = ref(false)
const otlpSaveSuccess = ref(false)
const otlpError = ref('')

onMounted(async () => {
  await loadOtlpSettings()
})

async function loadOtlpSettings() {
  try {
    const response = await api.get('/config/otlp')
    otlpEndpoint.value = response.data.endpoint || ''
    otlpMetricsEnabled.value = response.data.metricsEnabled || false
    otlpTracingEnabled.value = response.data.tracingEnabled || false
  } catch (e) {
    console.debug('OTLP settings not available:', e)
  }
}

async function saveOtlpSettings() {
  savingOtlp.value = true
  otlpSaveSuccess.value = false
  otlpError.value = ''
  try {
    await api.put('/config/otlp', {
      endpoint: otlpEndpoint.value || null,
      metricsEnabled: otlpMetricsEnabled.value,
      tracingEnabled: otlpTracingEnabled.value
    })
    otlpSaveSuccess.value = true
    setTimeout(() => { otlpSaveSuccess.value = false }, 5000)
  } catch (e: any) {
    otlpError.value = e.response?.data?.message || 'Failed to save OTLP settings'
  } finally {
    savingOtlp.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Settings</h1>

    <div class="space-y-6">
      <!-- Observability -->
      <div class="card">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 bg-yellow-100 rounded-lg">
            <Zap class="h-5 w-5 text-yellow-600" />
          </div>
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Observability (OpenTelemetry)</h2>
            <p class="text-sm text-gray-500">Configure metrics and tracing export</p>
          </div>
        </div>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">OTLP Endpoint</label>
            <input 
              v-model="otlpEndpoint" 
              type="text" 
              class="input"
              placeholder="e.g., http://otel-collector:4318"
            />
            <p class="text-xs text-gray-500 mt-1">OpenTelemetry collector endpoint URL</p>
          </div>

          <div class="flex flex-wrap gap-6">
            <label class="flex items-center gap-2">
              <input 
                v-model="otlpMetricsEnabled" 
                type="checkbox" 
                class="rounded border-gray-300 text-primary-600"
                :disabled="!otlpEndpoint"
              />
              <span class="text-sm text-gray-700">Enable Metrics Export</span>
            </label>
            
            <label class="flex items-center gap-2">
              <input 
                v-model="otlpTracingEnabled" 
                type="checkbox" 
                class="rounded border-gray-300 text-primary-600"
                :disabled="!otlpEndpoint"
              />
              <span class="text-sm text-gray-700">Enable Tracing</span>
            </label>
          </div>

          <div v-if="!otlpEndpoint" class="p-3 bg-yellow-50 border border-yellow-200 rounded-lg text-yellow-700 text-sm">
            Enter an OTLP endpoint to enable metrics and tracing options
          </div>

          <div v-if="otlpSaveSuccess" class="p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm flex items-center gap-2">
            <Check class="h-4 w-4" />
            OTLP settings saved successfully. Restart the client for changes to take effect.
          </div>

          <div v-if="otlpError" class="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ otlpError }}
          </div>

          <button @click="saveOtlpSettings" class="btn btn-primary" :disabled="savingOtlp">
            {{ savingOtlp ? 'Saving...' : 'Save OTLP Settings' }}
          </button>
        </div>
      </div>

      <!-- About -->
      <div class="card">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 bg-blue-100 rounded-lg">
            <Info class="h-5 w-5 text-blue-600" />
          </div>
          <div>
            <h2 class="text-lg font-semibold text-gray-900">About</h2>
          </div>
        </div>

        <div class="space-y-2 text-sm">
          <p><strong>Vectis Client UI</strong></p>
          <p class="text-gray-600">Version: 1.0.0</p>
          <p class="text-gray-600">A web interface for Vectis file transfers.</p>
        </div>
      </div>
    </div>
  </div>
</template>
