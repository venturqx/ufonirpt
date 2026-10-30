package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static java.util.Objects.requireNonNull;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TabsJsonHelperTest {
    private static final String JSON_TABS_ARRAY_KEY = "tabs";
    private static final String JSON_TAB_ID_KEY = "tab_id";
    private static final List<String> DEFAULT_TAB_VALUES = List.of(
            "DEFAULT_KIOSK",
            "SUBSCRIPTIONS",
            "LIVE",
            "BOOKMARKS",
            "FEED",
            "TRENDING_PODCASTS",
            "TRENDING_GAMING",
            "TRENDING_MUSIC",
            "TRENDING_MOVIES",
            "TOURNESOL");

    @Test
    public void testEmptyAndNullRead() throws TabsJsonHelper.InvalidJsonException {
        final List<Tab> defaultTabs = TabsJsonHelper.getDefaultTabs();

        final String emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":[]}";
        List<Tab> tabs = TabsJsonHelper.getTabsFromJson(null, emptyTabsJson);
        assertEquals(defaultTabs, tabs);

        final String nullSource = null;
        tabs = TabsJsonHelper.getTabsFromJson(null, nullSource);
        assertEquals(defaultTabs, tabs);
    }

    @Test
    public void testInvalidIdRead() throws TabsJsonHelper.InvalidJsonException {
        final int blankTabId = Tab.Type.BLANK.getTabId();
        final String emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":["
                + "{\"" + JSON_TAB_ID_KEY + "\":" + blankTabId + "},"
                + "{\"" + JSON_TAB_ID_KEY + "\":" + 12345678 + "}" + "]}";
        final List<Tab> tabs = TabsJsonHelper.getTabsFromJson(null, emptyTabsJson);

        assertEquals("Should ignore the tab with invalid id", 1, tabs.size());
        assertEquals(blankTabId, tabs.get(0).getTabId());
    }

    @Test
    public void testInvalidRead() {
        final List<String> invalidList = Arrays.asList(
                "{\"notTabsArray\":[]}",
                "{invalidJSON]}",
                "{}"
        );

        for (final String invalidContent : invalidList) {
            try {
                TabsJsonHelper.getTabsFromJson(null, invalidContent);

                fail("didn't throw exception");
            } catch (final Exception e) {
                final boolean isExpectedException =
                        e instanceof TabsJsonHelper.InvalidJsonException;
                assertTrue("\"" + e.getClass().getSimpleName()
                        + "\" is not the expected exception", isExpectedException);
            }
        }
    }

    @Test
    public void testEmptyAndNullSave() throws JsonParserException {
        final List<Tab> emptyList = Collections.emptyList();
        String returnedJson = TabsJsonHelper.getJsonToSave(emptyList);
        assertTrue(isTabsArrayEmpty(returnedJson));

        final List<Tab> nullList = null;
        returnedJson = TabsJsonHelper.getJsonToSave(nullList);
        assertTrue(isTabsArrayEmpty(returnedJson));
    }

    private boolean isTabsArrayEmpty(final String returnedJson) throws JsonParserException {
        final JsonObject jsonObject = JsonParser.object().from(returnedJson);
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY));
        return jsonObject.getArray(JSON_TABS_ARRAY_KEY).isEmpty();
    }

    @Test
    public void testSaveAndReading() throws JsonParserException {
        // Saving
        final Tab.BlankTab blankTab = new Tab.BlankTab();
        final Tab.DefaultKioskTab defaultKioskTab = new Tab.DefaultKioskTab();
        final Tab.SubscriptionsTab subscriptionsTab = new Tab.SubscriptionsTab();
        final Tab.ChannelTab channelTab = new Tab.ChannelTab(
                666, "https://example.org", "testName");
        final Tab.KioskTab kioskTab = new Tab.KioskTab(123, "trending_key");
        final Tab.FeedGroupTab feedGroupTab = new Tab.FeedGroupTab(
                1L, "x", 123);

        final List<Tab> tabs = Arrays.asList(
                blankTab, defaultKioskTab, subscriptionsTab, channelTab, kioskTab, feedGroupTab);
        final String returnedJson = TabsJsonHelper.getJsonToSave(tabs);

        // Reading
        final JsonObject jsonObject = JsonParser.object().from(returnedJson);
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY));
        final JsonArray tabsFromArray = jsonObject.getArray(JSON_TABS_ARRAY_KEY);

        assertEquals(tabs.size(), tabsFromArray.size());

        final Tab.BlankTab blankTabFromReturnedJson = requireNonNull((Tab.BlankTab) Tab.from(
                (JsonObject) tabsFromArray.get(0)));
        assertEquals(blankTab.getTabId(), blankTabFromReturnedJson.getTabId());

        final Tab.DefaultKioskTab defaultKioskTabFromReturnedJson = requireNonNull(
                (Tab.DefaultKioskTab) Tab.from((JsonObject) tabsFromArray.get(1)));
        assertEquals(defaultKioskTab.getTabId(), defaultKioskTabFromReturnedJson.getTabId());

        final Tab.SubscriptionsTab subscriptionsTabFromReturnedJson = requireNonNull(
                (Tab.SubscriptionsTab) Tab.from((JsonObject) tabsFromArray.get(2)));
        assertEquals(subscriptionsTab.getTabId(), subscriptionsTabFromReturnedJson.getTabId());

        final Tab.ChannelTab channelTabFromReturnedJson = requireNonNull((Tab.ChannelTab) Tab.from(
                (JsonObject) tabsFromArray.get(3)));
        assertEquals(channelTab.getTabId(), channelTabFromReturnedJson.getTabId());
        assertEquals(channelTab.getChannelServiceId(),
                channelTabFromReturnedJson.getChannelServiceId());
        assertEquals(channelTab.getChannelUrl(), channelTabFromReturnedJson.getChannelUrl());
        assertEquals(channelTab.getChannelName(), channelTabFromReturnedJson.getChannelName());

        final Tab.KioskTab kioskTabFromReturnedJson = requireNonNull((Tab.KioskTab) Tab.from(
                (JsonObject) tabsFromArray.get(4)));
        assertEquals(kioskTab.getTabId(), kioskTabFromReturnedJson.getTabId());
        assertEquals(kioskTab.getKioskServiceId(), kioskTabFromReturnedJson.getKioskServiceId());
        assertEquals(kioskTab.getKioskId(), kioskTabFromReturnedJson.getKioskId());

        final Tab.FeedGroupTab grpTabFromReturnedJson = requireNonNull(
                (Tab.FeedGroupTab) Tab.from((JsonObject) tabsFromArray.get(5)
                ));
        assertEquals(feedGroupTab.getTabId(), grpTabFromReturnedJson.getTabId());
        assertEquals(feedGroupTab.getFeedGroupId(), grpTabFromReturnedJson.getFeedGroupId());
        assertEquals(feedGroupTab.getIconId(), grpTabFromReturnedJson.getIconId());
        assertEquals(feedGroupTab.getFeedGroupName(), grpTabFromReturnedJson.getFeedGroupName());
    }

    @Test
    public void testDefaultTabsHaveNoDuplicatesAndSelectedTabFirst() {
        for (final String value : DEFAULT_TAB_VALUES) {
            final List<Tab> tabs = TabsJsonHelper.getDefaultTabsFor(value);

            assertEquals(value, Tab.Type.valueOf(value).getTab(), tabs.get(0));
            assertEquals(value, tabs.size(), tabs.stream().distinct().count());
        }
    }

    @Test
    public void testDefaultTabsExactLists() {
        final List<Tab> liveTabs = tabsOf(Tab.Type.LIVE, Tab.Type.COMPARE,
                Tab.Type.SUBSCRIPTIONS, Tab.Type.BOOKMARKS, Tab.Type.MANIFESTO);
        assertEquals(liveTabs, TabsJsonHelper.getDefaultTabsFor("LIVE"));
        assertEquals(liveTabs, TabsJsonHelper.getDefaultTabs());

        assertEquals(tabsOf(Tab.Type.TOURNESOL, Tab.Type.COMPARE,
                        Tab.Type.SUBSCRIPTIONS, Tab.Type.BOOKMARKS, Tab.Type.MANIFESTO),
                TabsJsonHelper.getDefaultTabsFor("TOURNESOL"));
    }

    @Test
    public void testDefaultTabsKeepOrderWhenSelectedTabWasAlreadyListed() {
        assertEquals(tabsOf(Tab.Type.SUBSCRIPTIONS, Tab.Type.COMPARE,
                        Tab.Type.BOOKMARKS, Tab.Type.MANIFESTO),
                TabsJsonHelper.getDefaultTabsFor("SUBSCRIPTIONS"));
        assertEquals(tabsOf(Tab.Type.BOOKMARKS, Tab.Type.COMPARE,
                        Tab.Type.SUBSCRIPTIONS, Tab.Type.MANIFESTO),
                TabsJsonHelper.getDefaultTabsFor("BOOKMARKS"));
        assertEquals(tabsOf(Tab.Type.COMPARE, Tab.Type.SUBSCRIPTIONS,
                        Tab.Type.BOOKMARKS, Tab.Type.MANIFESTO),
                TabsJsonHelper.getDefaultTabsFor("COMPARE"));
    }

    @Test
    public void testDefaultTabsFallBackToLive() {
        final List<Tab> liveTabs = TabsJsonHelper.getDefaultTabsFor("LIVE");

        assertEquals(liveTabs, TabsJsonHelper.getDefaultTabsFor(null));
        assertEquals(liveTabs, TabsJsonHelper.getDefaultTabsFor(""));
        assertEquals(liveTabs, TabsJsonHelper.getDefaultTabsFor("UNKNOWN"));
        assertEquals(liveTabs, TabsJsonHelper.getDefaultTabsFor("live"));
    }

    @Test
    public void testDefaultTabsAcceptExactlyTheSelectableTypes() {
        for (final Tab.Type type : Tab.Type.values()) {
            final String name = type.name();
            final boolean accepted = DEFAULT_TAB_VALUES.contains(name) || "COMPARE".equals(name);
            final Tab expectedFirst = accepted ? type.getTab() : Tab.Type.LIVE.getTab();

            assertEquals(name, expectedFirst, TabsJsonHelper.getDefaultTabsFor(name).get(0));
        }
    }

    private static List<Tab> tabsOf(final Tab.Type... types) {
        return Arrays.stream(types).map(Tab.Type::getTab).collect(Collectors.toList());
    }
}
