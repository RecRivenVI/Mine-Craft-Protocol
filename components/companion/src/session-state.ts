import { RuntimeError } from './runtime-client.js';

export class CompanionSessionState {
  leaseId: string | undefined;
  debugArmId: string | undefined;

  requireLease(explicit?: string): string {
    const value = explicit ?? this.leaseId;
    if (!value) throw new RuntimeError('CONTROL_LEASE_REQUIRED', 409, 'Acquire a Control Lease first');
    return value;
  }

  requireDebugArm(explicit?: string): string {
    const value = explicit ?? this.debugArmId;
    if (!value) throw new RuntimeError('DEBUG_ARM_REQUIRED', 409, 'Arm Debug first');
    return value;
  }
}
