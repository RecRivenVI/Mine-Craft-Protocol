import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = fileURLToPath(new URL('.', import.meta.url));
const specificationPath = resolve(directory, '../../protocol-schema/src/main/openapi/minecraft-control-v0.json');
const specification = JSON.parse(await readFile(specificationPath, 'utf8'));

const requiredPaths = [
  '/v0/session',
  '/v0/capabilities',
  '/v0/ui/tree',
  '/v0/ui/action',
  '/v0/pipelines',
  '/v0/state/frames',
  '/v0/capture',
  '/v0/recordings',
  '/v0/diagnostics/hooks',
  '/v0/server/peer'
  ,'/v0/observe/deep'
  ,'/v0/observe/deep/capabilities'
];
const requiredSchemas = [
  'CapabilitiesResponse',
  'UiTreeResponse',
  'PipelineRequest',
  'StateFrameRequest',
  'RecordingRequest',
  'HookManifestResponse',
  'ErrorResponse'
  ,'DeepObservationRequest'
  ,'DeepObservationResponse'
  ,'ResourceRevisionRef'
];

if (specification.info?.version !== '0.0.1-phase9b2') {
  throw new Error(`Companion requires OpenAPI 0.0.1-phase9b2, received ${specification.info?.version ?? 'missing'}`);
}
for (const path of requiredPaths) {
  if (!specification.paths?.[path]) throw new Error(`Companion-required protocol path is missing: ${path}`);
}
for (const schema of requiredSchemas) {
  if (!specification.components?.schemas?.[schema]) throw new Error(`Companion-required schema is missing: ${schema}`);
}
const serialized = JSON.stringify(specification);
if (serialized.includes('expectedWorldRevision')) {
  throw new Error('Companion protocol must not restore global expectedWorldRevision');
}
