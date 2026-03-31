import { ref } from 'vue'
import { ensureValidAccessToken } from '@/api/client'

export interface UseSSEOptions {
  url: string
  body: unknown
  headers?: Record<string, string>
  onChunk?: (chunk: string) => void
  onDone?: () => void
  onError?: (error: Error) => void
}

export function useSSE() {
  const response = ref('')
  const isDone = ref(false)
  const isStreaming = ref(false)
  let controller: AbortController | null = null

  async function connect(options: UseSSEOptions) {
    response.value = ''
    isDone.value = false
    isStreaming.value = true
    controller = new AbortController()

    const authHeaders: Record<string, string> = {}
    const token = await ensureValidAccessToken()
    if (token) {
      authHeaders['Authorization'] = `Bearer ${token}`
    }

    try {
      const res = await fetch(options.url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...authHeaders,
          ...(options.headers || {})
        },
        body: JSON.stringify(options.body),
        signal: controller.signal
      })

      if (!res.ok || !res.body) {
        throw new Error(`HTTP ${res.status}`)
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed || !trimmed.startsWith('data:')) continue

          const jsonStr = trimmed.slice(5).trim()
          if (!jsonStr || jsonStr === '[DONE]') continue

          try {
            const data = JSON.parse(jsonStr)
            if (data.chunk) {
              response.value += data.chunk
              options.onChunk?.(data.chunk)
            }
            if (data.done) {
              isDone.value = true
              options.onDone?.()
            }
          } catch {
            // non-JSON SSE line, append as raw text
            response.value += jsonStr
          }
        }
      }

      if (!isDone.value) {
        isDone.value = true
        options.onDone?.()
      }
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        options.onError?.(err as Error)
      }
    } finally {
      isStreaming.value = false
      controller = null
    }
  }

  function abort() {
    controller?.abort()
    isStreaming.value = false
  }

  return { response, isDone, isStreaming, connect, abort }
}
