import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';

// jsdom does not implement these browser APIs used by charts and dialogs.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = globalThis.ResizeObserver || ResizeObserverStub;

afterEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});
