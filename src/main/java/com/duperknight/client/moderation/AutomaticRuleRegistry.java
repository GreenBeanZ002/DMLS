package com.duperknight.client.moderation;

import com.duperknight.DMLS;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict loader for the bundled automatic-moderation rule definitions. */
final class AutomaticRuleRegistry {
    private static final String RESOURCE = "/assets/dmls/moderation_rules.json";
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_WINDOW_MILLIS = 600_000L;
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "rules");
    private static final Set<String> COMMON_FIELDS = Set.of("id", "type", "labelKey", "defaultEnabled");
    private static final Set<String> SPAM_FIELDS = Set.of(
            "repeatCount", "repeatWindowMs", "similarityThreshold", "fuzzyMinLength",
            "burstCount", "burstWindowMs");
    private static final Set<String> TEXT_WALL_FIELDS = Set.of("minLines");
    private static final Set<String> ALL_RULE_FIELDS;

    static {
        Set<String> fields = new HashSet<>(COMMON_FIELDS);
        fields.addAll(SPAM_FIELDS);
        fields.addAll(TEXT_WALL_FIELDS);
        ALL_RULE_FIELDS = Set.copyOf(fields);
    }

    private AutomaticRuleRegistry() {
    }

    record LoadResult(List<AutomaticRuleDefinition> definitions, String error) {
        LoadResult {
            definitions = List.copyOf(definitions);
            error = Objects.requireNonNullElse(error, "");
        }

        boolean successful() {
            return error.isEmpty();
        }
    }

    static LoadResult loadBundled() {
        try (InputStream input = AutomaticRuleRegistry.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing bundled resource " + RESOURCE);
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return new LoadResult(parse(reader), "");
            }
        } catch (Exception exception) {
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            DMLS.LOGGER.warn("Automatic moderation rules are disabled: {}", error, exception);
            return new LoadResult(List.of(), error);
        }
    }

    static List<AutomaticRuleDefinition> parse(Reader source) throws IOException {
        Objects.requireNonNull(source, "source");
        JsonReader json = new JsonReader(source);
        try {
            requireToken(json, JsonToken.BEGIN_OBJECT, "Automatic-rules root must be an object");
            json.beginObject();
            Set<String> seen = new HashSet<>();
            Integer schemaVersion = null;
            List<AutomaticRuleDefinition> definitions = null;
            while (json.hasNext()) {
                String field = json.nextName();
                if (!ROOT_FIELDS.contains(field)) throw new IOException("Unknown root field: " + field);
                if (!seen.add(field)) throw new IOException("Duplicate root field: " + field);
                if (field.equals("schemaVersion")) {
                    schemaVersion = readInteger(json, "schemaVersion");
                } else {
                    definitions = readRules(json);
                }
            }
            json.endObject();
            requireToken(json, JsonToken.END_DOCUMENT, "Automatic-rules JSON contains trailing data");
            if (schemaVersion == null) throw new IOException("Missing root field: schemaVersion");
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IOException("Unsupported automatic-rules schema version: " + schemaVersion);
            }
            if (definitions == null) throw new IOException("Missing root field: rules");
            return List.copyOf(definitions);
        } catch (IllegalStateException | NumberFormatException exception) {
            throw new IOException("Automatic-rules resource is not valid JSON", exception);
        }
    }

    private static List<AutomaticRuleDefinition> readRules(JsonReader json) throws IOException {
        requireToken(json, JsonToken.BEGIN_ARRAY, "rules must be an array");
        json.beginArray();
        List<AutomaticRuleDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        while (json.hasNext()) {
            AutomaticRuleDefinition definition = readRule(json, definitions.size());
            if (!ids.add(definition.id())) throw new IOException("Duplicate automatic rule id: " + definition.id());
            definitions.add(definition);
        }
        json.endArray();
        return definitions;
    }

    private static AutomaticRuleDefinition readRule(JsonReader json, int index) throws IOException {
        requireToken(json, JsonToken.BEGIN_OBJECT, "Rule " + index + " must be an object");
        json.beginObject();
        Map<String, Object> values = new HashMap<>();
        while (json.hasNext()) {
            String field = json.nextName();
            if (!ALL_RULE_FIELDS.contains(field)) throw new IOException("Unknown field in rule " + index + ": " + field);
            if (values.containsKey(field)) throw new IOException("Duplicate field in rule " + index + ": " + field);
            values.put(field, readValue(json, field));
        }
        json.endObject();

        String id = requiredString(values, "id", index);
        if (!RULE_ID.matcher(id).matches()) throw new IOException("Invalid rule id: " + id);
        String type = requiredString(values, "type", index);
        String labelKey = requiredString(values, "labelKey", index);
        boolean defaultEnabled = requiredBoolean(values, "defaultEnabled", index);

        return switch (type) {
            case "spam" -> buildSpam(index, values, id, labelKey, defaultEnabled);
            case "text_wall" -> buildTextWall(index, values, id, labelKey, defaultEnabled);
            default -> throw new IOException("Unsupported rule type in rule " + index + ": " + type);
        };
    }

    private static AutomaticRuleDefinition buildSpam(int index, Map<String, Object> values,
                                                      String id, String labelKey, boolean defaultEnabled)
            throws IOException {
        requireExactFields(index, values, SPAM_FIELDS);
        int repeatCount = positiveCount(values, "repeatCount", index);
        long repeatWindowMs = positiveWindow(values, "repeatWindowMs", index);
        double similarity = requiredDouble(values, "similarityThreshold", index);
        if (!(similarity > 0.0D && similarity <= 1.0D)) {
            throw new IOException("Rule " + index + " similarityThreshold must be greater than 0 and at most 1");
        }
        int fuzzyMinLength = requiredInteger(values, "fuzzyMinLength", index);
        if (fuzzyMinLength < 1) throw new IOException("Rule " + index + " fuzzyMinLength must be positive");
        int burstCount = positiveCount(values, "burstCount", index);
        long burstWindowMs = positiveWindow(values, "burstWindowMs", index);
        return new SpamRuleDefinition(id, labelKey, defaultEnabled, repeatCount,
                repeatWindowMs * 1_000_000L, similarity, fuzzyMinLength, burstCount,
                burstWindowMs * 1_000_000L);
    }

    private static AutomaticRuleDefinition buildTextWall(int index, Map<String, Object> values,
                                                          String id, String labelKey, boolean defaultEnabled)
            throws IOException {
        requireExactFields(index, values, TEXT_WALL_FIELDS);
        int minLines = requiredInteger(values, "minLines", index);
        if (minLines < 2) throw new IOException("Rule " + index + " minLines must be at least 2");
        return new TextWallRuleDefinition(id, labelKey, defaultEnabled, minLines);
    }

    private static void requireExactFields(int index, Map<String, Object> values, Set<String> typeFields)
            throws IOException {
        Set<String> expected = new HashSet<>(COMMON_FIELDS);
        expected.addAll(typeFields);
        for (String field : expected) {
            if (!values.containsKey(field)) throw new IOException("Missing field in rule " + index + ": " + field);
        }
        for (String field : values.keySet()) {
            if (!expected.contains(field)) throw new IOException("Field " + field + " is invalid for rule " + index);
        }
    }

    private static Object readValue(JsonReader json, String field) throws IOException {
        return switch (field) {
            case "id", "type", "labelKey" -> readString(json, field);
            case "defaultEnabled" -> readBoolean(json, field);
            case "repeatCount", "repeatWindowMs", "fuzzyMinLength", "burstCount", "burstWindowMs", "minLines" ->
                    readInteger(json, field);
            case "similarityThreshold" -> readDouble(json, field);
            default -> throw new IOException("Unsupported field: " + field);
        };
    }

    private static String readString(JsonReader json, String field) throws IOException {
        requireToken(json, JsonToken.STRING, field + " must be a string");
        return json.nextString();
    }

    private static boolean readBoolean(JsonReader json, String field) throws IOException {
        requireToken(json, JsonToken.BOOLEAN, field + " must be a boolean");
        return json.nextBoolean();
    }

    private static int readInteger(JsonReader json, String field) throws IOException {
        requireToken(json, JsonToken.NUMBER, field + " must be an integer");
        String value = json.nextString();
        if (!value.matches("-?(?:0|[1-9][0-9]*)")) throw new IOException(field + " must be an integer");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException(field + " is outside the supported integer range", exception);
        }
    }

    private static double readDouble(JsonReader json, String field) throws IOException {
        requireToken(json, JsonToken.NUMBER, field + " must be a number");
        double value = json.nextDouble();
        if (!Double.isFinite(value)) throw new IOException(field + " must be finite");
        return value;
    }

    private static String requiredString(Map<String, Object> values, String field, int index) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IOException("Missing or blank field in rule " + index + ": " + field);
        }
        return string;
    }

    private static boolean requiredBoolean(Map<String, Object> values, String field, int index) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof Boolean flag)) throw new IOException("Missing field in rule " + index + ": " + field);
        return flag;
    }

    private static int requiredInteger(Map<String, Object> values, String field, int index) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof Integer number)) throw new IOException("Missing field in rule " + index + ": " + field);
        return number;
    }

    private static double requiredDouble(Map<String, Object> values, String field, int index) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof Double number)) throw new IOException("Missing field in rule " + index + ": " + field);
        return number;
    }

    private static int positiveCount(Map<String, Object> values, String field, int index) throws IOException {
        int count = requiredInteger(values, field, index);
        if (count < 2) throw new IOException("Rule " + index + " " + field + " must be at least 2");
        return count;
    }

    private static long positiveWindow(Map<String, Object> values, String field, int index) throws IOException {
        int window = requiredInteger(values, field, index);
        if (window <= 0 || window > MAX_WINDOW_MILLIS) {
            throw new IOException("Rule " + index + " " + field + " must be between 1 and "
                    + MAX_WINDOW_MILLIS + " milliseconds");
        }
        return window;
    }

    private static void requireToken(JsonReader json, JsonToken expected, String message) throws IOException {
        if (json.peek() != expected) throw new IOException(message);
    }
}
