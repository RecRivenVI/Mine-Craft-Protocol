export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonObject | JsonValue[];
export type JsonObject = { [key: string]: JsonValue };

export interface RuntimeRequestOptions {
  headers?: Record<string, string>;
  timeoutMs?: number;
  maxResponseBytes?: number;
}

export interface RuntimeBinary {
  bytes: Uint8Array;
  contentType: string;
  requestId?: string;
}

export interface CompanionEnvelope {
  data: JsonValue;
  companion: {
    protocol: 'v0';
    plane: 'data';
    dataPlaneOnly: true;
    dynamicPolicyApplied: false;
    transport: 'runtime_http';
  };
}
