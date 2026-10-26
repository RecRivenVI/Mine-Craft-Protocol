import { readFileSync, realpathSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

export interface RuntimeDiscovery {
  schemaVersion: number;
  runtimeInstanceId: string;
  target: string;
  deploymentRole?: 'CLIENT' | 'DEDICATED_SERVER';
  gameDirectory: string;
  pid: number;
  processStartedAt: string;
  startedAt: string;
  baseUrl: string;
  port: number;
  state: string;
  tokenFile: string;
}

export function readRuntimeDiscovery(instanceDirectory: string, expectedTarget?: string): RuntimeDiscovery {
  const directory = realpathSync(instanceDirectory);
  const file = join(directory, 'minecraft-protocol', 'endpoint.json');
  if (statSync(file).size > 16384) throw new Error('Runtime discovery record exceeds 16 KiB');
  const record = JSON.parse(readFileSync(file, 'utf8')) as RuntimeDiscovery;
  if (record.schemaVersion !== 1 || record.state !== 'RUNNING'
      || typeof record.runtimeInstanceId !== 'string' || !/^[0-9a-f-]{36}$/.test(record.runtimeInstanceId)
      || typeof record.target !== 'string' || !record.target || (expectedTarget && record.target !== expectedTarget)
      || (record.deploymentRole !== undefined && !['CLIENT', 'DEDICATED_SERVER'].includes(record.deploymentRole))
      || typeof record.gameDirectory !== 'string' || realpathSync(record.gameDirectory) !== directory
      || !Number.isSafeInteger(record.pid) || record.pid < 1
      || !Number.isSafeInteger(record.port) || record.port < 1 || record.port > 65535
      || record.baseUrl !== `http://127.0.0.1:${record.port}` || record.tokenFile !== 'token'
      || !Number.isFinite(Date.parse(record.processStartedAt)) || !Number.isFinite(Date.parse(record.startedAt))) {
    throw new Error('Invalid Runtime discovery identity, target or loopback endpoint');
  }
  try { process.kill(record.pid, 0); }
  catch { throw new Error('Stale Runtime discovery: owner process is not alive'); }
  return record;
}

export function discoveryToken(instanceDirectory: string): string {
  const path = resolve(instanceDirectory, 'minecraft-protocol', 'token');
  if (statSync(path).size > 16384) throw new Error('Runtime token exceeds 16 KiB');
  const token = readFileSync(path, 'utf8').trim();
  if (!token) throw new Error('Runtime discovery token is empty');
  return token;
}
