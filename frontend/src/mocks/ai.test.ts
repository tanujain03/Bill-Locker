import { describe, expect, it } from 'vitest';
import { answerQuestion, applySearch, interpretSearch } from './ai';
import { db, documentsOf, productsOf, serviceRecordsOf, toDocumentSummary, toProductDto, toServiceRecordDto } from './db';
import { DEMO_USER_ID } from './seed';

function context() {
  return {
    products: productsOf(DEMO_USER_ID).map(toProductDto),
    documents: documentsOf(DEMO_USER_ID).map(toDocumentSummary),
    services: serviceRecordsOf(DEMO_USER_ID).map(toServiceRecordDto),
  };
}

describe('mock assistant (grounded answers)', () => {
  it('answers warranty questions about a specific product with references', () => {
    const answer = answerQuestion('Is my laptop still under warranty?', context());
    expect(answer.content).toMatch(/Dell Inspiron 15 3530 Laptop/);
    expect(answer.content).toMatch(/Yes/);
    expect(answer.references[0]).toMatchObject({ type: 'PRODUCT', id: 'prd_dell' });
  });

  it('says so when a value is not on the documents instead of inventing it', () => {
    const answer = answerQuestion('What is the serial number of my headphones?', context());
    expect(answer.content).toMatch(/no serial number/i);
  });

  it('finds purchases by seller', () => {
    const answer = answerQuestion('What products did I buy from Amazon?', context());
    expect(answer.content).toMatch(/Sony Bravia/);
    expect(answer.content).toMatch(/iPhone 15/);
    expect(answer.references).toHaveLength(2);
  });

  it('lists warranties expiring within a window', () => {
    const answer = answerQuestion('Which warranties expire within 90 days?', context());
    expect(answer.content).toMatch(/Dell Inspiron/);
    expect(answer.content).toMatch(/boAt Rockerz/);
    expect(answer.content).not.toMatch(/LG 8 kg/);
  });

  it('finds the invoice document for a product', () => {
    const answer = answerQuestion('Find my laptop bill', context());
    expect(answer.references.some((reference) => reference.type === 'DOCUMENT')).toBe(true);
  });

  it('falls back honestly for questions outside the data', () => {
    const answer = answerQuestion('What is the weather tomorrow?', context());
    expect(answer.content).toMatch(/couldn’t find anything/i);
    expect(answer.references).toHaveLength(0);
  });
});

describe('natural-language search → structured filters', () => {
  it('turns "expires within 90 days" into filters, not SQL', () => {
    const products = productsOf(DEMO_USER_ID).map(toProductDto);
    const filters = interpretSearch('Show products whose warranty expires within 90 days', db().categories, products);
    expect(filters.daysUntilExpiry).toBe(90);
    const results = applySearch(products, filters);
    expect(results.map((product) => product.id)).toEqual(expect.arrayContaining(['prd_dell', 'prd_boat']));
    expect(results.every((product) => (product.warranty?.daysRemaining ?? -1) >= 0)).toBe(true);
  });

  it('understands "most expensive"', () => {
    const products = productsOf(DEMO_USER_ID).map(toProductDto);
    const filters = interpretSearch('my most expensive purchase', db().categories, products);
    const results = applySearch(products, filters);
    expect(results).toHaveLength(1);
    expect(results[0].name).toMatch(/Samsung/);
  });

  it('filters by seller and expired status', () => {
    const products = productsOf(DEMO_USER_ID).map(toProductDto);
    expect(interpretSearch('things I bought from Flipkart', db().categories, products).seller).toBe('flipkart');
    expect(interpretSearch('expired warranties', db().categories, products).warrantyStatus).toBe('EXPIRED');
  });
});
