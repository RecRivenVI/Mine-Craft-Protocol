import type { CallToolResult } from '@modelcontextprotocol/server';

import { RuntimeError } from './runtime-client.js';
import type { CompanionEnvelope, JsonValue } from './types.js';

export function envelope(data: JsonValue): CompanionEnvelope {
  return {
    data,
    companion: {
      protocol: 'v0',
      plane: 'data',
      dataPlaneOnly: true,
      dynamicPolicyApplied: false,
      transport: 'runtime_http'
    }
  };
}

function safeMessage(error: unknown): string {
  if (error instanceof RuntimeError) return error.message;
  return error instanceof Error ? error.message.replace(/Bearer\s+[^\s]+/gi, 'Bearer [REDACTED]') : 'Unknown Companion error';
}

export async function asToolResult(operation: () => Promise<JsonValue>): Promise<CallToolResult> {
  try {
    const structuredContent = envelope(await operation());
    return {
      content: [{ type: 'text', text: JSON.stringify(structuredContent) }],
      structuredContent
    };
  } catch (error) {
    const runtime = error instanceof RuntimeError ? error : undefined;
    const structuredContent = {
      error: {
        code: runtime?.code ?? 'COMPANION_ERROR',
        status: runtime?.status ?? 500,
        message: safeMessage(error),
        ...(runtime?.requestId ? { requestId: runtime.requestId } : {})
      },
      companion: {
        protocol: 'v0',
        plane: 'data',
        dataPlaneOnly: true,
        dynamicPolicyApplied: false
      }
    };
    return {
      isError: true,
      content: [{ type: 'text', text: JSON.stringify(structuredContent) }],
      structuredContent
    };
  }
}
