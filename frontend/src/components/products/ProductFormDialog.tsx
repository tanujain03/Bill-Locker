import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useId } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/toast-context';
import { useCategories, useCreateProduct, useUpdateProduct } from '@/hooks/useProducts';
import { getErrorMessage } from '@/lib/api-client';
import { applyServerFieldErrors } from '@/lib/form-errors';
import type { Product } from '@/types';
import { ProductFields } from './ProductFields';
import {
  PRODUCT_FORM_FIELDS,
  productFormSchema,
  productToFormValues,
  toProductInput,
  type ProductFormValues,
} from './product-form';

interface ProductFormDialogProps {
  open: boolean;
  onClose: () => void;
  /** Present when editing. */
  product?: Product;
}

export function ProductFormDialog({ open, onClose, product }: ProductFormDialogProps) {
  const formId = useId();
  const toast = useToast();
  const navigate = useNavigate();
  const { data: categories = [] } = useCategories();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct(product?.id ?? '');
  const isEdit = Boolean(product);

  const form = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: productToFormValues(product),
  });
  const { reset, handleSubmit, setError, formState } = form;

  useEffect(() => {
    if (open) reset(productToFormValues(product));
  }, [open, product, reset]);

  const onSubmit = handleSubmit(async (values) => {
    const input = toProductInput(values);
    try {
      const saved = isEdit ? await updateProduct.mutateAsync(input) : await createProduct.mutateAsync(input);
      toast.success(isEdit ? 'Product updated' : 'Product added', saved.name);
      onClose();
      if (!isEdit) navigate(`/products/${saved.id}`);
    } catch (error) {
      if (!applyServerFieldErrors(error, setError, PRODUCT_FORM_FIELDS)) {
        toast.error('Could not save the product', getErrorMessage(error));
      }
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      dismissible={!formState.isSubmitting}
      title={isEdit ? 'Edit product' : 'Add a product manually'}
      description={
        isEdit
          ? 'Changes to the purchase date or warranty period recalculate the expiry date.'
          : 'No bill handy? Enter the details yourself — you can attach documents later.'
      }
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={formState.isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={formState.isSubmitting}>
            {isEdit ? 'Save changes' : 'Add product'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={onSubmit} noValidate>
        <ProductFields form={form} categories={categories} autoFocusName />
      </form>
    </Modal>
  );
}
