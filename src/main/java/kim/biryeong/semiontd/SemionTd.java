package kim.biryeong.semiontd;

import java.nio.file.Path;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.balance.BalancePatchNotifier;
import kim.biryeong.semiontd.balance.manage.BalanceManagementBootstrap;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.cosmetic.CosmeticService;
import kim.biryeong.semiontd.cosmetic.SemionCosmeticItems;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.SemionPolymerEntityDataWarmup;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerLimitBypassService;
import kim.biryeong.semiontd.music.SemionMusicLibrary;
import kim.biryeong.semiontd.music.SemionMusicResourcePack;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.placeholder.SemionPlaceholders;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.skybox.SemionSkyboxLibrary;
import kim.biryeong.semiontd.skybox.SemionSkyboxResourcePack;
import kim.biryeong.semiontd.skybox.SemionSkyboxService;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.area.AreaEffectService;
import kim.biryeong.semiontd.tower.area.AreaVfxStyleRegistryImpl;
import kim.biryeong.semiontd.tower.area.BuiltinAreaVfxStyles;
import kim.biryeong.semiontd.tower.atlantis.AtlantisVfx;
import kim.biryeong.semiontd.tower.succubus.SuccubusVfx;
import kim.biryeong.semiontd.tower.thunder.ThunderVfx;
import kim.biryeong.semiontd.tip.SemionTipService;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import kim.biryeong.semiontd.ui.SemionTowerInteractionService;
import kim.biryeong.semiontd.ui.dialog.body.AlignedItemBody;
import kim.biryeong.semiontd.ui.dialog.body.AlignedMessage;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import kim.biryeong.semiontd.ui.dialog.body.ImageBody;
import kim.biryeong.semiontd.ui.dialog.body.SplitAlignedMessage;
import kim.biryeong.semiontd.ui.rp.ImageHandler;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemionTd implements ModInitializer {
    public static final String MOD_ID = "semion-td";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final SemionGameManager gameManager = new SemionGameManager();
    private BalanceManagementBootstrap balanceManagement;

    @Override
    public void onInitialize() {
        SemionCosmeticItems.register();
        PolymerResourcePackUtils.addModAssets(MOD_ID);
        kim.biryeong.semiontd.ui.rp.PolyFactoryResourceCompatibility.register();
        SemionEntityTypes.register();
        BuiltInTraits.register();

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        LoadedConfigs configs = SemionConfigLoader.load(configDir, LOGGER);
        TraitBalanceRuntime.apply(configs.traitBalance());
        ProductionTowerCatalogs.reloadBuiltIns(configs.towerBalance());
        IncomeSummons.reloadBuiltIns(configs.summons());
        SemionPolymerEntityDataWarmup.warm(configs, LOGGER);
        SemionMusicLibrary musicLibrary = SemionMusicLibrary.load(configDir.resolve("music"), LOGGER);
        SemionSkyboxLibrary skyboxLibrary = SemionSkyboxLibrary.load(configDir.resolve("skyboxes"), LOGGER);
        SemionMusicService musicService = new SemionMusicService(musicLibrary);
        SemionSkyboxService skyboxService = new SemionSkyboxService(skyboxLibrary, gameManager);
        BalancePatchNotifier balancePatchNotifier =
                new BalancePatchNotifier(configDir.resolve("balance_notification_state.json"));
        SemionMusicResourcePack.register(musicService::library, LOGGER);
        SemionSkyboxResourcePack.register(skyboxService::library, LOGGER);
        kim.biryeong.semiontd.vfx.DisplaySpriteResourcePack.register(
                () -> kim.biryeong.semiontd.tower.demonlord.DemonLordDisplayVfx.SPRITES, LOGGER);
        kim.biryeong.semiontd.vfx.DisplaySpriteResourcePack.register(
                () -> kim.biryeong.semiontd.summon.invasion.InvasionVfx.SPRITES, LOGGER);
        kim.biryeong.semiontd.vfx.DisplaySpriteResourcePack.register(
                () -> kim.biryeong.semiontd.tower.plant.PlantDisplayVfx.SPRITES, LOGGER);
        gameManager.configureWebIntegration(configs.webIntegration());
        gameManager.configureCombatSpeed(configs.combatSpeed());
        gameManager.configureJobAvailability(configs.jobAvailability());
        gameManager.configureAugments(configs.augments());
        gameManager.configure(
                configs.economy(),
                configs.waves(),
                configs.map(),
                configs.progression(),
                configs.rating(),
                configs.towerBalance(),
                configs.summons(),
                configs.persistence(),
                configs.leaderTargeting(),
                configs.incomeLaneRouting(),
                configs.monsterScaling(),
                configDir.resolve("profiles.json")
        );
        gameManager.configureTips(configs.tips());
        gameManager.configureTraits(configs.traits());
        gameManager.configureMusic(musicService);
        CosmeticService cosmeticService = new CosmeticService(gameManager, configDir.resolve("cosmetics.json"));
        SemionTipService tipService = new SemionTipService(gameManager);
        Path bugReportPath = Path.of(configs.persistence().sqlitePath());
        var bugReports = new kim.biryeong.semiontd.report.BugReportCommands(
                bugReportPath.isAbsolute() ? bugReportPath : configDir.resolve(bugReportPath).normalize(), gameManager);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> bugReports.close());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> bugReports.register(dispatcher));
        AreaVfxStyleRegistryImpl areaVfxStyles = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(areaVfxStyles);
        AtlantisVfx.register(areaVfxStyles);
        ThunderVfx.register(areaVfxStyles);
        SuccubusVfx.register(areaVfxStyles);
        SemionTdApi.initializeInternal(new AreaEffectService(gameManager), areaVfxStyles);
        TowerVfxService.initialize(configs.vfx(), gameManager, areaVfxStyles);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> areaVfxStyles.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            balanceManagement = BalanceManagementBootstrap.start(server, gameManager, configDir);
            var active = gameManager.captureBalanceBundle();
            LOGGER.info("Runtime tower balance: end damageScale={}, ranged warlock damageScale={}, healthThreshold={}, healthScale={}, melee lifeCap={}",
                    active.tower().ability("end_global", "damageScale", -1.0),
                    active.tower().ability("ranged_warlock_tower", "damageScale", -1.0),
                    active.tower().ability("ranged_warlock_tower", "healthThreshold", -1.0),
                    active.tower().ability("ranged_warlock_tower", "healthScale", -1.0),
                    active.tower().ability("melee_warlock_tower", "lifeCap", -1.0));
            LOGGER.info("Runtime End assault parameters: {}", active.augment().parametersFor("job_end_towers_p"));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(balancePatchNotifier::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (balanceManagement != null) { balanceManagement.close(); balanceManagement = null; }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> balancePatchNotifier.close());
        SemionPlayerLimitBypassService.configure(gameManager);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SemionCommands.register(dispatcher, gameManager, skyboxService, musicService, tipService, cosmeticService, configDir));
        SemionPlaceholders.register(gameManager);
        SemionHotbarService.register(gameManager);
        SemionTowerInteractionService.register(gameManager);
        cosmeticService.registerUseProtection();
        Events.initialize(gameManager, skyboxService, tipService, cosmeticService);

        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, Identifier.fromNamespaceAndPath("ttt", "aligned_message"), AlignedMessage.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, Identifier.fromNamespaceAndPath("ttt", "aligned_item"), AlignedItemBody.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, Identifier.fromNamespaceAndPath("ttt", "header_message"), HeaderMessage.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, Identifier.fromNamespaceAndPath("ttt", "image"), ImageBody.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, Identifier.fromNamespaceAndPath("ttt", "split_aligned_message"), SplitAlignedMessage.MAP_CODEC);
        ImageHandler.init();
        SemionUiFont.init();
        kim.biryeong.semiontd.ui.rp.GambleGlyphs.init();
        kim.biryeong.semiontd.ui.rp.AugmentCardIcons.init();
        kim.biryeong.semiontd.ui.augment.AugmentCardFrames.init();
        LOGGER.info("Semion TD initialized.");
    }
}
