import { describe, expect, it, vi } from 'vitest';
import api, { authApi, errorMessage, setUnauthorizedHandler, stockApi, subscriptionApi, tokenStore } from '../api/client';

const requestInterceptor = () => api.interceptors.request.handlers[0].fulfilled;
const responseErrorInterceptor = () => api.interceptors.response.handlers[0].rejected;

describe('errorMessage', () => {
  it('explains when the backend cannot be reached', () => {
    expect(errorMessage(new Error('Network Error'))).toMatch(/Cannot reach the server/);
  });

  it('prefers the first field validation error', () => {
    const err = { response: { data: { message: 'Validation failed', fieldErrors: { email: 'Enter a valid email' } } } };
    expect(errorMessage(err)).toBe('Enter a valid email');
  });

  it('uses the server message, then the fallback', () => {
    expect(errorMessage({ response: { data: { message: 'Unknown stock: XYZ' } } })).toBe('Unknown stock: XYZ');
    expect(errorMessage({ response: { data: {} } }, 'Sign in failed.')).toBe('Sign in failed.');
  });
});

describe('tokenStore', () => {
  it('saves, reads and clears the JWT in localStorage', () => {
    tokenStore.set('abc');
    expect(tokenStore.get()).toBe('abc');
    expect(localStorage.getItem('sb_token')).toBe('abc');
    tokenStore.clear();
    expect(tokenStore.get()).toBeNull();
  });
});

describe('axios interceptors', () => {
  it('attaches the Bearer token to every request', () => {
    tokenStore.set('jwt-123');
    const config = requestInterceptor()({ headers: {} });
    expect(config.headers.Authorization).toBe('Bearer jwt-123');
  });

  it('sends no Authorization header when signed out', () => {
    const config = requestInterceptor()({ headers: {} });
    expect(config.headers.Authorization).toBeUndefined();
  });

  it('signs the user out on 401 from a protected endpoint', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    const err = { config: { url: '/api/stocks' }, response: { status: 401 } };

    await expect(responseErrorInterceptor()(err)).rejects.toBe(err);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not sign out on a failed login attempt or other errors', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);

    await expect(responseErrorInterceptor()({ config: { url: '/api/auth/login' }, response: { status: 401 } })).rejects.toBeTruthy();
    await expect(responseErrorInterceptor()({ config: { url: '/api/stocks' }, response: { status: 500 } })).rejects.toBeTruthy();
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});

describe('API wrappers', () => {
  it('auth endpoints', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 't' } });
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: { email: 'a@b.com' } });

    await expect(authApi.login({ email: 'a@b.com', password: 'x' })).resolves.toEqual({ token: 't' });
    expect(post).toHaveBeenCalledWith('/api/auth/login', { email: 'a@b.com', password: 'x' });
    await authApi.register({ fullName: 'A' });
    expect(post).toHaveBeenCalledWith('/api/auth/register', { fullName: 'A' });
    await authApi.logout();
    expect(post).toHaveBeenCalledWith('/api/auth/logout');
    await expect(authApi.me()).resolves.toEqual({ email: 'a@b.com' });
    expect(get).toHaveBeenCalledWith('/api/auth/me');
    post.mockRestore();
    get.mockRestore();
  });

  it('stock endpoints', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: [] });

    await stockApi.list();
    expect(get).toHaveBeenCalledWith('/api/stocks');
    await stockApi.quote('TCS');
    expect(get).toHaveBeenCalledWith('/api/stocks/TCS');
    await stockApi.history('TCS', 60);
    expect(get).toHaveBeenCalledWith('/api/stocks/TCS/history', { params: { limit: 60 } });
    await stockApi.analysis('TCS');
    expect(get).toHaveBeenCalledWith('/api/stocks/TCS/analysis');
    get.mockRestore();
  });

  it('subscription endpoints', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: {} });
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: [] });

    await subscriptionApi.plans();
    expect(get).toHaveBeenCalledWith('/api/subscriptions/plans');
    await subscriptionApi.status();
    expect(get).toHaveBeenCalledWith('/api/subscriptions/me');
    await subscriptionApi.history();
    expect(get).toHaveBeenCalledWith('/api/subscriptions/history');
    await subscriptionApi.subscribe('PREMIUM_YEARLY');
    expect(post).toHaveBeenCalledWith('/api/subscriptions/subscribe', { plan: 'PREMIUM_YEARLY' });
    await subscriptionApi.cancel();
    expect(post).toHaveBeenCalledWith('/api/subscriptions/cancel');
    post.mockRestore();
    get.mockRestore();
  });
});
