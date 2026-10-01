import type { ISODateTime } from './api';

export interface User {
  id: string;
  name: string;
  email: string;
  createdAt: ISODateTime;
}

export interface AuthResponse {
  token: string;
  tokenType: 'Bearer';
  expiresAt: ISODateTime;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface UpdateProfileRequest {
  name: string;
}
