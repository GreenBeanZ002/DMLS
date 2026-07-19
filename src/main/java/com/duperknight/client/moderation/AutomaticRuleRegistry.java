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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict loader for the bundled, requirement-composed automatic moderation rules. */
final class AutomaticRuleRegistry {
    private static final String RESOURCE = "/assets/dmls/moderation_rules.json";
    private static final int SCHEMA_VERSION = 2;
    private static final long MAX_WINDOW_MILLIS = 600_000L;
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "rules");
    private static final Set<String> RULE_FIELDS = Set.of(
            "id", "labelKey", "defaultEnabled", "channels", "match", "requirements");
    private static final Set<String> REQUIREMENT_FIELDS = Set.of(
            "metric", "groupBy", "minimum", "windowMs", "similarityThreshold", "fuzzyMinLength");
    private static final Set<String> RENDERED_LINES_FIELDS = Set.of("metric", "minimum");
    private static final Set<String> SENDER_COUNT_FIELDS = Set.of(
            "metric", "groupBy", "minimum", "windowMs");
    private static final Set<String> SIMILAR_COUNT_FIELDS = Set.of(
            "metric", "groupBy", "minimum", "windowMs", "similarityThreshold", "fuzzyMinLength");

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
        Set<String> seen = new HashSet<>();
        Map<String, Object> values = new HashMap<>();
        Set<ChatChannel> channels = null;
        List<AutomaticRuleRequirement> requirements = null;
        while (json.hasNext()) {
            String field = json.nextName();
            if (!RULE_FIELDS.contains(field)) throw new IOException("Unknown field in rule " + index + ": " + field);
            if (!seen.add(field)) throw new IOException("Duplicate field in rule " + index + ": " + field);
            switch (field) {
                case "channels" -> channels = readChannels(json, index);
                case "requirements" -> requirements = readRequirements(json, index);
                default -> values.put(field, readRuleValue(json, field));
            }
        }
        json.endObject();
        requireFields(index, seen, RULE_FIELDS);

        String id = requiredString(values, "id", "rule " + index);
        if (!RULE_ID.matcher(id).matches()) throw new IOException("Invalid rule id: " + id);
        String labelKey = requiredString(values, "labelKey", "rule " + index);
        boolean defaultEnabled = requiredBoolean(values, "defaultEnabled", "rule " + index);
        int minimumMatches = parseMinimumMatches(values.get("match"), requirements.size(), index);
        return new AutomaticRuleDefinition(id, labelKey, defaultEnabled, channels, minimumMatches, requirements);
    }

    private static Set<ChatChannel> readChannels(JsonReader json, int ruleIndex) throws IOException {
        requireToken(json, JsonToken.BEGIN_ARRAY, "Rule " + ruleIndex + " channels must be an array");
        json.beginArray();
        Set<ChatChannel> channels = new LinkedHashSet<>();
        while (json.hasNext()) {
            String value = readString(json, "channel");
            ChatChannel channel = switch (value) {
                case "global" -> ChatChannel.GLOBAL;
                case "local" -> ChatChannel.LOCAL;
                case "trade" -> ChatChannel.TRADE;
                case "rp" -> ChatChannel.RP;
                case "staff" -> ChatChannel.STAFF;
                case "admin" -> ChatChannel.ADMIN;
                default -> throw new IOException("Unsupported channel in rule " + ruleIndex + ": " + value);
            };
            if (!channels.add(channel)) throw new IOException("Duplicate channel in rule " + ruleIndex + ": " + value);
        }
        json.endArray();
        if (channels.isEmpty()) throw new IOException("Rule " + ruleIndex + " channels must not be empty");
        return channels;
    }

    private static List<AutomaticRuleRequirement> readRequirements(JsonReader json, int ruleIndex) throws IOException {
        requireToken(json, JsonToken.BEGIN_ARRAY, "Rule " + ruleIndex + " requirements must be an array");
        json.beginArray();
        List<AutomaticRuleRequirement> requirements = new ArrayList<>();
        while (json.hasNext()) {
            requirements.add(readRequirement(json, ruleIndex, requirements.size()));
        }
        json.endArray();
        if (requirements.isEmpty()) throw new IOException("Rule " + ruleIndex + " requirements must not be empty");
        return requirements;
    }

    private static AutomaticRuleRequirement readRequirement(JsonReader json, int ruleIndex, int requirementIndex)
            throws IOException {
        String context = "requirement " + requirementIndex + " in rule " + ruleIndex;
        requireToken(json, JsonToken.BEGIN_OBJECT, context + " must be an object");
        json.beginObject();
        Map<String, Object> values = new HashMap<>();
        while (json.hasNext()) {
            String field = json.nextName();
            if (!REQUIREMENT_FIELDS.contains(field)) throw new IOException("Unknown field in " + context + ": " + field);
            if (values.containsKey(field)) throw new IOException("Duplicate field in " + context + ": " + field);
            values.put(field, readRequirementValue(json, field));
        }
        json.endObject();

        String metric = requiredString(values, "metric", context);
        return switch (metric) {
            case "rendered_lines" -> buildRenderedLines(values, context);
            case "message_count" -> buildMessageCount(values, context);
            default -> throw new IOException("Unsupported metric in " + context + ": " + metric);
        };
    }

    private static AutomaticRuleRequirement buildRenderedLines(Map<String, Object> values, String context)
            throws IOException {
        requireExactFields(values, RENDERED_LINES_FIELDS, context);
        int minimum = requiredInteger(values, "minimum", context);
        if (minimum < 2) throw new IOException(context + " minimum must be at least 2");
        return new RenderedLinesRequirement(minimum);
    }

    private static AutomaticRuleRequirement buildMessageCount(Map<String, Object> values, String context)
            throws IOException {
        String grouping = requiredString(values, "groupBy", context);
        MessageCountGrouping groupBy = switch (grouping) {
            case "similar_content" -> MessageCountGrouping.SIMILAR_CONTENT;
            case "sender" -> MessageCountGrouping.SENDER;
            default -> throw new IOException("Unsupported groupBy in " + context + ": " + grouping);
        };
        requireExactFields(values,
                groupBy == MessageCountGrouping.SIMILAR_CONTENT ? SIMILAR_COUNT_FIELDS : SENDER_COUNT_FIELDS,
                context);
        int minimum = requiredInteger(values, "minimum", context);
        if (minimum < 2) throw new IOException(context + " minimum must be at least 2");
        long windowMs = requiredInteger(values, "windowMs", context);
        if (windowMs <= 0 || windowMs > MAX_WINDOW_MILLIS) {
            throw new IOException(context + " windowMs must be between 1 and "
                    + MAX_WINDOW_MILLIS + " milliseconds");
        }
        if (groupBy == MessageCountGrouping.SENDER) {
            return new MessageCountRequirement(groupBy, minimum, windowMs * 1_000_000L, 1.0D, 1);
        }
        double similarity = requiredDouble(values, "similarityThreshold", context);
        if (!(similarity > 0.0D && similarity <= 1.0D)) {
            throw new IOException(context + " similarityThreshold must be greater than 0 and at most 1");
        }
        int fuzzyMinLength = requiredInteger(values, "fuzzyMinLength", context);
        if (fuzzyMinLength < 1) throw new IOException(context + " fuzzyMinLength must be positive");
        return new MessageCountRequirement(
                groupBy, minimum, windowMs * 1_000_000L, similarity, fuzzyMinLength);
    }

    private static int parseMinimumMatches(Object value, int requirementCount, int ruleIndex) throws IOException {
        if (value instanceof String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "any" -> 1;
                case "all" -> requirementCount;
                default -> throw new IOException(
                        "Rule " + ruleIndex + " match must be 'any', 'all', or a positive integer");
            };
        }
        if (value instanceof Integer count) {
            if (count < 1) throw new IOException("Rule " + ruleIndex + " match must be positive");
            return Math.min(count, requirementCount);
        }
        throw new IOException("Missing field in rule " + ruleIndex + ": match");
    }

    private static void requireFields(int index, Set<String> actual, Set<String> expected) throws IOException {
        for (String field : expected) {
            if (!actual.contains(field)) throw new IOException("Missing field in rule " + index + ": " + field);
        }
    }

    private static void requireExactFields(Map<String, Object> values, Set<String> expected, String context)
            throws IOException {
        for (String field : expected) {
            if (!values.containsKey(field)) throw new IOException("Missing field in " + context + ": " + field);
        }
        for (String field : values.keySet()) {
            if (!expected.contains(field)) throw new IOException("Field " + field + " is invalid for " + context);
        }
    }

    private static Object readRuleValue(JsonReader json, String field) throws IOException {
        return switch (field) {
            case "id", "labelKey" -> readString(json, field);
            case "match" -> readMatchValue(json);
            case "defaultEnabled" -> readBoolean(json, field);
            default -> throw new IOException("Unsupported rule field: " + field);
        };
    }

    private static Object readMatchValue(JsonReader json) throws IOException {
        return switch (json.peek()) {
            case STRING -> json.nextString();
            case NUMBER -> readInteger(json, "match");
            default -> throw new IOException("match must be 'any', 'all', or a positive integer");
        };
    }

    private static Object readRequirementValue(JsonReader json, String field) throws IOException {
        return switch (field) {
            case "metric", "groupBy" -> readString(json, field);
            case "minimum", "windowMs", "fuzzyMinLength" -> readInteger(json, field);
            case "similarityThreshold" -> readDouble(json, field);
            default -> throw new IOException("Unsupported requirement field: " + field);
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

    private static String requiredString(Map<String, Object> values, String field, String context) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IOException("Missing or blank field in " + context + ": " + field);
        }
        return string;
    }

    private static boolean requiredBoolean(Map<String, Object> values, String field, String context)
            throws IOException {
        Object value = values.get(field);
        if (!(value instanceof Boolean flag)) throw new IOException("Missing field in " + context + ": " + field);
        return flag;
    }

    private static int requiredInteger(Map<String, Object> values, String field, String context) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof Integer number)) throw new IOException("Missing field in " + context + ": " + field);
        return number;
    }

    private static double requiredDouble(Map<String, Object> values, String field, String context) throws IOException {
        Object value = values.get(field);
        if (!(value instanceof Double number)) throw new IOException("Missing field in " + context + ": " + field);
        return number;
    }

    private static void requireToken(JsonReader json, JsonToken expected, String message) throws IOException {
        if (json.peek() != expected) throw new IOException(message);
    }
}
