package com.duperknight.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Asynchronous username-to-UUID lookup through Mojang's bulk profile API. */
public final class MojangProfileLookup {
    public static final int MAX_USERNAMES = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern RAW_UUID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MojangProfileLookup() {
    }

    public static CompletableFuture<BatchResult> lookup(List<String> usernames) {
        List<String> requested = List.copyOf(usernames);
        if (requested.isEmpty() || requested.size() > MAX_USERNAMES
                || requested.stream().anyMatch(username -> !InputValidators.isUsername(username))) {
            return CompletableFuture.completedFuture(BatchResult.error());
        }

        JsonArray body = new JsonArray();
        requested.forEach(username -> body.add(new JsonPrimitive(username)));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/profiles/minecraft"))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "DuperKnight/DMLS")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) return BatchResult.error();
                    if (response.statusCode() == 429) return BatchResult.rateLimited();
                    if (response.statusCode() != 200) return BatchResult.error();
                    return parseSuccess(response.body(), requested);
                });
    }

    static BatchResult parseSuccess(String body, List<String> requested) {
        try {
            Map<String, Profile> returned = new LinkedHashMap<>();
            JsonArray array = JsonParser.parseString(body).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                String id = object.get("id").getAsString();
                String name = object.get("name").getAsString();
                String key = name.toLowerCase(Locale.ROOT);
                boolean requestedName = requested.stream().anyMatch(value -> value.equalsIgnoreCase(name));
                if (!RAW_UUID.matcher(id).matches() || !InputValidators.isUsername(name)
                        || !requestedName || returned.putIfAbsent(key, new Profile(name, hyphenateUuid(id))) != null) {
                    return BatchResult.error();
                }
            }

            List<Entry> entries = new ArrayList<>();
            for (String requestedName : requested) {
                Profile profile = returned.get(requestedName.toLowerCase(Locale.ROOT));
                entries.add(profile == null
                        ? new Entry(requestedName, requestedName, "", false)
                        : new Entry(requestedName, profile.username(), profile.uuid(), true));
            }
            return BatchResult.success(entries);
        } catch (RuntimeException exception) {
            return BatchResult.error();
        }
    }

    public static String hyphenateUuid(String rawUuid) {
        String value = rawUuid.toLowerCase(Locale.ROOT);
        if (!RAW_UUID.matcher(value).matches()) throw new IllegalArgumentException("Invalid Mojang UUID");
        return value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                + "-" + value.substring(16, 20) + "-" + value.substring(20);
    }

    public record Entry(String requestedUsername, String username, String uuid, boolean found) {
    }

    public record BatchResult(Status status, List<Entry> entries) {
        private static BatchResult success(List<Entry> entries) { return new BatchResult(Status.SUCCESS, List.copyOf(entries)); }
        private static BatchResult rateLimited() { return new BatchResult(Status.RATE_LIMITED, List.of()); }
        private static BatchResult error() { return new BatchResult(Status.ERROR, List.of()); }
    }

    private record Profile(String username, String uuid) {
    }

    public enum Status { SUCCESS, RATE_LIMITED, ERROR }
}
