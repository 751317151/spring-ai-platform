import axios from 'axios'

function authHeader() {
  const user = localStorage.getItem('api-user') || 'ai-admin'
  const pass = localStorage.getItem('api-pass') || 'ai-admin-pass'
  return 'Basic ' + btoa(`${user}:${pass}`)
}

const client = axios.create({
  baseURL: '/',
  timeout: 10000
})

client.interceptors.request.use((config) => {
  config.headers.Authorization = authHeader()
  return config
})

export default client
