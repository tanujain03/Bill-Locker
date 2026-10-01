import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateLockerData } from '@/lib/invalidate';
import { queryKeys } from '@/lib/query-keys';
import { productService } from '@/services/product.service';
import type { ProductFilters, ProductInput } from '@/types';

export function useProducts(filters: ProductFilters = {}) {
  return useQuery({
    queryKey: queryKeys.products.list(filters),
    queryFn: () => productService.list(filters),
    placeholderData: (previous) => previous,
  });
}

export function useProduct(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.products.detail(id ?? ''),
    queryFn: () => productService.get(id as string),
    enabled: Boolean(id),
  });
}

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories,
    queryFn: productService.categories,
    staleTime: Infinity,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProductInput) => productService.create(input),
    onSuccess: () => invalidateLockerData(queryClient),
  });
}

export function useUpdateProduct(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProductInput) => productService.update(id, input),
    onSuccess: (product) => {
      queryClient.setQueryData(queryKeys.products.detail(id), product);
      return invalidateLockerData(queryClient);
    },
  });
}

export function useDeleteProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => productService.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: queryKeys.products.detail(id) });
      return invalidateLockerData(queryClient);
    },
  });
}
