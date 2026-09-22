package dev.zomboidds.bridge.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates between protocol JSON and Java values. */
public final class JsonCodec {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            // Integral numbers come back as Long, the rest as Double (instead of all Double).
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    public String encode(OutboundMessage message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", Protocol.VERSION);
        envelope.put("type", message.type());
        envelope.put("seq", message.seq());
        envelope.put("data", message.data());
        return gson.toJson(envelope);
    }

    public Command decodeCommand(String json) throws ProtocolException {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new ProtocolException("message is not a JSON object", null);
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ProtocolException("malformed JSON: " + e.getMessage(), null);
        }

        String id = string(root, "id");
        String version = string(root, "v");
        if (!String.valueOf(Protocol.VERSION).equals(version)) {
            throw new ProtocolException("unsupported protocol version " + version, id);
        }
        if (!Protocol.TYPE_COMMAND.equals(string(root, "type"))) {
            throw new ProtocolException("expected type 'command'", id);
        }
        if (id == null || id.isBlank()) {
            throw new ProtocolException("command without id", null);
        }
        String name = string(root, "name");
        if (name == null || name.isBlank()) {
            throw new ProtocolException("command without name", id);
        }
        Map<String, Object> args = root.has("args") && root.get("args").isJsonObject()
                ? gson.fromJson(root.get("args"), MAP_TYPE)
                : Map.of();
        return new Command(id, name, args);
    }

    private static String string(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
