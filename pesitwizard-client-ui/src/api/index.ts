import { createApiClient, apiKeyAuth } from '@pesitwizard/frontend-common/api'

const api = createApiClient({
  baseURL: '/api/v1',
  authStrategy: apiKeyAuth('pesitwizard-api-key'),
  onAuthError: () => {
    sessionStorage.removeItem('pesitwizard-api-key')
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  },
})

export default api
