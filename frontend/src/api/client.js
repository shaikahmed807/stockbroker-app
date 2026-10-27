import axios from 'axios';

const TOKEN_KEY = 'sb_token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

// VITE_API_URL is empty by default: requests go to /api on the same origin
// (Vite proxy in dev, nginx in Docker).
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let onUnauthorized = () => {};
export const setUnauthorizedHandler = (fn) => {
  onUnauthorized = fn;
};

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const url = err.config?.url || '';
    if (err.response?.status === 401 && !url.includes('/api/auth/login')) {
      onUnauthorized();
    }
    return Promise.reject(err);
  }
);

/** Turns an axios error into a message the user can act on. */
export function errorMessage(err, fallback = 'Something went wrong. Please try again.') {
  if (!err.response) return 'Cannot reach the server. Check that the backend is running on port 8080.';
  const data = err.response.data;
  if (data?.fieldErrors) return Object.values(data.fieldErrors)[0];
  return data?.message || fallback;
}

export const authApi = {
  register: (body) => api.post('/api/auth/register', body).then((r) => r.data),
  login: (body) => api.post('/api/auth/login', body).then((r) => r.data),
  logout: () => api.post('/api/auth/logout').then((r) => r.data),
  me: () => api.get('/api/auth/me').then((r) => r.data),
};

export const stockApi = {
  list: () => api.get('/api/stocks').then((r) => r.data),
  quote: (s) => api.get(`/api/stocks/${s}`).then((r) => r.data),
  history: (s, limit = 300) => api.get(`/api/stocks/${s}/history`, { params: { limit } }).then((r) => r.data),
  analysis: (s) => api.get(`/api/stocks/${s}/analysis`).then((r) => r.data),
};

export const subscriptionApi = {
  plans: () => api.get('/api/subscriptions/plans').then((r) => r.data),
  status: () => api.get('/api/subscriptions/me').then((r) => r.data),
  history: () => api.get('/api/subscriptions/history').then((r) => r.data),
  subscribe: (plan) => api.post('/api/subscriptions/subscribe', { plan }).then((r) => r.data),
  cancel: () => api.post('/api/subscriptions/cancel').then((r) => r.data),
};

export default api;
