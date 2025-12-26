<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { 
  Shield, 
  Key, 
  Lock, 
  Plus, 
  Download, 
  RefreshCw, 
  Trash2,
  CheckCircle,
  AlertCircle,
  FileKey,
  Building2,
  Calendar,
  Copy,
  Check
} from 'lucide-vue-next'
import api from '@/api'

interface CertificateStore {
  id: number
  name: string
  description?: string
  storeType: 'KEYSTORE' | 'TRUSTSTORE'
  format: 'PKCS12' | 'JKS' | 'PEM'
  purpose: 'SERVER' | 'CLIENT' | 'CA' | 'PARTNER'
  partnerId?: string
  subjectDn?: string
  issuerDn?: string
  serialNumber?: string
  expiresAt?: string
  active: boolean
  isDefault: boolean
}

interface SignedCertificate {
  certificatePem: string
  subjectDn: string
  issuerDn: string
  serialNumber: string
  expiresAt: string
}

// State
const certificates = ref<CertificateStore[]>([])
const loading = ref(true)
const caInitialized = ref(false)
const caStore = ref<CertificateStore | null>(null)

// Modals
const showGenerateModal = ref(false)
const showSignModal = ref(false)
const showDownloadModal = ref(false)

// Form data
const generateForm = ref({
  partnerId: '',
  commonName: '',
  purpose: 'CLIENT' as 'CLIENT' | 'SERVER',
  validityDays: 365
})

const signForm = ref({
  csrPem: '',
  purpose: 'CLIENT' as 'CLIENT' | 'SERVER',
  validityDays: 365,
  partnerId: ''
})

const signedCertificate = ref<SignedCertificate | null>(null)
const caCertPem = ref('')

// Actions state
const saving = ref(false)
const error = ref('')
const success = ref('')
const copied = ref(false)

// Computed
const keystores = computed(() => certificates.value.filter(c => c.storeType === 'KEYSTORE'))
const truststores = computed(() => certificates.value.filter(c => c.storeType === 'TRUSTSTORE'))

onMounted(async () => {
  await loadCertificates()
  await checkCaStatus()
})

async function loadCertificates() {
  loading.value = true
  try {
    const response = await api.get('/certificates')
    certificates.value = response.data || []
  } catch (e) {
    console.error('Failed to load certificates:', e)
  } finally {
    loading.value = false
  }
}

async function checkCaStatus() {
  try {
    const response = await api.get('/certificates/ca/certificate')
    caCertPem.value = response.data
    caInitialized.value = true
    caStore.value = certificates.value.find(c => c.purpose === 'CA' && c.storeType === 'KEYSTORE') || null
  } catch (e) {
    caInitialized.value = false
  }
}

async function initializeCa() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const response = await api.post('/certificates/ca/initialize')
    caStore.value = response.data
    caInitialized.value = true
    success.value = 'CA initialized successfully!'
    await loadCertificates()
    await checkCaStatus()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to initialize CA'
  } finally {
    saving.value = false
  }
}

async function generatePartnerCertificate() {
  saving.value = true
  error.value = ''
  try {
    await api.post(
      `/certificates/ca/partner/${generateForm.value.partnerId}/generate`,
      null,
      {
        params: {
          commonName: generateForm.value.commonName,
          purpose: generateForm.value.purpose,
          validityDays: generateForm.value.validityDays
        }
      }
    )
    success.value = `Certificate generated for ${generateForm.value.partnerId}`
    showGenerateModal.value = false
    generateForm.value = { partnerId: '', commonName: '', purpose: 'CLIENT', validityDays: 365 }
    await loadCertificates()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to generate certificate'
  } finally {
    saving.value = false
  }
}

async function signCsr() {
  saving.value = true
  error.value = ''
  try {
    const response = await api.post('/certificates/ca/sign', null, {
      params: {
        csrPem: signForm.value.csrPem,
        purpose: signForm.value.purpose,
        validityDays: signForm.value.validityDays,
        partnerId: signForm.value.partnerId || undefined
      }
    })
    signedCertificate.value = response.data
    success.value = 'CSR signed successfully!'
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to sign CSR'
  } finally {
    saving.value = false
  }
}

async function downloadCaCert() {
  try {
    const response = await api.get('/certificates/ca/certificate')
    caCertPem.value = response.data
    showDownloadModal.value = true
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to download CA certificate'
  }
}

async function deleteCertificate(cert: CertificateStore) {
  if (!confirm(`Delete certificate "${cert.name}"?`)) return
  try {
    await api.delete(`/certificates/${cert.id}`)
    await loadCertificates()
    success.value = 'Certificate deleted'
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to delete certificate'
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function downloadAsFile(content: string, filename: string) {
  const blob = new Blob([content], { type: 'application/x-pem-file' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function formatDate(dateStr?: string) {
  if (!dateStr) return 'N/A'
  return new Date(dateStr).toLocaleDateString('fr-FR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

function isExpiringSoon(dateStr?: string) {
  if (!dateStr) return false
  const expiry = new Date(dateStr)
  const thirtyDays = 30 * 24 * 60 * 60 * 1000
  return expiry.getTime() - Date.now() < thirtyDays
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Certificates & mTLS</h1>
      <button @click="loadCertificates" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
        <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        Refresh
      </button>
    </div>

    <!-- Success/Error Messages -->
    <div v-if="success" class="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm flex items-center gap-2">
      <CheckCircle class="h-4 w-4" />
      {{ success }}
    </div>
    <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm flex items-center gap-2">
      <AlertCircle class="h-4 w-4" />
      {{ error }}
    </div>

    <!-- CA Status Card -->
    <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div :class="['p-3 rounded-lg', caInitialized ? 'bg-green-100' : 'bg-yellow-100']">
            <Shield :class="['h-8 w-8', caInitialized ? 'text-green-600' : 'text-yellow-600']" />
          </div>
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Certificate Authority (CA)</h2>
            <p v-if="caInitialized" class="text-green-600 flex items-center gap-1">
              <CheckCircle class="h-4 w-4" /> CA is initialized and ready
            </p>
            <p v-else class="text-yellow-600 flex items-center gap-1">
              <AlertCircle class="h-4 w-4" /> CA not initialized - mTLS unavailable
            </p>
            <p v-if="caStore" class="text-sm text-gray-500 mt-1">
              {{ caStore.subjectDn }}
            </p>
          </div>
        </div>
        
        <div class="flex gap-2">
          <button 
            v-if="!caInitialized"
            @click="initializeCa" 
            class="btn btn-primary flex items-center gap-2"
            :disabled="saving"
          >
            <Key class="h-4 w-4" />
            {{ saving ? 'Initializing...' : 'Initialize CA' }}
          </button>
          <template v-else>
            <button @click="downloadCaCert" class="btn btn-secondary flex items-center gap-2">
              <Download class="h-4 w-4" />
              Download CA Cert
            </button>
            <button @click="showGenerateModal = true" class="btn btn-primary flex items-center gap-2">
              <Plus class="h-4 w-4" />
              Generate Partner Cert
            </button>
          </template>
        </div>
      </div>
    </div>

    <!-- Actions Grid -->
    <div v-if="caInitialized" class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
      <div class="card hover:shadow-md cursor-pointer" @click="showGenerateModal = true">
        <div class="flex items-center gap-3">
          <div class="p-2 bg-blue-100 rounded-lg">
            <Building2 class="h-5 w-5 text-blue-600" />
          </div>
          <div>
            <h3 class="font-medium text-gray-900">Generate Partner Certificate</h3>
            <p class="text-sm text-gray-500">Create a new signed certificate for a partner</p>
          </div>
        </div>
      </div>
      
      <div class="card hover:shadow-md cursor-pointer" @click="showSignModal = true">
        <div class="flex items-center gap-3">
          <div class="p-2 bg-purple-100 rounded-lg">
            <FileKey class="h-5 w-5 text-purple-600" />
          </div>
          <div>
            <h3 class="font-medium text-gray-900">Sign CSR</h3>
            <p class="text-sm text-gray-500">Sign a Certificate Signing Request</p>
          </div>
        </div>
      </div>
      
      <div class="card hover:shadow-md cursor-pointer" @click="downloadCaCert">
        <div class="flex items-center gap-3">
          <div class="p-2 bg-green-100 rounded-lg">
            <Download class="h-5 w-5 text-green-600" />
          </div>
          <div>
            <h3 class="font-medium text-gray-900">Download CA Certificate</h3>
            <p class="text-sm text-gray-500">Get CA cert for client truststore</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Certificates List -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Keystores -->
      <div>
        <h3 class="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
          <Key class="h-5 w-5" />
          Keystores ({{ keystores.length }})
        </h3>
        
        <div v-if="keystores.length === 0" class="card text-center py-8">
          <Lock class="h-12 w-12 mx-auto mb-3 text-gray-400" />
          <p class="text-gray-500">No keystores configured</p>
        </div>
        
        <div v-else class="space-y-3">
          <div 
            v-for="cert in keystores" 
            :key="cert.id"
            class="card hover:shadow-md"
          >
            <div class="flex items-start justify-between">
              <div class="flex items-start gap-3">
                <div :class="['p-2 rounded-lg', cert.active ? 'bg-green-100' : 'bg-gray-100']">
                  <Key :class="['h-5 w-5', cert.active ? 'text-green-600' : 'text-gray-400']" />
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <h4 class="font-medium text-gray-900">{{ cert.name }}</h4>
                    <span v-if="cert.purpose === 'CA'" class="badge badge-info">CA</span>
                    <span v-else-if="cert.purpose === 'SERVER'" class="badge badge-success">Server</span>
                    <span v-else-if="cert.purpose === 'CLIENT'" class="badge badge-warning">Client</span>
                    <span v-else class="badge">{{ cert.purpose }}</span>
                  </div>
                  <p v-if="cert.subjectDn" class="text-sm text-gray-600 truncate max-w-xs">{{ cert.subjectDn }}</p>
                  <p v-if="cert.partnerId" class="text-xs text-gray-500">Partner: {{ cert.partnerId }}</p>
                  <div v-if="cert.expiresAt" class="flex items-center gap-1 mt-1">
                    <Calendar class="h-3 w-3 text-gray-400" />
                    <span :class="['text-xs', isExpiringSoon(cert.expiresAt) ? 'text-red-600' : 'text-gray-500']">
                      Expires: {{ formatDate(cert.expiresAt) }}
                    </span>
                  </div>
                </div>
              </div>
              
              <button 
                v-if="cert.purpose !== 'CA'"
                @click="deleteCertificate(cert)"
                class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                title="Delete"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Truststores -->
      <div>
        <h3 class="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
          <Shield class="h-5 w-5" />
          Truststores ({{ truststores.length }})
        </h3>
        
        <div v-if="truststores.length === 0" class="card text-center py-8">
          <Shield class="h-12 w-12 mx-auto mb-3 text-gray-400" />
          <p class="text-gray-500">No truststores configured</p>
        </div>
        
        <div v-else class="space-y-3">
          <div 
            v-for="cert in truststores" 
            :key="cert.id"
            class="card hover:shadow-md"
          >
            <div class="flex items-start justify-between">
              <div class="flex items-start gap-3">
                <div :class="['p-2 rounded-lg', cert.active ? 'bg-green-100' : 'bg-gray-100']">
                  <Shield :class="['h-5 w-5', cert.active ? 'text-green-600' : 'text-gray-400']" />
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <h4 class="font-medium text-gray-900">{{ cert.name }}</h4>
                    <span v-if="cert.isDefault" class="badge badge-info">Default</span>
                  </div>
                  <p v-if="cert.description" class="text-sm text-gray-600">{{ cert.description }}</p>
                </div>
              </div>
              
              <button 
                @click="deleteCertificate(cert)"
                class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                title="Delete"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Generate Partner Certificate Modal -->
    <div v-if="showGenerateModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showGenerateModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6">
          <h2 class="text-xl font-bold text-gray-900 mb-6 flex items-center gap-2">
            <Building2 class="h-6 w-6 text-blue-600" />
            Generate Partner Certificate
          </h2>

          <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <form @submit.prevent="generatePartnerCertificate" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Partner ID *</label>
              <input 
                v-model="generateForm.partnerId" 
                type="text" 
                class="input" 
                required 
                placeholder="e.g., BANQUE_XYZ"
              />
              <p class="text-xs text-gray-500 mt-1">Unique identifier for the partner</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Common Name (CN) *</label>
              <input 
                v-model="generateForm.commonName" 
                type="text" 
                class="input" 
                required 
                placeholder="e.g., banque-xyz.example.com"
              />
              <p class="text-xs text-gray-500 mt-1">FQDN or identifier for the certificate</p>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Purpose</label>
                <select v-model="generateForm.purpose" class="input">
                  <option value="CLIENT">Client (mTLS)</option>
                  <option value="SERVER">Server</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Validity (days)</label>
                <input 
                  v-model.number="generateForm.validityDays" 
                  type="number" 
                  class="input" 
                  min="1" 
                  max="3650"
                />
              </div>
            </div>

            <div class="flex justify-end gap-3 pt-4">
              <button type="button" @click="showGenerateModal = false" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                {{ saving ? 'Generating...' : 'Generate Certificate' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Sign CSR Modal -->
    <div v-if="showSignModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showSignModal = false; signedCertificate = null" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-2xl w-full p-6">
          <h2 class="text-xl font-bold text-gray-900 mb-6 flex items-center gap-2">
            <FileKey class="h-6 w-6 text-purple-600" />
            Sign Certificate Signing Request (CSR)
          </h2>

          <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {{ error }}
          </div>

          <!-- CSR Input Form -->
          <form v-if="!signedCertificate" @submit.prevent="signCsr" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">CSR (PEM format) *</label>
              <textarea 
                v-model="signForm.csrPem" 
                class="input font-mono text-sm h-40" 
                required 
                placeholder="-----BEGIN CERTIFICATE REQUEST-----
...
-----END CERTIFICATE REQUEST-----"
              />
              <p class="text-xs text-gray-500 mt-1">Paste the CSR provided by the client</p>
            </div>

            <div class="grid grid-cols-3 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Purpose</label>
                <select v-model="signForm.purpose" class="input">
                  <option value="CLIENT">Client (mTLS)</option>
                  <option value="SERVER">Server</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Validity (days)</label>
                <input v-model.number="signForm.validityDays" type="number" class="input" min="1" max="3650" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Partner ID</label>
                <input v-model="signForm.partnerId" type="text" class="input" placeholder="Optional" />
              </div>
            </div>

            <div class="flex justify-end gap-3 pt-4">
              <button type="button" @click="showSignModal = false" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                {{ saving ? 'Signing...' : 'Sign CSR' }}
              </button>
            </div>
          </form>

          <!-- Signed Certificate Result -->
          <div v-else class="space-y-4">
            <div class="p-4 bg-green-50 border border-green-200 rounded-lg">
              <div class="flex items-center gap-2 text-green-700 font-medium mb-2">
                <CheckCircle class="h-5 w-5" />
                Certificate Signed Successfully
              </div>
              <div class="text-sm text-green-600 space-y-1">
                <p><strong>Subject:</strong> {{ signedCertificate.subjectDn }}</p>
                <p><strong>Issuer:</strong> {{ signedCertificate.issuerDn }}</p>
                <p><strong>Serial:</strong> {{ signedCertificate.serialNumber }}</p>
                <p><strong>Expires:</strong> {{ formatDate(signedCertificate.expiresAt) }}</p>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Signed Certificate (PEM)</label>
              <textarea 
                :value="signedCertificate.certificatePem" 
                class="input font-mono text-sm h-40" 
                readonly
              />
            </div>

            <div class="flex justify-end gap-3">
              <button 
                @click="copyToClipboard(signedCertificate.certificatePem)" 
                class="btn btn-secondary flex items-center gap-2"
              >
                <component :is="copied ? Check : Copy" class="h-4 w-4" />
                {{ copied ? 'Copied!' : 'Copy to Clipboard' }}
              </button>
              <button 
                @click="downloadAsFile(signedCertificate.certificatePem, 'signed-certificate.pem')" 
                class="btn btn-secondary flex items-center gap-2"
              >
                <Download class="h-4 w-4" />
                Download .pem
              </button>
              <button @click="showSignModal = false; signedCertificate = null" class="btn btn-primary">
                Done
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Download CA Certificate Modal -->
    <div v-if="showDownloadModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showDownloadModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-2xl w-full p-6">
          <h2 class="text-xl font-bold text-gray-900 mb-6 flex items-center gap-2">
            <Shield class="h-6 w-6 text-green-600" />
            CA Certificate
          </h2>

          <div class="space-y-4">
            <div class="p-4 bg-blue-50 border border-blue-200 rounded-lg text-blue-700 text-sm">
              <strong>Instructions:</strong> Clients must import this CA certificate into their truststore 
              to trust certificates signed by this CA.
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">CA Certificate (PEM)</label>
              <textarea 
                :value="caCertPem" 
                class="input font-mono text-sm h-48" 
                readonly
              />
            </div>

            <div class="flex justify-end gap-3">
              <button 
                @click="copyToClipboard(caCertPem)" 
                class="btn btn-secondary flex items-center gap-2"
              >
                <component :is="copied ? Check : Copy" class="h-4 w-4" />
                {{ copied ? 'Copied!' : 'Copy to Clipboard' }}
              </button>
              <button 
                @click="downloadAsFile(caCertPem, 'pesit-ca.pem')" 
                class="btn btn-secondary flex items-center gap-2"
              >
                <Download class="h-4 w-4" />
                Download pesit-ca.pem
              </button>
              <button @click="showDownloadModal = false" class="btn btn-primary">
                Done
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
