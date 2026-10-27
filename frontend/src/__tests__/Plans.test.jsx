import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Plans from '../pages/Plans';
import { useAuth } from '../context/AuthContext';
import { subscriptionApi } from '../api/client';
import { PLANS } from '../test/fixtures';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    subscriptionApi: { plans: vi.fn(), status: vi.fn(), history: vi.fn(), subscribe: vi.fn(), cancel: vi.fn() },
  };
});

const FREE_STATUS = { plan: 'FREE', premium: false, premiumUntil: null, daysRemaining: 0 };
const PREMIUM_STATUS = { plan: 'PREMIUM_MONTHLY', premium: true, premiumUntil: '2026-12-01T00:00:00Z', daysRemaining: 30 };
const refreshUser = vi.fn();

function renderPlans() {
  render(
    <MemoryRouter>
      <Plans />
    </MemoryRouter>
  );
}

describe('Premium plans page', () => {
  beforeEach(() => {
    refreshUser.mockReset().mockResolvedValue({});
    useAuth.mockReturnValue({ isPremium: false, refreshUser });
    subscriptionApi.plans.mockResolvedValue(PLANS);
    subscriptionApi.status.mockResolvedValue(FREE_STATUS);
    subscriptionApi.history.mockResolvedValue([]);
  });

  it('lists the three plans with prices', async () => {
    renderPlans();
    expect(await screen.findByRole('heading', { name: 'Premium Monthly' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Premium Yearly' })).toBeInTheDocument();
    expect(screen.getByText('₹499.00')).toBeInTheDocument();
    expect(screen.getByText('₹4,999.00')).toBeInTheDocument();
    expect(screen.getByText(/Works out to ₹417.00 a month/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Current plan' })).toBeDisabled();
  });

  it('subscribes after confirming in the dialog', async () => {
    subscriptionApi.subscribe.mockResolvedValue({ endDate: '2026-12-01T00:00:00Z' });
    renderPlans();

    await userEvent.click(await screen.findByRole('button', { name: 'Choose Premium Monthly' }));
    const dialog = screen.getByRole('dialog', { name: 'Subscribe to Premium Monthly' });
    expect(dialog).toHaveTextContent('30 days');
    expect(dialog).toHaveTextContent('nothing is charged');

    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹499.00 and subscribe' }));

    expect(subscriptionApi.subscribe).toHaveBeenCalledWith('PREMIUM_MONTHLY');
    expect(await screen.findByRole('status')).toHaveTextContent('Subscribed to Premium Monthly');
    expect(refreshUser).toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('closes the dialog with Cancel or Escape', async () => {
    renderPlans();
    await userEvent.click(await screen.findByRole('button', { name: 'Choose Premium Yearly' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Choose Premium Yearly' }));
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(subscriptionApi.subscribe).not.toHaveBeenCalled();
  });

  it('shows an error if activation fails', async () => {
    subscriptionApi.subscribe.mockRejectedValue({ response: { data: { message: 'Payment declined' } } });
    renderPlans();
    await userEvent.click(await screen.findByRole('button', { name: 'Choose Premium Monthly' }));
    await userEvent.click(screen.getByRole('button', { name: /Pay/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Payment declined');
  });

  it('shows plan status, billing history and lets Premium users cancel', async () => {
    useAuth.mockReturnValue({ isPremium: true, refreshUser });
    subscriptionApi.status.mockResolvedValue(PREMIUM_STATUS);
    subscriptionApi.history.mockResolvedValue([
      { id: 1, plan: 'PREMIUM_MONTHLY', amount: 499, status: 'ACTIVE', startDate: '2026-11-01T00:00:00Z', endDate: '2026-12-01T00:00:00Z', paymentReference: 'DEMO-ABC' },
    ]);
    subscriptionApi.cancel.mockResolvedValue(FREE_STATUS);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPlans();

    expect(await screen.findByRole('heading', { name: 'Your Premium plan' })).toBeInTheDocument();
    expect(await screen.findByText(/30 days left/)).toBeInTheDocument();
    expect(screen.getByText('DEMO-ABC')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Extend this plan' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel Premium' }));

    expect(window.confirm).toHaveBeenCalled();
    expect(subscriptionApi.cancel).toHaveBeenCalled();
    expect(await screen.findByRole('status')).toHaveTextContent('Premium cancelled');
  });

  it('does nothing if the user backs out of cancelling', async () => {
    subscriptionApi.status.mockResolvedValue(PREMIUM_STATUS);
    subscriptionApi.history.mockResolvedValue([
      { id: 1, plan: 'PREMIUM_MONTHLY', amount: 499, status: 'ACTIVE', startDate: null, endDate: null, paymentReference: 'DEMO-1' },
    ]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderPlans();

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel Premium' }));
    expect(subscriptionApi.cancel).not.toHaveBeenCalled();
  });

  it('reports when plans cannot be loaded', async () => {
    subscriptionApi.plans.mockRejectedValue({ response: { data: { message: 'Server unavailable' } } });
    renderPlans();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Server unavailable'));
  });
});
