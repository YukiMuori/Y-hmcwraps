package de.skyslycer.hmcwraps;

import com.bgsoftware.common.config.CommentedConfiguration;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import de.skyslycer.hmcwraps.actions.ActionHandler;
import de.skyslycer.hmcwraps.actions.register.DefaultActionRegister;
import de.skyslycer.hmcwraps.commands.CommandRegister;
import de.skyslycer.hmcwraps.converter.FileConverter;
import de.skyslycer.hmcwraps.integration.AllIntegrationsHandler;
import de.skyslycer.hmcwraps.integration.IntegrationHandler;
import de.skyslycer.hmcwraps.compat.Scheduler;
import de.skyslycer.hmcwraps.compat.paper.PaperScheduler;
import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.database.SqlDatabase;
import de.skyslycer.hmcwraps.discord.DiscordWebhook;
import de.skyslycer.hmcwraps.economy.EconomyManager;
import de.skyslycer.hmcwraps.economy.EconomyServiceImpl;
import de.skyslycer.hmcwraps.economy.ExcellentEconomyProvider;
import de.skyslycer.hmcwraps.economy.PurchaseTransactionService;
import de.skyslycer.hmcwraps.economy.VaultEconomyProvider;
import de.skyslycer.hmcwraps.economy.EconomyService;
import de.skyslycer.hmcwraps.itemhook.*;
import de.skyslycer.hmcwraps.listener.*;
import de.skyslycer.hmcwraps.messages.MessageHandler;
import de.skyslycer.hmcwraps.messages.MessageHandlerImpl;
import de.skyslycer.hmcwraps.metrics.PluginMetrics;
import de.skyslycer.hmcwraps.nbtapi.logger.NoInfoLogger;
import de.skyslycer.hmcwraps.placeholderapi.HMCWrapsPlaceholders;
import de.skyslycer.hmcwraps.pool.MessagePool;
import de.skyslycer.hmcwraps.pool.ObjectPool;
import de.skyslycer.hmcwraps.preview.PreviewManager;
import de.skyslycer.hmcwraps.serialization.Config;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import de.skyslycer.hmcwraps.storage.FavoriteWrapStorage;
import de.skyslycer.hmcwraps.storage.PlayerFilterStorage;
import de.skyslycer.hmcwraps.storage.SqliteOwnershipStorage;
import de.skyslycer.hmcwraps.storage.Storage;
import de.skyslycer.hmcwraps.lang.LanguageManager;
import de.skyslycer.hmcwraps.skin.CompatibilityRegistry;
import de.skyslycer.hmcwraps.skin.ItemIconFactory;
import de.skyslycer.hmcwraps.skin.ItemSkinManagerImpl;
import de.skyslycer.hmcwraps.skin.SkinCatalog;
import de.skyslycer.hmcwraps.skin.SkinOwnershipService;
import de.skyslycer.hmcwraps.skin.SkinTradeManager;
import de.skyslycer.hmcwraps.repository.CollectionRewardRepository;
import de.skyslycer.hmcwraps.repository.CouponRepository;
import de.skyslycer.hmcwraps.repository.FavoriteRepository;
import de.skyslycer.hmcwraps.repository.GiftRepository;
import de.skyslycer.hmcwraps.repository.OwnershipRepository;
import de.skyslycer.hmcwraps.repository.PlayerRepository;
import de.skyslycer.hmcwraps.repository.PurchaseRepository;
import de.skyslycer.hmcwraps.repository.ShopStateRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlCollectionRewardRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlCouponRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlFavoriteRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlGiftRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlOwnershipRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlPlayerRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlPurchaseRepository;
import de.skyslycer.hmcwraps.repository.sql.SqlShopStateRepository;
import de.skyslycer.hmcwraps.shop.CollectionServiceImpl;
import de.skyslycer.hmcwraps.shop.CouponServiceImpl;
import de.skyslycer.hmcwraps.shop.DailyShopService;
import de.skyslycer.hmcwraps.shop.GiftServiceImpl;
import de.skyslycer.hmcwraps.shop.ProfileServiceImpl;
import de.skyslycer.hmcwraps.shop.ShopListener;
import de.skyslycer.hmcwraps.shop.ShopRegistry;
import de.skyslycer.hmcwraps.shop.ShopServiceImpl;
import de.skyslycer.hmcwraps.shop.menu.ShopMenuManager;
import de.skyslycer.hmcwraps.storage.SqlStorageProvider;
import de.skyslycer.hmcwraps.transformation.ConfigFileTransformations;
import de.skyslycer.hmcwraps.updater.ContinuousUpdateChecker;
import de.skyslycer.hmcwraps.updater.version.PluginVersion;
import de.skyslycer.hmcwraps.util.PermissionUtil;
import de.skyslycer.hmcwraps.util.VersionUtil;
import de.skyslycer.hmcwraps.wrap.*;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import de.tr7zw.changeme.nbtapi.utils.VersionChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.*;
import java.util.logging.Level;

public class HMCWrapsPlugin extends JavaPlugin implements HMCWraps {

    private static final YamlConfigurationLoader LOADER = YamlConfigurationLoader.builder()
            .defaultOptions(ConfigurationOptions.defaults().implicitInitialization(false))
            .path(CONFIG_PATH)
            .build();

    private final ObjectPool<UUID, Component> messagePool = new MessagePool();
    private final Set<ItemHook> hooks = new HashSet<>();
    private final Set<String> loadedHooks = new HashSet<>();
    private final Wrapper wrapper = new WrapperImpl(this);
    private final PreviewManager previewManager = new PreviewManager(this);
    private final CollectionHelper collectionHelper = new CollectionHelperImpl(this);
    private final ActionHandler actionHandler = new ActionHandler();
    private final FileConverter fileConverter = new FileConverter(this);
    private final Storage<Player, Boolean> filterStorage = new PlayerFilterStorage(this);
    private final Storage<Player, List<Wrap>> favoriteWrapStorage = new FavoriteWrapStorage(this);
    private final ContinuousUpdateChecker updateChecker = new ContinuousUpdateChecker(this);
    private final WrapsLoader wrapsLoader = new WrapsLoaderImpl(this);
    private final IntegrationHandler integrationHandler = new AllIntegrationsHandler(this);
    private final LanguageManager languageManager = new LanguageManager(this);
    private final ItemIconFactory itemIconFactory = new ItemIconFactory(this);
    private final SkinCatalog skinCatalog = new SkinCatalog(this, itemIconFactory);
    private final CompatibilityRegistry compatibilityRegistry = new CompatibilityRegistry(this);
    private final EconomyManager economyManager = new EconomyManager();
    private final SqliteOwnershipStorage skinStorage = new SqliteOwnershipStorage(this);
    private final SkinOwnershipService skinOwnership = new SkinOwnershipService(skinStorage);
    private final ItemSkinManagerImpl itemSkinManager = new ItemSkinManagerImpl(this, skinCatalog, compatibilityRegistry, skinOwnership, economyManager);
    private final SkinTradeManager skinTradeManager = new SkinTradeManager(this, itemSkinManager);
    private volatile java.util.concurrent.CompletableFuture<Boolean> skinStorageInitialization;
    private HookAccessor hookAccessor;
    private Config config;
    private MessageHandler messageHandler;
    private WrappedTask checkTask;
    private FoliaLib foliaLib;
    private final Map<UUID, String> wrapGui = new HashMap<>();

    private Scheduler scheduler;
    private DiscordWebhook discordWebhook;
    private Database database;
    private SqlStorageProvider shopStorage;
    private ShopRegistry shopRegistry;
    private DailyShopService dailyShopService;
    private CouponServiceImpl couponService;
    private CollectionServiceImpl collectionService;
    private ProfileServiceImpl profileService;
    private ShopServiceImpl shopService;
    private ShopMenuManager shopMenuManager;
    private GiftServiceImpl giftService;
    private EconomyService economyService;
    private PurchaseTransactionService transactionService;
    private PurchaseRepository purchaseRepository;
    private GiftRepository giftRepository;
    private CouponRepository couponRepository;
    private PlayerRepository playerRepository;
    private ShopStateRepository shopStateRepository;
    private CollectionRewardRepository collectionRewardRepository;
    private Scheduler.Cancellable shopRefreshTask;

    @Override
    public void onLoad() {
        MinecraftVersion.replaceLogger(new NoInfoLogger("HMCWraps-NBT", null));
        VersionChecker.hideOk = true;
        new NBTContainer();
    }

    @Override
    public void onEnable() {
        foliaLib = new FoliaLib(this);
        scheduler = new PaperScheduler(foliaLib);
        discordWebhook = new DiscordWebhook(this);
        checkDependency("PlaceholderAPI", false);
        if (checkDependency("ItemsAdder", false)) {
            hooks.add(new ItemsAdderItemHook());
        }
        if (checkDependency("Oraxen", false)) {
            hooks.add(new OraxenItemHook());
        }
        if (checkDependency("Nexo", false)) {
            hooks.add(new NexoItemHook());
        }
        if (checkDependency("CraftEngine", false)) {
            hooks.add(new CraftEngineItemHook());
        }
        checkDependency("zAuctionHouseV3", false);
        if (checkDependency("MythicCrucible", false)) {
            var mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (mythicMobs != null) {
                var version = mythicMobs.getDescription().getVersion();
                if (version.split("-").length >= 2) {
                    version = version.split("-")[0];
                }
                if (PluginVersion.fromString(version).isOlderThan(new PluginVersion(5, 6, 2))) {
                    logSevere("""
                        The plugin 'MythicMobs' is an installed dependency but the version of the dependency is too old!
                        If you don't intend to use the plugin with HMCWraps, you can safely ignore this warning.
                        Please restart the server after you have updated the plugin!""");
                } else {
                    hooks.add(new MythicItemHook());
                }
            }
        }
        hookAccessor = new HookAccessor(hooks);
        economyManager.register(new ExcellentEconomyProvider());
        economyManager.register(new VaultEconomyProvider());

        if (!load()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Loaded all configuration files and wraps. (" + wrapsLoader.getWraps().size() + " wraps)");

        Bukkit.getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InventoryClickListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerShiftListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerPickupListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDropListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerHitEntityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DurabilityChangeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ItemBurnListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerOffHandSwitchListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DispenserArmorListener(this), this);
        Bukkit.getPluginManager().registerEvents(itemSkinManager.menuManager(), this);
        Bukkit.getPluginManager().registerEvents(skinTradeManager, this);
        Bukkit.getPluginManager().registerEvents(new PlayerItemBreakListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);

        CommandRegister.registerCommands(this);

        new DefaultActionRegister(this).register();
        if (!this.getDescription().getVersion().contains("-b")) { // Don't send metrics for beta versions
            new PluginMetrics(this).init();
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new HMCWrapsPlaceholders(this).register();
        }

        if (!VersionUtil.isSupported()) {
            logSevere("Your server version is not supported by this plugin! Please update your server to a newer version. Expect decreased functionality!");
        }
    }

    @Override
    public void onDisable() {
        unload();
        if (discordWebhook != null) {
            discordWebhook.close();
        }
        if (database != null) {
            database.close();
        }
        skinStorage.close();
        hooks.clear();
    }

    @Override
    public boolean load() {
        if (Files.notExists(PLUGIN_PATH)) {
            try {
                Files.createDirectory(PLUGIN_PATH);
            } catch (IOException exception) {
                logSevere("Could not create the folder (please report this to the developers)! The plugin will shut down now.", exception);
                return false;
            }
        }
        initializeSkinStorage();
        if (!loadConfig()) {
            return false;
        }
        if (!languageManager.load()) {
            getLogger().warning("The v2 language catalog could not be loaded; legacy messages and wraps will remain available.");
        }
        if (!loadMessages()) {
            return false;
        }
        if (!skinCatalog.load()) {
            getLogger().warning("The v2 skin catalog could not be loaded; legacy wraps remain available.");
        }
        if (!itemSkinManager.load()) {
            getLogger().warning("The v2 skin GUI configuration could not be loaded; legacy wraps remain available.");
        }
        initializeShop();
        integrationHandler.load();
        getPreviewManager().removeAll(true);
        getUpdateChecker().check();
        startCheckTask();
        return true;
    }

    @Override
    public void unload() {
        if (shopRefreshTask != null) {
            shopRefreshTask.cancel();
            shopRefreshTask = null;
        }
        itemSkinManager.menuManager().closeAll();
        if (shopMenuManager != null) {
            shopMenuManager.closeAll();
        }
        skinTradeManager.cancelAll();
        integrationHandler.unload();
        getWrapsLoader().unload();
        if (checkTask != null) {
            checkTask.cancel();
        }
    }

    private synchronized void initializeSkinStorage() {
        if (skinStorageInitialization != null) return;
        skinStorageInitialization = skinStorage.initialize().toCompletableFuture();
        skinStorageInitialization.whenComplete((ready, error) -> {
            if (error != null || !Boolean.TRUE.equals(ready)) {
                getLogger().severe("SQLite ownership storage did not initialize; legacy wraps remain available, but paid v2 skins are disabled.");
            } else {
                getLogger().info("SQLite skin ownership storage is ready.");
            }
        });
    }

    private boolean loadMessages() {
        try {
            if (Files.notExists(MESSAGES_PATH)) {
                Files.copy(this.getClassLoader().getResourceAsStream("messages.properties"), MESSAGES_PATH);
            }
        } catch (IOException exception) {
            logSevere(
                    "Could not copy the configuration (please report this to the developers)! The plugin will shut down now.", exception);
            return false;
        }
        messageHandler = new MessageHandlerImpl(this);
        messageHandler.update(MESSAGES_PATH);
        return messageHandler.load(MESSAGES_PATH);
    }

    private boolean loadConfig() {
        try {
            if (Files.notExists(WRAP_FILES_PATH)) {
                Files.createDirectory(WRAP_FILES_PATH);
                Files.copy(getResource("silver_wraps.yml"), WRAP_FILES_PATH.resolve("silver_wraps.yml"));
                Files.copy(getResource("emerald_wraps.yml"), WRAP_FILES_PATH.resolve("emerald_wraps.yml"));
            }
            if (Files.notExists(COLLECTION_FILES_PATH)) {
                Files.createDirectory(COLLECTION_FILES_PATH);
                Files.copy(getResource("some_collections.yml"), COLLECTION_FILES_PATH.resolve("some_collections.yml"));
            }
            if (Files.notExists(CONFIG_PATH)) {
                Files.copy(getResource("config.yml"), CONFIG_PATH);
            }
            new ConfigFileTransformations().updateToLatest(CONFIG_PATH);
            CommentedConfiguration.loadConfiguration(CONFIG_PATH.toFile()).syncWithConfig(CONFIG_PATH.toFile(), getResource("config.yml"),
                   "items", "inventory.items", "collections", "unwrapper", "inventory.actions");
            config = LOADER.load().get(Config.class);
            getWrapsLoader().load();
        } catch (IOException exception) {
            logSevere("Could not load the configuration (please report this to the developers)! The plugin will shut down now.", exception);
            return false;
        }
        return true;
    }

    private boolean checkDependency(String name, boolean needed) {
        if (!Bukkit.getPluginManager().isPluginEnabled(name)) {
            if (needed) {
                logSevere("""
                        The plugin '""" + name + """
                        ' is a required dependency but was not found on this server! Please restart the server after you have added the missing plugin!
                        This plugin will shut down now.""");
            }
            return false;
        }
        if (!loadedHooks.contains(name)) {
            getLogger().info("Plugin '" + name + "' found. Initializing hook.");
            loadedHooks.add(name);
        }
        return true;
    }

    private void startCheckTask() {
        if (config.getPermissions().getInventoryCheckInterval() == -1) {
            return;
        }
        if (foliaLib.isFolia()) {
            checkTask = foliaLib.getScheduler().runTimer(() -> Bukkit.getOnlinePlayers().forEach((player) -> foliaLib.getScheduler().runAtEntity(player, (ignored) -> checkInventory(player))),
                    0L, config.getPermissions().getInventoryCheckInterval() < 1 ? 10L * 20 * 60 : config.getPermissions().getInventoryCheckInterval() * 20L * 60L);
        } else {
            checkTask = foliaLib.getScheduler().runTimerAsync(() -> Bukkit.getOnlinePlayers().forEach(this::checkInventory),
                    0L, config.getPermissions().getInventoryCheckInterval() < 1 ? 10L * 20 * 60 : config.getPermissions().getInventoryCheckInterval() * 20L * 60L);
        }
    }

    private void checkInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContents().length - 1; i++) {
            var item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            var wrap = getWrapper().getWrap(item);
            if (wrap == null) {
                continue;
            }
            if (!PermissionUtil.hasPermission(this, wrap, item, player)) {
                int finalI = i; // ;(
                getFoliaLib().getScheduler().runAtEntity(player, (ignored) -> {
                    var newItem = getWrapper().removeWrap(item, player);
                    player.getInventory().setItem(finalI, newItem);
                });
            }
        }
    }

    @Override
    public void logSevere(String message, Throwable thrown) {
        if (thrown != null) {
            getLogger().log(Level.SEVERE,
                    "\n=============================\n" +
                            message + "\n" +
                            "=============================", thrown);
        } else {
            getLogger().log(Level.SEVERE,
                    "\n=============================\n" +
                            message + "\n" +
                            "=============================");
        }
    }

    @Override
    public void logSevere(String message) {
        logSevere(message, null);
    }

    @Override
    public Config getConfiguration() {
        return config;
    }

    @Override
    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    @Override
    public Wrapper getWrapper() {
        return wrapper;
    }

    @Override
    public PreviewManager getPreviewManager() {
        return previewManager;
    }

    @Override
    public CollectionHelper getCollectionHelper() {
        return collectionHelper;
    }

    @Override
    public ActionHandler getActionHandler() {
        return actionHandler;
    }

    @Override
    public ObjectPool<UUID, Component> getMessagePool() {
        return messagePool;
    }

    @Override
    public Storage<Player, Boolean> getFilterStorage() {
        return filterStorage;
    }

    @Override
    public Storage<Player, List<Wrap>> getFavoriteWrapStorage() {
        return favoriteWrapStorage;
    }

    @Override
    public WrapsLoader getWrapsLoader() {
        return wrapsLoader;
    }

    @Override
    public HookAccessor getHookAccessor() {
        return hookAccessor;
    }

    @Override
    public ItemSkinManagerImpl getItemSkinManager() {
        return itemSkinManager;
    }

    public SkinTradeManager getSkinTradeManager() { return skinTradeManager; }

    public LanguageManager getLanguageManager() { return languageManager; }
    @Override public LanguageManager getLanguageService() { return languageManager; }
    public ItemIconFactory getItemIconFactory() { return itemIconFactory; }
    public SkinCatalog getSkinCatalog() { return skinCatalog; }
    public SkinOwnershipService getSkinOwnership() { return skinOwnership; }
    public CompatibilityRegistry getCompatibilityRegistry() { return compatibilityRegistry; }
    public EconomyManager getEconomyManager() { return economyManager; }

    public FileConverter getFileConverter() {
        return fileConverter;
    }

    public ContinuousUpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    /**
     * Boots the shop system: database, repositories, storage, economy facade and every service that
     * builds on them. A failure here only disables the shop; the classic wrap and menu systems keep
     * working exactly as before.
     */
    private void initializeShop() {
        if (scheduler == null) {
            scheduler = new PaperScheduler(getFoliaLib());
        }
        if (shopService != null) {
            // A reload must not rebuild the database or the services; it only re-reads the definition files.
            shopService.reload();
            return;
        }
        try {
            database = new SqlDatabase(HMCWraps.PLUGIN_PATH.resolve("skins.db"), message -> getLogger().severe(message));
            OwnershipRepository ownershipRepository = new SqlOwnershipRepository(database);
            FavoriteRepository favoriteRepository = new SqlFavoriteRepository(database);
            purchaseRepository = new SqlPurchaseRepository(database);
            couponRepository = new SqlCouponRepository(database);
            playerRepository = new SqlPlayerRepository(database);
            shopStateRepository = new SqlShopStateRepository(database);
            collectionRewardRepository = new SqlCollectionRewardRepository(database);
            giftRepository = new SqlGiftRepository(database);
            shopStorage = new SqlStorageProvider(database, ownershipRepository, favoriteRepository);
            database.initialize().whenComplete((ready, error) -> {
                if (error != null || !Boolean.TRUE.equals(ready)) {
                    getLogger().severe("The shop database could not be initialized; paid shop operations are disabled.");
                }
            });
            economyService = new EconomyServiceImpl(economyManager, () -> config == null ? null : config.getEconomy(),
                    message -> getLogger().warning(message));
            transactionService = new PurchaseTransactionService(shopStorage, purchaseRepository,
                    message -> getLogger().warning(message));
            shopRegistry = new ShopRegistry(this, skinCatalog);
            shopRegistry.load();
            dailyShopService = new DailyShopService(shopStateRepository, Clock.systemUTC(),
                    message -> getLogger().warning(message));
            couponService = new CouponServiceImpl(this, shopRegistry, couponRepository, playerRepository, Clock.systemUTC());
            collectionService = new CollectionServiceImpl(this, skinCatalog, skinOwnership, shopStorage,
                    collectionRewardRepository, shopRegistry, economyService, scheduler, playerRepository,
                    playerId -> {
                        if (profileService != null) {
                            profileService.invalidate(playerId);
                        }
                    });
            profileService = new ProfileServiceImpl(skinOwnership, purchaseRepository, giftRepository, couponRepository,
                    playerRepository, collectionService, skinId -> skinCatalog.skinMap().containsKey(skinId),
                    message -> getLogger().warning(message));
            shopService = new ShopServiceImpl(this, shopRegistry, skinCatalog, skinOwnership, shopStorage,
                    transactionService, couponService, economyService, dailyShopService, collectionService, scheduler);
            giftService = new GiftServiceImpl(this, shopRegistry, skinCatalog, skinOwnership, economyService,
                    transactionService, giftRepository, scheduler, () -> config == null ? null : config.getGifts(),
                    message -> getLogger().warning(message));
            shopMenuManager = new ShopMenuManager(this);
            Bukkit.getPluginManager().registerEvents(shopMenuManager, this);
            Bukkit.getPluginManager().registerEvents(new ShopListener(this), this);
            startShopTasks();
            getLogger().info("The shop system is ready (" + shopRegistry.bundles().size() + " bundles, "
                    + shopRegistry.dailyPool().size() + " daily pool candidates, " + shopRegistry.coupons().size()
                    + " coupons).");
        } catch (Throwable throwable) {
            logSevere("Could not initialize the shop system; the classic skin menu remains available.", throwable);
            shopService = null;
        }
    }

    /** Keeps the daily rotation fresh and refreshes it immediately after startup. */
    private void startShopTasks() {
        if (shopService == null || scheduler == null) {
            return;
        }
        refreshDailyShop(false);
        shopRefreshTask = scheduler.runTimer(() -> refreshDailyShop(false), 20L * 60, 20L * 60 * 5);
    }

    private void refreshDailyShop(boolean force) {
        if (shopService == null) {
            return;
        }
        shopService.refreshDailyShop(force).exceptionally(error -> {
            getLogger().warning("Could not refresh the daily shop: " + de.skyslycer.hmcwraps.util.AsyncUtil.describe(error));
            return false;
        });
    }

    /** The scheduling boundary used by the shop services. */
    public Scheduler getScheduler() {
        if (scheduler == null) {
            scheduler = new PaperScheduler(getFoliaLib());
        }
        return scheduler;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public Database getDatabase() {
        return database;
    }

    public SqlStorageProvider getShopStorage() {
        return shopStorage;
    }

    public ShopRegistry getShopRegistry() {
        return shopRegistry;
    }

    public DailyShopService getDailyShopService() {
        return dailyShopService;
    }

    public CouponServiceImpl getCouponService() {
        return couponService;
    }

    public CollectionServiceImpl getCollectionService() {
        return collectionService;
    }

    public ProfileServiceImpl getProfileService() {
        return profileService;
    }

    public ShopServiceImpl getShopService() {
        return shopService;
    }

    public ShopMenuManager getShopMenuManager() {
        return shopMenuManager;
    }

    public GiftServiceImpl getGiftService() {
        return giftService;
    }

    public PurchaseTransactionService getTransactionService() {
        return transactionService;
    }

    public PurchaseRepository getPurchaseRepository() {
        return purchaseRepository;
    }

    public GiftRepository getGiftRepository() {
        return giftRepository;
    }

    public CouponRepository getCouponRepository() {
        return couponRepository;
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    public ShopStateRepository getShopStateRepository() {
        return shopStateRepository;
    }

    public CollectionRewardRepository getCollectionRewardRepository() {
        return collectionRewardRepository;
    }

    /** The economy facade; created lazily so it is always available for diagnostics and reloads. */
    public synchronized EconomyService getEconomyService() {
        if (economyService == null) {
            economyService = new EconomyServiceImpl(economyManager, () -> config == null ? null : config.getEconomy(),
                    message -> getLogger().warning(message));
        }
        return economyService;
    }

    public Map<UUID, String> getWrapGui() {
        return wrapGui;
    }

    @Override
    public FoliaLib getFoliaLib() {
        return foliaLib;
    }

}
