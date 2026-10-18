import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = fileURLToPath(new URL('.', import.meta.url));
const specificationPath = resolve(directory, '../../protocol_schema/src/main/openapi/minecraft-control-v0.json');
const specification = JSON.parse(await readFile(specificationPath, 'utf8'));

const requiredPaths = [
  '/v0/session',
  '/v0/control/mode',
  '/v0/capabilities',
  '/v0/ui/tree',
  '/v0/ui/action',
  '/v0/pipelines',
  '/v0/state/frames',
  '/v0/world/scene',
  '/v0/capture',
  '/v0/recordings',
  '/v0/diagnostics/hooks',
  '/v0/server/peer'
  ,'/v0/observe/deep'
  ,'/v0/observe/deep/capabilities'
  ,'/v0/debug/capabilities'
  ,'/v0/debug/mutations'
  ,'/v0/debug/batches'
];
const requiredSchemas = [
  'WorldDataCategory', 'WorldReadContract', 'WorldUserPlayerResponse', 'WorldSceneResponse', 'RuntimeConnection',
  'CapabilitiesResponse', 'PlayerCommandSubmission',
  'AgentMode', 'AgentModeVersion', 'ControlModeRequest', 'ControlModeResponse',
  'UiTreeResponse',
  'PipelineRequest',
  'StateFrameRequest',
  'RecordingRequest',
  'HookManifestResponse',
  'ErrorResponse'
  ,'DeepObservationRequest'
  ,'DeepObservationResponse'
  ,'ResourceRevisionRef'
  ,'DebugMutationRequest'
  ,'DebugMutationResult'
  ,'DebugBatchRequest'
];

if (specification.info?.version !== '0.0.1-ui-m1') {
  throw new Error(`Companion requires OpenAPI 0.0.1-ui-m1, received ${specification.info?.version ?? 'missing'}`);
}
for (const path of requiredPaths) {
  if (!specification.paths?.[path]) throw new Error(`Companion-required protocol path is missing: ${path}`);
}
for (const schema of requiredSchemas) {
  if (!specification.components?.schemas?.[schema]) throw new Error(`Companion-required schema is missing: ${schema}`);
}
const serialized = JSON.stringify(specification);
for(const field of ['configurationKind','configuredCommandEffect'])if(!specification.components.schemas.PlayerCommandSubmission.properties[field])throw new Error('Missing typed command configuration provenance: '+field);
if(!specification.components.schemas.OperationDescriptor.properties.additionalScopesByStep||!specification.components.schemas.AssertResponse.allOf[1].properties.dataCategory)throw new Error('Missing conditional input scopes or categorized assertion response');
for(const path of ['/v0/ui/action','/v0/pipelines'])for(const parameter of ['ExpectedScreenRevision','ExpectedMenuRevision']){
  if(!specification.paths[path].post.parameters?.some(value=>value.$ref==='#/components/parameters/'+parameter))throw new Error(`Missing queued input precondition ${parameter} on ${path}`);
}
if (serialized.includes('expectedWorldRevision')) {
  throw new Error('Companion protocol must not restore global expectedWorldRevision');
}

const policies = ['READ_COMPATIBLE', 'OPERATE_OR_TAKEOVER', 'TAKEOVER_REQUIRED', 'MODE_INDEPENDENT', 'COMMAND_ROUTE_DEPENDENT'];
for (const [path, methods] of Object.entries(specification.paths)) {
  if (!methods.parameters?.some(parameter => parameter.$ref === '#/components/parameters/RuntimeInstance')) throw new Error(`Missing instance pin on ${path}`);
  for (const [method, operation] of Object.entries(methods)) {
    if (!operation.operationId) continue;
    if (!policies.includes(operation['x-agent-mode'])) throw new Error(`Unclassified native operation ${method} ${path}`);
  }
}
