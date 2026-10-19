import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/upload-batch.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { processUploadBatch, validateUpload } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

const entries = (...names) => names.map((name, key) => ({ key, file: { name, size: 100 } }));
const options = (upload) => ({ upload, duplicateId: (e) => e.duplicateId ?? null, errorText: (e) => e.message, onUpdate: () => {}, shouldContinue: () => true });

test('uploads every selected file sequentially and reports individual progress', async () => {
  const updates = [];
  let active = 0;
  const result = await processUploadBatch(entries('a.pdf', 'b.png', 'c.jpg'), {
    ...options(async (file) => {
      assert.equal(++active, 1);
      await new Promise((resolve) => setImmediate(resolve));
      active--;
      return { id: file.name };
    }),
    onUpdate: (r) => updates.push([r.file.name, r.status]),
  });
  assert.deepEqual(result.map((r) => r.documentId), ['a.pdf', 'b.png', 'c.jpg']);
  assert.deepEqual(updates.map(([, status]) => status), ['uploading', 'uploaded', 'uploading', 'uploaded', 'uploading', 'uploaded']);
});

test('a failure or duplicate does not prevent later files from uploading', async () => {
  const result = await processUploadBatch(entries('bad.pdf', 'duplicate.pdf', 'good.pdf'), options(async (file) => {
    if (file.name === 'bad.pdf') throw new Error('Upload failed');
    if (file.name === 'duplicate.pdf') throw { duplicateId: 'existing-id' };
    return { id: 'new-id' };
  }));
  assert.deepEqual(result.map((r) => r.status), ['failed', 'duplicate', 'uploaded']);
  assert.equal(result[1].documentId, 'existing-id');
  const retried = [];
  await processUploadBatch(result.filter((r) => r.status === 'failed'), options(async (file) => {
    retried.push(file.name);
    return { id: 'retried-id' };
  }));
  assert.deepEqual(retried, ['bad.pdf']);
});

test('invalid files fail locally without preventing a valid file in the same selection', async () => {
  const input = entries('empty.pdf', 'large.png', 'unsupported.exe', 'good.WEBP');
  input[0].file.size = 0;
  input[1].file.size = 10 * 1024 * 1024 + 1;
  const calls = [];
  const result = await processUploadBatch(input, options(async (file) => { calls.push(file.name); return { id: 'ok' }; }));
  assert.deepEqual(calls, ['good.WEBP']);
  assert.deepEqual(result.map((r) => r.status), ['failed', 'failed', 'failed', 'uploaded']);
  assert.equal(validateUpload({ name: 'limit.JPEG', size: 10 * 1024 * 1024 }), null);
});

test('stops the queue when the upload provider is unmounted', async () => {
  let mounted = true;
  const calls = [];
  const updates = [];
  await processUploadBatch(entries('first.pdf', 'second.pdf'), {
    ...options(async (file) => { calls.push(file.name); mounted = false; return { id: 'ok' }; }),
    shouldContinue: () => mounted,
    onUpdate: (r) => updates.push(r.status),
  });
  assert.deepEqual(calls, ['first.pdf']);
  assert.deepEqual(updates, ['uploading']);
});

test('empty selections send no upload requests', async () => {
  assert.deepEqual(await processUploadBatch([], options(async () => { assert.fail('Unexpected upload'); })), []);
});

test('distinct files with the same name are both uploaded', async () => {
  let uploaded = 0;
  const result = await processUploadBatch(entries('invoice.pdf', 'invoice.pdf'), options(async () => ({ id: String(++uploaded) })));
  assert.deepEqual(result.map((r) => r.documentId), ['1', '2']);
  assert.deepEqual(result.map((r) => r.key), [0, 1]);
});
