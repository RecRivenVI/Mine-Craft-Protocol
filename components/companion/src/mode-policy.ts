// Static discovery metadata only. Native Runtime performs authorization and intent checks.
export const TOOL_MODE_POLICY = {
  "minecraft_get_session": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_get_capabilities": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_get_ui": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_get_state": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_deep_observe": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_query_world": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_execute_player_command": {
    "default": "COMMAND_ROUTE_DEPENDENT",
    "discriminator": "route",
    "actions": { "AUTO": "COMMAND_ROUTE_DEPENDENT", "CHAT": "TAKEOVER_REQUIRED", "NATIVE": "OPERATE_OR_TAKEOVER" },
    "typedActions": { "execute": "COMMAND_ROUTE_DEPENDENT", "discover": "READ_COMPATIBLE", "batch": "OPERATE_OR_TAKEOVER" },
    "note": "action defaults to execute; AUTO chooses NATIVE in OPERATE, CHAT otherwise. Discovery is read+command and never executes; batch is bounded NATIVE only. Never changes intent or permissions."
  },
  "minecraft_control": {
    "discriminator": "action",
    "actions": {
      "status": "READ_COMPATIBLE",
      "begin_task": "READ_COMPATIBLE",
      "heartbeat_task": "READ_COMPATIBLE",
      "end_task": "READ_COMPATIBLE",
      "set_mode": "MODE_INDEPENDENT",
      "acquire": "MODE_INDEPENDENT",
      "renew": "MODE_INDEPENDENT",
      "release": "MODE_INDEPENDENT",
      "release_inputs": "TAKEOVER_REQUIRED",
      "emergency_release": "MODE_INDEPENDENT"
    },
    "note": "Only explicit acquire establishes TAKEOVER; set_mode only READ/OPERATE. No authority is granted by the label."
  },
  "minecraft_interact_ui": {
    "default": "TAKEOVER_REQUIRED"
  },
  "minecraft_run_input_pipeline": {
    "default": "TAKEOVER_REQUIRED"
  },
  "minecraft_get_operation": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_wait_operation": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_cancel_operation": {
    "default": "MODE_INDEPENDENT"
  },
  "minecraft_wait": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_assert": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_capture": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_start_recording": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_recording": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_get_artifact": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_diagnostics": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_peer": {
    "default": "READ_COMPATIBLE"
  },
  "minecraft_fixture": {
    "default": "OPERATE_OR_TAKEOVER"
  },
  "minecraft_debug_arm": {
    "default": "MODE_INDEPENDENT",
    "note": "Credential management still requires Debug scope; does not change mode."
  },
  "minecraft_debug": {
    "discriminator": "action",
    "actions": {
      "capabilities": "READ_COMPATIBLE",
      "mutate": "OPERATE_OR_TAKEOVER",
      "batch": "OPERATE_OR_TAKEOVER",
      "act_start": "READ_COMPATIBLE",
      "act_finish": "READ_COMPATIBLE"
    }
  }
} as const;
