import { createContext, useContext } from 'react';

/** Same shapes as the backend's UserResponse and AuthResponse records. */
export type User = { id: string; name: string; email: string; createdAt: string };
export type AuthResponse = { token: string; tokenType: 'Bearer'; expiresAt: string; user: User };

export type AuthState = {
  /** The signed-in user, or null when signed out. */
  user: User | null;
  /** True while we check a saved token at start-up (don't redirect yet). */
  loading: boolean;
  /** remember = stay signed in after the browser is closed. */
  login: (email: string, password: string, remember: boolean) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
};

export const AuthContext = createContext<AuthState | null>(null);

/** Any component can ask "who is signed in?" with useAuth(). */
export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>');
  return auth;
}
