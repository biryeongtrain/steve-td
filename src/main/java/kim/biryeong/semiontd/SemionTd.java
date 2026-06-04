package kim.biryeong.semiontd;

import java.nio.file.Path;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.SemionPolymerEntityDataWarmup;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerLimitBypassService;
import kim.biryeong.semiontd.music.SemionMusicLibrary;
import kim.biryeong.semiontd.music.SemionMusicResourcePack;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.placeholder.SemionPlaceholders;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
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
        SemionEntityTypes.register();

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        LoadedConfigs configs = SemionConfigLoader.load(configDir, LOGGER);
        ProductionTowerCatalogs.reloadBuiltIns(configs.towerBalance());
        IncomeSummons.reloadBuiltIns(configs.summons());
        SemionPolymerEntityDataWarmup.warm(configs, LOGGER);
        SemionMusicLibrary musicLibrary = SemionMusicLibrary.load(configDir.resolve("music"), LOGGER);
        SemionMusicResourcePack.register(musicLibrary, LOGGER);
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
                configDir.resolve("profiles.json")
        );
        gameManager.configureMusic(new SemionMusicService(musicLibrary));
        SemionPlayerLimitBypassService.configure(gameManager);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SemionCommands.register(dispatcher, gameManager));
        SemionPlaceholders.register(gameManager);
        SemionHotbarService.register(gameManager);
        SemionTowerInteractionService.register(gameManager);
        Events.initialize(gameManager);

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
