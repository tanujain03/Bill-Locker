import { describe, expect, it } from 'vitest';
import { safeWebLink } from './scanned-codes';

describe('safeWebLink', () => {
  it('makes links only of web addresses', () => {
    expect(safeWebLink('https://www.lg.com/in/support/register?model=CH-320F')).toEqual({
      href: 'https://www.lg.com/in/support/register?model=CH-320F',
      host: 'www.lg.com',
    });
    expect(safeWebLink('javascript:alert(1)')).toBeNull();
    expect(safeWebLink('upi://pay?pa=shop@bank')).toBeNull();
    expect(safeWebLink('not a link')).toBeNull();
  });
});
