import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

// Run the TypeScript helper with Node's test runner using the project's existing compiler.
const source = await readFile(new URL('../src/lib/bulk-delete.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { deleteSelectedDocuments } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('deletes each selected document once and leaves unselected documents alone', async () => {
  const remaining = new Set(['a', 'b', 'c']);
  const calls = [];
  const result = await deleteSelectedDocuments(['a', 'b', 'a'], async (id) => {
    calls.push(id);
    remaining.delete(id);
  });
  assert.deepEqual(calls.sort(), ['a', 'b']);
  assert.deepEqual(result.deleted.sort(), ['a', 'b']);
  assert.deepEqual(result.failed, []);
  assert.deepEqual([...remaining], ['c']);
});

test('continues after failure and reports the failed ID and error for retry', async () => {
  const error = new Error('Unable to delete b');
  const result = await deleteSelectedDocuments(['a', 'b', 'c'], async (id) => {
    if (id === 'b') throw error;
  });
  assert.deepEqual(result.deleted.sort(), ['a', 'c']);
  assert.deepEqual(result.failed, [{ id: 'b', error }]);
  const retried = [];
  await deleteSelectedDocuments(result.failed.map(({ id }) => id), async (id) => { retried.push(id); });
  assert.deepEqual(retried, ['b']);
});

test('limits in-flight requests to four without skipping a document', async () => {
  let active = 0;
  let peak = 0;
  const ids = Array.from({ length: 21 }, (_, i) => String(i));
  const result = await deleteSelectedDocuments(ids, async () => {
    active++;
    peak = Math.max(peak, active);
    await new Promise((resolve) => setImmediate(resolve));
    active--;
  });
  assert.equal(peak, 4);
  assert.deepEqual(result.deleted.sort(), [...ids].sort());
  assert.deepEqual(result.failed, []);
});

test('an empty selection sends no requests', async () => {
  const result = await deleteSelectedDocuments([], async () => { assert.fail('Unexpected request'); });
  assert.deepEqual(result, { deleted: [], failed: [] });
});

test('all failures are reported without throwing or claiming success', async () => {
  const result = await deleteSelectedDocuments(['a', 'b'], async () => { throw new Error('Offline'); });
  assert.deepEqual(result.deleted, []);
  assert.deepEqual(result.failed.map(({ id }) => id).sort(), ['a', 'b']);
});
