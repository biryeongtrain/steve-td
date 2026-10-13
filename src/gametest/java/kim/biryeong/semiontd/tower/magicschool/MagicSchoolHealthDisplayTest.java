package kim.biryeong.semiontd.tower.magicschool;

import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowerIntegrationTest.add;
import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowerIntegrationTest.game;
import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowerIntegrationTest.requireClose;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;

public final class MagicSchoolHealthDisplayTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void everySchoolTowerExposesRealHealthToHoverWhileWizardProxyStaysHidden(GameTestHelper context) {
        try (var fixture = new Fixture(context)) {
            for (var type : MagicSchoolTowers.all()) {
                var tower = add(fixture.lane, type, fixture.plot);
                var entity = tower.runtimeEntity(fixture.lane).orElseThrow();
                fixture.checkHover(tower);
                if (tower instanceof MagicSchoolWizardTower) {
                    double maximum = entity.getMaxHealth();
                    entity.hurtIgnoringReductions(entity.damageSources().generic(), maximum / 4);
                    requireClose(maximum * .75, entity.getHealth(), "Damage must update the hover source.");
                    fixture.checkHover(tower);
                    check(entity.receiveHealing(maximum / 8), "Support healing must succeed.");
                    requireClose(maximum * .875, entity.getHealth(), "Healing must update the hover source.");
                    fixture.checkHover(tower);
                    check(!entity.isCustomNameVisible(), "The hidden anchor must not duplicate the player model name.");
                    check(FakePlayerTowerVisuals.visualEntity(tower).isPresent(), "The wizard model must remain attached.");
                }
                fixture.lane.removeTower(tower);
                check(entity.isRemoved(), "Removal must discard the hover source.");
                check(FakePlayerTowerVisuals.visualEntity(tower).isEmpty(), "Removal must clean the player visual.");
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void healthHoverSurvivesPotionPromotionDeathAndNextRound(GameTestHelper context) {
        try (var fixture = new Fixture(context)) {
            var wizard = (MagicSchoolWizardTower) add(fixture.lane, MagicSchoolTowers.FRESHMAN, fixture.plot);
            var economy = fixture.game.players().get(fixture.viewer.getUUID()).economy();
            economy.addDiamond(10000);
            check(MagicSchoolCurriculum.purchase(fixture.viewer.getUUID(), MagicSchoolCurriculum.Upgrade.POTIONS, economy)
                    == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "The potion curriculum must be available.");
            fixture.lane.markWaveStarted(1);
            var original = wizard.runtimeEntity(fixture.lane).orElseThrow();
            original.hurtIgnoringReductions(original.damageSources().generic(), 10000);
            requireClose(wizard.currentMaxHealth() * .08, original.getHealth(), "Potion survival must retain the existing eight-percent rule.");
            fixture.checkHover(wizard);
            var promoted = (MagicSchoolWizardTower) ProductionTowerCatalog.find(MagicSchoolTowers.GRYFFINDOR.id()).orElseThrow()
                    .create(wizard.ownerPlayer(), fixture.lane.teamId(), fixture.lane.laneId(), fixture.plot);
            promoted.copyFrom(wizard, 200);
            fixture.lane.replaceTower(wizard, promoted);
            check(original.isRemoved() && FakePlayerTowerVisuals.visualEntity(wizard).isEmpty(), "Promotion must remove the old display source.");
            fixture.checkHover(promoted);
            var defeated = promoted.runtimeEntity(fixture.lane).orElseThrow();
            defeated.hurtIgnoringReductions(defeated.damageSources().generic(), 10000);
            check(defeated.getHealth() <= 0, "Promotion must not restore the consumed potion.");
            fixture.lane.killTower(promoted);
            check(FakePlayerTowerVisuals.visualEntity(promoted).isEmpty(), "Death must remove the wizard visual.");
            fixture.lane.resetForRound();
            fixture.lane.markWaveStarted(2);
            fixture.checkHover(promoted);
            var restored = promoted.runtimeEntity(fixture.lane).orElseThrow();
            check(restored != defeated && !restored.isRemoved(), "A new round must restore a fresh live hover source.");
            restored.hurtIgnoringReductions(restored.damageSources().generic(), 10000);
            requireClose(promoted.currentMaxHealth() * .08, restored.getHealth(), "The next round restores the existing potion charge.");
            fixture.checkHover(promoted);
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void reconnectKeepsHealthHoverAndTowerDialogUsesTheSameHealth(GameTestHelper context) throws Exception {
        try (var fixture = new Fixture(context)) {
            var wizard = (MagicSchoolWizardTower) add(fixture.lane, MagicSchoolTowers.FRESHMAN, fixture.plot);
            var entity = wizard.runtimeEntity(fixture.lane).orElseThrow();
            entity.hurtIgnoringReductions(entity.damageSources().generic(), 50);
            double health = entity.getHealth();
            var manager = new SemionGameManager();
            var active = SemionGameManager.class.getDeclaredField("activeGame");
            active.setAccessible(true);
            active.set(manager, fixture.game);
            manager.handlePlayerDisconnect(fixture.viewer);
            kim.biryeong.semiontd.ui.GambleRevealService.clear(fixture.viewer.getUUID());
            kim.biryeong.semiontd.tower.demonlord.DemonLordService.disconnectPlayer(fixture.viewer);
            kim.biryeong.semiontd.tower.frost.FrostFullOperationService.cleanupPlayer(fixture.viewer);
            check(fixture.game.restorePlayerPlacement(context.getLevel().getServer(), fixture.viewer), "The participant must reconnect.");
            requireClose(health, wizard.health(), "Reconnect must preserve current health.");
            fixture.checkHover(wizard);
            var connection = fixture.viewer.connection;
            var sent = new ArrayList<Packet<?>>();
            try {
                fixture.viewer.connection = new ServerGamePacketListenerImpl(context.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                        fixture.viewer, CommonListenerCookie.createInitial(fixture.viewer.getGameProfile(), false)) {
                    @Override public void send(Packet<?> packet) { sent.add(packet); }
                };
                manager.dialogService().showTowerDetails(fixture.viewer, fixture.game, wizard);
                String dialog = sent.stream().filter(ClientboundShowDialogPacket.class::isInstance).findFirst().orElseThrow().toString();
                check(dialog.contains("150") && dialog.contains("200"), "The delivered dialog must include the current and maximum health.");
            } finally {
                fixture.viewer.connection = connection;
            }
        }
        context.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerPlayer viewer;
        final SemionGame game;
        final PlayerLane lane;
        final GridPosition plot;

        Fixture(GameTestHelper context) {
            viewer = context.makeMockServerPlayerInLevel();
            game = game(context, viewer.getUUID(), UUID.randomUUID());
            lane = game.playerLane(viewer.getUUID()).orElseThrow();
            plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
        }

        void checkHover(EntityBackedTower tower) {
            var entity = tower.runtimeEntity(lane).orElseThrow();
            entity.setNoAi(true);
            check(!entity.isInvisibleTo(viewer), tower.type().id() + " must not be excluded by the normal invisible-target filter.");
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 start = center.add(0, 0, -3);
            Vec3 end = center.add(0, 0, 3);
            var hit = ProjectileUtil.getEntityHitResult(viewer, start, end, entity.getBoundingBox().inflate(4),
                    target -> !target.isSpectator() && target.isPickable() && !target.isInvisibleTo(viewer), 36);
            check(hit != null && hit.getEntity() == entity, "The shared hover raycast must hit the real school tower.");
            requireClose(tower.health(), entity.getHealth(), "Hover current health must match combat health.");
            requireClose(tower.currentMaxHealth(), entity.getMaxHealth(), "Hover maximum health must match combat health.");
            var metadata = new ArrayList<SynchedEntityData.DataValue<?>>();
            entity.modifyRawTrackedData(metadata, viewer, true);
            check(metadata.stream().anyMatch(value -> value.id() == 0 && value.value() instanceof Byte flags && (flags & 0x20) != 0),
                    "The common Polymer proxy must stay invisible to the client.");
        }

        @Override public void close() { game.close(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
