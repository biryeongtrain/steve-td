package kim.biryeong.semionTd;

import kim.biryeong.semionTd.config.SemionConfigLoader;
import kim.biryeong.semionTd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semionTd.command.SemionCommands;
import kim.biryeong.semionTd.entity.SemionEntityTypes;
import kim.biryeong.semionTd.game.SemionGameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemionTd implements ModInitializer {
    public static final String MOD_ID = "semion-td";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final SemionGameManager gameManager = new SemionGameManager();

    @Override
    public void onInitialize() {
        SemionEntityTypes.register();

        LoadedConfigs configs = SemionConfigLoader.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), LOGGER);
        gameManager.configure(configs.economy(), configs.waves(), configs.map());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SemionCommands.register(dispatcher, gameManager));
        ServerTickEvents.END_SERVER_TICK.register(gameManager::tick);

        LOGGER.info("Semion TD initialized.");
    }
}
