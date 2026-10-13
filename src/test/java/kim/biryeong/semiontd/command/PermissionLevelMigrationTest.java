package kim.biryeong.semiontd.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.Permissions;
import org.junit.jupiter.api.Test;

final class PermissionLevelMigrationTest {
    @Test
    void numericLevelOnePassesModeratorButNotGamemaster() {
        var levelOne = LevelBasedPermissionSet.forLevel(PermissionLevel.byId(1));
        assertTrue(levelOne.hasPermission(Permissions.COMMANDS_MODERATOR));
        assertFalse(levelOne.hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    @Test
    void numericLevelTwoPassesGamemaster() {
        var levelTwo = LevelBasedPermissionSet.forLevel(PermissionLevel.byId(2));
        assertTrue(levelTwo.hasPermission(Permissions.COMMANDS_GAMEMASTER));
        assertTrue(levelTwo.hasPermission(Permissions.COMMANDS_MODERATOR));
    }

    @Test
    void numericLevelsZeroAndOneCannotUseRestoredAdministrativePermission() {
        for (int level = 0; level < 2; level++) {
            var permissions = LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level));
            assertFalse(permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER),
                    "Administrative command must reject permission level " + level);
        }
    }

    @Test
    void numericLevelsTwoThroughFourRetainAdministrativePermission() {
        for (int level = 2; level <= 4; level++) {
            var permissions = LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level));
            assertTrue(permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER),
                    "Administrative command must accept permission level " + level);
        }
    }
}
