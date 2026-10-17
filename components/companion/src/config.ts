import { readFileSync } from 'node:fs';
import { discoveryToken, readRuntimeDiscovery, type RuntimeDiscovery } from './discovery.js';

export interface CompanionConfig {
  baseUrl: URL;
  token: string;
  timeoutMs: number;
  maxJsonBytes: number;
  maxArtifactBytes: number;
  instanceDirectory?: string;
  discovery?: RuntimeDiscovery;
}

function booleanEnvironment(value: string | undefined): boolean {
  return value === '1' || value?.toLowerCase() === 'true';
}

function positiveInteger(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number.parseInt(value, 10);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) throw new Error('Companion numeric environment value must be a positive integer');
  return parsed;
}

export function loadConfig(environment: NodeJS.ProcessEnv = process.env): CompanionConfig {
  const instanceDirectory = environment.MCP_MINECRAFT_INSTANCE_DIR;
  if (instanceDirectory && (environment.MCP_MINECRAFT_BASE_URL || environment.MCP_MINECRAFT_TOKEN || environment.MCP_MINECRAFT_TOKEN_FILE)) {
    throw new Error('Choose instance discovery OR explicit BASE_URL and TOKEN credentials; do not mix connection authorities');
  }
  const discovery = instanceDirectory ? readRuntimeDiscovery(instanceDirectory, environment.MCP_MINECRAFT_TARGET) : undefined;
  if (!discovery && !environment.MCP_MINECRAFT_BASE_URL) throw new Error('Set MCP_MINECRAFT_INSTANCE_DIR or explicit MCP_MINECRAFT_BASE_URL and TOKEN credentials');
  const baseUrl = new URL(discovery?.baseUrl ?? environment.MCP_MINECRAFT_BASE_URL!);
  if (baseUrl.protocol !== 'http:' && baseUrl.protocol !== 'https:') throw new Error('Runtime requires HTTP or HTTPS');
  const loopback = baseUrl.hostname === '127.0.0.1' || baseUrl.hostname === 'localhost' || baseUrl.hostname === '[::1]';
  if (!loopback && !booleanEnvironment(environment.MCP_COMPANION_ALLOW_NON_LOOPBACK)) {
    throw new Error('Non-loopback Minecraft Runtime requires MCP_COMPANION_ALLOW_NON_LOOPBACK=true');
  }
  if (baseUrl.username || baseUrl.password || baseUrl.search || baseUrl.hash) {
    throw new Error('Minecraft Runtime base URL must not contain credentials, query or fragment');
  }
  baseUrl.pathname = baseUrl.pathname.replace(/\/$/, '');

  let token = instanceDirectory ? discoveryToken(instanceDirectory) : environment.MCP_MINECRAFT_TOKEN?.trim();
  if (!token && environment.MCP_MINECRAFT_TOKEN_FILE) {
    token = readFileSync(environment.MCP_MINECRAFT_TOKEN_FILE, 'utf8').trim();
  }
  if (!token) throw new Error('Set MCP_MINECRAFT_TOKEN or MCP_MINECRAFT_TOKEN_FILE');

  return {
    ...(instanceDirectory && discovery ? { instanceDirectory, discovery } : {}),
    baseUrl,
    token,
    timeoutMs: positiveInteger(environment.MCP_COMPANION_TIMEOUT_MS, 10_000),
    maxJsonBytes: positiveInteger(environment.MCP_COMPANION_MAX_JSON_BYTES, 16 * 1024 * 1024),
    maxArtifactBytes: positiveInteger(environment.MCP_COMPANION_MAX_ARTIFACT_BYTES, 256 * 1024 * 1024)
  };
}
