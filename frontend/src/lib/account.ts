/** The Settings page's calls: your own account (PUT /api/users/me...). */
import { api } from './api';
import type { User } from './auth-context';

export const updateProfile = (name: string) => api<User>('/users/me', { method: 'PUT', body: { name } });

export const changePassword = (currentPassword: string, newPassword: string) =>
  api<{ message: string }>('/users/me/password', { method: 'PUT', body: { currentPassword, newPassword } });
