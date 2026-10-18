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
  gameCaptured?: boolean;
  gameCursorVisible?: boolean;
  gameCursorRevision?: number;
  evidencePixels: false;
}
export interface OperatorPresenceState extends ControlModeState {
  presenceActive: boolean;
  hostCursorPolicy: 'never_capture_or_warp_during_takeover';
  /** Observed state; Vanilla may capture outside TAKEOVER. Absent while closing. */
  hostCursorCaptured?: boolean;
  /** @deprecated Compatibility only; native-click capture grants were removed. */
  hostCursorCaptureGranted: false;
  /** @deprecated Compatibility only; not a human-verification counter. */
  nativeCaptureGrants: 0;
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
export type UiDataCategory = 'USER' | 'DEBUG';
export interface UiContract {
  domain: 'UI';
  readCategories: UiDataCategory[];
  readModeIndependent: true;
  legacyCategorySemantics: 'takeover_input_and_typed_operate_mutation_not_read_restrictions';
  defaultReadCategory: 'USER';
  takeoverCategory: 'USER';
  operateCategory: 'DEBUG';
  debugReadScope: 'debug.ui.read';
  debugReadRequiresArm: false;
  milestoneComplete: boolean;
}
export interface ModeVersion { controlSessionId: string; generation: number }
export interface ControlModeState {
  authorization?: {
    principalId: string;
    grantedScopes: string[];
    grantsAuthority: false;
    sessionOpen: boolean;
    inputLease: { held: boolean; remainingTtlMs: number };
    debugArm: { status: 'armed' | 'disarmed'; remainingTtlMs: number; namespaces: string[]; contextValidated: false };
  };
  agentTask?: AgentTaskState;
  mode: AgentMode;
  takeoverActive: boolean;
  modeVersion: ModeVersion;
  reconsentRequired: boolean;
  reconsentScope?: 'TAKEOVER_ONLY';
  modeTransitionReason: string;
}

export interface AgentTaskState {
  taskId: string;
  status: 'IDLE' | 'ACTIVE' | 'ENDED' | 'EXPIRED' | 'INTERRUPTED' | 'CLOSED';
  reason: string;
  generation: number;
  active: boolean;
  expiresAtMillis: number;
  deadlineAtMillis: number;
  controlSessionId: string;
  grantsAuthority: false;
}
