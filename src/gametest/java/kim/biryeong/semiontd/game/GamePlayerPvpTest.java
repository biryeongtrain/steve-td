package kim.biryeong.semiontd.game;

import java.util.UUID;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class GamePlayerPvpTest {
    @GameTest
    public void playerMeleeProjectilesAndAttributedIndirectDamageAreBlockedButPveIsAllowed(GameTestHelper context) {
        var world = context.getLevel();
        Vec3 position = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2)));
        try (var attacker = RuntimePlayerFixture.connect(context, world, position, GameType.SURVIVAL, UUID.randomUUID(), "pvp-attacker");
                var victim = RuntimePlayerFixture.connect(context, world, position.add(2, 0, 0), GameType.SURVIVAL, UUID.randomUUID(), "pvp-victim")) {
            var arrow = new Arrow(EntityTypes.ARROW, world);
            arrow.setOwner(attacker.player());
            var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(EntityTypes.ZOMBIE, world);
            try {
                for (var damage : java.util.List.of(world.damageSources().playerAttack(attacker.player()),
                        world.damageSources().arrow(arrow, attacker.player()),
                        world.damageSources().indirectMagic(arrow, attacker.player()),
                        world.damageSources().thorns(attacker.player()))) {
                    context.assertTrue(!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(victim.player(), damage, 4),
                            "Player-attributed damage is denied by the registered server event");
                }
                context.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(victim.player(),
                        world.damageSources().mobAttack(zombie), 4), "Monster PvE remains allowed outside match protection");
                context.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(zombie,
                        world.damageSources().playerAttack(attacker.player()), 4), "Attacking a nonplayer is not blocked by the PvP policy");
                context.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(victim.player(),
                        world.damageSources().fall(), 4), "Unattributed environmental damage is not PvP");
                context.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(attacker.player(),
                        world.damageSources().arrow(arrow, attacker.player()), 4), "Self-attributed damage is not player-versus-player");
                context.succeed();
            } finally {
                arrow.discard();
                zombie.discard();
            }
        }
    }
}
