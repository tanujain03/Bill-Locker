import { describe, expect, it } from 'vitest';
import { tokenStorage } from '@/lib/token-storage';
import { aiService } from '@/services/ai.service';
import { authService } from '@/services/auth.service';
import { documentService } from '@/services/document.service';
import { productService } from '@/services/product.service';

/**
 * The contract every backend endpoint must honour: data is scoped to the user
 * in the JWT. These run against the mock API, which enforces the same rules.
 */
describe('user isolation', () => {
  it('rejects calls without a token', async () => {
    tokenStorage.clear();
    await expect(productService.list()).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
  });

  it('never exposes another user’s products, documents or AI context', async () => {
    const other = await authService.register({ name: 'Bob Tester', email: 'bob@example.com', password: 'Passw0rd1' });
    tokenStorage.set(other.token);

    expect(await productService.list()).toHaveLength(0);
    await expect(productService.get('prd_dell')).rejects.toMatchObject({ status: 404, code: 'PRODUCT_NOT_FOUND' });
    await expect(documentService.get('doc_dell_invoice')).rejects.toMatchObject({ status: 404, code: 'DOCUMENT_NOT_FOUND' });
    await expect(productService.remove('prd_dell')).rejects.toMatchObject({ status: 404 });

    const chat = await aiService.chat({ message: 'Is my laptop under warranty?' });
    expect(chat.message.content).toMatch(/locker is empty/i);
    expect(chat.message.references).toHaveLength(0);

    const search = await aiService.search('expired warranties');
    expect(search.results).toHaveLength(0);
  });
});
