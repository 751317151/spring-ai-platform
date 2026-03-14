import axios from 'axios'

const client = axios.create({
  timeout: 30000
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const data = response.data
    // Unwrap Result<T> envelope
    if (data && typeof data.code === 'number') {
      if (data.code !== 200) {
        return Promise.reject(new Error(data.message || 'Request failed'))
      }
      return data.data
    }
    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client

// Helper to get the auth token for raw fetch calls (SSE)
export function getAuthToken(): string | null {
  return localStorage.getItem('auth_token')
}

// Helper to build headers for raw fetch calls
export function buildHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  const token = getAuthToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  if (extra) {
    Object.assign(headers, extra)
  }
  return headers
}
