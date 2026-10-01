import { apiClient } from '@/lib/api-client';
import type { AuthResponse, LoginRequest, RegisterRequest, UpdateProfileRequest, User } from '@/types';

export const authService = {
  async login(body: LoginRequest): Promise<AuthResponse> {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', body);
    return data;
  },

  async register(body: RegisterRequest): Promise<AuthResponse> {
    const { data } = await apiClient.post<AuthResponse>('/auth/register', body);
    return data;
  },

  async me(): Promise<User> {
    const { data } = await apiClient.get<User>('/auth/me');
    return data;
  },

  async updateProfile(body: UpdateProfileRequest): Promise<User> {
    const { data } = await apiClient.put<User>('/users/me', body);
    return data;
  },
};
