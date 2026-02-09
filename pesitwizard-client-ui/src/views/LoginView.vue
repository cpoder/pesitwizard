<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { login, authMode } = useAuth()

const apiKeyInput = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    const success = await login(apiKeyInput.value)
    if (success) {
      router.push('/')
    } else {
      error.value = 'Invalid API key'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <h1>PeSIT Wizard</h1>
      <p class="subtitle">Authentication required</p>

      <form v-if="authMode === 'apikey'" @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="apiKey">API Key</label>
          <input
            id="apiKey"
            v-model="apiKeyInput"
            type="password"
            placeholder="Enter your API key"
            autocomplete="off"
            :disabled="loading"
          />
        </div>
        <p v-if="error" class="error" role="alert" aria-live="polite">{{ error }}</p>
        <button type="submit" :disabled="loading || !apiKeyInput">
          {{ loading ? 'Authenticating...' : 'Login' }}
        </button>
      </form>

      <div v-else-if="authMode === 'oauth2'" class="oauth-info">
        <p>This instance requires OAuth2 authentication.</p>
        <p>Please configure your identity provider.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}

.login-card {
  background: white;
  border-radius: 8px;
  padding: 2rem;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
  color: #333;
}

.subtitle {
  color: #666;
  margin: 0 0 1.5rem;
  font-size: 0.9rem;
}

.form-group {
  margin-bottom: 1rem;
}

label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #333;
}

input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  box-sizing: border-box;
}

input:focus {
  outline: none;
  border-color: #1976d2;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.2);
}

button {
  width: 100%;
  padding: 0.75rem;
  background: #1976d2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}

button:hover:not(:disabled) {
  background: #1565c0;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  color: #d32f2f;
  font-size: 0.875rem;
  margin: 0.5rem 0;
}

.oauth-info {
  text-align: center;
  color: #666;
}
</style>
