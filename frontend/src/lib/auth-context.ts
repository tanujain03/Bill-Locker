import { createContext, useContext } from 'react';
import type { LoginRequest, RegisterRequest, User } from '@/types';

/**
 * - `loading`: a stored token exists and the current user is being fetched.
 * - `error`: the token exists but `/auth/me` failed for a non-auth reason (e.g. offline).
 */
export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated' | 'error';

export interface AuthContextValue {
  user: User | null;
  status: AuthStatus;
  /** True when the last session ended because the server rejected the token. */
  sessionExpired: boolean;
  login: (request: LoginRequest) => Promise<User>;
  register: (request: RegisterRequest) => Promise<User>;
  logout: () => void;
  setUser: (user: User) => void;
  retry: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
