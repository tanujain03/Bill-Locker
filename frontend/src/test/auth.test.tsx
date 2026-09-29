import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DEMO_CREDENTIALS } from '@/lib/config';
import { renderApp } from './render';

describe('authentication', () => {
  it('sends unauthenticated visitors of protected pages to the login page', async () => {
    const { router } = renderApp('/dashboard');
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('validates the login form before calling the API', async () => {
    const { user } = renderApp('/login');
    await user.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('shows the server message for invalid credentials', async () => {
    const { user } = renderApp('/login');
    await user.type(await screen.findByLabelText('Email'), DEMO_CREDENTIALS.email);
    await user.type(screen.getByLabelText('Password'), 'not-the-password1');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Incorrect email or password.')).toBeInTheDocument();
  });

  it('signs in and returns the user to the page they wanted', async () => {
    const { user, router } = renderApp('/warranties');
    await user.type(await screen.findByLabelText('Email'), DEMO_CREDENTIALS.email);
    await user.type(screen.getByLabelText('Password'), DEMO_CREDENTIALS.password);
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Warranties' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/warranties');
  });

  it('registers a new account and shows the empty-state dashboard', async () => {
    const { user } = renderApp('/register');
    await user.type(await screen.findByLabelText('Full name'), 'Ravi Kumar');
    await user.type(screen.getByLabelText('Email'), 'ravi.kumar@example.com');
    await user.type(screen.getByLabelText('Password'), 'Str0ngPass');
    await user.type(screen.getByLabelText('Confirm password'), 'Str0ngPass');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('heading', { name: 'No products yet' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: /Ravi/ })).toBeInTheDocument();
  });

  it('rejects registering an email that already exists', async () => {
    const { user } = renderApp('/register');
    await user.type(await screen.findByLabelText('Full name'), 'Someone Else');
    await user.type(screen.getByLabelText('Email'), DEMO_CREDENTIALS.email);
    await user.type(screen.getByLabelText('Password'), 'Str0ngPass');
    await user.type(screen.getByLabelText('Confirm password'), 'Str0ngPass');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('This email is already registered')).toBeInTheDocument();
  });
});
