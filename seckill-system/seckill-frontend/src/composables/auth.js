export const AUTH_CHANGED = 'auth-changed'

export function notifyAuthChanged() {
  window.dispatchEvent(new Event(AUTH_CHANGED))
}

export function readAuthFromStorage() {
  return {
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || ''
  }
}
