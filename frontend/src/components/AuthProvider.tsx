import { useEffect, useState, type ReactNode } from 'react';
import { api, tokenStore } from '../lib/api';
import { AuthContext, type AuthResponse, type User } from '../lib/auth-context';

/**
 * Holds "who is signed in" for the whole app.
 * - Start-up: if a token is saved, ask GET /api/auth/me whose it is (an expired
 *   token gets 401, so we forget it).
 * - login/register: the backend returns { token, user }; we save both (see tokenStore).
 * - logout: forget the token. (JWTs live on the client, so there's nothing to tell the server.)
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);

  useEffect(() => {
    if (!tokenStore.get()) return;
    api<User>('/auth/me')
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, []);

  function signedIn(response: AuthResponse, remember: boolean) {
    tokenStore.set(response.token, remember);
    setUser(response.user);
  }

  const value = {
    user,
    loading,
    login: async (email: string, password: string, remember: boolean) =>
      signedIn(await api<AuthResponse>('/auth/login', { body: { email, password } }), remember),
    register: async (name: string, email: string, password: string) =>
      signedIn(await api<AuthResponse>('/auth/register', { body: { name, email, password } }), true),
    logout: () => {
      tokenStore.clear();
      setUser(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
