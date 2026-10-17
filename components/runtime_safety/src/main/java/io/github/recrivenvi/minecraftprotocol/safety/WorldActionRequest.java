package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

/** One bounded USER gesture. This describes intent, never authorization or a promised effect. */
public record WorldActionRequest(String targetRef, String action, int holdMs, JsonObject options) {
    public static final int MAX_HOLD_MILLIS = 10000;
    public static final List<String> ACTIONS =
            List.of("aim", "attack", "use", "mine", "use_hold", "place");
    public static final Set<String> FACES = Set.of("up", "down", "north", "south", "east", "west");

    public static WorldActionRequest parse(JsonObject step) {
        if (!Set.of(
                        "type",
                        "category",
                        "targetRef",
                        "action",
                        "holdMs",
                        "hand",
                        "face",
                        "hit",
                        "expectedDestinationBlockId")
                .containsAll(step.keySet()))
            throw new IllegalArgumentException("Unknown world action field");
        String ref = text(step, "targetRef", "");
        if (!text(step, "category", "USER").equals("USER"))
            throw new IllegalArgumentException("World player actions are USER only");
        if (!ref.startsWith("world:") || ref.length() > 80)
            throw new IllegalArgumentException("Discovered world targetRef required");
        String action = text(step, "action", "aim");
        if (!ACTIONS.contains(action))
            throw new IllegalArgumentException("Unsupported world action");
        boolean held = action.equals("mine") || action.equals("use_hold");
        int duration = action.equals("mine") ? 5000 : action.equals("use_hold") ? 1000 : 80;
        if (step.has("holdMs")) {
            if (!held
                    || !step.get("holdMs").isJsonPrimitive()
                    || !step.getAsJsonPrimitive("holdMs").isNumber())
                throw new IllegalArgumentException("holdMs belongs to mine/use_hold");
            duration = step.get("holdMs").getAsBigDecimal().intValueExact();
            if (duration < 50 || duration > MAX_HOLD_MILLIS)
                throw new IllegalArgumentException("holdMs must be 50..10000");
        }
        JsonObject options = new JsonObject();
        options.addProperty("holdMs", duration);
        if (action.equals("use_hold")) {
            String hand = text(step, "hand", "main_hand");
            if (!Set.of("main_hand", "off_hand").contains(hand))
                throw new IllegalArgumentException("hand must be main_hand or off_hand");
            options.addProperty("hand", hand);
        } else if (step.has("hand")) throw new IllegalArgumentException("hand belongs to use_hold");
        if (action.equals("place")) {
            String face = text(step, "face", "");
            if (!FACES.contains(face))
                throw new IllegalArgumentException("place requires a named face");
            options.addProperty("face", face);
            double u = .5, v = .5;
            if (step.has("hit")) {
                if (!step.get("hit").isJsonObject())
                    throw new IllegalArgumentException("hit must be face-local u/v");
                JsonObject hit = step.getAsJsonObject("hit");
                if (!Set.of("u", "v").containsAll(hit.keySet()))
                    throw new IllegalArgumentException("Unknown hit field");
                u = coordinate(hit, "u");
                v = coordinate(hit, "v");
            }
            options.addProperty("u", u);
            options.addProperty("v", v);
            if (step.has("expectedDestinationBlockId")) {
                String id = text(step, "expectedDestinationBlockId", "");
                if (id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                    throw new IllegalArgumentException("Invalid expected block ID");
                options.addProperty("expectedDestinationBlockId", id);
            }
        } else if (step.has("face") || step.has("hit") || step.has("expectedDestinationBlockId"))
            throw new IllegalArgumentException("Face/destination fields belong to place");
        return new WorldActionRequest(ref, action, duration, options);
    }

    private static String text(JsonObject source, String key, String fallback) {
        if (!source.has(key)) return fallback;
        if (!source.get(key).isJsonPrimitive() || !source.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Invalid " + key);
        return source.get(key).getAsString();
    }

    private static double coordinate(JsonObject source, String key) {
        if (!source.has(key)) return .5;
        if (!source.get(key).isJsonPrimitive() || !source.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Invalid face coordinate");
        double value = source.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < .05 || value > .95)
            throw new IllegalArgumentException("Face coordinates must be 0.05..0.95");
        return value;
    }
}
