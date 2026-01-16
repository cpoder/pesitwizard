<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Zap, Info, Check, Shield, Key, AlertTriangle, Copy, RefreshCw, Server } from 'lucide-vue-next'
import api from '@/api'

// OTLP settings
const otlpEndpoint = ref('')
const otlpMetricsEnabled = ref(false)
const otlpTracingEnabled = ref(false)
const savingOtlp = ref(false)
const otlpSaveSuccess = ref(false)
const otlpError = ref('')

// Security settings
const securityStatus = ref<{ enabled: boolean; mode: string; message: string } | null>(null)
const generatedKey = ref('')
const testingEncryption = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

// Vault settings
const showVaultConfig = ref(false)
const vaultAddress = ref('http://localhost:30200')
const vaultAuthMethod = ref<'token' | 'approle'>('token')
const vaultToken = ref('')
const vaultRoleId = ref('')
const vaultSecretId = ref('')
const testingVault = ref(false)
const vaultTestResult = ref<{ success: boolean; message: string } | null>(null)

onMounted(async () => {
  await Promise.all([loadOtlpSettings(), loadSecurityStatus()])
})

async function loadSecurityStatus() {
  try {
    const response = await api.get('/security/status')
    securityStatus.value = response.data.encryption
  } catch (e) {
    console.debug('Security status not available:', e)
  }
}

async function generateNewKey() {
  try {
    const response = await api.post('/security/generate-key')
    generatedKey.value = response.data.key
  } catch (e: any) {
    console.error('Failed to generate key:', e)
  }
}

async function testEncryption() {
  testingEncryption.value = true
  testResult.value = null
  try {
    const response = await api.post('/security/test', { value: 'test-value-' + Date.now() })
    testResult.value = response.data
  } catch (e: any) {
    testResult.value = { success: false, message: e.message }
  } finally {
    testingEncryption.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
}

async function testVaultConnection() {
  testingVault.value = true
  vaultTestResult.value = null
  try {
    const response = await api.post('/security/vault/test', {
      address: vaultAddress.value,
      token: vaultToken.value
    })
    vaultTestResult.value = response.data
  } catch (e: any) {
    vaultTestResult.value = { success: false, message: e.message }
  } finally {
    testingVault.value = false
  }
}

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
      <!-- Security / Encryption -->
      <div class="card">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 bg-green-100 rounded-lg">
            <Shield class="h-5 w-5 text-green-600" />
          </div>
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Security & Encryption</h2>
            <p class="text-sm text-gray-500">Protect sensitive data like passwords and API keys</p>
          </div>
        </div>

        <!-- Status -->
        <div class="mb-4">
          <div v-if="securityStatus" :class="[
            'p-3 rounded-lg flex items-center gap-2',
            securityStatus.enabled ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-yellow-50 border border-yellow-200 text-yellow-700'
          ]">
            <Check v-if="securityStatus.enabled" class="h-5 w-5" />
            <AlertTriangle v-else class="h-5 w-5" />
            <div>
              <span class="font-medium">{{ securityStatus.mode }}</span>
              <span class="mx-2">-</span>
              <span>{{ securityStatus.message }}</span>
            </div>
          </div>
        </div>

        <!-- Instructions when not configured -->
        <div v-if="securityStatus && !securityStatus.enabled" class="space-y-4">
          <div class="p-4 bg-gray-50 rounded-lg">
            <h3 class="font-medium text-gray-900 mb-2">How to enable encryption:</h3>
            <ol class="list-decimal list-inside space-y-2 text-sm text-gray-600">
              <li>Generate a new master key (click button below)</li>
              <li>Set it as environment variable: <code class="bg-gray-200 px-1 rounded">PESITWIZARD_SECURITY_MASTER_KEY</code></li>
              <li>Restart the PeSIT Wizard Client application</li>
            </ol>
          </div>

          <div class="flex items-center gap-3">
            <button @click="generateNewKey" class="btn btn-primary flex items-center gap-2">
              <Key class="h-4 w-4" />
              Generate New Key
            </button>
          </div>

          <div v-if="generatedKey" class="p-4 bg-blue-50 border border-blue-200 rounded-lg">
            <p class="text-sm font-medium text-blue-800 mb-2">Generated Master Key:</p>
            <div class="flex items-center gap-2">
              <code class="flex-1 bg-white p-2 rounded border text-xs font-mono break-all">{{ generatedKey }}</code>
              <button @click="copyToClipboard(generatedKey)" class="p-2 hover:bg-blue-100 rounded" title="Copy">
                <Copy class="h-4 w-4 text-blue-600" />
              </button>
            </div>
            <p class="text-xs text-blue-600 mt-2">⚠️ Save this key securely! You'll need it to decrypt your data.</p>
          </div>
        </div>

        <!-- Test encryption when configured -->
        <div v-if="securityStatus && securityStatus.enabled" class="space-y-4">
          <div class="flex items-center gap-3">
            <button @click="testEncryption" :disabled="testingEncryption" class="btn btn-secondary flex items-center gap-2">
              <RefreshCw :class="['h-4 w-4', testingEncryption && 'animate-spin']" />
              Test Encryption
            </button>
          </div>

          <div v-if="testResult" :class="[
            'p-3 rounded-lg flex items-center gap-2',
            testResult.success ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
          ]">
            <Check v-if="testResult.success" class="h-4 w-4" />
            <AlertTriangle v-else class="h-4 w-4" />
            {{ testResult.message }}
          </div>
        </div>

        <!-- Vault Configuration -->
        <div class="border-t pt-4 mt-4">
          <button @click="showVaultConfig = !showVaultConfig" class="flex items-center gap-2 text-blue-600 hover:text-blue-800">
            <Server class="h-4 w-4" />
            <span class="text-sm font-medium">{{ showVaultConfig ? 'Hide' : 'Configure' }} Vault Integration</span>
          </button>
          
          <div v-if="showVaultConfig" class="mt-4 space-y-4 p-4 bg-gray-50 rounded-lg">
            <div class="p-3 bg-blue-100 border border-blue-200 rounded-lg text-sm text-blue-800">
              <strong>Note:</strong> Vault configuration is done via environment variables. 
              Use this form to test connectivity and generate the required variables.
            </div>
            
            <!-- Auth Method Selection -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">Authentication Method</label>
              <div class="grid grid-cols-2 gap-3">
                <button
                  @click="vaultAuthMethod = 'token'"
                  :class="['p-3 border rounded-lg text-left transition', vaultAuthMethod === 'token' ? 'border-blue-500 bg-white' : 'hover:border-gray-400']"
                >
                  <span class="text-sm font-medium">Token</span>
                  <span class="text-xs block text-gray-500">Simple, for dev/test</span>
                </button>
                <button
                  @click="vaultAuthMethod = 'approle'"
                  :class="['p-3 border rounded-lg text-left transition', vaultAuthMethod === 'approle' ? 'border-blue-500 bg-white' : 'hover:border-gray-400']"
                >
                  <span class="text-sm font-medium">AppRole</span>
                  <span class="text-xs block text-green-600">✓ Recommended</span>
                </button>
              </div>
            </div>
            
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Vault Address</label>
              <input v-model="vaultAddress" type="text" class="input" placeholder="http://localhost:30200" />
            </div>
            
            <!-- Token auth -->
            <div v-if="vaultAuthMethod === 'token'">
              <label class="block text-sm font-medium text-gray-700 mb-1">Vault Token</label>
              <input v-model="vaultToken" type="password" class="input" placeholder="hvs.xxxxx or root" />
            </div>

            <!-- AppRole auth -->
            <template v-if="vaultAuthMethod === 'approle'">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Role ID</label>
                <input v-model="vaultRoleId" type="text" class="input" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Secret ID</label>
                <input v-model="vaultSecretId" type="password" class="input" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
              </div>
            </template>

            <div class="flex items-center gap-3">
              <button @click="testVaultConnection" :disabled="testingVault || !vaultAddress || (vaultAuthMethod === 'token' ? !vaultToken : (!vaultRoleId || !vaultSecretId))" class="btn btn-primary flex items-center gap-2">
                <RefreshCw :class="['h-4 w-4', testingVault && 'animate-spin']" />
                Test Connection
              </button>
            </div>

            <div v-if="vaultTestResult" :class="[
              'p-3 rounded-lg flex items-center gap-2',
              vaultTestResult.success ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
            ]">
              <Check v-if="vaultTestResult.success" class="h-4 w-4" />
              <AlertTriangle v-else class="h-4 w-4" />
              {{ vaultTestResult.message }}
            </div>

            <!-- Environment Variables Display -->
            <div class="p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <p class="text-sm font-medium text-blue-800 mb-2">Set these environment variables and restart:</p>
              <pre v-if="vaultAuthMethod === 'token'" class="bg-white p-2 rounded border text-xs font-mono overflow-x-auto">PESITWIZARD_SECURITY_MODE=VAULT
PESITWIZARD_SECURITY_VAULT_ADDRESS={{ vaultAddress }}
PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=token
PESITWIZARD_SECURITY_VAULT_TOKEN={{ vaultToken || '&lt;your-token&gt;' }}
PESITWIZARD_SECURITY_VAULT_PATH=secret/data/pesitwizard-client</pre>
              <pre v-else class="bg-white p-2 rounded border text-xs font-mono overflow-x-auto">PESITWIZARD_SECURITY_MODE=VAULT
PESITWIZARD_SECURITY_VAULT_ADDRESS={{ vaultAddress }}
PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=approle
PESITWIZARD_SECURITY_VAULT_ROLE_ID={{ vaultRoleId || '&lt;your-role-id&gt;' }}
PESITWIZARD_SECURITY_VAULT_SECRET_ID={{ vaultSecretId || '&lt;your-secret-id&gt;' }}
PESITWIZARD_SECURITY_VAULT_PATH=secret/data/pesitwizard-client</pre>
              <div class="flex gap-2 mt-2">
                <button @click="copyToClipboard(vaultAuthMethod === 'token' 
                  ? `PESITWIZARD_SECURITY_MODE=VAULT\nPESITWIZARD_SECURITY_VAULT_ADDRESS=${vaultAddress}\nPESITWIZARD_SECURITY_VAULT_AUTH_METHOD=token\nPESITWIZARD_SECURITY_VAULT_TOKEN=${vaultToken}\nPESITWIZARD_SECURITY_VAULT_PATH=secret/data/pesitwizard-client`
                  : `PESITWIZARD_SECURITY_MODE=VAULT\nPESITWIZARD_SECURITY_VAULT_ADDRESS=${vaultAddress}\nPESITWIZARD_SECURITY_VAULT_AUTH_METHOD=approle\nPESITWIZARD_SECURITY_VAULT_ROLE_ID=${vaultRoleId}\nPESITWIZARD_SECURITY_VAULT_SECRET_ID=${vaultSecretId}\nPESITWIZARD_SECURITY_VAULT_PATH=secret/data/pesitwizard-client`
                )" class="btn btn-sm btn-secondary flex items-center gap-1">
                  <Copy class="h-3 w-3" /> Copy
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

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
          <p><strong>PeSIT Wizard Client</strong></p>
          <p class="text-gray-600">Version: 1.0.0</p>
          <p class="text-gray-600">A web interface for PeSIT file transfers.</p>
        </div>
      </div>
    </div>
  </div>
</template>
