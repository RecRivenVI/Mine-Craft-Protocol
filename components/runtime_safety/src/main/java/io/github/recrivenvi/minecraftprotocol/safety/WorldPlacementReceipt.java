package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonObject;

/** One owner-thread native placement receipt, distinct from server confirmation. */
public final class WorldPlacementReceipt {
    private JsonObject before, result;
    private String block;
    private boolean pending;

    public boolean begin(JsonObject state, String expectedBlock) {
        if (pending || result != null) return false;
        before = state.deepCopy();
        block = expectedBlock;
        pending = true;
        return true;
    }

    public boolean pending() {
        return pending;
    }

    public boolean record(String nativeResult, boolean consumed, JsonObject after) {
        if (!pending || result != null) return false;
        pending = false;
        boolean changed = !before.equals(after);
        boolean predicted =
                !nativeResult.equals("fail")
                        && consumed
                        && changed
                        && after.has("block")
                        && block.equals(after.get("block").getAsString());
        result = new JsonObject();
        result.addProperty("nativeUseOnResultObserved", true);
        result.addProperty("nativeUseOnResult", nativeResult);
        result.addProperty("nativeConsumesAction", consumed);
        result.addProperty("clientDestinationChanged", changed);
        result.addProperty("clientPlacementPredicted", predicted);
        result.addProperty(
                "placementOutcome",
                nativeResult.equals("fail")
                        ? "native_rejected"
                        : predicted
                                ? "client_placement_predicted"
                                : consumed
                                        ? "native_consumed_not_confirmed"
                                        : "native_not_consumed");
        result.addProperty(
                "reason",
                nativeResult.equals("fail")
                        ? "native_use_on_failed"
                        : predicted
                                ? "native_use_on_consumed_and_expected_destination_changed"
                                : consumed
                                        ? "native_use_on_consumed_without_expected_destination_change"
                                        : "native_use_on_did_not_consume_action");
        JsonObject states = new JsonObject();
        states.add("before", before.deepCopy());
        states.add("after", after.deepCopy());
        result.add("placementState", states);
        result.addProperty("effectConfirmed", false);
        result.addProperty("serverValidationObserved", false);
        return true;
    }

    public JsonObject snapshot() {
        if (result != null) return result.deepCopy();
        JsonObject unavailable = new JsonObject();
        unavailable.addProperty("nativeUseOnResultObserved", false);
        unavailable.addProperty("clientPlacementPredicted", false);
        unavailable.addProperty("placementOutcome", "native_result_not_observed");
        unavailable.addProperty("reason", "native_use_on_result_not_observed");
        unavailable.addProperty("effectConfirmed", false);
        unavailable.addProperty("serverValidationObserved", false);
        return unavailable;
    }
}
