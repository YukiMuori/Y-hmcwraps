package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigSerializable
public class SkinMenuConfiguration {
    private String title = "<gradient:#8FEAF9:#CDA4F9>ɪᴛᴇᴍ sᴋɪɴs</gradient>";
    private int size = 54;
    private List<Integer> contentSlots = new ArrayList<>();
    private int itemSlot = 49;
    private String defaultSort = "rarity";
    private String defaultSortOrder = "descending";
    private boolean showLocked = true;
    private boolean fillerEnabled = true;
    private boolean itemEnabled = true;
    private SkinIconConfiguration filler = new SkinIconConfiguration();
    private Button previous = new Button();
    private Button next = new Button();
    private Button close = new Button();
    private Button sort = new Button();
    private Button filter = new Button();
    private Button search = new Button();
    private Button favorites = new Button();
    private Button collection = new Button();
    private Map<String, Integer> categorySlots = new HashMap<>();
    private Map<String, String> clickActions = new HashMap<>();
    private List<String> sortOptions = new ArrayList<>();
    private List<String> filterOptions = new ArrayList<>();

    public String getTitle() { return title; }
    public int getSize() { return size; }
    public List<Integer> getContentSlots() { return contentSlots == null ? List.of() : contentSlots; }
    public int getItemSlot() { return itemSlot; }
    public String getDefaultSort() { return defaultSort == null ? "rarity" : defaultSort; }
    public String getDefaultSortOrder() { return defaultSortOrder == null ? "descending" : defaultSortOrder; }
    public boolean isShowLocked() { return showLocked; }
    public boolean isFillerEnabled() { return fillerEnabled; }
    public boolean isItemEnabled() { return itemEnabled; }
    public SkinIconConfiguration getFiller() { return filler == null ? new SkinIconConfiguration() : filler; }
    public Button getPrevious() { return previous == null ? new Button() : previous; }
    public Button getNext() { return next == null ? new Button() : next; }
    public Button getClose() { return close == null ? new Button() : close; }
    public Button getSort() { return sort == null ? new Button() : sort; }
    public Button getFilter() { return filter == null ? new Button() : filter; }
    public Button getSearch() { return search == null ? new Button() : search; }
    public Button getFavorites() { return favorites == null ? new Button() : favorites; }
    public Button getCollection() { return collection == null ? new Button() : collection; }
    public Map<String, Integer> getCategorySlots() { return categorySlots == null ? Map.of() : categorySlots; }
    public Map<String, String> getClickActions() { return clickActions == null ? Map.of() : clickActions; }
    public List<String> getSortOptions() { return sortOptions == null ? List.of() : sortOptions; }
    public List<String> getFilterOptions() { return filterOptions == null ? List.of() : filterOptions; }

    @ConfigSerializable
    public static class Button {
        private boolean enabled = true;
        private int slot = -1;
        private SkinIconConfiguration item = new SkinIconConfiguration();
        public boolean isEnabled() { return enabled; }
        public int getSlot() { return slot; }
        public SkinIconConfiguration getItem() { return item == null ? new SkinIconConfiguration() : item; }
    }
}
