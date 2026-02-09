import { ref } from 'vue'
import axios from 'axios'

const authRequired = ref(false)
const authMode = ref<'none' | 'apikey' | 'oauth2'>('none')
const authenticated = ref(false)
const apiKey = ref('')
const initialized = ref(false)

/**
 * Authentication composable.
 * Checks backend auth status and manages API key storage.
 */
export function useAuth() {
  async function checkAuthStatus() {
    try {
      const { data } = await axios.get('/api/v1/auth/status')
      authRequired.value = data.authRequired
      authMode.value = data.mode
      if (!data.authRequired) {
        authenticated.value = true
      } else {
        // Check if we have a stored API key
        const stored = sessionStorage.getItem('pesitwizard-api-key')
        if (stored) {
          apiKey.value = stored
          authenticated.value = true
        }
      }
    } catch {
      // If auth status endpoint fails, assume no auth required (backward compat)
      authRequired.value = false
      authenticated.value = true
    }
    initialized.value = true
  }

  async function login(key: string): Promise<boolean> {
    try {
      await axios.post('/api/v1/auth/login', { apiKey: key })
      apiKey.value = key
      authenticated.value = true
      sessionStorage.setItem('pesitwizard-api-key', key)
      return true
    } catch {
      return false
    }
  }

  function logout() {
    apiKey.value = ''
    authenticated.value = false
    sessionStorage.removeItem('pesitwizard-api-key')
  }

  return {
    authRequired,
    authMode,
    authenticated,
    apiKey,
    initialized,
    checkAuthStatus,
    login,
    logout,
  }
}
