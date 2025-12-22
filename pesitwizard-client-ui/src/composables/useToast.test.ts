import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useToast } from './useToast'

describe('useToast Composable', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should add a toast with show()', () => {
    const { toasts, show } = useToast()
    
    show('Test message', 'success')
    
    expect(toasts.value.length).toBeGreaterThan(0)
  })

  it('should add success toast', () => {
    const { toasts, success } = useToast()
    
    success('Success message')
    
    const lastToast = toasts.value[toasts.value.length - 1]!
    expect(lastToast.message).toBe('Success message')
    expect(lastToast.type).toBe('success')
  })

  it('should add error toast', () => {
    const { toasts, error } = useToast()
    
    error('Error message')
    
    const lastToast = toasts.value[toasts.value.length - 1]!
    expect(lastToast.message).toBe('Error message')
    expect(lastToast.type).toBe('error')
  })

  it('should add warning toast', () => {
    const { toasts, warning } = useToast()
    
    warning('Warning message')
    
    const lastToast = toasts.value[toasts.value.length - 1]!
    expect(lastToast.message).toBe('Warning message')
    expect(lastToast.type).toBe('warning')
  })

  it('should add info toast', () => {
    const { toasts, info } = useToast()
    
    info('Info message')
    
    const lastToast = toasts.value[toasts.value.length - 1]!
    expect(lastToast.message).toBe('Info message')
    expect(lastToast.type).toBe('info')
  })

  it('should remove toast after duration', () => {
    const { toasts, show } = useToast()
    const initialLength = toasts.value.length
    
    show('Test message', 'success', 3000)
    expect(toasts.value.length).toBe(initialLength + 1)
    
    vi.advanceTimersByTime(3000)
    
    expect(toasts.value.length).toBe(initialLength)
  })

  it('should allow manual toast removal', () => {
    const { toasts, show, remove } = useToast()
    
    const id = show('Test message', 'success', 0) // 0 = no auto-remove
    const lengthAfterAdd = toasts.value.length
    
    remove(id)
    
    expect(toasts.value.length).toBe(lengthAfterAdd - 1)
  })

  it('should return toast ID from show()', () => {
    const { show } = useToast()
    
    const id = show('Message', 'info')
    
    expect(typeof id).toBe('number')
  })
})
