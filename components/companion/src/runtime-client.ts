import { randomUUID } from 'node:crypto';

import type { CompanionConfig } from './config.js';
import { readRuntimeDiscovery } from './discovery.js';
import type { JsonObject, JsonValue, RuntimeBinary, RuntimeRequestOptions } from './types.js';

function boundedHit(value: JsonValue | undefined): JsonObject | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const {kind,source}=value;
  if (typeof kind!=='string'||!['block','entity','miss','unknown'].includes(kind)
      ||typeof source!=='string'||!['native_interaction_pick','bounded_client_vanilla_ray','candidate_outline_ray','unknown'].includes(source)
      ||value.dataCategory!=='USER'||value.authority!=='CLIENT_KNOWN')return undefined;
  const result:JsonObject={kind,source,dataCategory:'USER',authority:'CLIENT_KNOWN'};
  if(kind==='unknown')return result;
  const vector=(key:string,integral:boolean):JsonObject|undefined=>{
    const v=value[key];if(!v||typeof v!=='object'||Array.isArray(v))return undefined;
    const out:JsonObject={};for(const axis of ['x','y','z']){const n=v[axis];if(typeof n!=='number'||!Number.isFinite(n)||Math.abs(n)>2147483647||integral&&!Number.isInteger(n))return undefined;out[axis]=n;}return out;
  };
  const position=vector('position',false),distance=value.distance;
  if(!position||typeof distance!=='number'||!Number.isFinite(distance)||distance<0||distance>32.001)return undefined;
  result.position=position;result.distance=distance;
  if(kind==='block'){const block=vector('block',true),face=value.face;if(!block||typeof face!=='string'||!['up','down','north','south','east','west'].includes(face))return undefined;result.block=block;result.face=face;}
  if(kind==='entity'){const type=value.entityType;if(typeof type!=='string'||type.length>256||!(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/).test(type))return undefined;result.entityType=type;}
  return result;
}

export class RuntimeError extends Error {
  readonly code: string;
  readonly status: number;
  readonly requestId?: string;
  readonly controlState?: 'MANUALLY_REVOKED';
  readonly reconsentRequired?: boolean;
  readonly manualRevocationReason?: string;
  readonly control?: JsonObject;
  readonly executionOutcome?: 'not_confirmed';
  readonly retrySafe?: false;
  readonly retryAfterMillis?: number;
  readonly rateLimit?: JsonObject;
  readonly actualHit?: JsonObject;
  readonly resourceRevision?: JsonObject;
  readonly readiness?: JsonObject;

  constructor(code: string, status: number, message: string, requestId?: string, details?: JsonObject) {
    super(message);
    this.name = 'RuntimeError';
    this.code = code;
    this.status = status;
    if (requestId !== undefined) this.requestId = requestId;
    const readiness = details?.readiness;
    if (['ASSERTION_FAILED', 'WAIT_TIMEOUT'].includes(code) && readiness
        && typeof readiness === 'object' && !Array.isArray(readiness)
        && ['screen', 'hud'].includes(String(readiness.kind)) && typeof readiness.ready === 'boolean'
        && readiness.checks && typeof readiness.checks === 'object' && !Array.isArray(readiness.checks)
        && Array.isArray(readiness.failures) && readiness.failures.length <= 8
        && readiness.failures.every(value => typeof value === 'string' && [
          'SCREEN_NOT_READY', 'OVERLAY_PRESENT', 'VIEWPORT_UNAVAILABLE',
          'TARGET_NOT_FOUND_OR_NOT_VISIBLE', 'TARGET_AMBIGUOUS', 'TARGET_INACTIVE',
          'TARGET_NOT_VISIBLE', 'ACTION_UNSUPPORTED', 'TARGET_BOUNDS_UNAVAILABLE'
        ].includes(value))) {
      const checks:JsonObject = {};
      for (const key of ['screen', 'overlay', 'viewport', 'selector', 'active', 'visible', 'action', 'bounds'])
        if (typeof readiness.checks[key] === 'boolean') checks[key] = readiness.checks[key];
      this.readiness = {kind: readiness.kind!, ready: readiness.ready, checks, failures: readiness.failures};
    }
    if (code === 'STALE_RESOURCE_REVISION' && details?.reinspectRequired === true && details.retrySafe === false
        && typeof details.expectedRevision === 'number' && Number.isSafeInteger(details.expectedRevision) && details.expectedRevision >= 0
        && typeof details.actualRevision === 'number' && Number.isSafeInteger(details.actualRevision) && details.actualRevision >= 0
        && details.actualRevision !== details.expectedRevision) {
      this.resourceRevision = { expectedRevision: details.expectedRevision, actualRevision: details.actualRevision, reinspectRequired: true, retrySafe: false };
    }
    if(code==='WORLD_TARGET_NOT_HIT'){
      const hit=boundedHit(details?.actualHit);if(hit)this.actualHit=hit;
    }
    const budget = details?.rateLimit;
    const retry = details?.retryAfterMillis;
    if (status === 429 && ['RATE_LIMITED', 'EXPENSIVE_OPERATION_RATE_LIMITED'].includes(code)
        && typeof retry === 'number' && Number.isSafeInteger(retry) && retry > 0 && retry <= 60000
        && budget && typeof budget === 'object' && !Array.isArray(budget)) {
      const { scope, category, operationCategory, capacity, remaining, refillPerSecond, cost } = budget;
      if (typeof scope === 'string' && ['principal', 'connection'].includes(scope)
          && typeof category === 'string' && /^[a-z_]{1,32}$/.test(category)
          && typeof operationCategory === 'string' && /^[a-z_]{1,32}$/.test(operationCategory)
          && typeof capacity === 'number' && Number.isSafeInteger(capacity) && capacity > 0
          && typeof remaining === 'number' && Number.isSafeInteger(remaining) && remaining >= 0 && remaining <= capacity
          && typeof refillPerSecond === 'number' && Number.isFinite(refillPerSecond) && refillPerSecond > 0
          && cost === 1) {
        this.retryAfterMillis = retry;
        this.rateLimit = { scope, category, operationCategory, capacity, remaining, refillPerSecond, cost };
      }
    }
    if (['REQUEST_CANCELLED', 'RUNTIME_SHUTTING_DOWN', 'REQUEST_DEADLINE_EXCEEDED', 'DEADLINE_EXCEEDED'].includes(code)
        && details?.executionOutcome === 'not_confirmed' && details.retrySafe === false) {
      this.executionOutcome = 'not_confirmed';
      this.retrySafe = false;
    }
    const control = details?.control;
    if (control && typeof control === 'object' && !Array.isArray(control)
        && ['READ', 'OPERATE', 'TAKEOVER'].includes(String(control.mode))
        && typeof control.reconsentRequired === 'boolean') {
      this.control = control;
    }
    if (code === 'USER_MANUALLY_ENDED_CONTROL') {
      this.controlState = 'MANUALLY_REVOKED';
      this.reconsentRequired = true;
      this.manualRevocationReason = typeof details?.manualRevocationReason === 'string'
        ? details.manualRevocationReason : 'human_manual_revocation';
    }
  }
}

function validatePath(path: string): void {
  if (!path.startsWith('/v0/') || path.includes('\\') || path.includes('\0') || path.includes('..')) {
    throw new Error('Companion Runtime path is outside the typed /v0 namespace');
  }
}

export class RuntimeClient {
  readonly config: CompanionConfig;
  private verified = false;
  private verification?: Promise<void>;

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
    const limit = options.maxResponseBytes ?? this.config.maxJsonBytes;
    const bytes = await this.readBounded(response, limit, 'Runtime JSON response exceeded Companion limit');
    const text = new TextDecoder().decode(bytes);
    let parsed: JsonObject;
    try {
      parsed = JSON.parse(text) as JsonObject;
    } catch {
      throw new RuntimeError('COMPANION_INVALID_RUNTIME_JSON', 502, 'Runtime returned invalid JSON');
    }
    if (!response.ok) {
      throw this.responseError(response, parsed);
    }
    return parsed as T;
  }

  async binary(
    method: 'GET' | 'POST',
    path: string,
    options: RuntimeRequestOptions = {}
  ): Promise<RuntimeBinary> {
    const response = await this.request(method, path, undefined, options);
    const limit = options.maxResponseBytes ?? this.config.maxArtifactBytes;
    const bytes = await this.readBounded(response, response.ok ? limit : Math.min(limit, this.config.maxJsonBytes), 'Runtime binary response exceeded Companion limit');
    if (!response.ok) {
      let parsed: JsonObject = {};
      try {
        const value: unknown = JSON.parse(new TextDecoder().decode(bytes));
        if (value && typeof value === 'object' && !Array.isArray(value)) parsed = value as JsonObject;
      } catch { /* Non-JSON failures retain their HTTP status without inventing metadata. */ }
      throw this.responseError(response, parsed);
    }
    const result: RuntimeBinary = {
      bytes,
      contentType: response.headers.get('content-type') ?? 'application/octet-stream'
    };
    const requestId = response.headers.get('x-mcp-request-id');
    if (requestId) result.requestId = requestId;
    return result;
  }

  private responseError(response: Response, parsed: JsonObject): RuntimeError {
    const code = typeof parsed.error === 'string' ? parsed.error : 'RUNTIME_HTTP_ERROR';
    const message = typeof parsed.message === 'string' ? parsed.message : `Runtime request failed with HTTP ${response.status}`;
    const requestId = typeof parsed.requestId === 'string' ? parsed.requestId : response.headers.get('x-mcp-request-id') ?? undefined;
    return new RuntimeError(code, response.status, message, requestId, parsed);
  }

  private async request(
    method: string,
    path: string,
    body: JsonValue | undefined,
    options: RuntimeRequestOptions
  ): Promise<Response> {
    validatePath(path);
    if (this.config.discovery) {
      try {
        const current = readRuntimeDiscovery(this.config.instanceDirectory!, this.config.discovery.target);
        if (current.runtimeInstanceId !== this.config.discovery.runtimeInstanceId || current.baseUrl !== this.config.discovery.baseUrl
            || current.pid !== this.config.discovery.pid || current.processStartedAt !== this.config.discovery.processStartedAt) {
          throw new Error('Runtime instance changed; explicitly reconnect');
        }
      } catch (error) {
        throw new RuntimeError('COMPANION_RUNTIME_INSTANCE_CHANGED', 409, error instanceof Error ? error.message : 'Runtime discovery changed');
      }
      if (!this.verified) {
        this.verification ??= this.verifyInstance();
        await this.verification;
      }
    }
    const basePath = this.config.baseUrl.pathname === '/' ? '' : this.config.baseUrl.pathname.replace(/\/$/, '');
    const url = new URL(`${basePath}${path}`, this.config.baseUrl.origin);
    const headers = new Headers({
      authorization: `Bearer ${this.config.token}`,
      accept: 'application/json',
      'x-mcp-request-id': randomUUID(),
      'x-mcp-protocol-version': 'v0'
    });
    for (const [name, value] of Object.entries(options.headers ?? {})) headers.set(name, value);
    if (this.config.discovery) headers.set('x-mcp-runtime-instance', this.config.discovery.runtimeInstanceId);
    const init: RequestInit = {
      method,
      headers,
      signal: options.signal
        ? AbortSignal.any([options.signal, AbortSignal.timeout(options.timeoutMs ?? this.config.timeoutMs)])
        : AbortSignal.timeout(options.timeoutMs ?? this.config.timeoutMs)
    };
    if (body !== undefined) {
      headers.set('content-type', 'application/json');
      init.body = JSON.stringify(body);
    }
    try {
      const response = await fetch(url, { ...init, redirect: 'error' });
      if (this.config.discovery && response.headers.get('x-mcp-runtime-instance') !== this.config.discovery.runtimeInstanceId) {
        await response.body?.cancel().catch(() => undefined);
        throw new RuntimeError('COMPANION_RUNTIME_INSTANCE_MISMATCH', 409, 'Runtime response identity mismatch; refusing this connection');
      }
      return response;
    } catch (error) {
      if (error instanceof RuntimeError) throw error;
      const message = error instanceof Error && error.name === 'TimeoutError'
        ? 'Minecraft Runtime request timed out'
        : 'Minecraft Runtime is unavailable';
      throw new RuntimeError(error instanceof Error && error.name === 'TimeoutError' ? 'COMPANION_RUNTIME_TIMEOUT' : 'COMPANION_RUNTIME_UNAVAILABLE', 503, message);
    }
  }

  private async verifyInstance(): Promise<void> {
    // Safe read before the first action. Discovery never grants a scope, Lease or consent.
    const discovery = this.config.discovery!;
    const response = await fetch(new URL('/v0/session', this.config.baseUrl), {
      headers: { authorization: `Bearer ${this.config.token}`, 'x-mcp-runtime-instance': discovery.runtimeInstanceId },
      signal: AbortSignal.timeout(this.config.timeoutMs), redirect: 'error'
    });
    const bytes = await this.readBounded(response, this.config.maxJsonBytes, 'Runtime identity response exceeded limit');
    const session = JSON.parse(new TextDecoder().decode(bytes)) as { target?: string; runtimeConnection?: { runtimeInstanceId?: string } };
    if (!response.ok || response.headers.get('x-mcp-runtime-instance') !== discovery.runtimeInstanceId
        || session.runtimeConnection?.runtimeInstanceId !== discovery.runtimeInstanceId || session.target !== discovery.target) {
      throw new RuntimeError('COMPANION_RUNTIME_INSTANCE_MISMATCH', 409, 'Runtime discovery handshake failed; no control request dispatched');
    }
    this.verified = true;
  }

  private async readBounded(response: Response, limit: number, message: string): Promise<Uint8Array> {
    const declared = response.headers.get('content-length');
    if (declared !== null) {
      const length = Number(declared);
      if (!Number.isSafeInteger(length) || length < 0 || length > limit) {
        await response.body?.cancel('response budget exceeded').catch(() => undefined);
        throw new RuntimeError('COMPANION_RESPONSE_TOO_LARGE', 502, message);
      }
    }
    if (!response.body) return new Uint8Array();
    const reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let total = 0;
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        total += value.byteLength;
        if (!Number.isSafeInteger(total) || total > limit) {
          await reader.cancel('response budget exceeded').catch(() => undefined);
          throw new RuntimeError('COMPANION_RESPONSE_TOO_LARGE', 502, message);
        }
        chunks.push(value);
      }
    } finally {
      reader.releaseLock();
    }
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
      bytes.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return bytes;
  }
}
