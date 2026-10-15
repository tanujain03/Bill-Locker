import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/document-actions.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { runDocumentAction } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('reads one document at a time and continues after a failure', async () => {
  const calls = [];
  let active = 0;
  let peak = 0;
  const error = new Error('AI unavailable');
  const result = await runDocumentAction(['a', 'b', 'c', 'a'], async (id) => {
    calls.push(id);
    peak = Math.max(peak, ++active);
    await new Promise((resolve) => setImmediate(resolve));
    active--;
    if (id === 'b') throw error;
    return { id, status: 'EXTRACTED' };
  }, 1);
  assert.equal(peak, 1);
  assert.deepEqual(calls, ['a', 'b', 'c']);
  assert.deepEqual(result.completed.map((r) => r.id), ['a', 'c']);
  assert.deepEqual(result.failed, [{ id: 'b', error }]);
});

test('keeps prepared files associated with the right document despite completion order', async () => {
  const result = await runDocumentAction(['slow', 'fast'], async (id) => {
    if (id === 'slow') await new Promise((resolve) => setImmediate(resolve));
    return `${id}.pdf`;
  });
  assert.deepEqual(result.completed, [{ id: 'fast', value: 'fast.pdf' }, { id: 'slow', value: 'slow.pdf' }]);
  assert.deepEqual(result.failed, []);
});

test('does nothing for an empty selection', async () => {
  assert.deepEqual(await runDocumentAction([], async () => { assert.fail('Unexpected action'); }), { completed: [], failed: [] });
});
