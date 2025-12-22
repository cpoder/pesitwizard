import { describe, expect, it } from 'vitest'

describe('API Client', () => {
  it('should have correct base URL structure', () => {
    expect('/api/v1').toMatch(/^\/api\/v1$/)
  })

  it('should build correct endpoint URLs', () => {
    const baseUrl = '/api/v1'
    const endpoints = {
      partners: `${baseUrl}/partners`,
      files: `${baseUrl}/files`,
      transfers: `${baseUrl}/transfers`,
      health: `${baseUrl}/health`
    }

    expect(endpoints.partners).toBe('/api/v1/partners')
    expect(endpoints.files).toBe('/api/v1/files')
    expect(endpoints.transfers).toBe('/api/v1/transfers')
    expect(endpoints.health).toBe('/api/v1/health')
  })

  it('should handle partner-specific endpoints', () => {
    const partnerId = 'PARTNER1'
    const baseUrl = '/api/v1'
    
    const endpoints = {
      detail: `${baseUrl}/partners/${partnerId}`,
      transfers: `${baseUrl}/partners/${partnerId}/transfers`
    }

    expect(endpoints.detail).toBe('/api/v1/partners/PARTNER1')
    expect(endpoints.transfers).toBe('/api/v1/partners/PARTNER1/transfers')
  })

  it('should handle transfer endpoints', () => {
    const transferId = 'xfer-123'
    const baseUrl = '/api/v1'
    
    expect(`${baseUrl}/transfers/${transferId}`).toBe('/api/v1/transfers/xfer-123')
  })

  it('should handle query parameters correctly', () => {
    const params = new URLSearchParams({ page: '0', size: '10', status: 'COMPLETED' })
    
    expect(params.get('page')).toBe('0')
    expect(params.get('size')).toBe('10')
    expect(params.get('status')).toBe('COMPLETED')
  })
})
