export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonObject | JsonValue[];
export type JsonObject = { [key: string]: JsonValue };

export interface RuntimeRequestOptions {
  headers?: Record<string, string>;
  timeoutMs?: number;
  maxResponseBytes?: number;
  signal?: AbortSignal;
}

export interface AgentPointerState {
  plane: 'HIDDEN' | 'GUI_ABSOLUTE' | 'GAMEPLAY_RELATIVE';
  x: number;
  y: number;
  alpha: number;
  evidencePixels: false;
}
export interface OperatorPresenceState extends ControlModeState {
  presenceActive: boolean;
  hostCursorPolicy: 'never_capture_or_warp_during_takeover';
  nativeInputPolicy: 'exclusive_except_physical_escape';
  activeGestureId: string;
  pointerOwnership: 'none' | 'serialized_gesture' | 'lease_raw_stream';
  agentPointer: AgentPointerState;
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

export type AgentMode = 'READ' | 'OPERATE' | 'TAKEOVER';
export interface ModeVersion { controlSessionId: string; generation: number }
export interface ControlModeState {
  mode: AgentMode;
  takeoverActive: boolean;
  modeVersion: ModeVersion;
  reconsentRequired: boolean;
  reconsentScope?: 'TAKEOVER_ONLY';
  modeTransitionReason: string;
}
