import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

// Compile the small module graph with the existing TypeScript compiler for Node's runner.
const compiled = new Map();
async function moduleUrl(name) {
  if (compiled.has(name)) return compiled.get(name);
  const source = await readFile(new URL(`../src/lib/${name}.ts`, import.meta.url), 'utf8');
  let { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
  for (const match of [...outputText.matchAll(/from ['"]\.\/([^'"]+)['"]/g)]) {
    outputText = outputText.replace(match[0], `from '${await moduleUrl(match[1])}'`);
  }
  const url = `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
  compiled.set(name, url);
  return url;
}

const { productLink } = await import(await moduleUrl('products'));
const { claimKitLink } = await import(await moduleUrl('claims'));
const { toForm, fromForm, emptyItem } = await import(await moduleUrl('document-form'));

test('product and claim links use only the permanent ID', () => {
  const id = '0ed84a0b-5b87-49ed-b4b7-8b842ea9838b';
  assert.equal(productLink(id), `/products/${id}`);
  assert.equal(claimKitLink(id), `/warranties/claim/${id}`);
});

test('editing, reordering, and removing form items preserves the remaining product ID', () => {
  const form = toForm({ items: [{ id: 'phone', productName: 'Phone' }, { id: 'charger', productName: 'Charger' }] });
  form.items.reverse();
  form.items[0].productName = 'Updated charger';
  form.items.pop();
  const saved = fromForm(form);
  assert.equal(saved.items.length, 1);
  assert.equal(saved.items[0].id, 'charger');
  assert.equal(saved.items[0].productName, 'Updated charger');
});

test('a new form item carries no server ID and cannot borrow another product link', () => {
  const form = toForm({ items: [] });
  form.items.push(emptyItem());
  assert.equal(fromForm(form).items[0].id, null);
});
