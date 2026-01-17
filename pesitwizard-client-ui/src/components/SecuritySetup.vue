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
  aes: { configured: boolean; usingFixedKey: boolean; message: string }
  vault: { enabled: boolean; connected: boolean; address: string; authMethod: string; message: string }
}

const loading = ref(true)
const error = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const status = ref<SecurityStatus | null>(null)

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

// Migration
const migratingSecrets = ref(false)
const migrationResult = ref<{ success: boolean; message: string; totalMigrated?: number } | null>(null)

const isConfigured = computed(() => {
  return status.value?.encryption?.enabled === true
})

const isDegradedMode = computed(() => {
  return !isConfigured.value
})

async function migrateSecretsToVault() {
  migratingSecrets.value = true
  migrationResult.value = null
  try {
    const response = await api.post('/security/encryption/migrate')
    migrationResult.value = response.data
    if (response.data.success) {
      successMessage.value = `Migration complete: ${response.data.totalMigrated} secrets migrated`
      setTimeout(() => successMessage.value = null, 5000)
    }
  } catch (e: any) {
    migrationResult.value = { success: false, message: e.response?.data?.message || e.message }
  } finally {
    migratingSecrets.value = false
  }
}

async function fetchStatus() {
  loading.value = true
  error.value = null
  try {
    const response = await api.get('/security/status')
    status.value = response.data
    // Pre-fill Vault fields from current config
    if (status.value?.vault?.address) {
      vaultTestAddress.value = status.value.vault.address
    }
    if (status.value?.vault?.authMethod) {
      vaultTestAuthMethod.value = status.value.vault.authMethod.toLowerCase() as 'token' | 'approle'
    }
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
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

// Use file-based secrets (more secure)
const useFileSecrets = ref(true)

// Generate env vars based on actual config (AES always + Vault if enabled)
function getEnvVarsText() {
  const vaultEnabled = status.value?.vault?.enabled
  const authMethod = status.value?.vault?.authMethod?.toLowerCase() || vaultTestAuthMethod.value
  
  let vars = `# AES Master Key (always required for bootstrap)
export PESITWIZARD_SECURITY_MASTER_KEY${useFileSecrets.value ? '_FILE=/etc/pesitwizard/master-key' : `=${generatedKey.value || '<generate-key-above>'}`}`
  
  if (vaultEnabled || initVaultResult.value?.success) {
    vars += `\n
# Vault Configuration (secrets storage)
export PESITWIZARD_SECURITY_ENCRYPTION_MODE=VAULT
export PESITWIZARD_SECURITY_VAULT_ADDRESS=${status.value?.vault?.address || vaultTestAddress.value || 'http://vault:8200'}`
    
    if (authMethod === 'approle') {
      if (useFileSecrets.value) {
        vars += `
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=approle
export PESITWIZARD_SECURITY_VAULT_ROLE_ID_FILE=/etc/pesitwizard/vault-role-id
export PESITWIZARD_SECURITY_VAULT_SECRET_ID_FILE=/etc/pesitwizard/vault-secret-id`
      } else {
        vars += `
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=approle
export PESITWIZARD_SECURITY_VAULT_ROLE_ID=${initVaultResult.value?.roleId || '<role-id>'}
export PESITWIZARD_SECURITY_VAULT_SECRET_ID=${initVaultResult.value?.secretId || '<secret-id>'}`
      }
    } else {
      if (useFileSecrets.value) {
        vars += `
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=token
export PESITWIZARD_SECURITY_VAULT_TOKEN_FILE=/etc/pesitwizard/vault-token`
      } else {
        vars += `
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=token
export PESITWIZARD_SECURITY_VAULT_TOKEN=${vaultTestToken.value || '<vault-token>'}`
      }
    }
  } else {
    vars += `\n
# AES-only mode (no Vault)
export PESITWIZARD_SECURITY_ENCRYPTION_MODE=AES`
  }
  
  if (useFileSecrets.value) {
    vars += `\n
# Create secret files (run as root):
# mkdir -p /etc/pesitwizard && chmod 700 /etc/pesitwizard
# echo '<value>' > /etc/pesitwizard/<secret-name>
# chmod 600 /etc/pesitwizard/*`
  }
  
  return vars
}

function copyEnvVars() {
  navigator.clipboard.writeText(getEnvVarsText())
  successMessage.value = 'Environment variables copied to clipboard'
  setTimeout(() => successMessage.value = null, 3000)
}

onMounted(() => {
  fetchStatus()
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

      <!-- Current Status (unified) -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold flex items-center gap-2">
            <Shield class="w-5 h-5" />
            Security Status
          </h2>
          <button @click="fetchStatus" class="px-3 py-1 text-sm border rounded hover:bg-gray-50 flex items-center gap-1">
            <RefreshCw class="w-3 h-3" />
            Refresh
          </button>
        </div>
        
        <div class="grid md:grid-cols-2 gap-4">
          <!-- AES Status -->
          <div class="p-4 rounded-lg border" :class="status?.aes?.usingFixedKey ? 'bg-green-50 border-green-200' : 'bg-yellow-50 border-yellow-200'">
            <div class="flex items-center gap-3">
              <Key :class="['w-6 h-6', status?.aes?.usingFixedKey ? 'text-green-500' : 'text-yellow-500']" />
              <div>
                <p class="font-medium">AES Encryption</p>
                <p class="text-sm" :class="status?.aes?.usingFixedKey ? 'text-green-600' : 'text-yellow-600'">
                  {{ status?.aes?.message || 'Loading...' }}
                </p>
              </div>
            </div>
          </div>

          <!-- Vault Status -->
          <div class="p-4 rounded-lg border" :class="status?.vault?.connected ? 'bg-green-50 border-green-200' : status?.vault?.enabled ? 'bg-yellow-50 border-yellow-200' : 'bg-gray-50 border-gray-200'">
            <div class="flex items-center gap-3">
              <Server :class="['w-6 h-6', status?.vault?.connected ? 'text-green-500' : status?.vault?.enabled ? 'text-yellow-500' : 'text-gray-400']" />
              <div>
                <p class="font-medium">HashiCorp Vault</p>
                <p class="text-sm" :class="status?.vault?.connected ? 'text-green-600' : status?.vault?.enabled ? 'text-yellow-600' : 'text-gray-500'">
                  {{ status?.vault?.enabled ? (status?.vault?.connected ? '✓ Connected' : status?.vault?.message) : 'Not configured (AES-only mode)' }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- Migration button when Vault is connected -->
        <div v-if="status?.vault?.connected" class="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div class="flex items-center justify-between">
            <div>
              <p class="font-medium text-blue-800">Migrate Secrets to Vault</p>
              <p class="text-sm text-blue-600">Move server passwords and connector credentials to Vault</p>
            </div>
            <button 
              @click="migrateSecretsToVault" 
              :disabled="migratingSecrets"
              class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
            >
              <RefreshCw :class="['w-4 h-4', migratingSecrets && 'animate-spin']" />
              {{ migratingSecrets ? 'Migrating...' : 'Migrate Now' }}
            </button>
          </div>
          <div v-if="migrationResult" class="mt-3 p-2 rounded" :class="migrationResult.success ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
            {{ migrationResult.message }}
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
          <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 text-sm cursor-pointer">
              <input type="checkbox" v-model="useFileSecrets" class="rounded" />
              <span :class="useFileSecrets ? 'text-green-700 font-medium' : 'text-gray-600'">
                Use *_FILE (recommended)
              </span>
            </label>
            <button @click="copyEnvVars" class="px-3 py-1 text-sm bg-gray-100 border rounded hover:bg-gray-200 flex items-center gap-1">
              <Copy class="w-4 h-4" />
              Copy
            </button>
          </div>
        </div>
        
        <p class="text-sm text-gray-600 mb-3">
          Variables based on your current configuration. AES master key is always required.
          {{ status?.vault?.enabled ? 'Vault is enabled for secrets storage.' : 'Vault not configured (AES-only mode).' }}
        </p>
        
        <pre class="bg-gray-900 text-green-400 p-4 rounded-lg text-xs font-mono overflow-x-auto whitespace-pre-wrap">{{ getEnvVarsText() }}</pre>
        
        <!-- Architecture explanation -->
        <div class="mt-4 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm">
          <p class="font-medium text-blue-800 mb-2">Architecture</p>
          <p class="text-blue-700">
            <strong>AES</strong> is always required to encrypt Vault credentials stored in the database (bootstrap).
            <strong>Vault</strong> (optional) provides external secrets storage for application data (passwords, tokens).
          </p>
        </div>
      </div>
    </template>
  </div>
</template>
