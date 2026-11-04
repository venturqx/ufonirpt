package org.schabi.newpipe.settings.tabs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.R;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class to get a JSON representation of a list of tabs, and the other way around.
 */
public final class TabsJsonHelper {
    public static final String DEFAULT_TAB_VALUE = Tab.Type.TOURNESOL.name();

    private static final String JSON_TABS_ARRAY_KEY = "tabs";

    private static final List<Tab> FALLBACK_INITIAL_TABS_LIST = List.of(
            Tab.Type.LIVE.getTab(),
            Tab.Type.COMPARE.getTab(),
            Tab.Type.SUBSCRIPTIONS.getTab(),
            Tab.Type.BOOKMARKS.getTab(),
            Tab.Type.MANIFESTO.getTab());

    private static final List<Tab> TABS_AFTER_FIRST =
            FALLBACK_INITIAL_TABS_LIST.subList(1, FALLBACK_INITIAL_TABS_LIST.size());

    private static final Set<Tab.Type> SELECTABLE_FIRST_TYPES = EnumSet.of(
            Tab.Type.DEFAULT_KIOSK,
            Tab.Type.FEED,
            Tab.Type.SUBSCRIPTIONS,
            Tab.Type.BOOKMARKS,
            Tab.Type.TRENDING_PODCASTS,
            Tab.Type.TRENDING_GAMING,
            Tab.Type.TRENDING_MUSIC,
            Tab.Type.TRENDING_MOVIES,
            Tab.Type.TOURNESOL,
            Tab.Type.LIVE,
            Tab.Type.COMPARE);

    private TabsJsonHelper() { }

    /**
     * Try to reads the passed JSON and returns the list of tabs if no error were encountered.
     * <p>
     * If the JSON is null or empty, or the list of tabs that it represents is empty, the
     * {@link #getDefaultTabs fallback list} will be returned.
     * <p>
     * Tabs with invalid ids (i.e. not in the {@link Tab.Type} enum) will be ignored.
     *
     * @param context a context used to read default tab preferences; can be null.
     * @param tabsJson a JSON string got from {@link #getJsonToSave(List)}.
     * @return a list of {@link Tab tabs}.
     * @throws InvalidJsonException if the JSON string is not valid
     */
    public static List<Tab> getTabsFromJson(@Nullable final Context context,
                                            @Nullable final String tabsJson)
            throws InvalidJsonException {
        if (tabsJson == null || tabsJson.isEmpty()) {
            return getDefaultTabs(context);
        }

        try {
            final JsonObject outerJsonObject = JsonParser.object().from(tabsJson);

            if (!outerJsonObject.has(JSON_TABS_ARRAY_KEY)) {
                throw new InvalidJsonException("JSON doesn't contain \"" + JSON_TABS_ARRAY_KEY
                        + "\" array");
            }

            final JsonArray tabsArray = outerJsonObject.getArray(JSON_TABS_ARRAY_KEY, null);

            final var returnTabs = dropRepeatedSelectableTabs(tabsArray.streamAsJsonObjects()
                    .map(Tab::from)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableList()));

            return returnTabs.isEmpty() ? getDefaultTabs(context) : returnTabs;
        } catch (final JsonParserException e) {
            throw new InvalidJsonException(e);
        }
    }

    private static List<Tab> dropRepeatedSelectableTabs(final List<Tab> tabs) {
        final Set<Tab.Type> seen = EnumSet.noneOf(Tab.Type.class);
        return tabs.stream()
                .filter(tab -> {
                    final Tab.Type type = Tab.typeFrom(tab.getTabId());
                    return !SELECTABLE_FIRST_TYPES.contains(type) || seen.add(type);
                })
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get a JSON representation from a list of tabs.
     *
     * @param tabList a list of {@link Tab tabs}.
     * @return a JSON string representing the list of tabs
     */
    public static String getJsonToSave(@Nullable final List<Tab> tabList) {
        final JsonStringWriter jsonWriter = JsonWriter.string();
        jsonWriter.object();

        jsonWriter.array(JSON_TABS_ARRAY_KEY);
        if (tabList != null) {
            for (final Tab tab : tabList) {
                tab.writeJsonOn(jsonWriter);
            }
        }
        jsonWriter.end();

        jsonWriter.end();
        return jsonWriter.done();
    }

    public static List<Tab> getDefaultTabs(@Nullable final Context context) {
        if (context == null) {
            return FALLBACK_INITIAL_TABS_LIST;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String defaultTabKey = context.getString(R.string.default_tab_key);
        return getDefaultTabsFor(prefs.getString(defaultTabKey, DEFAULT_TAB_VALUE));
    }

    @Nullable
    public static Tab.Type selectableTypeFrom(@Nullable final String selectedType) {
        return SELECTABLE_FIRST_TYPES.stream()
                .filter(type -> type.name().equals(selectedType))
                .findFirst()
                .orElse(null);
    }

    static List<Tab> getDefaultTabsFor(@Nullable final String selectedType) {
        final Tab.Type selectable = selectableTypeFrom(selectedType);
        final Tab firstTab = (selectable == null ? Tab.Type.LIVE : selectable).getTab();

        return Stream.concat(Stream.of(firstTab), TABS_AFTER_FIRST.stream())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    public static List<Tab> getDefaultTabs() {
        return FALLBACK_INITIAL_TABS_LIST;
    }

    public static final class InvalidJsonException extends Exception {
        private InvalidJsonException() {
            super();
        }

        private InvalidJsonException(final String message) {
            super(message);
        }

        private InvalidJsonException(final Throwable cause) {
            super(cause);
        }
    }
}
