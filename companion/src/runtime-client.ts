import { randomUUID } from 'node:crypto';

import type { CompanionConfig } from './config.js';
import type { JsonObject, JsonValue, RuntimeBinary, RuntimeRequestOptions } from './types.js';

export class RuntimeError extends Error {
  readonly code: string;
  readonly status: number;
  readonly requestId?: string;

  constructor(code: string, status: number, message: string, requestId?: string) {
    super(message);
    this.name = 'RuntimeError';
    this.code = code;
    this.status = status;
    if (requestId !== undefined) this.requestId = requestId;
  }
}

function validatePath(path: string): void {
  if (!path.startsWith('/v0/') || path.includes('\\') || path.includes('\0') || path.includes('..')) {
    throw new Error('Companion Runtime path is outside the typed /v0 namespace');
  }
}

export class RuntimeClient {
  readonly config: CompanionConfig;

  constructor(config: CompanionConfig) {
    this.config = config;
  }

  async json<T extends JsonValue = JsonObject>(
    method: 'GET' | 'POST' | 'DELETE',
    path: string,
    body?: JsonValue,
    options: RuntimeRequestOptions = {}
  ): Promise<T> {
    const response = await this.request(method, path, body, options);
    const bytes = new Uint8Array(await response.arrayBuffer());
    const limit = options.maxResponseBytes ?? this.config.maxJsonBytes;
    if (bytes.byteLength > limit) throw new RuntimeError('COMPANION_RESPONSE_TOO_LARGE', 502, 'Runtime JSON response exceeded Companion limit');
    const text = new TextDecoder().decode(bytes);
    let parsed: JsonObject;
    try {
      parsed = JSON.parse(text) as JsonObject;
    } catch {
      throw new RuntimeError('COMPANION_INVALID_RUNTIME_JSON', 502, 'Runtime returned invalid JSON');
    }
    if (!response.ok) {
      const code = typeof parsed.error === 'string' ? parsed.error : 'RUNTIME_HTTP_ERROR';
      const message = typeof parsed.message === 'string' ? parsed.message : `Runtime request failed with HTTP ${response.status}`;
      const requestId = typeof parsed.requestId === 'string' ? parsed.requestId : response.headers.get('x-mcp-request-id') ?? undefined;
      throw new RuntimeError(code, response.status, message, requestId);
    }
    return parsed as T;
  }

  async binary(
    method: 'GET' | 'POST',
    path: string,
    options: RuntimeRequestOptions = {}
  ): Promise<RuntimeBinary> {
    const response = await this.request(method, path, undefined, options);
    const bytes = new Uint8Array(await response.arrayBuffer());
    const limit = options.maxResponseBytes ?? this.config.maxArtifactBytes;
    if (bytes.byteLength > limit) throw new RuntimeError('COMPANION_RESPONSE_TOO_LARGE', 502, 'Runtime binary response exceeded Companion limit');
    if (!response.ok) {
      throw new RuntimeError('RUNTIME_HTTP_ERROR', response.status, `Runtime binary request failed with HTTP ${response.status}`);
    }
    const result: RuntimeBinary = {
      bytes,
      contentType: response.headers.get('content-type') ?? 'application/octet-stream'
    };
    const requestId = response.headers.get('x-mcp-request-id');
    if (requestId) result.requestId = requestId;
    return result;
  }

  private async request(
    method: string,
    path: string,
    body: JsonValue | undefined,
    options: RuntimeRequestOptions
  ): Promise<Response> {
    validatePath(path);
    const basePath = this.config.baseUrl.pathname === '/' ? '' : this.config.baseUrl.pathname.replace(/\/$/, '');
    const url = new URL(`${basePath}${path}`, this.config.baseUrl.origin);
    const headers = new Headers({
      authorization: `Bearer ${this.config.token}`,
      accept: 'application/json',
      'x-mcp-request-id': randomUUID(),
      'x-mcp-protocol-version': 'v0'
    });
    for (const [name, value] of Object.entries(options.headers ?? {})) headers.set(name, value);
    const init: RequestInit = {
      method,
      headers,
      signal: AbortSignal.timeout(options.timeoutMs ?? this.config.timeoutMs)
    };
    if (body !== undefined) {
      headers.set('content-type', 'application/json');
      init.body = JSON.stringify(body);
    }
    try {
      return await fetch(url, init);
    } catch (error) {
      const message = error instanceof Error && error.name === 'TimeoutError'
        ? 'Minecraft Runtime request timed out'
        : 'Minecraft Runtime is unavailable';
      throw new RuntimeError(error instanceof Error && error.name === 'TimeoutError' ? 'COMPANION_RUNTIME_TIMEOUT' : 'COMPANION_RUNTIME_UNAVAILABLE', 503, message);
    }
  }
}
