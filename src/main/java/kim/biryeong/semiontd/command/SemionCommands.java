package kim.biryeong.semiontd.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.minimessage.MiniMessage;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.cosmetic.CosmeticCatalog;
import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import kim.biryeong.semiontd.cosmetic.CosmeticService;
import kim.biryeong.semiontd.game.*;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.map.ArenaLoadException;
import kim.biryeong.semiontd.music.SemionMusicLibrary;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.skybox.SemionSkybox;
import kim.biryeong.semiontd.skybox.SemionSkyboxService;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.tip.SemionTipService;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.tower.ocean.OceanVfx;
import kim.biryeong.semiontd.trait.SemionTrait;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitRegistry;
import kim.biryeong.semiontd.trait.TraitSelectionSession;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.ui.SemionText;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

public final class SemionCommands {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final long RATING_SOFT_RESET_CONFIRMATION_MILLIS = 30_000L;
    private static final Map<String, Long> RATING_SOFT_RESET_CONFIRMATIONS = new ConcurrentHashMap<>();

    private SemionCommands() {
    }

    private enum DebugBuildGuideView {
        LIST,
        DETAIL,
        TOWER_UI
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> SemionText.prefixedPlain(message), false);
    }

    private static void successMini(CommandSourceStack source, String markup) {
        source.sendSuccess(() -> SemionText.prefixedMini(markup), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(SemionText.prefixedError(message));
    }

    private static SemionGame playableGame(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        return gameManager.playableGame(source.getPlayerOrException().getUUID()).orElse(null);
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            SemionGameManager gameManager,
            SemionSkyboxService skyboxService,
            SemionMusicService musicService,
            SemionTipService tipService,
            CosmeticService cosmeticService,
            Path configDir
    ) {
        dispatcher.register(literal("semiontd")
                .then(literal("create")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> createGame(context.getSource(), gameManager)))
                .then(literal("start")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> startGame(context.getSource(), gameManager)))
                .then(literal("end")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetGame(context.getSource(), gameManager, "강제 종료")))
                .then(literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetGame(context.getSource(), gameManager, "리셋")))
                .then(literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadConfigs(context.getSource(), gameManager)))
                .then(literal("resourcepack")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("reload")
                                .executes(context -> reloadResourcePackAssets(
                                        context.getSource(),
                                        skyboxService,
                                        musicService,
                                        configDir
                                ))))
                .then(literal("testmode")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setTestMode(
                                        context.getSource(),
                                        gameManager,
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
                .then(literal("autojoin")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> autojoin(context.getSource(), gameManager)))
                .then(literal("playerlimit")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("add")
                                .then(argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> addPlayerLimitBypass(
                                                context.getSource(),
                                                gameManager,
                                                GameProfileArgument.getGameProfiles(context, "player")
                                        ))))
                        .then(literal("remove")
                                .then(argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> removePlayerLimitBypass(
                                                context.getSource(),
                                                gameManager,
                                                GameProfileArgument.getGameProfiles(context, "player")
                                        ))))
                        .then(literal("list")
                                .executes(context -> listPlayerLimitBypasses(context.getSource(), gameManager))))
                .then(literal("ready")
                        .executes(context -> ready(context.getSource(), gameManager)))
                .then(literal("unready")
                        .executes(context -> unready(context.getSource(), gameManager)))
                .then(literal("spectate")
                        .executes(context -> spectate(context.getSource(), gameManager))
                        .then(argument("team", StringArgumentType.word())
                                .executes(context -> spectate(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "team")
                                ))))
                .then(sandboxCommand("sandbox", gameManager))
                .then(literal("economy")
                        .executes(context -> economy(context.getSource(), gameManager)))
                .then(literal("money")
                        .then(literal("request")
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> requestTeamMoney(
                                                context.getSource(),
                                                gameManager,
                                                IntegerArgumentType.getInteger(context, "amount")
                                        ))))
                        .then(literal("accept")
                                .then(argument("requestId", StringArgumentType.word())
                                        .executes(context -> acceptTeamMoney(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "requestId")
                                        )))))
                .then(literal("profile")
                        .executes(context -> profile(context.getSource(), gameManager)))
                .then(cosmeticCommand("cosmetic", gameManager, cosmeticService))
                .then(skyboxCommand("skybox", skyboxService))
                .then(tipCommand("tip", gameManager, tipService))
                .then(literal("rating")
                        .executes(context -> rating(context.getSource(), gameManager))
                        .then(literal("top")
                                .executes(context -> ratingTop(context.getSource(), gameManager)))
                        .then(literal("softreset")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> ratingSoftReset(context.getSource(), gameManager))))
                .then(literal("job")
                        .then(literal("list")
                                .executes(context -> listJobs(context.getSource())))
                        .then(literal("stats")
                                .executes(context -> jobStatisticsDialog(context.getSource(), gameManager))
                                .then(argument("id", ResourceLocationArgument.id())
                                        .executes(context -> jobStatisticsDetailDialog(
                                                context.getSource(),
                                                gameManager,
                                                ResourceLocationArgument.getId(context, "id").toString()
                                        ))))
                        .then(literal("ui")
                                .executes(context -> jobDialog(context.getSource(), gameManager)))
                        .then(literal("current")
                                .executes(context -> currentJob(context.getSource(), gameManager)))
                        .then(literal("select")
                                .then(argument("id", StringArgumentType.string())
                                        .executes(context -> selectJob(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "id")
                                        )))))
                .then(traitCommand("trait", gameManager))
                .then(literal("tower")
                        .then(literal("list")
                                .executes(context -> listProductionTowers(context.getSource(), gameManager)))
                        .then(literal("ui")
                                .executes(context -> towerDialog(context.getSource(), gameManager)))
                        .then(literal("limitup")
                                .executes(context -> towerLimitUp(context.getSource(), gameManager)))
                        .then(literal("build")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(context -> buildProductionTower(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "id")
                                        ))))
                        .then(literal("sell")
                                .executes(context -> sellTower(context.getSource(), gameManager))
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("y", IntegerArgumentType.integer())
                                                .then(argument("z", IntegerArgumentType.integer())
                                                        .executes(context -> sellTower(
                                                                context.getSource(),
                                                                gameManager,
                                                                new GridPosition(
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z")
                                                                )
                                                        ))))))
                        .then(literal("test")
                                .executes(context -> placeTestTower(context.getSource(), gameManager)))
                        .then(literal("upgrades")
                                .executes(context -> listTowerUpgrades(context.getSource(), gameManager)))
                        .then(literal("upgrade")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(context -> upgradeTower(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "id")
                                        ))
                                        .then(argument("x", IntegerArgumentType.integer())
                                                .then(argument("y", IntegerArgumentType.integer())
                                                        .then(argument("z", IntegerArgumentType.integer())
                                                                .executes(context -> upgradeTower(
                                                                        context.getSource(),
                                                                        gameManager,
                                                                        StringArgumentType.getString(context, "id"),
                                                                        new GridPosition(
                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                IntegerArgumentType.getInteger(context, "z")
                                                                        )
                                                                ))))))))
                .then(literal("gasup")
                        .executes(context -> emeraldUp(context.getSource(), gameManager)))
                .then(literal("emeraldup")
                        .executes(context -> emeraldUp(context.getSource(), gameManager)))
                .then(literal("summon")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> summon(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "id")
                                ))))
                .then(literal("summons")
                        .executes(context -> summons(context.getSource(), gameManager)))
                .then(literal("summonui")
                        .executes(context -> summonDialog(context.getSource(), gameManager, 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> summonDialog(
                                        context.getSource(),
                                        gameManager,
                                        IntegerArgumentType.getInteger(context, "page")
                                ))))
                .then(literal("leader")
                        .then(literal("target")
                                .then(argument("team", StringArgumentType.word())
                                        .executes(context -> leaderTarget(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "team")
                                        )))))
                .then(literal("killboss")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("team", StringArgumentType.word())
                                .executes(context -> killBoss(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "team")
                                ))))
                .then(literal("status")
                        .executes(context -> status(context.getSource(), gameManager))
                        .then(literal("teams")
                                .executes(context -> statusTeams(context.getSource(), gameManager)))
                        .then(literal("lanes")
                                .executes(context -> statusLanes(context.getSource(), gameManager)))
                        .then(literal("players")
                                .executes(context -> statusPlayers(context.getSource(), gameManager))))
                .then(literal("ui")
                        .executes(context -> statusDialog(context.getSource(), gameManager))));

        dispatcher.register(literal("직업")
                .executes(context -> jobDialog(context.getSource(), gameManager)));
        dispatcher.register(literal("치장")
                .executes(context -> openCosmeticShop(context.getSource(), cosmeticService)));
        dispatcher.register(skyboxCommand("스카이박스", skyboxService));
        dispatcher.register(traitCommand("특성", gameManager));
        dispatcher.register(literal("레이팅")
                .executes(context -> rating(context.getSource(), gameManager))
                .then(literal("순위")
                        .executes(context -> ratingTop(context.getSource(), gameManager)))
                .then(literal("top")
                        .executes(context -> ratingTop(context.getSource(), gameManager))));
        dispatcher.register(literal("랭크")
                .executes(context -> rating(context.getSource(), gameManager))
                .then(literal("순위")
                        .executes(context -> ratingTop(context.getSource(), gameManager)))
                .then(literal("top")
                        .executes(context -> ratingTop(context.getSource(), gameManager))));
        dispatcher.register(literal("순위")
                .executes(context -> ratingTop(context.getSource(), gameManager)));
        dispatcher.register(literal("준비")
                .executes(context -> ready(context.getSource(), gameManager)));
        dispatcher.register(literal("요청")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> requestTeamMoney(
                                context.getSource(),
                                gameManager,
                                IntegerArgumentType.getInteger(context, "amount")
                        ))));
        dispatcher.register(sandboxCommand("샌드박스", gameManager));
        dispatcher.register(literal("빌드")
                .then(literal("기록")
                        .then(argument("title", StringArgumentType.greedyString())
                                .executes(context -> publishBuild(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "title")
                                ))))
                .then(literal("목록")
                        .executes(context -> buildListDialog(context.getSource(), gameManager))));

        dispatcher.register(literal("semiontd-internal")
                .then(literal("build")
                        .then(literal("list")
                                .then(argument("publicPage", IntegerArgumentType.integer(1))
                                        .then(argument("myPage", IntegerArgumentType.integer(1))
                                                .executes(context -> buildListDialog(
                                                        context.getSource(),
                                                        gameManager,
                                                        IntegerArgumentType.getInteger(context, "publicPage"),
                                                        IntegerArgumentType.getInteger(context, "myPage")
                                                )))))
                        .then(literal("track")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> trackBuild(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code")
                                        ))))
                        .then(literal("detail")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> showBuildDetails(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code")
                                        ))))
                        .then(literal("public")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> setBuildVisibility(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code"),
                                                BuildGuide.VISIBILITY_PUBLIC
                                        ))))
                        .then(literal("private")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> setBuildVisibility(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code"),
                                                BuildGuide.VISIBILITY_PRIVATE
                                        ))))
                        .then(literal("delete")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> deleteBuild(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code")
                                        ))))
                        .then(literal("clear")
                                .executes(context -> clearTrackedBuild(context.getSource(), gameManager)))));

        dispatcher.register(literal("semiontd-debug")
                .requires(source -> source.hasPermission(2))
                .then(literal("towerui")
                        .executes(context -> debugTowerDialog(context.getSource(), gameManager)))
                .then(literal("tower")
                        .then(literal("ui")
                                .executes(context -> debugTowerDialog(context.getSource(), gameManager))))
                .then(literal("vfx")
                        .then(literal("stats")
                                .executes(context -> debugVfxStats(context.getSource())))
                        .then(literal("reset")
                                .executes(context -> resetVfxStats(context.getSource())))
                        .then(literal("warlock_sacrifice")
                                .executes(context -> debugWarlockSacrificeVfx(context.getSource())))
                        .then(literal("transcendence")
                                .executes(context -> debugTranscendenceVfx(context.getSource())))
                        .then(literal("ocean_supply")
                                .executes(context -> debugOceanSupplyVfx(context.getSource())))
                        .then(literal("ocean_dehydrated")
                                .executes(context -> debugOceanDehydratedVfx(context.getSource()))))
                .then(literal("summonui")
                        .executes(context -> debugSummonDialog(context.getSource(), gameManager, 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> debugSummonDialog(
                                        context.getSource(),
                                        gameManager,
                                        IntegerArgumentType.getInteger(context, "page")
                                ))))
                .then(literal("summon")
                        .then(literal("ui")
                                .executes(context -> debugSummonDialog(context.getSource(), gameManager, 1))
                                .then(argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> debugSummonDialog(
                                                context.getSource(),
                                                gameManager,
                                                IntegerArgumentType.getInteger(context, "page")
                                        )))))
                .then(literal("give")
                        .then(literal("diamond")
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> debugGiveCurrency(
                                                context.getSource(),
                                                gameManager,
                                                CurrencyDebugType.DIAMOND,
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                null
                                        ))
                                        .then(argument("player", EntityArgument.player())
                                                .executes(context -> debugGiveCurrency(
                                                        context.getSource(),
                                                        gameManager,
                                                        CurrencyDebugType.DIAMOND,
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player")
                                                )))))
                        .then(literal("emerald")
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> debugGiveCurrency(
                                                context.getSource(),
                                                gameManager,
                                                CurrencyDebugType.EMERALD,
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                null
                                        ))
                                        .then(argument("player", EntityArgument.player())
                                                .executes(context -> debugGiveCurrency(
                                                        context.getSource(),
                                                        gameManager,
                                                        CurrencyDebugType.EMERALD,
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player")
                                                ))))))
                .then(literal("buildguide")
                        .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.LIST))
                        .then(literal("list")
                                .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.LIST)))
                        .then(literal("detail")
                                .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.DETAIL)))
                        .then(literal("towerui")
                                .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.TOWER_UI)))));
    }

    private static int debugVfxStats(CommandSourceStack source) {
        success(source, TowerVfxService.statsSummary());
        return 1;
    }

    private static int debugWarlockSacrificeVfx(CommandSourceStack source) throws CommandSyntaxException {
        TowerVfxService.showWarlockSacrificeDebug(source.getPlayerOrException());
        success(source, "흑마법사 흡수 VFX를 재생했습니다.");
        return 1;
    }

    private static int debugTranscendenceVfx(CommandSourceStack source) throws CommandSyntaxException {
        TowerVfxService.showTranscendenceDebug(source.getPlayerOrException());
        success(source, "초월 VFX를 재생했습니다.");
        return 1;
    }

    private static int debugOceanSupplyVfx(CommandSourceStack source) throws CommandSyntaxException {
        OceanVfx.showWaterSupplyDebug(source.getPlayerOrException());
        success(source, "물 공급 VFX를 재생했습니다.");
        return 1;
    }

    private static int debugOceanDehydratedVfx(CommandSourceStack source) throws CommandSyntaxException {
        OceanVfx.showDehydratedDebug(source.getPlayerOrException());
        success(source, "물 고갈 VFX를 재생했습니다.");
        return 1;
    }

    private static int resetVfxStats(CommandSourceStack source) {
        TowerVfxService.resetStats();
        success(source, "VFX 통계를 초기화했습니다.");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cosmeticCommand(
            String rootName,
            SemionGameManager gameManager,
            CosmeticService cosmeticService
    ) {
        return literal(rootName)
                .executes(context -> openCosmeticShop(context.getSource(), cosmeticService))
                .then(literal("points")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("give")
                                .then(argument("player", GameProfileArgument.gameProfile())
                                        .then(argument("amount", LongArgumentType.longArg(1))
                                                .executes(context -> grantCosmeticPoints(
                                                        context.getSource(),
                                                        gameManager,
                                                        GameProfileArgument.getGameProfiles(context, "player"),
                                                        LongArgumentType.getLong(context, "amount")
                                                ))))))
                .then(literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("price", LongArgumentType.longArg(0))
                                        .executes(context -> addCosmetic(
                                                context.getSource(),
                                                cosmeticService,
                                                StringArgumentType.getString(context, "id"),
                                                LongArgumentType.getLong(context, "price")
                                        ))
                                        .then(argument("slot", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("head", "offhand"), builder
                                                ))
                                                .executes(context -> addCosmetic(
                                                        context.getSource(),
                                                        cosmeticService,
                                                        StringArgumentType.getString(context, "id"),
                                                        LongArgumentType.getLong(context, "price"),
                                                        StringArgumentType.getString(context, "slot")
                                                ))))))
                .then(literal("update")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        cosmeticService.entries().stream().map(CosmeticCatalog.Entry::id),
                                        builder
                                ))
                                .then(argument("price", LongArgumentType.longArg(0))
                                        .executes(context -> updateCosmetic(
                                                context.getSource(),
                                                cosmeticService,
                                                StringArgumentType.getString(context, "id"),
                                                LongArgumentType.getLong(context, "price")
                                        ))
                                        .then(argument("slot", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("head", "offhand"), builder
                                                ))
                                                .executes(context -> updateCosmetic(
                                                        context.getSource(),
                                                        cosmeticService,
                                                        StringArgumentType.getString(context, "id"),
                                                        LongArgumentType.getLong(context, "price"),
                                                        StringArgumentType.getString(context, "slot")
                                                ))))))
                .then(literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        cosmeticService.entries().stream().map(CosmeticCatalog.Entry::id),
                                        builder
                                ))
                                .executes(context -> removeCosmetic(
                                        context.getSource(),
                                        cosmeticService,
                                        StringArgumentType.getString(context, "id")
                                ))))
                .then(literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> listCosmetics(context.getSource(), cosmeticService)))
                .then(literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadCosmetics(context.getSource(), cosmeticService)));
    }

    private static int grantCosmeticPoints(
            CommandSourceStack source,
            SemionGameManager gameManager,
            Collection<GameProfile> targets,
            long amount
    ) {
        if (targets.size() != 1) {
            failure(source, "치장 포인트는 한 번에 한 플레이어에게만 지급할 수 있습니다.");
            return 0;
        }
        GameProfile target = targets.iterator().next();
        String targetName = target.getName() == null || target.getName().isBlank()
                ? target.getId().toString()
                : target.getName();
        var updated = gameManager.grantCosmeticCurrency(target.getId(), targetName, amount);
        if (updated.isEmpty()) {
            failure(source, "치장 포인트를 저장하지 못했거나 보유량 최대치를 초과했습니다.");
            return 0;
        }
        String feedback = targetName + "에게 치장 포인트 " + amount
                + "개를 지급했습니다. 현재 포인트=" + updated.get().cosmeticCurrency();
        source.sendSuccess(() -> SemionText.prefixedPlain(feedback), true);
        return 1;
    }

    private static int openCosmeticShop(CommandSourceStack source, CosmeticService cosmeticService) throws CommandSyntaxException {
        cosmeticService.openShop(source.getPlayerOrException());
        return 1;
    }

    private static int addCosmetic(
            CommandSourceStack source,
            CosmeticService cosmeticService,
            String id,
            long price
    ) throws CommandSyntaxException {
        return addCosmetic(source, cosmeticService, id, price, EquipmentSlot.HEAD);
    }

    private static int addCosmetic(
            CommandSourceStack source,
            CosmeticService cosmeticService,
            String id,
            long price,
            String slotName
    ) throws CommandSyntaxException {
        EquipmentSlot slot = cosmeticSlot(slotName);
        if (slot == null) {
            failure(source, "슬롯은 head 또는 offhand여야 합니다.");
            return 0;
        }
        return addCosmetic(source, cosmeticService, id, price, slot);
    }

    private static int addCosmetic(
            CommandSourceStack source,
            CosmeticService cosmeticService,
            String id,
            long price,
            EquipmentSlot slot
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getMainHandItem().isEmpty()) {
            failure(source, "주 손에 등록할 치장 아이템을 들어 주세요.");
            return 0;
        }
        CosmeticCatalog.MutationResult result = cosmeticService.add(
                source.getServer(), id, price, slot, player.getMainHandItem()
        );
        if (result != CosmeticCatalog.MutationResult.SUCCESS) {
            return cosmeticMutationFailure(source, result, true);
        }
        success(source, "치장 아이템 '" + id + "'을(를) " + price + " 포인트, "
                + CosmeticItemSupport.slotName(slot) + " 슬롯으로 등록했습니다.");
        return 1;
    }

    private static int updateCosmetic(
            CommandSourceStack source,
            CosmeticService cosmeticService,
            String id,
            long price
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getMainHandItem().isEmpty()) {
            failure(source, "주 손에 교체할 치장 아이템을 들어 주세요.");
            return 0;
        }
        CosmeticCatalog.MutationResult result = cosmeticService.update(
                source.getServer(), id, price, player.getMainHandItem()
        );
        if (result != CosmeticCatalog.MutationResult.SUCCESS) {
            return cosmeticMutationFailure(source, result, false);
        }
        success(source, "치장 아이템 '" + id + "'의 아이템과 가격을 갱신했습니다.");
        return 1;
    }

    private static int updateCosmetic(
            CommandSourceStack source,
            CosmeticService cosmeticService,
            String id,
            long price,
            String slotName
    ) throws CommandSyntaxException {
        EquipmentSlot slot = cosmeticSlot(slotName);
        if (slot == null) {
            failure(source, "슬롯은 head 또는 offhand여야 합니다.");
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (player.getMainHandItem().isEmpty()) {
            failure(source, "주 손에 교체할 치장 아이템을 들어 주세요.");
            return 0;
        }
        CosmeticCatalog.MutationResult result = cosmeticService.update(
                source.getServer(), id, price, slot, player.getMainHandItem()
        );
        if (result != CosmeticCatalog.MutationResult.SUCCESS) {
            return cosmeticMutationFailure(source, result, false);
        }
        success(source, "치장 아이템 '" + id + "'의 아이템, 가격, 착용 슬롯을 갱신했습니다.");
        return 1;
    }

    private static EquipmentSlot cosmeticSlot(String name) {
        return switch (name) {
            case "head" -> EquipmentSlot.HEAD;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static int removeCosmetic(CommandSourceStack source, CosmeticService cosmeticService, String id) {
        CosmeticService.RemoveResult result = cosmeticService.remove(source.getServer(), id);
        if (result.catalogResult() != CosmeticCatalog.MutationResult.SUCCESS) {
            return cosmeticMutationFailure(source, result.catalogResult(), false);
        }
        if (!result.profilesSaved()) {
            failure(source, "목록에서는 제거했지만 일부 착용 선택을 저장하지 못했습니다.");
            return 0;
        }
        success(source, "치장 아이템 '" + id + "'을(를) 판매 목록에서 제거했습니다.");
        return 1;
    }

    private static int listCosmetics(CommandSourceStack source, CosmeticService cosmeticService) {
        List<CosmeticCatalog.Entry> entries = cosmeticService.entries();
        if (entries.isEmpty()) {
            success(source, "등록된 치장 아이템이 없습니다.");
            return 1;
        }
        success(source, "치장 아이템 " + entries.size() + "개:");
        for (CosmeticCatalog.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal("- " + entry.id() + ": " + entry.price()
                    + " [" + entry.slot().getSerializedName() + "]"), false);
        }
        return entries.size();
    }

    private static int reloadCosmetics(CommandSourceStack source, CosmeticService cosmeticService) {
        if (!cosmeticService.reload(source.getServer())) {
            failure(source, "치장 목록을 다시 불러오지 못했습니다. 기존 목록을 유지합니다.");
            return 0;
        }
        success(source, "치장 목록을 다시 불러왔습니다. 등록 상품: " + cosmeticService.entries().size() + "개");
        return 1;
    }

    private static int cosmeticMutationFailure(
            CommandSourceStack source,
            CosmeticCatalog.MutationResult result,
            boolean adding
    ) {
        String message = switch (result) {
            case DUPLICATE -> "이미 같은 ID의 치장 아이템이 있습니다.";
            case MISSING -> "해당 ID의 치장 아이템이 없습니다.";
            case INVALID -> "주 손 아이템은 지정한 슬롯에 착용 가능한 아이템이어야 합니다.";
            case SAVE_FAILED -> "치장 목록을 저장하지 못해 변경을 취소했습니다.";
            case UNAVAILABLE -> "치장 목록을 불러오지 못해 변경할 수 없습니다.";
            default -> adding ? "치장 아이템을 등록하지 못했습니다." : "치장 아이템을 변경하지 못했습니다.";
        };
        failure(source, message);
        return 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> skyboxCommand(
            String name,
            SemionSkyboxService skyboxService
    ) {
        return literal(name)
                .executes(context -> listSkyboxes(context.getSource(), skyboxService))
                .then(literal("off")
                        .executes(context -> selectSkybox(
                                context.getSource(),
                                skyboxService,
                                SemionSkyboxService.OFF_SELECTION
                        )))
                .then(argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                skyboxService.availableSkyboxes().stream().map(SemionSkybox::id),
                                builder
                        ))
                        .executes(context -> selectSkybox(
                                context.getSource(),
                                skyboxService,
                                StringArgumentType.getString(context, "id")
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tipCommand(
            String name,
            SemionGameManager gameManager,
            SemionTipService tipService
    ) {
        return literal(name)
                .executes(context -> tipStatus(context.getSource(), gameManager, tipService))
                .then(literal("on")
                        .executes(context -> setTipsEnabled(context.getSource(), tipService, true)))
                .then(literal("off")
                        .executes(context -> setTipsEnabled(context.getSource(), tipService, false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> traitCommand(
            String name,
            SemionGameManager gameManager
    ) {
        return literal(name)
                .executes(context -> traitDialog(context.getSource(), gameManager))
                .then(literal("ui")
                        .executes(context -> traitDialog(context.getSource(), gameManager))
                        .then(argument("slot", StringArgumentType.word())
                                .executes(context -> traitDialog(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "slot")
                                ))))
                .then(literal("current")
                        .executes(context -> currentTrait(context.getSource(), gameManager)))
                .then(literal("list")
                        .executes(context -> listTraits(context.getSource(), gameManager)))
                .then(literal("select")
                        .then(argument("slot", StringArgumentType.word())
                                .then(argument("id", StringArgumentType.string())
                                        .executes(context -> selectTrait(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "slot"),
                                                StringArgumentType.getString(context, "id")
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sandboxCommand(
            String name,
            SemionGameManager gameManager
    ) {
        return literal(name)
                .executes(context -> startSandbox(context.getSource(), gameManager))
                .then(literal("start")
                        .executes(context -> startSandbox(context.getSource(), gameManager)))
                .then(literal("reset")
                        .executes(context -> resetSandbox(context.getSource(), gameManager)))
                .then(literal("leave")
                        .executes(context -> leaveSandbox(context.getSource(), gameManager)))
                .then(sandboxCurrencyCommand("give", gameManager))
                .then(sandboxCurrencyCommand("money", gameManager));
    }

    private static int createGame(CommandSourceStack source, SemionGameManager gameManager) {
        try {
            gameManager.createGame(source.getServer());
            success(source, "로비와 아레나를 생성했습니다.");
            return 1;
        } catch (ArenaLoadException exception) {
            failure(source, "아레나 생성 실패: " + exception.getMessage());
            return 0;
        }
    }

    private static int resetGame(CommandSourceStack source, SemionGameManager gameManager, String actionLabel) {
        try {
            boolean hadActiveGame = gameManager.resetToLobby(source.getServer());
            String suffix = hadActiveGame ? "" : " 진행 중인 게임은 없었습니다.";
            success(source, "게임을 " + actionLabel + "하고 모두 로비로 이동했습니다." + suffix);
            return 1;
        } catch (ArenaLoadException exception) {
            failure(source, "로비 이동 실패: " + exception.getMessage());
            return 0;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD lobby reset failure.", exception);
            failure(source, "로비 이동 중 예기치 못한 오류가 발생했습니다: " + exception.getMessage());
            return 0;
        }
    }

    private static int startGame(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = activeWaitingGame(source, gameManager, "start");
        if (game == null) {
            return 0;
        }

        Optional<ParticipantSelectionPlan> plan = buildSelectionPlan(source, gameManager, game);
        if (plan.isEmpty()) {
            failure(source, "현재 게임 모드로 시작할 준비 완료 인원이 부족합니다.");
            return 0;
        }

        SemionGameManager.StartCountdownResult countdownResult = gameManager.scheduleStart(source.getServer(), plan.get());
        if (countdownResult != SemionGameManager.StartCountdownResult.SCHEDULED) {
            failure(source, startCountdownFailureMessage(countdownResult));
            return 0;
        }

        applyPlanToVanillaTeams(source, plan.get());
        assignUnreadyPlayersToSpectatorTeam(source, game, plan.get());
        String lobbyLoaded = gameManager.lobbyWorld().isPresent() ? ", lobbyLoaded=true" : ", lobbyLoaded=false";
        String startStage = gameManager.traitSelectionActive()
                ? "특성 선택 단계를 시작했습니다."
                : "시작 카운트다운을 시작했습니다.";
        success(source, startStage + " 참가자="
                + plan.get().activePlayerCount()
                + ", 팀=" + plan.get().activeTeamCount()
                + ", 구성=" + plan.get().compositionSummary()
                + ", 관전자=" + plan.get().spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", 모드=" + gameManager.matchMode()
                + (gameManager.traitSelectionActive()
                ? ", 선택시간=" + gameManager.traitSelectionSecondsRemaining() + "초"
                : "")
                + lobbyLoaded);
        return 1;
    }

    private static int reloadConfigs(CommandSourceStack source, SemionGameManager gameManager) {
        try {
            SemionGameManager.ReloadConfigResult result = gameManager.reloadConfigs(source.getServer());
            if (!result.reloaded()) {
                failure(source, "설정 경로를 찾지 못해 리로드하지 못했습니다.");
                return 0;
            }
            String activeGameText = result.activeGameUpdated()
                    ? "진행 중인 게임의 경제/웨이브 설정도 즉시 반영했습니다."
                    : "진행 중인 게임은 없습니다.";
            success(source, "컨픽을 다시 불러왔습니다. " + activeGameText + " 타워 카탈로그는 즉시 갱신되며, 맵 설정은 다음 게임 생성부터 적용됩니다.");
            return 1;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD config reload failure.", exception);
            failure(source, "컨픽 리로드 중 예기치 못한 오류가 발생했습니다: " + exception.getMessage());
            return 0;
        }
    }

    private static int reloadResourcePackAssets(
            CommandSourceStack source,
            SemionSkyboxService skyboxService,
            SemionMusicService musicService,
            Path configDir
    ) {
        try {
            SemionMusicLibrary musicLibrary = SemionMusicLibrary.load(configDir.resolve("music"), SemionTd.LOGGER);
            kim.biryeong.semiontd.skybox.SemionSkyboxLibrary skyboxLibrary =
                    kim.biryeong.semiontd.skybox.SemionSkyboxLibrary.load(
                            configDir.resolve("skyboxes"),
                            SemionTd.LOGGER
                    );
            musicService.replaceLibrary(source.getServer(), musicLibrary);
            skyboxService.replaceLibrary(skyboxLibrary);

            source.getServer().getCommands().performPrefixedCommand(
                    source.getServer().createCommandSourceStack(),
                    "polymer generate-pack reload"
            );
            success(source, "스카이박스 " + skyboxLibrary.skyboxes().size()
                    + "개와 음악 " + musicLibrary.tracks().size()
                    + "개를 다시 읽었습니다. 생성팩 재생성과 접속자 재전송을 시작합니다.");
            return 1;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD resource-pack asset reload failure.", exception);
            failure(source, "스카이박스/음악 리로드 중 예기치 못한 오류가 발생했습니다: " + exception.getMessage());
            return 0;
        }
    }

    private static int autojoin(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = activeWaitingGame(source, gameManager, "autojoin");
        if (game == null) {
            return 0;
        }

        Optional<ParticipantSelectionPlan> plan = buildSelectionPlan(source, gameManager, game);
        if (plan.isEmpty()) {
            failure(source, "현재 게임 모드로 팀을 배정할 준비 완료 인원이 부족합니다.");
            return 0;
        }

        applyPlanToVanillaTeams(source, plan.get());
        String lobbyLoaded = gameManager.lobbyWorld().isPresent() ? ", lobbyLoaded=true" : ", lobbyLoaded=false";
        success(source, "다음 시작을 위한 팀을 배정했습니다. 참가자="
                + plan.get().activePlayerCount()
                + ", 팀=" + plan.get().activeTeamCount()
                + ", 구성=" + plan.get().compositionSummary()
                + ", 관전자=" + plan.get().spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", 모드=" + gameManager.matchMode()
                + lobbyLoaded);
        return plan.get().activePlayerCount();
    }

    private static int addPlayerLimitBypass(
            CommandSourceStack source,
            SemionGameManager gameManager,
            Collection<GameProfile> profiles
    ) {
        List<String> added = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        for (GameProfile profile : profiles) {
            if (profile.getId() == null) {
                continue;
            }
            String name = playerLimitBypassName(profile);
            if (gameManager.addPlayerLimitBypass(profile.getId(), name)) {
                added.add(name);
            } else {
                existing.add(name);
            }
        }
        if (added.isEmpty() && existing.isEmpty()) {
            failure(source, "정원 초과 입장을 허용할 플레이어를 찾지 못했습니다.");
            return 0;
        }
        if (!added.isEmpty()) {
            success(source, "정원 초과 입장 허용 목록에 추가했습니다: " + String.join(", ", added));
            return added.size();
        }
        success(source, "이미 정원 초과 입장 허용 목록에 있습니다: " + String.join(", ", existing));
        return 1;
    }

    private static int removePlayerLimitBypass(
            CommandSourceStack source,
            SemionGameManager gameManager,
            Collection<GameProfile> profiles
    ) {
        List<String> removed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (GameProfile profile : profiles) {
            if (profile.getId() == null) {
                continue;
            }
            String name = playerLimitBypassName(profile);
            if (gameManager.removePlayerLimitBypass(profile.getId())) {
                removed.add(name);
            } else {
                missing.add(name);
            }
        }
        if (removed.isEmpty() && missing.isEmpty()) {
            failure(source, "정원 초과 입장 허용 목록에서 제거할 플레이어를 찾지 못했습니다.");
            return 0;
        }
        if (!removed.isEmpty()) {
            success(source, "정원 초과 입장 허용 목록에서 제거했습니다: " + String.join(", ", removed));
            return removed.size();
        }
        success(source, "정원 초과 입장 허용 목록에 없습니다: " + String.join(", ", missing));
        return 1;
    }

    private static int listPlayerLimitBypasses(CommandSourceStack source, SemionGameManager gameManager) {
        Map<UUID, String> entries = gameManager.playerLimitBypasses();
        if (entries.isEmpty()) {
            success(source, "정원 초과 입장 허용 목록이 비어 있습니다.");
            return 1;
        }
        List<String> names = entries.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> entry.getValue() + " (" + entry.getKey() + ")")
                .toList();
        success(source, "정원 초과 입장 허용 목록: " + String.join(", ", names));
        return entries.size();
    }

    private static int listSkyboxes(CommandSourceStack source, SemionSkyboxService skyboxService)
            throws CommandSyntaxException {
        if (skyboxService.availableSkyboxes().isEmpty()) {
            failure(source, "등록된 스카이박스가 없습니다. config/semion-td/skyboxes/에 2:1 PNG를 추가한 뒤 /semiontd resourcepack reload를 실행하세요.");
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        String selected = skyboxService.selectedSkybox(player)
                .map(SemionSkybox::id)
                .orElse(SemionSkyboxService.OFF_SELECTION);
        String available = skyboxService.availableSkyboxes().stream()
                .map(skybox -> skybox.id() + " (" + skybox.displayName() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        success(source, "현재 스카이박스: " + selected + ". 사용 가능: " + available
                + ". 선택: /semiontd skybox <id>, 끄기: /semiontd skybox off");
        return skyboxService.availableSkyboxes().size();
    }

    private static int selectSkybox(
            CommandSourceStack source,
            SemionSkyboxService skyboxService,
            String skyboxId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!skyboxService.select(player, skyboxId)) {
            failure(source, "알 수 없는 스카이박스입니다: " + skyboxId + ". /semiontd skybox를 확인하세요.");
            return 0;
        }
        if (SemionSkyboxService.OFF_SELECTION.equalsIgnoreCase(skyboxId)) {
            success(source, "개인 스카이박스를 껐습니다.");
        } else {
            success(source, "개인 스카이박스를 " + skyboxId + "(으)로 변경했습니다.");
        }
        return 1;
    }

    private static int tipStatus(
            CommandSourceStack source,
            SemionGameManager gameManager,
            SemionTipService tipService
    ) throws CommandSyntaxException {
        boolean personalEnabled = tipService.tipsEnabled(source.getPlayerOrException());
        String globalStatus = gameManager.tipConfig().enabled() ? "활성화" : "서버 설정에서 비활성화";
        success(source, "팁 수신=" + (personalEnabled ? "켜짐" : "꺼짐") + ", 전체 설정=" + globalStatus
                + ". 끄기: /semiontd tip off, 켜기: /semiontd tip on");
        return 1;
    }

    private static int setTipsEnabled(
            CommandSourceStack source,
            SemionTipService tipService,
            boolean enabled
    ) throws CommandSyntaxException {
        tipService.setTipsEnabled(source.getPlayerOrException(), enabled);
        success(source, enabled
                ? "팁을 켰습니다."
                : "팁을 껐습니다. 다시 받으려면 /semiontd tip on을 사용하세요.");
        return 1;
    }

    private static String playerLimitBypassName(GameProfile profile) {
        return profile.getName() == null || profile.getName().isBlank() ? profile.getId().toString() : profile.getName();
    }

    private static int ready(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "ready");
        if (game == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.markReady(player.getUUID())) {
            failure(source, "준비 완료 처리에 실패했습니다.");
            return 0;
        }

        success(source, "준비 완료했습니다. 준비 인원=" + game.readyPlayerCount());
        return 1;
    }

    private static int unready(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "unready");
        if (game == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.markNotReady(player.getUUID())) {
            failure(source, "준비 해제에 실패했습니다.");
            return 0;
        }

        success(source, "준비를 해제했습니다. 준비 인원=" + game.readyPlayerCount());
        return 1;
    }

    private static int spectate(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.rosterLocked() || game.phase() == RoundPhase.ENDED) {
            failure(source, "관전할 수 있는 진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.addLateSpectator(source.getServer(), player)) {
            failure(source, "현재 상태에서는 관전으로 전환할 수 없습니다.");
            return 0;
        }

        success(source, "게임 관전으로 이동했습니다.");
        return 1;
    }

    private static int spectate(CommandSourceStack source, SemionGameManager gameManager, String teamName) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.rosterLocked() || game.phase() == RoundPhase.ENDED) {
            failure(source, "관전할 수 있는 진행 중인 게임이 없습니다.");
            return 0;
        }

        TeamId targetTeam;
        try {
            targetTeam = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            failure(source, "알 수 없는 팀입니다. red, blue, green, yellow 중 하나를 사용하세요.");
            return 0;
        }

        if (!game.canSpectateTeam(targetTeam)) {
            failure(source, "현재 관전할 수 없는 팀입니다: " + targetTeam.name());
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.addLateSpectator(source.getServer(), player, targetTeam)) {
            failure(source, "현재 상태에서는 관전으로 전환할 수 없습니다.");
            return 0;
        }

        success(source, targetTeam.name() + " 팀 관전으로 이동했습니다.");
        return 1;
    }

    private static int startSandbox(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGameManager.SandboxStartResult result = gameManager.startSandbox(source.getServer(), player);
        return switch (result) {
            case STARTED -> {
                success(source, "샌드박스 모드를 시작했습니다. 타워/인컴 명령은 샌드박스 세션에만 적용됩니다.");
                yield 1;
            }
            case REPLACED -> {
                success(source, "기존 샌드박스를 초기화하고 새 샌드박스를 시작했습니다.");
                yield 1;
            }
            case PLAYER_IN_MATCH -> {
                failure(source, "현재 경기 참가자 또는 시작 대기자로 등록되어 있어 샌드박스를 시작할 수 없습니다.");
                yield 0;
            }
            case FAILED -> {
                failure(source, "샌드박스 모드 시작에 실패했습니다.");
                yield 0;
            }
        };
    }

    private static int resetSandbox(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGameManager.SandboxStartResult result = gameManager.startSandbox(source.getServer(), player);
        if (result == SemionGameManager.SandboxStartResult.STARTED || result == SemionGameManager.SandboxStartResult.REPLACED) {
            success(source, "샌드박스를 초기화했습니다.");
            return 1;
        }
        if (result == SemionGameManager.SandboxStartResult.PLAYER_IN_MATCH) {
            failure(source, "현재 경기 참가자 또는 시작 대기자로 등록되어 있어 샌드박스를 초기화할 수 없습니다.");
            return 0;
        }
        failure(source, "샌드박스 초기화에 실패했습니다.");
        return 0;
    }

    private static int leaveSandbox(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        if (!gameManager.leaveSandbox(source.getServer(), source.getPlayerOrException())) {
            failure(source, "종료할 샌드박스 세션이 없습니다.");
            return 0;
        }
        success(source, "샌드박스 모드를 종료했습니다.");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sandboxCurrencyCommand(
            String name,
            SemionGameManager gameManager
    ) {
        return literal(name)
                .then(literal("diamond")
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> grantSandboxCurrency(
                                        context.getSource(),
                                        gameManager,
                                        SemionGameManager.SandboxCurrency.DIAMOND,
                                        IntegerArgumentType.getInteger(context, "amount")
                                ))))
                .then(literal("emerald")
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> grantSandboxCurrency(
                                        context.getSource(),
                                        gameManager,
                                        SemionGameManager.SandboxCurrency.EMERALD,
                                        IntegerArgumentType.getInteger(context, "amount")
                                ))))
                .then(literal("income")
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> grantSandboxCurrency(
                                        context.getSource(),
                                        gameManager,
                                        SemionGameManager.SandboxCurrency.INCOME,
                                        IntegerArgumentType.getInteger(context, "amount")
                                ))));
    }

    private static int grantSandboxCurrency(
            CommandSourceStack source,
            SemionGameManager gameManager,
            SemionGameManager.SandboxCurrency currency,
            int amount
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!gameManager.grantSandboxCurrency(player.getUUID(), currency, amount)) {
            failure(source, "진행 중인 샌드박스가 없습니다. /semiontd sandbox start를 먼저 사용하세요.");
            return 0;
        }
        success(source, "샌드박스 " + sandboxCurrencyLabel(currency) + " " + amount + "개를 지급했습니다.");
        return 1;
    }

    private static String sandboxCurrencyLabel(SemionGameManager.SandboxCurrency currency) {
        return switch (currency) {
            case DIAMOND -> "다이아";
            case EMERALD -> "에메랄드";
            case INCOME -> "수입";
        };
    }

    private static int setTestMode(CommandSourceStack source, SemionGameManager gameManager, boolean enabled) {
        SemionGame activeGame = gameManager.activeGame().orElse(null);
        if (activeGame != null && activeGame.rosterLocked()) {
            failure(source, "참가자 확정 후에는 테스트 모드를 변경할 수 없습니다.");
            return 0;
        }

        gameManager.setMatchMode(enabled ? MatchMode.TEST : MatchMode.NORMAL);
        success(source, "테스트 모드=" + enabled);
        return 1;
    }

    private static int status(CommandSourceStack source, SemionGameManager gameManager) {
        return sendStatusLines(source, statusLines(gameManager));
    }

    private static int statusTeams(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        return sendStatusLines(source, teamStatusLines(game));
    }

    private static int statusPlayers(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        return sendStatusLines(source, playerStatusLines(game));
    }

    private static int statusLanes(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        return sendStatusLines(source, laneStatusLines(game));
    }

    private static int sendStatusLines(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            success(source, line);
        }
        return lines.isEmpty() ? 0 : 1;
    }

    public static List<String> statusLines(SemionGameManager gameManager) {
        List<String> lines = new ArrayList<>();
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean lobbyLoaded = gameManager.lobbyWorld().isPresent();
        if (game == null) {
            lines.add("상태 activeGame=false, phase=NONE, matchMode="
                    + gameManager.matchMode()
                    + ", lobbyLoaded=" + lobbyLoaded
                    + ", arenaLoaded=false");
            gameManager.lastMatchResult().ifPresent(result -> lines.add("최근 결과 winners="
                    + winnersText(result)
                    + ", finalRound=" + result.finalRound()
                    + ", participants=" + result.participantCount()
                    + ", spectators=" + result.spectatorIds().size()));
            return lines;
        }

        lines.add("상태 activeGame=true, phase="
                + game.phase()
                + ", round=" + game.currentRound()
                + ", matchMode=" + gameManager.matchMode()
                + ", rosterLocked=" + game.rosterLocked());
        lines.add("운영 상태 ready="
                + game.readyPlayerCount()
                + ", activeParticipants=" + game.players().size()
                + ", spectators=" + game.spectatorCount()
                + ", lobbyLoaded=" + lobbyLoaded
                + ", arenaLoaded=" + loadedArenaCount(game) + "/" + TeamId.values().length);
        lines.addAll(teamStatusLines(game));
        return lines;
    }

    public static List<String> teamStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        for (SemionTeam team : game.teams().values()) {
            lines.add("팀 " + team.id()
                    + " active=" + team.active()
                    + ", eliminated=" + team.eliminated()
                    + ", arenaLoaded=" + game.arena().teamArena(team.id()).isPresent()
                    + ", players=" + team.memberIds().size()
                    + ", lanes=" + team.laneGroup().lanes().size()
                    + ", boss=" + bossHealthStatus(team));
        }
        return lines;
    }

    public static List<String> laneStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            for (PlayerLane lane : team.laneGroup().lanes()) {
                SemionPlayer owner = game.players().get(lane.ownerPlayer());
                BlockPos areaMin = lane.laneLayout().laneArea().min();
                BlockPos areaMax = lane.laneLayout().laneArea().max();
                BlockPos towerSample = centerBlockPos(areaMin, areaMax);
                lines.add("라인 " + team.id()
                        + "#" + lane.laneId()
                        + " player=" + (owner == null ? lane.ownerPlayer() : owner.name())
                        + ", towerSample=" + blockPosText(towerSample)
                        + ", laneArea=" + blockPosText(areaMin) + ".." + blockPosText(areaMax)
                        + ", monsters=" + lane.activeMonsters().size()
                        + ", towers=" + lane.towers().size());
            }
        }
        if (lines.isEmpty()) {
            lines.add("활성 라인 없음");
        }
        return lines;
    }

    public static List<String> playerStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        if (game.players().isEmpty()) {
            lines.add("참가자 없음");
        } else {
            game.players().values().stream()
                    .sorted(java.util.Comparator.comparing(SemionPlayer::name))
                    .forEach(player -> lines.add("참가자 "
                            + player.name()
                            + " uuid=" + player.uuid()
                            + ", team=" + player.teamId()
                            + ", lane=" + player.laneId()
                            + ", eliminated=" + game.teams().get(player.teamId()).eliminated()));
        }

        List<UUID> spectators = game.matchSpectatorIds().stream()
                .filter(spectatorId -> !game.players().containsKey(spectatorId))
                .sorted()
                .toList();
        if (spectators.isEmpty()) {
            lines.add("관전자 없음");
        } else {
            spectators.forEach(spectatorId -> lines.add("관전자 uuid=" + spectatorId));
        }
        return lines;
    }

    private static int loadedArenaCount(SemionGame game) {
        int loaded = 0;
        for (TeamId teamId : TeamId.values()) {
            if (game.arena().teamArena(teamId).isPresent()) {
                loaded++;
            }
        }
        return loaded;
    }

    private static String bossHealthStatus(SemionTeam team) {
        if (team.eliminated()) {
            return "ELIMINATED";
        }
        return Math.round(team.laneGroup().boss().health())
                + "/"
                + Math.round(team.laneGroup().boss().maxHealth());
    }

    private static String blockPosText(BlockPos blockPos) {
        return blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
    }

    private static BlockPos centerBlockPos(BlockPos min, BlockPos max) {
        return new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2)
        );
    }

    private static String winnersText(MatchResult result) {
        if (result.winningTeams().isEmpty()) {
            return "none";
        }
        return result.winningTeams().stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static int statusDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.playableGame(player.getUUID()).or(() -> gameManager.activeGame()).orElse(null);
        if (game != null) {
            gameManager.dialogService().showGameStatus(player, game);
            success(source, "상태 창을 열었습니다.");
            return 1;
        }

        Optional<MatchResult> lastResult = gameManager.lastMatchResult();
        if (lastResult.isPresent()) {
            gameManager.dialogService().showLastResult(player, lastResult.get());
            success(source, "최근 결과 창을 열었습니다.");
            return 1;
        }

        failure(source, "진행 중인 게임이나 최근 결과가 없습니다.");
        return 0;
    }

    private static int economy(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        SemionPlayer player = game.players().get(source.getPlayerOrException().getUUID());
        if (player == null) {
            failure(source, "현재 게임 참가자가 아닙니다.");
            return 0;
        }

        PlayerEconomy economy = player.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        success(source, "경제 다이아=" + economy.diamond()
                + ", 에메랄드=" + economy.emerald()
                + ", 수입=" + economy.income()
                + ", 에메랄드/초=" + economy.emeraldPerSec()
                + ", 생산업글=" + economy.emeraldProductionUpgradeCount()
                + ", 다음업글비용=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대"));
        return 1;
    }

    private static int requestTeamMoney(
            CommandSourceStack source,
            SemionGameManager gameManager,
            int amount
    ) throws CommandSyntaxException {
        ServerPlayer requesterPlayer = source.getPlayerOrException();
        SemionGame game = gameManager.playableGame(requesterPlayer.getUUID()).orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        TeamMoneyTransferResult result = game.requestTeamMoney(requesterPlayer.getUUID(), amount);
        if (result.type() != TeamMoneyTransferResultType.SUCCESS) {
            failure(source, teamMoneyFailureMessage(result));
            return 0;
        }

        String requestId = result.requestId().orElseThrow();
        SemionPlayer requester = game.players().get(requesterPlayer.getUUID());
        notifyTeamMoneyRequest(source, game, requester, requestId, result.amount());
        success(source, teamMoneyRequestSuccessMessage(result.amount()));
        return 1;
    }

    static String teamMoneyRequestSuccessMessage(long amount) {
        return "다이아 " + amount + "개 지원 요청을 보냈습니다.";
    }

    private static int acceptTeamMoney(
            CommandSourceStack source,
            SemionGameManager gameManager,
            String requestId
    ) throws CommandSyntaxException {
        ServerPlayer senderPlayer = source.getPlayerOrException();
        SemionGame game = gameManager.playableGame(senderPlayer.getUUID()).orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        TeamMoneyTransferResult result = game.acceptTeamMoneyRequest(senderPlayer.getUUID(), requestId);
        if (result.type() != TeamMoneyTransferResultType.SUCCESS) {
            failure(source, teamMoneyFailureMessage(result));
            return 0;
        }

        result.requesterId()
                .flatMap(requesterId -> Optional.ofNullable(source.getServer().getPlayerList().getPlayer(requesterId)))
                .ifPresent(requester -> requester.sendSystemMessage(SemionText.prefixed(Component.literal(
                        senderPlayer.getGameProfile().getName() + "님에게 다이아 " + result.amount() + "개를 받았습니다."
                ).withStyle(ChatFormatting.GREEN))));
        success(source, "다이아 " + result.amount() + "개를 보냈습니다.");
        return 1;
    }

    private static int notifyTeamMoneyRequest(
            CommandSourceStack source,
            SemionGame game,
            SemionPlayer requester,
            String requestId,
            long amount
    ) {
        if (requester == null) {
            return 0;
        }
        int notified = 0;
        String acceptCommand = "/semiontd money accept " + requestId;
        Component message = SemionText.prefixed(Component.empty()
                .append(Component.literal(requester.name() + "님이 다이아 " + amount + "개 지원을 요청했습니다. ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[보내기]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand(acceptCommand))))
                .append(Component.literal(" 클릭 시 즉시 송금됩니다.").withStyle(ChatFormatting.GRAY)));

        for (SemionPlayer candidate : game.players().values()) {
            if (candidate.uuid().equals(requester.uuid()) || candidate.teamId() != requester.teamId()) {
                continue;
            }
            ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayer(candidate.uuid());
            if (onlinePlayer == null) {
                continue;
            }
            onlinePlayer.sendSystemMessage(message);
            notified++;
        }
        return notified;
    }

    private static String teamMoneyFailureMessage(TeamMoneyTransferResult result) {
        return switch (result.type()) {
            case DISABLED -> "팀원 간 다이아 지원 요청 기능이 비활성화되어 있습니다.";
            case INVALID_AMOUNT -> "요청 금액은 1 이상이어야 합니다.";
            case PLAYER_NOT_IN_GAME -> "현재 게임 참가자가 아닙니다.";
            case TEAM_NOT_ACTIVE -> "활성 팀 참가자만 다이아 지원을 요청하거나 보낼 수 있습니다.";
            case NOT_TEAMMATE -> "같은 팀원의 요청에만 보낼 수 있습니다.";
            case SELF_TRANSFER -> "자기 자신의 요청에는 보낼 수 없습니다.";
            case RECEIVE_COOLDOWN_ACTIVE -> "아직 다이아를 받을 수 없습니다. 남은 라운드="
                    + result.remainingCooldownRounds();
            case AMOUNT_EXCEEDS_ROUND_LIMIT -> "요청 금액이 현재 라운드 한도를 초과했습니다. 최대="
                    + result.maxAllowedAmount();
            case REQUEST_NOT_FOUND -> "지원 요청을 찾을 수 없습니다. 이미 처리되었거나 만료된 요청입니다.";
            case REQUEST_ALREADY_OPEN -> "이미 처리 대기 중인 지원 요청이 있습니다. 팀원이 [보내기]를 누르거나 다음 라운드에 다시 요청해주세요.";
            case REQUEST_EXPIRED -> "지원 요청이 만료되었습니다. 같은 라운드 안에서만 보낼 수 있습니다.";
            case REQUESTER_NO_LONGER_ELIGIBLE -> "요청자가 더 이상 받을 수 없는 상태입니다.";
            case MATCH_ENDED -> "경기가 종료되어 다이아 지원을 요청하거나 보낼 수 없습니다.";
            case NOT_ENOUGH_DIAMOND -> "보낼 다이아가 부족합니다.";
            case SUCCESS -> "성공";
        };
    }

    private enum CurrencyDebugType {
        DIAMOND,
        EMERALD
    }

    private static int debugGiveCurrency(
            CommandSourceStack source,
            SemionGameManager gameManager,
            CurrencyDebugType currency,
            int amount,
            ServerPlayer targetPlayer
    ) throws CommandSyntaxException {
        ServerPlayer target = targetPlayer == null ? source.getPlayerOrException() : targetPlayer;
        SemionGame game = gameManager.playableGame(target.getUUID()).orElse(null);
        if (game == null) {
            failure(source, "대상 플레이어의 진행 중인 게임 또는 샌드박스가 없습니다: " + target.getGameProfile().getName());
            return 0;
        }
        SemionPlayer semionPlayer = game.players().get(target.getUUID());
        if (semionPlayer == null) {
            failure(source, "현재 게임 참가자가 아닙니다: " + target.getGameProfile().getName());
            return 0;
        }

        PlayerEconomy economy = semionPlayer.economy();
        if (currency == CurrencyDebugType.DIAMOND) {
            economy.addDiamond(amount);
        } else {
            economy.addEmerald(amount);
        }

        success(source, target.getGameProfile().getName()
                + "에게 "
                + (currency == CurrencyDebugType.DIAMOND ? "다이아" : "에메랄드")
                + " "
                + amount
                + "개를 지급했습니다. 현재 다이아="
                + economy.diamond()
                + ", 에메랄드="
                + economy.emerald());
        return 1;
    }

    private static int profile(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var profile = gameManager.profile(source.getServer(), player.getUUID(), player.getGameProfile().getName());
        success(source, "프로필 장식재화=" + profile.cosmeticCurrency()
                + ", 플레이=" + profile.gamesPlayed()
                + ", 승=" + profile.wins()
                + ", 패=" + profile.losses()
                + ", 저장 직업=" + profile.selectedJobResource()
                        .flatMap(JobRegistry::find)
                        .map(job -> job.displayName().getString())
                        .orElse(JobRegistry.defaultJob().displayName().getString()));
        return 1;
    }

    private static int rating(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<PlayerRatingProfile> profile = gameManager.ratingProfile(player.getUUID());
        if (profile.isEmpty()) {
            success(source, "아직 Rating 기록이 없습니다. 첫 경기를 완료하면 1500 ELO에서 시작합니다.");
            return 1;
        }
        success(source, formatRatingProfile(profile.get()));
        return 1;
    }

    private static int ratingTop(CommandSourceStack source, SemionGameManager gameManager) {
        List<PlayerRatingProfile> profiles = gameManager.topRatingProfiles();
        if (profiles.isEmpty()) {
            success(source, "Rating Top: 아직 기록된 플레이어가 없습니다.");
            return 1;
        }
        success(source, "Rating Top " + profiles.size());
        int rank = 1;
        for (PlayerRatingProfile profile : profiles) {
            success(source, rank + ". " + profile.lastKnownName()
                    + " — " + profile.displayElo()
                    + " ELO (" + profile.wins() + "W " + profile.losses() + "L)");
            rank++;
        }
        return profiles.size();
    }

    private static int ratingSoftReset(CommandSourceStack source, SemionGameManager gameManager) {
        String confirmationKey = ratingSoftResetConfirmationKey(source);
        long now = System.currentTimeMillis();
        long confirmedUntil = RATING_SOFT_RESET_CONFIRMATIONS.getOrDefault(confirmationKey, 0L);
        if (confirmedUntil < now) {
            RATING_SOFT_RESET_CONFIRMATIONS.put(confirmationKey, now + RATING_SOFT_RESET_CONFIRMATION_MILLIS);
            success(source, "ELO 소프트 리셋 확인 필요: 30초 안에 /semiontd rating softreset 을 한 번 더 입력하면 백업 후 리셋됩니다.");
            return 1;
        }

        RATING_SOFT_RESET_CONFIRMATIONS.remove(confirmationKey);
        try {
            SemionGameManager.RatingSoftResetResult result = gameManager.softResetRatingsWithBackup();
            success(source, "ELO 소프트 리셋 완료. 백업: " + result.backupPath());
            return 1;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD rating soft reset failure.", exception);
            failure(source, "ELO 소프트 리셋 실패: " + exception.getMessage());
            return 0;
        }
    }

    private static String ratingSoftResetConfirmationKey(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getUUID().toString();
        }
        return "console";
    }

    private static String formatRatingProfile(PlayerRatingProfile profile) {
        return "Rating: " + profile.displayElo()
                + " ELO, 전적=" + profile.gamesPlayed()
                + "전 " + profile.wins()
                + "승 " + profile.losses()
                + "패, 시스템=" + profile.ratingSystemId()
                + " v" + profile.ratingVersion();
    }

    private static int listJobs(CommandSourceStack source) {
        success(source, "직업 목록:");
        for (SemionJob job : JobRegistry.all()) {
            success(source, " - " + job.id() + " => " + job.displayName().getString());
            for (Component line : job.description()) {
                success(source, "   " + line.getString());
            }
        }
        return JobRegistry.all().size();
    }

    private static int currentJob(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 관리자에게 /semiontd create 실행을 요청하세요.");
            return 0;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        SemionJob job = semionPlayer != null
                ? semionPlayer.job().orElse(JobRegistry.defaultJob())
                : game.selectedJobOrDefault(player.getUUID());
        success(source, "현재 직업: "
                + job.id()
                + " => "
                + job.displayName().getString());
        return 1;
    }

    private static int jobDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 관리자에게 /semiontd create 실행을 요청하세요.");
            return 0;
        }
        gameManager.dialogService().showJobSelection(source.getPlayerOrException(), game);
        success(source, "직업 선택 창을 열었습니다.");
        return 1;
    }

    private static int jobStatisticsDialog(
            CommandSourceStack source,
            SemionGameManager gameManager
    ) throws CommandSyntaxException {
        gameManager.dialogService().showJobStatistics(
                source.getPlayerOrException(),
                gameManager.jobStatisticsSnapshot(),
                gameManager.jobStatisticsState()
        );
        return 1;
    }

    private static int jobStatisticsDetailDialog(
            CommandSourceStack source,
            SemionGameManager gameManager,
            String rawJobId
    ) throws CommandSyntaxException {
        ResourceLocation jobId = ResourceLocation.tryParse(rawJobId);
        if (jobId == null) {
            failure(source, "올바르지 않은 직업 ID입니다: " + rawJobId);
            return 0;
        }
        gameManager.dialogService().showJobStatisticsDetail(
                source.getPlayerOrException(),
                gameManager.jobStatisticsSnapshot(),
                gameManager.jobStatisticsState(),
                jobId.toString()
        );
        return 1;
    }

    private static int traitDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        if (!ensureTraitsEnabled(source, gameManager)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean sandbox = gameManager.sandboxGame(player.getUUID()).isPresent();
        if (game == null && !sandbox) {
            failure(source, "참여 중인 로비 또는 샌드박스가 없습니다.");
            return 0;
        }
        SemionPlayer semionPlayer = game == null ? null : game.players().get(player.getUUID());
        if (!sandbox && game.phase() != RoundPhase.WAITING && semionPlayer != null) {
            gameManager.dialogService().showAppliedTraits(player, semionPlayer.traitLoadout());
            success(source, "현재 적용 중인 특성을 열었습니다.");
            return 1;
        }
        gameManager.dialogService().showTraitSelection(
                player,
                gameManager.traitLoadoutOrDefault(player.getUUID()),
                sandbox ? -1 : Math.max(0, gameManager.traitSelectionSecondsRemaining())
        );
        success(source, "특성 선택 창을 열었습니다.");
        return 1;
    }

    private static int traitDialog(CommandSourceStack source, SemionGameManager gameManager, String rawSlot) throws CommandSyntaxException {
        if (!ensureTraitsEnabled(source, gameManager)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean sandbox = gameManager.sandboxGame(player.getUUID()).isPresent();
        if (game == null && !sandbox) {
            failure(source, "참여 중인 로비 또는 샌드박스가 없습니다.");
            return 0;
        }
        TraitSlot slot;
        try {
            slot = parseTraitSlot(rawSlot);
        } catch (IllegalArgumentException exception) {
            failure(source, exception.getMessage());
            return 0;
        }
        gameManager.dialogService().showTraitSelection(
                player,
                gameManager.traitLoadoutOrDefault(player.getUUID()),
                sandbox ? -1 : Math.max(0, gameManager.traitSelectionSecondsRemaining()),
                slot
        );
        success(source, slot.displayName() + " 선택 창을 열었습니다.");
        return 1;
    }

    private static int currentTrait(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        if (!ensureTraitsEnabled(source, gameManager)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (gameManager.activeGame().isEmpty() && gameManager.sandboxGame(player.getUUID()).isEmpty()) {
            failure(source, "참여 중인 로비 또는 샌드박스가 없습니다.");
            return 0;
        }
        TraitLoadout loadout = gameManager.traitLoadoutOrDefault(player.getUUID());
        success(source, "현재 특성: 주특성="
                + traitName(loadout.primaryTraitId())
                + ", 부특성="
                + traitName(loadout.secondaryTraitId()));
        return 1;
    }

    private static int listTraits(CommandSourceStack source, SemionGameManager gameManager) {
        if (!ensureTraitsEnabled(source, gameManager)) {
            return 0;
        }
        List<String> lines = new ArrayList<>();
        for (SemionTrait trait : TraitRegistry.all()) {
            lines.add(trait.id() + " => " + trait.displayName().getString());
        }
        source.sendSuccess(() -> SemionText.prefixedPlain(String.join("\n", lines)), false);
        return lines.isEmpty() ? 0 : 1;
    }

    private static int selectTrait(CommandSourceStack source, SemionGameManager gameManager, String rawSlot, String rawTraitId)
            throws CommandSyntaxException {
        if (!ensureTraitsEnabled(source, gameManager)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        boolean sandbox = gameManager.sandboxGame(player.getUUID()).isPresent();
        if (gameManager.activeGame().isEmpty() && !sandbox) {
            failure(source, "참여 중인 로비 또는 샌드박스가 없습니다.");
            return 0;
        }
        TraitSlot slot;
        try {
            slot = parseTraitSlot(rawSlot);
        } catch (IllegalArgumentException exception) {
            failure(source, exception.getMessage());
            return 0;
        }
        ResourceLocation traitId;
        try {
            traitId = parseTraitId(rawTraitId);
        } catch (IllegalArgumentException exception) {
            failure(source, exception.getMessage());
            return 0;
        }
        TraitSelectionSession.SelectionResult result = gameManager.selectTrait(
                source.getServer(),
                player.getUUID(),
                slot,
                traitId
        );
        if (result != TraitSelectionSession.SelectionResult.SELECTED) {
            failure(source, traitSelectionFailureMessage(result, traitId));
            return 0;
        }
        TraitLoadout loadout = gameManager.traitLoadoutOrDefault(player.getUUID());
        success(source, slot.displayName()
                + "을 선택했습니다: "
                + traitName(loadout.traitId(slot))
                + " (주특성 100%, 부특성 50%)");
        gameManager.dialogService().showTraitSelection(
                player,
                loadout,
                sandbox ? -1 : Math.max(0, gameManager.traitSelectionSecondsRemaining())
        );
        return 1;
    }

    private static int selectJob(CommandSourceStack source, SemionGameManager gameManager, String rawJobId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 먼저 /semiontd create를 실행하세요.");
            return 0;
        }
        if (gameManager.startCountdownActive()) {
            failure(source, "시작 카운트다운이 이미 진행 중입니다.");
            return 0;
        }

        ResourceLocation jobId;
        try {
            jobId = parseJobId(rawJobId);
        } catch (IllegalArgumentException exception) {
            failure(source, exception.getMessage());
            return 0;
        }

        Optional<SemionJob> selectedJob = JobRegistry.find(jobId);
        if (selectedJob.isEmpty()) {
            failure(source, "알 수 없는 직업입니다: " + jobId + ". /semiontd job list를 확인하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        UUID playerId = player.getUUID();
        SemionJob job;
        if (game.canConfigureRoster()) {
            if (!game.selectJob(playerId, jobId)) {
                failure(source, "알 수 없는 직업입니다: " + jobId + ". /semiontd job list를 확인하세요.");
                return 0;
            }
            job = game.selectedJobOrDefault(playerId);
        } else {
            if (game.phase() != RoundPhase.ENDED && game.isActiveParticipant(playerId)) {
                failure(source, "진행 중인 경기 참가자는 직업을 변경할 수 없습니다. 다음 경기에 적용하려면 경기 종료 후 다시 선택하세요.");
                return 0;
            }
            job = selectedJob.get();
        }

        gameManager.saveSelectedJob(source.getServer(), playerId, player.getGameProfile().getName(), job.id());
        success(source, "직업을 선택했습니다: "
                + job.id()
                + " => "
                + job.displayName().getString()
                + " (다음 로비부터 자동 적용)");
        return 1;
    }

    private static int emeraldUp(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        UUID playerId = source.getPlayerOrException().getUUID();
        boolean upgraded = game.upgradeGasProduction(playerId);
        if (!upgraded) {
            failure(source, "에메랄드 생산 업그레이드에 실패했습니다.");
            return 0;
        }

        PlayerEconomy economy = game.players().get(playerId).economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        success(source, "에메랄드 생산을 업그레이드했습니다. 에메랄드/초=" + economy.emeraldPerSec()
                + ", 생산업글=" + economy.emeraldProductionUpgradeCount()
                + ", 다음비용=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대"));
        return 1;
    }

    private static int towerLimitUp(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        UUID playerId = source.getPlayerOrException().getUUID();
        boolean upgraded = game.purchaseTowerLimit(playerId);
        if (!upgraded) {
            failure(source, "타워 설치 수 증가 구매에 실패했습니다.");
            return 0;
        }

        PlayerEconomy economy = game.players().get(playerId).economy();
        long nextDiamondCost = game.nextTowerLimitPurchaseDiamondCost(playerId);
        long nextEmeraldCost = game.nextTowerLimitPurchaseEmeraldCost(playerId);
        success(source, "타워 설치 수를 증가시켰습니다. 현재제한=" + game.towerLimitForPlayer(playerId)
                + ", 구매횟수=" + economy.towerLimitPurchaseCount()
                + ", 다음비용=" + formatTowerLimitPurchaseCost(nextDiamondCost, nextEmeraldCost));
        return 1;
    }

    private static int placeTestTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = TestTowerService.placeTestTower(game, player.getUUID(), player.blockPosition());
        if (result != TowerPlacementResult.SUCCESS) {
            failure(source, "테스트 타워 설치 실패: " + placementFailureMessage(result));
            return 0;
        }

        success(source, towerPlacementSuccessMessage("테스트 타워"));
        return 1;
    }

    private static int listProductionTowers(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        if (entries.isEmpty()) {
            failure(source, "사용할 수 있는 타워가 없습니다.");
            return 0;
        }

        success(source, "사용 가능한 타워:");
        for (ProductionTowerCatalog.CatalogEntry entry : entries) {
            success(source, " - " + entry.type().id()
                    + " => " + entry.type().displayName()
                    + " 다이아비용=" + entry.type().mineralCost());
        }
        return entries.size();
    }

    private static int buildProductionTower(CommandSourceStack source, SemionGameManager gameManager, String towerId)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = ProductionTowerService.placeTower(game, player.getUUID(), player.blockPosition(), towerId);
        if (result != TowerPlacementResult.SUCCESS) {
            failure(source, "타워 설치 실패: " + placementFailureMessage(result));
            return 0;
        }

        String towerName = ProductionTowerCatalog.find(towerId)
                .map(entry -> entry.type().displayName())
                .orElse(towerId);
        success(source, towerPlacementSuccessMessage(towerName));
        return 1;
    }

    private static int towerDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }
        gameManager.dialogService().showTowerControl(source.getPlayerOrException(), game, gameManager.buildGuideService());
        success(source, "타워 관리 창을 열었습니다.");
        return 1;
    }

    private static int debugTowerDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        gameManager.dialogService().showDebugTowerControl(source.getPlayerOrException());
        success(source, "디버그 타워 설치 창을 열었습니다.");
        return 1;
    }

    private static int sellTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        return sellTower(source, gameManager, null);
    }

    private static int sellTower(CommandSourceStack source, SemionGameManager gameManager, GridPosition position)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ProductionTowerService.SaleResult result = position == null
                ? ProductionTowerService.sellTower(game, player.getUUID(), player.blockPosition())
                : ProductionTowerService.sellTower(game, player.getUUID(), position);
        if (result.result() != TowerSellResult.SUCCESS) {
            failure(source, "타워 판매 실패: " + towerSellFailureMessage(result.result()));
            return 0;
        }

        success(source, "타워를 판매했습니다. 환불 다이아=" + result.refundAmount());
        return 1;
    }


    private static int listTowerUpgrades(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<TowerUpgradeOption> upgrades = ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (upgrades.isEmpty()) {
            failure(source, "해당 위치에 사용 가능한 타워 업그레이드가 없습니다: " + player.blockPosition());
            return 0;
        }

        success(source, "사용 가능한 업그레이드: 위치=" + player.blockPosition());
        for (TowerUpgradeOption option : upgrades) {
            success(source, " - " + option.id()
                    + " => " + option.displayName()
                    + " 다이아비용=" + option.mineralCost());
        }
        return upgrades.size();
    }

    private static int upgradeTower(CommandSourceStack source, SemionGameManager gameManager, String upgradeId)
            throws CommandSyntaxException {
        return upgradeTower(source, gameManager, upgradeId, null);
    }

    private static int upgradeTower(
            CommandSourceStack source,
            SemionGameManager gameManager,
            String upgradeId,
            GridPosition position
    ) throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerUpgradeResult result = position == null
                ? ProductionTowerService.upgradeTower(game, player.getUUID(), player.blockPosition(), upgradeId)
                : ProductionTowerService.upgradeTower(game, player.getUUID(), position, upgradeId);
        if (result != TowerUpgradeResult.SUCCESS) {
            failure(source, "타워 업그레이드 실패: " + towerUpgradeFailureMessage(result));
            return 0;
        }

        Object upgradedPosition = position == null ? player.blockPosition() : position;
        success(source, "타워를 업그레이드했습니다: " + upgradedPosition + ", 업그레이드=" + upgradeId);
        return 1;
    }

    private static int summon(CommandSourceStack source, SemionGameManager gameManager, String summonId)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        SummonResult result = game.summonMonster(player.getUUID(), summonId);
        if (result.type() != SummonResultType.SUCCESS) {
            failure(source, "소환 실패: " + summonFailureMessage(result.type()));
            return 0;
        }

        int scheduledRound = result.scheduledRound().orElse(game.currentRound());
        successMini(source, summonSuccessMarkup(game, result, summonId, game.currentRound(), scheduledRound));
        gameManager.dialogService().showSummonShop(player, game);
        return 1;
    }

    static String towerPlacementSuccessMessage(String towerDisplayName) {
        String normalizedName = towerDisplayName == null || towerDisplayName.isBlank()
                ? "타워"
                : towerDisplayName.trim();
        return normalizedName.endsWith("타워")
                ? normalizedName + "를 소환했습니다"
                : normalizedName + " 타워를 소환했습니다";
    }

    public static String summonSuccessMarkup(
            SemionGame game,
            SummonResult result,
            String summonId,
            int currentRound,
            int scheduledRound
    ) {
        String incomeName = game.summonShop().find(summonId)
                .map(SummonMonsterType::displayName)
                .orElse(summonId);
        return summonSuccessMarkup(incomeName, summonTargetOwnerMarkup(game, result), currentRound, scheduledRound);
    }

    static String summonSuccessMarkup(String incomeName, String targetOwnerMarkup, int currentRound, int scheduledRound) {
        return MINI_MESSAGE.escapeTags(incomeName) + " 이(가) " + targetOwnerMarkup + " 의 라인으로 공격합니다!";
    }

    private static String summonTargetOwnerMarkup(SemionGame game, SummonResult result) {
        TeamId teamId = result.targetTeam().orElse(null);
        Integer laneId = result.targetLaneId().orElse(null);
        if (teamId == null || laneId == null) {
            return MINI_MESSAGE.escapeTags("알 수 없음");
        }
        SemionTeam team = game.teams().get(teamId);
        if (team == null) {
            return MINI_MESSAGE.escapeTags("알 수 없음");
        }
        return team.laneGroup().lane(laneId)
                .map(PlayerLane::ownerPlayer)
                .map(game.players()::get)
                .map(SemionPlayer::name)
                .map(name -> teamColoredNameMarkup(teamId, name))
                .orElseGet(() -> MINI_MESSAGE.escapeTags("알 수 없음"));
    }

    private static String teamColoredNameMarkup(TeamId teamId, String playerName) {
        String color = switch (teamId) {
            case RED -> "red";
            case BLUE -> "blue";
            case GREEN -> "green";
            case YELLOW -> "yellow";
            case PURPLE -> "light_purple";
        };
        return "<" + color + ">" + MINI_MESSAGE.escapeTags(playerName) + "</" + color + ">";
    }

    private static int leaderTarget(CommandSourceStack source, SemionGameManager gameManager, String teamName)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        TeamId targetTeam;
        try {
            targetTeam = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            failure(source, LeaderTargetResult.INVALID_TARGET_TEAM.message());
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        LeaderTargetResult result = game.setLeaderTarget(player.getUUID(), targetTeam);
        if (result != LeaderTargetResult.SUCCESS) {
            failure(source, result.message());
            return 0;
        }

        success(source, targetTeam.name() + " 팀을 견제 타깃으로 지정했습니다.");
        gameManager.dialogService().showLeaderTargetControl(player, game);
        return 1;
    }

    private static int summons(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }

        success(source, "소환 목록:");
        for (SummonMonsterType type : game.summonShop().all()) {
            success(source, " - " + type.id()
                    + " 에메랄드비용=" + type.gasCost()
                    + ", 수입=" + type.incomeGain()
                    + ", 체력=" + Math.round(type.maxHealth()));
        }
        return 1;
    }

    private static int summonDialog(CommandSourceStack source, SemionGameManager gameManager, int page)
            throws CommandSyntaxException {
        SemionGame game = playableGame(source, gameManager);
        if (game == null) {
            failure(source, "진행 중인 게임 또는 샌드박스가 없습니다. /semiontd sandbox start를 사용하세요.");
            return 0;
        }
        gameManager.dialogService().showSummonShop(source.getPlayerOrException(), game, page);
        success(source, "견제 소환 창을 열었습니다.");
        return 1;
    }

    private static int debugSummonDialog(CommandSourceStack source, SemionGameManager gameManager, int page)
            throws CommandSyntaxException {
        gameManager.dialogService().showDebugSummonShop(source.getPlayerOrException(), page);
        success(source, "디버그 견제 소환 창을 열었습니다.");
        return 1;
    }

    private static int debugBuildGuideVisual(
            CommandSourceStack source,
            SemionGameManager gameManager,
            DebugBuildGuideView view
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        SemionPlayer semionPlayer = game == null ? null : game.players().get(player.getUUID());
        PlayerLane lane = game == null ? null : game.playerLane(player.getUUID()).orElse(null);
        boolean liveParticipant = game != null && semionPlayer != null && lane != null;

        int currentRound = liveParticipant ? game.currentRound() : 1;
        String jobId = liveParticipant
                ? semionPlayer.job().map(job -> job.id().toString()).orElse("")
                : "semion-td:debug";
        List<BuildAction> actions = liveParticipant
                ? debugBuildGuideActions(game, player.getUUID(), lane, debugTargetPosition(lane, player.blockPosition()))
                : debugBuildGuideActions(currentRound, GridPosition.from(player.blockPosition()));
        BuildGuide guide = saveDebugBuildGuideSamples(gameManager, player, jobId, currentRound, actions);
        gameManager.trackBuild(source.getServer(), player, guide.code());
        if (liveParticipant) {
            gameManager.buildGuideService().onPreparePhaseStarted(source.getServer(), game, game.currentRound());
        }

        if (view == DebugBuildGuideView.DETAIL) {
            gameManager.dialogService().showBuildGuideDetails(player, guide);
            success(source, "임시 빌드 상세 UI를 열었습니다. 코드=" + guide.code());
        } else if (view == DebugBuildGuideView.TOWER_UI) {
            if (liveParticipant) {
                gameManager.dialogService().showTowerControl(player, game, gameManager.buildGuideService());
                success(source, "디버그 빌드 가이드를 추적하고 타워 UI를 열었습니다. 추천 버튼은 파란색으로 표시됩니다. 코드=" + guide.code());
            } else {
                gameManager.dialogService().showDebugTowerControl(player);
                success(source, "경기 밖이라 디버그 타워 UI를 열었습니다. 빌드 목록/상세 임시 데이터도 준비했습니다. 코드=" + guide.code());
            }
        } else {
            gameManager.showDebugBuildList(player);
            String suffix = liveParticipant ? " 파티클/라운드 안내도 표시했습니다." : "";
            success(source, "임시 데이터가 들어간 빌드 목록 UI를 열었습니다." + suffix + " 코드=" + guide.code());
        }
        return 1;
    }

    private static BuildGuide saveDebugBuildGuideSamples(
            SemionGameManager gameManager,
            ServerPlayer player,
            String jobId,
            int currentRound,
            List<BuildAction> primaryActions
    ) {
        BuildGuide primary = gameManager.buildGuideService().saveDebugGuide(
                "DEBUG1",
                "디버그 추천 빌드",
                player.getUUID(),
                player.getGameProfile().getName(),
                jobId,
                currentRound + 3,
                primaryActions
        );
        gameManager.buildGuideService().saveDebugGuide(
                "DEBUG2",
                "초반 인컴 압박 빌드",
                player.getUUID(),
                player.getGameProfile().getName(),
                jobId,
                currentRound + 5,
                List.of(
                        BuildAction.towerPlace(currentRound, "sample_income_tower", new GridPosition(2, 64, 2), 75),
                        BuildAction.summon(currentRound, "chicken", 20, 1, currentRound, "BLUE", 1),
                        BuildAction.emeraldProductionUpgrade(currentRound + 1, 2, 50, 1),
                        BuildAction.towerUpgrade(currentRound + 2, "sample_upgrade", new GridPosition(2, 64, 2), 120)
                )
        );
        gameManager.buildGuideService().saveDebugGuide(
                "DEBUG3",
                "방어 안정화 빌드",
                player.getUUID(),
                player.getGameProfile().getName(),
                jobId,
                currentRound + 7,
                List.of(
                        BuildAction.towerPlace(currentRound, "sample_guard_tower", new GridPosition(-2, 64, -2), 100),
                        BuildAction.towerPlace(currentRound + 1, "sample_support_tower", new GridPosition(-1, 64, -2), 90),
                        BuildAction.towerUpgrade(currentRound + 2, "sample_guard_upgrade", new GridPosition(-2, 64, -2), 160),
                        BuildAction.summon(currentRound + 3, "piglin", 80, 5, currentRound + 3, "GREEN", 1)
                )
        );
        return primary;
    }

    private static GridPosition debugTargetPosition(PlayerLane lane, BlockPos playerPos) {
        return TowerPlacementPositions.resolveGrid(lane, playerPos)
                .or(() -> TowerPlacementPositions.resolveGrid(lane, BlockPos.containing(lane.laneLayout().positionAt(0.35))))
                .orElseGet(() -> GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(0.35))));
    }

    private static List<BuildAction> debugBuildGuideActions(
            SemionGame game,
            UUID playerId,
            PlayerLane lane,
            GridPosition targetPosition
    ) {
        ArrayList<BuildAction> actions = new ArrayList<>();
        Tower tower = lane.towerAt(targetPosition);
        if (tower == null) {
            ProductionTowerService.availableTowers(game, playerId).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            entry -> actions.add(BuildAction.towerPlace(
                                    game.currentRound(),
                                    entry.type().id(),
                                    targetPosition,
                                    entry.type().mineralCost()
                            )),
                            () -> actions.add(BuildAction.towerPlace(game.currentRound(), "debug_tower", targetPosition, 0))
                    );
        } else {
            ProductionTowerService.availableUpgrades(game, playerId, tower.position()).stream()
                    .findFirst()
                    .ifPresent(option -> actions.add(BuildAction.towerUpgrade(
                            game.currentRound(),
                            option.id(),
                            tower.position(),
                            option.mineralCost()
                    )));
        }
        game.summonShop().all().stream()
                .findFirst()
                .ifPresent(type -> actions.add(BuildAction.summon(
                        game.currentRound(),
                        type.id(),
                        type.gasCost(),
                        type.incomeGain(),
                        game.currentRound(),
                        "",
                        0
                )));
        actions.add(BuildAction.emeraldProductionUpgrade(game.currentRound(), 1, 0, 1));
        return actions;
    }

    private static List<BuildAction> debugBuildGuideActions(int currentRound, GridPosition targetPosition) {
        return List.of(
                BuildAction.towerPlace(currentRound, "debug_tower", targetPosition, 100),
                BuildAction.summon(currentRound, "chicken", 20, 1, currentRound, "BLUE", 1),
                BuildAction.emeraldProductionUpgrade(currentRound, 1, 50, 1),
                BuildAction.towerUpgrade(currentRound + 1, "debug_upgrade", targetPosition, 125)
        );
    }

    private static int publishBuild(CommandSourceStack source, SemionGameManager gameManager, String title)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BuildGuide> guide = gameManager.publishLastBuild(player, title);
        if (guide.isEmpty()) {
            failure(source, "저장할 수 있는 마지막 경기 빌드 기록이 없습니다. 경기 종료 후 실제 행동 기록이 있어야 합니다.");
            return 0;
        }
        success(source, "빌드를 비공개로 저장했습니다: " + guide.get().title() + " 코드=" + guide.get().code() + " /빌드 목록에서 공개로 전환할 수 있습니다.");
        return 1;
    }

    private static int buildListDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        gameManager.showBuildList(source.getPlayerOrException());
        success(source, "빌드 목록 창을 열었습니다.");
        return 1;
    }

    private static int buildListDialog(CommandSourceStack source, SemionGameManager gameManager, int publicPage, int myPage)
            throws CommandSyntaxException {
        gameManager.showBuildList(source.getPlayerOrException(), publicPage, myPage);
        success(source, "빌드 목록 창을 열었습니다.");
        return 1;
    }

    private static int trackBuild(CommandSourceStack source, SemionGameManager gameManager, String code)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BuildGuide> guide = gameManager.trackBuild(source.getServer(), player, code);
        if (guide.isEmpty()) {
            failure(source, "빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, "빌드 추적을 시작했습니다: " + guide.get().title() + " 코드=" + guide.get().code());
        gameManager.showBuildList(player);
        return 1;
    }

    private static int clearTrackedBuild(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        gameManager.clearTrackedBuild(player);
        success(source, "현재 추적 중인 빌드를 해제했습니다.");
        gameManager.showBuildList(player);
        return 1;
    }

    private static int showBuildDetails(CommandSourceStack source, SemionGameManager gameManager, String code)
            throws CommandSyntaxException {
        if (!gameManager.showBuildDetails(source.getPlayerOrException(), code)) {
            failure(source, "빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, "빌드 상세 창을 열었습니다: " + code);
        return 1;
    }

    private static int setBuildVisibility(CommandSourceStack source, SemionGameManager gameManager, String code, String visibility)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BuildGuide> guide = gameManager.setBuildVisibility(player, code, visibility);
        if (guide.isEmpty()) {
            failure(source, "내 빌드가 아니거나 빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, guide.get().isPublic() ? "빌드를 공개했습니다: " + guide.get().title() : "빌드를 비공개로 전환했습니다: " + guide.get().title());
        gameManager.dialogService().showBuildGuideDetails(player, guide.get());
        return 1;
    }

    private static int deleteBuild(CommandSourceStack source, SemionGameManager gameManager, String code)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!gameManager.deleteBuild(player, code)) {
            failure(source, "내 빌드가 아니거나 빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, "빌드를 삭제했습니다: " + code);
        gameManager.showBuildList(player);
        return 1;
    }

    private static int killBoss(CommandSourceStack source, SemionGameManager gameManager, String teamName) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        TeamId teamId;
        try {
            teamId = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            failure(source, "알 수 없는 팀입니다: " + teamName + ". RED, BLUE, GREEN, YELLOW, PURPLE 중 하나를 사용하세요.");
            return 0;
        }

        boolean killed = game.killBoss(source.getServer(), teamId);
        if (!killed) {
            failure(source, "보스 처치 실패: 팀=" + teamId);
            return 0;
        }

        success(source, "보스를 처치했습니다: 팀=" + teamId);
        return 1;
    }

    private static TeamId parseTeam(String teamName) {
        return TeamId.valueOf(teamName.toUpperCase());
    }

    private static ResourceLocation parseJobId(String rawJobId) {
        ResourceLocation parsed = ResourceLocation.tryParse(rawJobId);
        if (parsed != null && rawJobId.contains(":")) {
            return parsed;
        }
        ResourceLocation defaulted = ResourceLocation.tryBuild(SemionTd.MOD_ID, rawJobId);
        if (defaulted == null) {
            throw new IllegalArgumentException("잘못된 직업 ID입니다: " + rawJobId);
        }
        return defaulted;
    }

    private static TraitSlot parseTraitSlot(String rawSlot) {
        return switch (rawSlot.toLowerCase()) {
            case "primary", "main", "주특성" -> TraitSlot.PRIMARY;
            case "secondary", "sub", "부특성" -> TraitSlot.SECONDARY;
            default -> throw new IllegalArgumentException("잘못된 특성 슬롯입니다: " + rawSlot + ". primary 또는 secondary를 사용하세요.");
        };
    }

    private static ResourceLocation parseTraitId(String rawTraitId) {
        ResourceLocation parsed = ResourceLocation.tryParse(rawTraitId);
        if (parsed != null && rawTraitId.contains(":")) {
            return parsed;
        }
        ResourceLocation defaulted = ResourceLocation.tryBuild(SemionTd.MOD_ID, rawTraitId);
        if (defaulted == null) {
            throw new IllegalArgumentException("잘못된 특성 ID입니다: " + rawTraitId);
        }
        return defaulted;
    }

    private static String traitName(ResourceLocation traitId) {
        return TraitRegistry.find(traitId)
                .map(trait -> trait.displayName().getString())
                .orElse(traitId.toString());
    }

    private static String traitSelectionFailureMessage(TraitSelectionSession.SelectionResult result, ResourceLocation traitId) {
        return switch (result) {
            case NOT_PARTICIPANT -> "현재 특성 선택 대상 참가자가 아닙니다.";
            case UNKNOWN_TRAIT -> "알 수 없는 특성입니다: " + traitId + ". /semiontd trait list를 확인하세요.";
            case DUPLICATE_TRAIT -> "같은 특성은 주특성/부특성에 동시에 선택할 수 없습니다: " + traitId;
            case STARTED -> "게임 시작 후에는 특성을 변경할 수 없습니다.";
            case DISABLED -> "특성 기능은 현재 비활성화되어 있습니다.";
            case SELECTED -> "특성을 선택했습니다.";
        };
    }

    private static boolean ensureTraitsEnabled(CommandSourceStack source, SemionGameManager gameManager) {
        if (!gameManager.traitsEnabled()) {
            failure(source, "특성 기능은 현재 비활성화되어 있습니다.");
            return false;
        }
        return true;
    }

    private static boolean ensureWaitingSetup(CommandSourceStack source, SemionGame game, String action) {
        if (!game.canConfigureRoster()) {
            failure(source, "참가자 확정 후에는 해당 작업을 할 수 없습니다: " + action);
            return false;
        }
        return true;
    }

    private static SemionGame activeWaitingGame(
            CommandSourceStack source,
            SemionGameManager gameManager,
            String action
    ) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 먼저 /semiontd create를 실행하세요.");
            return null;
        }
        if (gameManager.startCountdownActive()) {
            failure(source, "시작 카운트다운이 이미 진행 중입니다.");
            return null;
        }
        if (!ensureWaitingSetup(source, game, action)) {
            return null;
        }
        return game;
    }

    private static String startCountdownFailureMessage(SemionGameManager.StartCountdownResult result) {
        return switch (result) {
            case NO_ACTIVE_GAME -> "열린 로비가 없습니다. 먼저 /semiontd create를 실행하세요.";
            case NOT_WAITING -> "참가자 확정 후에는 게임 시작 카운트다운을 시작할 수 없습니다.";
            case ALREADY_PENDING -> "시작 카운트다운이 이미 진행 중입니다.";
            case PRELOAD_FAILED -> "게임 시작 전 맵 프리로드에 실패했습니다.";
            case SCHEDULED -> "시작 카운트다운을 시작했습니다.";
        };
    }

    private static Optional<ParticipantSelectionPlan> buildSelectionPlan(
            CommandSourceStack source,
            SemionGameManager gameManager,
            SemionGame game
    ) {
        VanillaTeamBridge.ensureTeams(source.getServer());
        List<StartCandidate> candidates = new ArrayList<>();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            candidates.add(new StartCandidate(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    gameManager.ratingProfile(player.getUUID())
                            .map(PlayerRatingProfile::displayElo)
                            .orElse(PlayerRatingProfile.INITIAL_DISPLAY_ELO)
            ));
        }
        return ParticipantSelectionService.selectReady(
                candidates,
                game.readyPlayerIds(),
                gameManager.matchMode(),
                gameManager.nextMatchPriorityPlayerIds(),
                gameManager.teamEloMatchmakingEnabled()
        );
    }

    private static void applyPlanToVanillaTeams(CommandSourceStack source, ParticipantSelectionPlan plan) {
        VanillaTeamBridge.ensureTeams(source.getServer());
        for (AssignedParticipant participant : plan.activeParticipants()) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(participant.uuid());
            if (player != null) {
                VanillaTeamBridge.assignPlayer(source.getServer(), player, participant.teamId());
            }
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (plan.spectatorIds().contains(player.getUUID())) {
                VanillaTeamBridge.assignSpectator(source.getServer(), player);
            }
        }
    }

    private static void assignUnreadyPlayersToSpectatorTeam(
            CommandSourceStack source,
            SemionGame game,
            ParticipantSelectionPlan plan
    ) {
        java.util.Set<UUID> selectedIds = new java.util.HashSet<>();
        for (AssignedParticipant participant : plan.activeParticipants()) {
            selectedIds.add(participant.uuid());
        }
        selectedIds.addAll(plan.spectatorIds());

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (!game.readyPlayerIds().contains(playerId) && !selectedIds.contains(playerId)) {
                VanillaTeamBridge.assignSpectator(source.getServer(), player);
            }
        }
    }

    private static long nextGasUpgradeCost(SemionGame game, PlayerEconomy economy) {
        var gasProduction = game.economyConfig().gasProduction();
        if (economy.gasProductionUpgradeCount() >= gasProduction.maxUpgradeCount()) {
            return -1;
        }
        return gasProduction.upgradeCost(economy.gasProductionUpgradeCount());
    }

    private static String formatTowerLimitPurchaseCost(long diamondCost, long emeraldCost) {
        if (diamondCost < 0 || emeraldCost < 0) {
            return "최대";
        }
        return diamondCost + " 다이아 + " + emeraldCost + " 에메랄드";
    }

    private static String placementFailureMessage(TowerPlacementResult result) {
        return switch (result) {
            case INVALID_PHASE -> "준비 단계에서만 설치할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_LANE -> "활성화된 라인이 아닙니다";
            case UNKNOWN_TOWER -> "알 수 없는 타워입니다";
            case TOWER_NOT_ALLOWED -> "현재 직업으로 사용할 수 없는 타워입니다";
            case OUTSIDE_LANE_AREA -> "lane_path 영역 안에서 실행하세요";
            case OCCUPIED -> "이미 타워가 있는 위치입니다";
            case TOWER_LIMIT_REACHED -> "타워 설치 제한에 도달했습니다";
            case NOT_ENOUGH_MINERAL -> "다이아가 부족합니다";
            case SUCCESS -> "성공";
        };
    }

    private static String towerUpgradeFailureMessage(TowerUpgradeResult result) {
        return switch (result) {
            case INVALID_PHASE -> "준비 단계에서만 업그레이드할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_LANE -> "활성화된 라인이 아닙니다";
            case NO_TOWER_AT_POSITION -> "해당 위치에 타워가 없습니다";
            case TOWER_NOT_OWNED -> "자신이 설치한 타워만 업그레이드할 수 있습니다";
            case TOWER_NOT_UPGRADABLE -> "사용 가능한 진화 경로가 없습니다";
            case UNKNOWN_UPGRADE -> "해당 타워에 없는 업그레이드 ID입니다";
            case UNKNOWN_TARGET_TYPE -> "타워 진화 대상 타입이 등록되지 않았습니다";
            case TOWER_NOT_ALLOWED -> "현재 직업으로 사용할 수 없는 타워입니다";
            case NOT_ENOUGH_MINERAL -> "다이아가 부족합니다";
            case NOT_ENOUGH_ADV_EXPERIENCE -> "주민 ADV 경험치가 부족합니다";
            case SUCCESS -> "성공";
        };
    }

    private static String towerSellFailureMessage(TowerSellResult result) {
        return switch (result) {
            case SUCCESS -> "성공";
            case INVALID_PHASE -> "현재 단계에서는 판매할 수 없습니다.";
            case PLAYER_NOT_IN_GAME -> "현재 게임 참가자가 아닙니다.";
            case PLAYER_TEAM_ELIMINATED -> "탈락한 팀은 판매할 수 없습니다.";
            case UNKNOWN_LANE -> "담당 라인을 찾을 수 없습니다.";
            case NO_TOWER_AT_POSITION -> "현재 위치에 판매할 타워가 없습니다.";
            case TOWER_NOT_OWNED -> "자신이 설치한 타워만 판매할 수 있습니다.";
        };
    }

    private static String summonFailureMessage(SummonResultType result) {
        return switch (result) {
            case INVALID_PHASE -> "준비 단계 또는 웨이브 중에만 구매할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_SUMMON -> "알 수 없는 소환 ID입니다";
            case SUMMON_NOT_ALLOWED_BY_JOB -> "현재 직업은 해당 소환을 사용할 수 없습니다";
            case NOT_ENOUGH_GAS -> "에메랄드가 부족합니다";
            case NO_TARGET_TEAM -> "공격할 수 있는 상대 팀이 없습니다";
            case NO_TARGET_LANE -> "대상 팀에 활성화된 라인이 없습니다";
            case NO_ACTIVE_GAME -> "진행 중인 게임이 없습니다";
            case SUCCESS -> "성공";
        };
    }
}
