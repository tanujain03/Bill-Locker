import { apiClient } from '@/lib/api-client';
import type { Category, Product, ProductFilters, ProductInput } from '@/types';

export const productService = {
  async list(filters: ProductFilters = {}): Promise<Product[]> {
    const { data } = await apiClient.get<Product[]>('/products', {
      params: {
        search: filters.search || undefined,
        categoryId: filters.categoryId || undefined,
        warrantyStatus: filters.warrantyStatus || undefined,
      },
    });
    return data;
  },

  async get(id: string): Promise<Product> {
    const { data } = await apiClient.get<Product>(`/products/${encodeURIComponent(id)}`);
    return data;
  },

  async create(body: ProductInput): Promise<Product> {
    const { data } = await apiClient.post<Product>('/products', body);
    return data;
  },

  async update(id: string, body: ProductInput): Promise<Product> {
    const { data } = await apiClient.put<Product>(`/products/${encodeURIComponent(id)}`, body);
    return data;
  },

  async remove(id: string): Promise<void> {
    await apiClient.delete(`/products/${encodeURIComponent(id)}`);
  },

  async categories(): Promise<Category[]> {
    const { data } = await apiClient.get<Category[]>('/categories');
    return data;
  },
};
