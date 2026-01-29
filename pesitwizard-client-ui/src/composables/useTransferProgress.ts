import { Client, type IMessage } from '@stomp/stompjs'
import { onUnmounted, ref } from 'vue'

// Backend TransferEvent structure
interface TransferEventPayload {
  transferId: string
  type: 'PROGRESS' | 'STATE_CHANGE' | 'SYNC_POINT' | 'ERROR' | 'COMPLETED' | 'CANCELLED'
  timestamp: string
  bytesTransferred: number
  totalBytes: number
  percentComplete: number
  syncPointNumber?: number
  syncPointBytePosition?: number
  errorMessage?: string
  diagnosticCode?: string
}

// Normalized progress interface for UI consumption
export interface TransferProgress {
  transferId: string
  bytesTransferred: number
  fileSize: number
  percentage: number
  lastSyncPoint: number
  status: string
  errorMessage?: string
  bytesTransferredFormatted?: string
  fileSizeFormatted?: string
}

// Convert backend event to frontend-friendly format
function normalizeEvent(event: TransferEventPayload): TransferProgress {
  // Map backend EventType to frontend status
  const statusMap: Record<string, string> = {
    'PROGRESS': 'IN_PROGRESS',
    'STATE_CHANGE': 'IN_PROGRESS',
    'SYNC_POINT': 'IN_PROGRESS',
    'ERROR': 'FAILED',
    'COMPLETED': 'COMPLETED',
    'CANCELLED': 'CANCELLED'
  }

  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  return {
    transferId: event.transferId,
    bytesTransferred: event.bytesTransferred || 0,
    fileSize: event.totalBytes || 0,
    percentage: event.percentComplete || 0,
    lastSyncPoint: event.syncPointNumber || 0,
    status: statusMap[event.type] || 'UNKNOWN',
    errorMessage: event.errorMessage,
    bytesTransferredFormatted: formatBytes(event.bytesTransferred || 0),
    fileSizeFormatted: formatBytes(event.totalBytes || 0)
  }
}

export function useTransferProgress() {
  const progress = ref<TransferProgress | null>(null)
  const connected = ref(false)
  const error = ref<string | null>(null)
  
  let stompClient: Client | null = null
  let subscription: { unsubscribe: () => void } | null = null
  let pendingTransferId: string | null = null

  const connect = () => {
    if (stompClient?.connected) {
      console.log('[WS] Already connected')
      return
    }

    // Use native WebSocket endpoint (not SockJS)
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws-raw`
    
    console.log('[WS] Connecting to:', wsUrl)
    
    stompClient = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        console.log('[STOMP]', str)
      },
      onConnect: () => {
        connected.value = true
        error.value = null
        console.log('[WS] Connected!')
        // If there's a pending subscription, do it now
        if (pendingTransferId) {
          doSubscribe(pendingTransferId)
          pendingTransferId = null
        }
      },
      onDisconnect: () => {
        connected.value = false
        console.log('[WS] Disconnected')
      },
      onStompError: (frame) => {
        error.value = frame.headers['message'] || 'WebSocket error'
        console.error('[WS] STOMP error:', frame)
      },
      onWebSocketError: (event) => {
        console.error('[WS] WebSocket error:', event)
        error.value = 'WebSocket connection error'
      },
      onWebSocketClose: (event) => {
        console.log('[WS] WebSocket closed:', event.code, event.reason)
      }
    })

    stompClient.activate()
  }

  const subscribeToTransfer = (transferId: string) => {
    console.log('[WS] subscribeToTransfer called for:', transferId)
    
    if (!stompClient) {
      pendingTransferId = transferId
      connect()
      return
    }
    
    if (!stompClient.connected) {
      console.log('[WS] Not connected yet, will subscribe when connected')
      pendingTransferId = transferId
      return
    }
    
    doSubscribe(transferId)
  }

  const doSubscribe = (transferId: string) => {
    if (subscription) {
      subscription.unsubscribe()
    }

    const destination = `/topic/transfer/${transferId}/progress`
    console.log('[WS] Subscribing to:', destination)

    subscription = stompClient?.subscribe(destination, (message: IMessage) => {
      try {
        const rawEvent = JSON.parse(message.body) as TransferEventPayload
        const data = normalizeEvent(rawEvent)
        progress.value = data
        console.log('[WS] Progress:', data.bytesTransferredFormatted, '/', data.fileSizeFormatted,
          `(${data.percentage}%) [${rawEvent.type}]`)
      } catch (e) {
        console.error('[WS] Failed to parse progress message:', e)
      }
    }) || null
  }

  const unsubscribe = () => {
    if (subscription) {
      subscription.unsubscribe()
      subscription = null
    }
    pendingTransferId = null
  }

  const disconnect = () => {
    unsubscribe()
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
  }

  const reset = () => {
    progress.value = null
    error.value = null
    pendingTransferId = null
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    progress,
    connected,
    error,
    connect,
    subscribeToTransfer,
    unsubscribe,
    disconnect,
    reset
  }
}
