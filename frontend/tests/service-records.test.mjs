import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

async function compiled(name, imports = {}) {
  const source = await readFile(new URL(`../src/lib/${name}.ts`, import.meta.url), 'utf8');
  let { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
  for (const [path, url] of Object.entries(imports)) outputText = outputText.replace(`from '${path}'`, `from '${url}'`);
  return `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
}
const service = await import(await compiled('service-records', { './api': await compiled('api') }));
globalThis.localStorage = { getItem: () => 'test-token' };
globalThis.sessionStorage = { getItem: () => null };
const events = [];
globalThis.window = { dispatchEvent: (event) => events.push(event.type) };

test('create guards against a stale item position using the permanent product ID', async (t) => {
  let request;
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    request = { path, options };
    return new Response(JSON.stringify({ id: 'service-id' }), { status: 201 });
  });
  const data = { type: 'REPAIR', title: 'Fan repaired', serviceDate: '2026-10-01', cost: null };
  await service.createServiceRecord({ documentId: 'bill-id', productId: 'permanent-id', position: 2 }, data);
  assert.equal(request.path, '/api/documents/bill-id/items/2/services?productId=permanent-id');
  assert.equal(request.options.headers.Authorization, 'Bearer test-token');
  assert.deepEqual(JSON.parse(request.options.body), data);
  assert.equal(events.at(-1), 'services-changed');
});

test('filters are URL encoded and never fetch another endpoint directly', async (t) => {
  let url;
  t.mock.method(globalThis, 'fetch', async (path) => { url = path; return new Response('[]'); });
  await service.listServiceRecords({ q: 'fan & filter', type: 'REPAIR', due: 'OVERDUE' });
  const parsed = new URL(url, 'https://example.test');
  assert.equal(parsed.pathname, '/api/services');
  assert.equal(parsed.searchParams.get('q'), 'fan & filter');
  assert.equal(parsed.searchParams.get('due'), 'OVERDUE');
});

test('validation failures keep inline field errors and do not report a successful change', async (t) => {
  const count = events.length;
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    success: false, code: 'VALIDATION_ERROR', message: 'Check the date.', fieldErrors: { serviceDate: 'Must not be in the future.' },
  }), { status: 400 }));
  await assert.rejects(service.updateServiceRecord('id', {}), (error) => error.fieldErrors.serviceDate === 'Must not be in the future.');
  assert.equal(events.length, count);
});
