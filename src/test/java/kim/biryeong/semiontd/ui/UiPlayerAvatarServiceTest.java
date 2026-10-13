package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

final class UiPlayerAvatarServiceTest {
    @ParameterizedTest
    @ValueSource(strings={"Sandbox Dummy","","abcdefghijklmnopq","../profile","player?x=1","한글","a/b"})
    void syntheticNamesNeverBecomeRemoteProfileRequests(String name) {
        assertFalse(UiPlayerAvatarService.isRemoteProfileName(name));
        assertSame(UiPlayerAvatarService.avatarComponent(name,UiPlayerAvatarService.AvatarVariant.COMPACT),
                UiPlayerAvatarService.avatarComponent("Sandbox Dummy",UiPlayerAvatarService.AvatarVariant.COMPACT));
    }

    @ParameterizedTest
    @ValueSource(strings={"Steve","_player_","A1","abcdefghijklmnop"})
    void preservesMinecraftProfileNames(String name) {
        assertTrue(UiPlayerAvatarService.isRemoteProfileName(name));
    }

    @Test
    void missingNameUsesFallbackWithoutRemoteLookup() {
        assertFalse(UiPlayerAvatarService.isRemoteProfileName(null));
        assertDoesNotThrow(()->UiPlayerAvatarService.avatarComponent(null,UiPlayerAvatarService.AvatarVariant.COMPACT));
    }
}
