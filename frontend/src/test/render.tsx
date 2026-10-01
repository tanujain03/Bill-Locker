import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { ToastProvider } from '@/components/ui/ToastProvider';
import { DEMO_CREDENTIALS } from '@/lib/config';
import { tokenStorage } from '@/lib/token-storage';
import { routes } from '@/router';
import { authService } from '@/services/auth.service';

/** Renders the real app (all routes and providers) at `path`. */
export function renderApp(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
  return { ...view, router, queryClient, user: userEvent.setup() };
}

/** Signs in through the (mock) API and stores the token like the app does. */
export async function signIn(credentials: { email: string; password: string } = DEMO_CREDENTIALS) {
  const response = await authService.login(credentials);
  tokenStorage.set(response.token);
  return response;
}
