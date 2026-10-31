import { copyFile, mkdir } from 'node:fs/promises';

const files = {
  rules: 'development/procedure-author_extension.md',
  contracts: 'reference/api-extension_platform.md',
  testing: 'development/testing-extension_platform.md'
};
const destination = new URL('../dist/authoring/', import.meta.url);
await mkdir(destination, { recursive: true });
for (const [id, source] of Object.entries(files))
  await copyFile(new URL(`../../../documents/${source}`, import.meta.url), new URL(`${id}.md`, destination));
