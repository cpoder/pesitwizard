import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { onUnmounted, ref } from 'vue'

export interface TransferProgress {
  transferId: string
  bytesTransferred: number
  fileSize: number
  percentage: number
  lastSyncPoint: number
  status: string
  errorMessage?: string
}

export function useTransferProgress() {
  const progress = ref<TransferProgress | null>(null)
  const connected = ref(false)
  const error = ref<string | null>(null)
  
  let stompClient: Client | null = null
  let subscription: any = null

  const connect = () => {
    if (stompClient?.connected) {
      return
    }

    const baseUrl = import.meta.env.VITE_API_URL || ''
    
    stompClient = new Client({
      webSocketFactory: () => new SockJS(baseUrl + '/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        connected.value = true
        error.value = null
        console.log('WebSocket connected')
      },
      onDisconnect: () => {
        connected.value = false
        console.log('WebSocket disconnected')
      },
      onStompError: (frame) => {
        error.value = frame.headers['message'] || 'WebSocket error'
        console.error('STOMP error:', frame)
      }
    })

    stompClient.activate()
  }

  const subscribeToTransfer = (transferId: string) => {
    if (!stompClient?.connected) {
      connect()
      // Wait for connection then subscribe
      const checkConnection = setInterval(() => {
        if (stompClient?.connected) {
          clearInterval(checkConnection)
          doSubscribe(transferId)
        }
      }, 100)
      // Timeout after 5 seconds
      setTimeout(() => clearInterval(checkConnection), 5000)
    } else {
      doSubscribe(transferId)
    }
  }

  const doSubscribe = (transferId: string) => {
    if (subscription) {
      subscription.unsubscribe()
    }

    const destination = `/topic/transfer/${transferId}/progress`
    console.log('Subscribing to:', destination)
    
    subscription = stompClient?.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body) as TransferProgress
        progress.value = data
        console.log('Progress update:', data.percentage + '%', data.bytesTransferred, '/', data.fileSize)
      } catch (e) {
        console.error('Failed to parse progress message:', e)
      }
    })
  }

  const unsubscribe = () => {
    if (subscription) {
      subscription.unsubscribe()
      subscription = null
    }
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
