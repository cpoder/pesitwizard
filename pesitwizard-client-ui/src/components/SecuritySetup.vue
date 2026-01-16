<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Shield, Key, Server, AlertTriangle, CheckCircle, XCircle, RefreshCw, Copy, Play, Settings } from 'lucide-vue-next'
import api from '@/api'

const props = defineProps<{
  apiBase?: string
}>()

const emit = defineEmits<{
  (e: 'configured'): void
}>()

interface SecurityStatus {
  encryption: { mode: string; enabled: boolean; message: string }
}

interface VaultStatus {
  mode: string
  configured: boolean
  connected: boolean
  connectionMessage: string
  kvEngineReady?: boolean
  kvEngineMessage?: string
  vaultAddress?: string
  authMethod?: string
  secretPath?: string
}

const loading = ref(true)
const error = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const status = ref<SecurityStatus | null>(null)
const vaultStatus = ref<VaultStatus | null>(null)

// AES key generation
const generatedKey = ref('')
const generatingKey = ref(false)

// Vault test
const vaultTestAddress = ref('http://localhost:8200')
const vaultTestAuthMethod = ref<'token' | 'approle'>('token')
const vaultTestToken = ref('')
const vaultTestRoleId = ref('')
const vaultTestSecretId = ref('')
const testingVault = ref(false)
const vaultTestResult = ref<{ success: boolean; message: string } | null>(null)

// Vault initialization
const initializingVault = ref(false)
const initVaultResult = ref<{ success: boolean; message: string; roleId?: string; secretId?: string } | null>(null)

const isConfigured = computed(() => {
  return status.value?.encryption?.enabled === true
})

const isDegradedMode = computed(() => {
  return !isConfigured.value
})

async function fetchStatus() {
  loading.value = true
  error.value = null
  try {
    const response = await api.get('/security/status')
    status.value = response.data
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function fetchVaultStatus() {
  try {
    const response = await api.get('/security/vault/status')
    vaultStatus.value = response.data
    // Pre-fill test fields from current config
    if (vaultStatus.value?.vaultAddress) {
      vaultTestAddress.value = vaultStatus.value.vaultAddress
    }
    if (vaultStatus.value?.authMethod) {
      vaultTestAuthMethod.value = vaultStatus.value.authMethod.toLowerCase() as 'token' | 'approle'
    }
  } catch (e) {
    console.debug('Vault status not available:', e)
  }
}

async function generateAesKey() {
  generatingKey.value = true
  try {
    const response = await api.post('/security/generate-key')
    generatedKey.value = response.data.key
  } catch (e: any) {
    error.value = 'Failed to generate key: ' + e.message
  } finally {
    generatingKey.value = false
  }
}

async function testVaultConnection() {
  testingVault.value = true
  vaultTestResult.value = null
  try {
    const endpoint = vaultTestAuthMethod.value === 'approle' 
      ? '/security/vault/test-approle'
      : '/security/vault/test'
    
    const body = vaultTestAuthMethod.value === 'approle'
      ? { address: vaultTestAddress.value, roleId: vaultTestRoleId.value, secretId: vaultTestSecretId.value }
      : { address: vaultTestAddress.value, token: vaultTestToken.value }

    const response = await api.post(endpoint, body)
    vaultTestResult.value = { success: response.data.success, message: response.data.message }
  } catch (e: any) {
    vaultTestResult.value = { success: false, message: e.response?.data?.message || e.message }
  } finally {
    testingVault.value = false
  }
}

async function initializeVault() {
  if (!vaultTestAddress.value || !vaultTestToken.value) {
    initVaultResult.value = { success: false, message: 'Vault address and admin token are required' }
    return
  }
  initializingVault.value = true
  initVaultResult.value = null
  try {
    const response = await api.post('/security/vault/setup', {
      address: vaultTestAddress.value,
      token: vaultTestToken.value,
      setupAppRole: true
    })
    initVaultResult.value = response.data
    if (response.data.success) {
      await fetchVaultStatus()
    }
  } catch (e: any) {
    initVaultResult.value = { success: false, message: e.response?.data?.message || e.message }
  } finally {
    initializingVault.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
}

// Selected mode for env vars display
const envVarsMode = ref<'aes' | 'vault-token' | 'vault-approle'>('aes')

function getEnvVarsText() {
  if (envVarsMode.value === 'vault-approle') {
    return `export PESITWIZARD_SECURITY_ENCRYPTION_MODE=VAULT
export PESITWIZARD_SECURITY_VAULT_ADDRESS=${vaultTestAddress.value || 'http://vault:8200'}
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=approle
export PESITWIZARD_SECURITY_VAULT_ROLE_ID=${initVaultResult.value?.roleId || '<role-id>'}
export PESITWIZARD_SECURITY_VAULT_SECRET_ID=${initVaultResult.value?.secretId || '<secret-id>'}`
  } else if (envVarsMode.value === 'vault-token') {
    return `export PESITWIZARD_SECURITY_ENCRYPTION_MODE=VAULT
export PESITWIZARD_SECURITY_VAULT_ADDRESS=${vaultTestAddress.value || 'http://vault:8200'}
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=token
export PESITWIZARD_SECURITY_VAULT_TOKEN=${vaultTestToken.value || '<vault-token>'}`
  } else {
    return `export PESITWIZARD_SECURITY_ENCRYPTION_MODE=AES
export PESITWIZARD_SECURITY_MASTER_KEY=${generatedKey.value || '<base64-key>'}`
  }
}

function copyEnvVars() {
  navigator.clipboard.writeText(getEnvVarsText())
  successMessage.value = 'Environment variables copied to clipboard'
  setTimeout(() => successMessage.value = null, 3000)
}

onMounted(() => {
  fetchStatus()
  fetchVaultStatus()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
    </div>

    <template v-else>
      <!-- Degraded Mode Warning -->
      <div v-if="isDegradedMode" class="bg-red-50 border-2 border-red-300 rounded-lg p-6">
        <div class="flex items-center gap-3 mb-4">
          <AlertTriangle class="w-8 h-8 text-red-500" />
          <div>
            <h2 class="text-lg font-bold text-red-700">Security Not Configured</h2>
            <p class="text-red-600">Encryption must be configured before using the application.</p>
          </div>
        </div>
      </div>

      <!-- Success/Error Messages -->
      <div v-if="successMessage" class="bg-green-50 border border-green-200 rounded-lg p-4 flex items-center gap-2">
        <CheckCircle class="w-5 h-5 text-green-500 flex-shrink-0" />
        <p class="text-green-700">{{ successMessage }}</p>
        <button @click="successMessage = null" class="ml-auto text-green-600 hover:text-green-800">
          <XCircle class="w-4 h-4" />
        </button>
      </div>

      <div v-if="error" class="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-2">
        <AlertTriangle class="w-5 h-5 text-red-500 flex-shrink-0" />
        <p class="text-red-700">{{ error }}</p>
        <button @click="error = null" class="ml-auto text-red-600 hover:text-red-800">
          <XCircle class="w-4 h-4" />
        </button>
      </div>

      <!-- Current Status -->
      <div class="card">
        <h2 class="text-lg font-semibold flex items-center gap-2 mb-4">
          <Shield class="w-5 h-5" />
          Current Status
        </h2>
        
        <div v-if="status?.encryption" class="flex items-center gap-3 p-4 rounded-lg" :class="status.encryption.enabled ? 'bg-green-50 border border-green-200' : 'bg-yellow-50 border border-yellow-200'">
          <component :is="status.encryption.enabled ? CheckCircle : AlertTriangle" :class="['w-8 h-8', status.encryption.enabled ? 'text-green-500' : 'text-yellow-500']" />
          <div>
            <p class="font-semibold">{{ status.encryption.mode }}</p>
            <p class="text-sm text-gray-600">{{ status.encryption.message }}</p>
          </div>
        </div>

        <!-- Vault Status (if configured) -->
        <div v-if="vaultStatus?.mode === 'VAULT'" class="mt-4 p-4 rounded-lg border" :class="vaultStatus.connected ? 'bg-green-50 border-green-200' : 'bg-yellow-50 border-yellow-200'">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <Server :class="['w-6 h-6', vaultStatus.connected ? 'text-green-500' : 'text-yellow-500']" />
              <div>
                <p class="font-medium">Vault: {{ vaultStatus.connected ? 'Connected' : 'Not Connected' }}</p>
                <p class="text-sm text-gray-600">{{ vaultStatus.connectionMessage }}</p>
                <p v-if="vaultStatus.kvEngineReady !== undefined" class="text-xs mt-1" :class="vaultStatus.kvEngineReady ? 'text-green-600' : 'text-yellow-600'">
                  KV Engine: {{ vaultStatus.kvEngineMessage }}
                </p>
              </div>
            </div>
            <button @click="fetchVaultStatus" class="px-3 py-1 text-sm border rounded hover:bg-white flex items-center gap-1">
              <RefreshCw class="w-3 h-3" />
              Refresh
            </button>
          </div>
        </div>
      </div>

      <!-- AES Key Generation -->
      <div class="card">
        <h2 class="text-lg font-semibold flex items-center gap-2 mb-4">
          <Key class="w-5 h-5 text-green-500" />
          AES Encryption Setup
        </h2>
        
        <p class="text-sm text-gray-600 mb-4">Generate a secure 256-bit AES key for local encryption.</p>
        
        <div class="flex gap-2 mb-4">
          <button @click="generateAesKey" :disabled="generatingKey" class="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 flex items-center gap-2">
            <RefreshCw :class="['w-4 h-4', generatingKey && 'animate-spin']" />
            Generate New Key
          </button>
        </div>
        
        <div v-if="generatedKey" class="bg-gray-100 p-4 rounded-lg">
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm font-medium text-gray-700">Generated Key (Base64):</span>
            <button @click="copyToClipboard(generatedKey)" class="text-blue-600 hover:text-blue-800 flex items-center gap-1 text-sm">
              <Copy class="w-4 h-4" />
              Copy
            </button>
          </div>
          <code class="block bg-white p-2 rounded border text-xs font-mono break-all">{{ generatedKey }}</code>
          <p class="text-xs text-gray-500 mt-2">Set this as <code class="bg-gray-200 px-1 rounded">PESITWIZARD_SECURITY_MASTER_KEY</code> and restart.</p>
        </div>
      </div>

      <!-- Vault Configuration -->
      <div class="card">
        <h2 class="text-lg font-semibold flex items-center gap-2 mb-4">
          <Server class="w-5 h-5 text-blue-500" />
          Vault Configuration
        </h2>
        
        <div class="space-y-4">
          <!-- Test Connection -->
          <div class="border rounded-lg p-4">
            <h3 class="font-medium text-gray-700 mb-3">Test Vault Connection</h3>
            
            <div class="grid md:grid-cols-2 gap-4 mb-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Vault Address</label>
                <input v-model="vaultTestAddress" type="text" class="w-full px-3 py-2 border rounded-lg" placeholder="http://vault:8200" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Auth Method</label>
                <select v-model="vaultTestAuthMethod" class="w-full px-3 py-2 border rounded-lg">
                  <option value="token">Token</option>
                  <option value="approle">AppRole (recommended)</option>
                </select>
              </div>
            </div>
            
            <div v-if="vaultTestAuthMethod === 'token'" class="mb-4">
              <label class="block text-sm font-medium text-gray-700 mb-1">Vault Token</label>
              <input v-model="vaultTestToken" type="password" class="w-full px-3 py-2 border rounded-lg" placeholder="hvs.xxxxx" />
            </div>
            
            <div v-else class="grid md:grid-cols-2 gap-4 mb-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Role ID</label>
                <input v-model="vaultTestRoleId" type="text" class="w-full px-3 py-2 border rounded-lg" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Secret ID</label>
                <input v-model="vaultTestSecretId" type="password" class="w-full px-3 py-2 border rounded-lg" />
              </div>
            </div>
            
            <button @click="testVaultConnection" :disabled="testingVault" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
              <Play v-if="!testingVault" class="w-4 h-4" />
              <RefreshCw v-else class="w-4 h-4 animate-spin" />
              Test Connection
            </button>
            
            <div v-if="vaultTestResult" class="mt-3 p-3 rounded-lg flex items-center gap-2" :class="vaultTestResult.success ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
              <component :is="vaultTestResult.success ? CheckCircle : XCircle" class="w-5 h-5" />
              {{ vaultTestResult.message }}
            </div>
          </div>

          <!-- Initialize Vault -->
          <div class="border rounded-lg p-4 bg-blue-50">
            <h3 class="font-medium text-gray-700 mb-3">Initialize Vault</h3>
            <p class="text-sm text-gray-600 mb-3">
              Create the KV secrets engine and set up AppRole authentication for this application.
              Requires an admin token with sufficient permissions.
            </p>
            
            <button 
              @click="initializeVault" 
              :disabled="initializingVault || !vaultTestAddress || !vaultTestToken || vaultTestAuthMethod !== 'token'"
              class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
            >
              <Settings :class="['w-4 h-4', initializingVault && 'animate-spin']" />
              {{ initializingVault ? 'Initializing...' : 'Initialize Vault & Create AppRole' }}
            </button>
            
            <p v-if="vaultTestAuthMethod !== 'token'" class="text-xs text-yellow-600 mt-2">
              Switch to Token auth and provide an admin token to initialize Vault.
            </p>
            
            <div v-if="initVaultResult" class="mt-3 p-3 rounded-lg" :class="initVaultResult.success ? 'bg-green-100' : 'bg-red-100'">
              <p :class="initVaultResult.success ? 'text-green-700' : 'text-red-700'">{{ initVaultResult.message }}</p>
              <div v-if="initVaultResult.roleId && initVaultResult.secretId" class="mt-2 p-2 bg-white rounded border">
                <p class="text-sm font-medium text-green-800 mb-2">AppRole Credentials Generated:</p>
                <div class="space-y-1 text-xs font-mono">
                  <div class="flex justify-between">
                    <span>Role ID:</span>
                    <div class="flex items-center gap-1">
                      <code>{{ initVaultResult.roleId }}</code>
                      <button @click="copyToClipboard(initVaultResult.roleId!)" class="text-blue-600"><Copy class="w-3 h-3" /></button>
                    </div>
                  </div>
                  <div class="flex justify-between">
                    <span>Secret ID:</span>
                    <div class="flex items-center gap-1">
                      <code>{{ initVaultResult.secretId }}</code>
                      <button @click="copyToClipboard(initVaultResult.secretId!)" class="text-blue-600"><Copy class="w-3 h-3" /></button>
                    </div>
                  </div>
                </div>
                <p class="text-xs text-yellow-600 mt-2">⚠️ Save these credentials! The Secret ID cannot be retrieved again.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Environment Variables Reference -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold">Environment Variables</h2>
          <button @click="copyEnvVars" class="px-3 py-1 text-sm bg-gray-100 border rounded hover:bg-gray-200 flex items-center gap-1">
            <Copy class="w-4 h-4" />
            Copy All
          </button>
        </div>
        
        <!-- Mode selector -->
        <div class="flex gap-2 mb-4">
          <button 
            @click="envVarsMode = 'aes'" 
            :class="['px-3 py-1 text-sm rounded border', envVarsMode === 'aes' ? 'bg-blue-600 text-white border-blue-600' : 'hover:bg-gray-100']"
          >AES</button>
          <button 
            @click="envVarsMode = 'vault-token'" 
            :class="['px-3 py-1 text-sm rounded border', envVarsMode === 'vault-token' ? 'bg-blue-600 text-white border-blue-600' : 'hover:bg-gray-100']"
          >Vault (Token)</button>
          <button 
            @click="envVarsMode = 'vault-approle'" 
            :class="['px-3 py-1 text-sm rounded border', envVarsMode === 'vault-approle' ? 'bg-blue-600 text-white border-blue-600' : 'hover:bg-gray-100']"
          >Vault (AppRole)</button>
        </div>
        
        <p class="text-sm text-gray-600 mb-3">Add to your shell profile or systemd unit file:</p>
        
        <pre class="bg-gray-900 text-green-400 p-4 rounded-lg text-xs font-mono overflow-x-auto whitespace-pre-wrap">{{ getEnvVarsText() }}</pre>
        
        <!-- Security Warning -->
        <div class="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
          <div class="flex items-start gap-2">
            <AlertTriangle class="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
            <div class="text-sm">
              <p class="font-medium text-yellow-800">Security Note</p>
              <p class="text-yellow-700 mt-1">
                Environment variables can be visible via <code class="bg-yellow-100 px-1 rounded">/proc</code> or process listings.
                For production, consider:
              </p>
              <ul class="list-disc list-inside text-yellow-700 mt-1 space-y-1">
                <li><strong>systemd credentials</strong>: <code class="bg-yellow-100 px-1 rounded">LoadCredential=</code> directive</li>
                <li><strong>Vault Agent</strong>: auto-injects secrets, no env vars needed</li>
                <li><strong>File-based</strong>: <code class="bg-yellow-100 px-1 rounded">chmod 600</code> file read at startup</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
