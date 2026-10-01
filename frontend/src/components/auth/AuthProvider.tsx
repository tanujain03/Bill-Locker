import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setUnauthorizedHandler } from '@/lib/api-client';
import { AuthContext, type AuthContextValue, type AuthStatus } from '@/lib/auth-context';
import { queryKeys } from '@/lib/query-keys';
import { sessionCache } from '@/lib/session-cache';
import { isTokenExpired, tokenStorage } from '@/lib/token-storage';
import { authService } from '@/services/auth.service';
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types';

function readStoredToken(): string | null {
  const stored = tokenStorage.get();
  if (stored && isTokenExpired(stored)) {
    tokenStorage.clear();
    return null;
  }
  return stored;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(readStoredToken);
  const [sessionExpired, setSessionExpired] = useState(false);

  const meQuery = useQuery({
    queryKey: queryKeys.me,
    queryFn: authService.me,
    enabled: token !== null,
    staleTime: 5 * 60_000,
    retry: false,
  });

  const clearSession = useCallback(() => {
    tokenStorage.clear();
    sessionCache.clearAll();
    setToken(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (tokenStorage.get()) setSessionExpired(true);
      clearSession();
    });
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  const applySession = useCallback(
    (response: AuthResponse) => {
      queryClient.clear();
      tokenStorage.set(response.token);
      queryClient.setQueryData(queryKeys.me, response.user);
      setSessionExpired(false);
      setToken(response.token);
      return response.user;
    },
    [queryClient],
  );

  const login = useCallback(
    async (request: LoginRequest) => applySession(await authService.login(request)),
    [applySession],
  );

  const register = useCallback(
    async (request: RegisterRequest) => applySession(await authService.register(request)),
    [applySession],
  );

  const logout = useCallback(() => {
    setSessionExpired(false);
    clearSession();
  }, [clearSession]);

  const setUser = useCallback((user: User) => queryClient.setQueryData(queryKeys.me, user), [queryClient]);

  const { refetch } = meQuery;
  const retry = useCallback(() => void refetch(), [refetch]);

  let status: AuthStatus;
  if (!token) status = 'unauthenticated';
  else if (meQuery.isSuccess) status = 'authenticated';
  else if (meQuery.isError) status = 'error';
  else status = 'loading';

  const user = token && meQuery.data ? meQuery.data : null;

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, sessionExpired, login, register, logout, setUser, retry }),
    [user, status, sessionExpired, login, register, logout, setUser, retry],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
