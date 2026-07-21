package kim.biryeong.semiontd;

import java.nio.file.Path;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.balance.BalancePatchNotifier;
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
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemionTd implements ModInitializer {
    public static final String MOD_ID = "semion-td";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final SemionGameManager gameManager = new SemionGameManager();

    @Override
    public void onInitialize() {
        SemionCosmeticItems.register();
        PolymerResourcePackUtils.addModAssets(MOD_ID);
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
        AreaVfxStyleRegistryImpl areaVfxStyles = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(areaVfxStyles);
        SemionTdApi.initializeInternal(new AreaEffectService(gameManager), areaVfxStyles);
        TowerVfxService.initialize(configs.vfx(), gameManager, areaVfxStyles);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> areaVfxStyles.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(balancePatchNotifier::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> balancePatchNotifier.close());
        SemionPlayerLimitBypassService.configure(gameManager);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SemionCommands.register(dispatcher, gameManager, skyboxService, musicService, tipService, cosmeticService, configDir));
        SemionPlaceholders.register(gameManager);
        SemionHotbarService.register(gameManager);
        SemionTowerInteractionService.register(gameManager);
        cosmeticService.registerUseProtection();
        Events.initialize(gameManager, skyboxService, tipService, cosmeticService);

        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, ResourceLocation.fromNamespaceAndPath("ttt", "aligned_message"), AlignedMessage.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, ResourceLocation.fromNamespaceAndPath("ttt", "aligned_item"), AlignedItemBody.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, ResourceLocation.fromNamespaceAndPath("ttt", "header_message"), HeaderMessage.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, ResourceLocation.fromNamespaceAndPath("ttt", "image"), ImageBody.MAP_CODEC);
        Registry.register(BuiltInRegistries.DIALOG_BODY_TYPE, ResourceLocation.fromNamespaceAndPath("ttt", "split_aligned_message"), SplitAlignedMessage.MAP_CODEC);
        ImageHandler.init();
        SemionUiFont.init();
        LOGGER.info("Semion TD initialized.");
    }
}
