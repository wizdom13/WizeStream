package org.schabi.newpipe.local.bookmark;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Local organization metadata stored with full-backup preferences. */
public final class PlaylistCategories {
    public static final String PREFERENCE_KEY = "playlist_categories_v1";

    public static String preferenceKey(final String profileId) {
        return PREFERENCE_KEY + "_" + profileId;
    }
    public static final String ALL = "*";
    public static final String UNCATEGORIZED = "";
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, String> memberships = new LinkedHashMap<>();

    public static boolean allowsReordering(final boolean searchActive, final String categoryId) {
        return !searchActive && ALL.equals(categoryId);
    }

    public static PlaylistCategories fromJson(final String json) throws JsonParserException {
        final PlaylistCategories result = new PlaylistCategories();
        if (json == null || json.isEmpty()) {
            return result;
        }
        final JsonObject root = JsonParser.object().from(json);
        for (final Object entry : root.getArray("categories")) {
            if (entry instanceof JsonObject) {
                final JsonObject category = (JsonObject) entry;
                final String id = category.getString("id");
                final String name = category.getString("name");
                if (id != null && !id.isEmpty() && !ALL.equals(id)
                        && name != null && !name.trim().isEmpty()) {
                    result.names.put(id, name);
                }
            }
        }
        root.getObject("memberships").forEach((key, value) -> {
            if (value instanceof String && result.names.containsKey(value)) {
                result.memberships.put(key, (String) value);
            }
        });
        return result;
    }

    public String toJson() {
        final JsonArray categories = new JsonArray();
        names.forEach((id, name) -> {
            final JsonObject category = new JsonObject();
            category.put("id", id);
            category.put("name", name);
            categories.add(category);
        });
        final JsonObject root = new JsonObject();
        root.put("categories", categories);
        final JsonObject assignments = new JsonObject();
        memberships.forEach(assignments::put);
        root.put("memberships", assignments);
        return JsonWriter.string(root);
    }

    public List<String> ids() {
        final List<String> ids = new ArrayList<>(names.keySet());
        ids.sort(Comparator.comparing(names::get, String.CASE_INSENSITIVE_ORDER));
        return ids;
    }

    public String name(final String id) {
        return names.get(id);
    }

    public String create(final String name) {
        final String id = UUID.randomUUID().toString();
        rename(id, name);
        return id;
    }

    public void rename(final String id, final String name) {
        final String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80 || names.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(id)
                        && entry.getValue().equalsIgnoreCase(trimmed))) {
            throw new IllegalArgumentException("Choose a unique name of 1 to 80 characters");
        }
        names.put(id, trimmed);
    }

    public void delete(final String id) {
        names.remove(id);
        memberships.values().removeIf(id::equals);
    }

    public void assign(final String playlistKey, final String categoryId) {
        if (UNCATEGORIZED.equals(categoryId)) {
            memberships.remove(playlistKey);
        } else if (names.containsKey(categoryId)) {
            memberships.put(playlistKey, categoryId);
        } else {
            throw new IllegalArgumentException("Unknown playlist category");
        }
    }

    public boolean matches(final String playlistKey, final String categoryId) {
        return ALL.equals(categoryId) || categoryId.equals(
                memberships.getOrDefault(playlistKey, UNCATEGORIZED));
    }
}
