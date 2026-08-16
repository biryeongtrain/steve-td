package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.adversary.AdversaryFoxTower;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.adversary.FoxForm;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.animal.PigTower;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTower;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TowerRuntimeDetailsTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("tower-runtime-details".getBytes(StandardCharsets.UTF_8));
    private static final GridPosition POSITION = new GridPosition(0, 0, 0);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void applyDefaultBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void resetTowerBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void towerDetailsConvertDividerTokensWithoutActionButtons() {
        List<DialogBody> bodies = SemionDialogService.actionDialogBodies(
                "\nStats\n<divider>\nOther owner's tower",
                () -> Component.literal("----------").withStyle(style -> style.withStrikethrough(true))
        );

        PlainMessage message = assertInstanceOf(PlainMessage.class, bodies.getFirst());
        assertFalse(message.contents().getString().contains("<divider>"));
        assertTrue(message.contents().getString().contains("Stats"));
        assertTrue(message.contents().getString().contains("Other owner's tower"));
        assertTrue(containsStrikethrough(message.contents()));
    }

    @Test
    void towerDetailsUsePrimaryDamageTypeFromPlacement() {
        PigTower physicalTower = new PigTower(AnimalTowers.T2_PIG_TOWER, OWNER, TeamId.RED, 1, POSITION);
        assertEquals(
                "<#ec8d34>🪓 피해</#ec8d34><white>: </white><#ec8d34>42</#ec8d34>",
                SemionDialogService.formatTowerDamage(physicalTower, 42.0)
        );

        AncientCityTower ancientCityTower = new AncientCityTower(
                AncientCityTowers.SENSOR_T1, OWNER, TeamId.RED, 1, POSITION
        );
        assertEquals(
                "<#796CFF>🔥 피해</#796CFF><white>: </white><#796CFF>42</#796CFF>",
                SemionDialogService.formatTowerDamage(ancientCityTower, 42.0)
        );

        AdversaryFoxTower sculkCore = new AdversaryFoxTower(
                AdversaryTowers.typeFor(FoxForm.SCULK_CORE), OWNER, TeamId.RED, 1, POSITION
        );
        assertEquals(
                "<#796CFF>🔥 피해</#796CFF><white>: </white><#796CFF>42</#796CFF>",
                SemionDialogService.formatTowerDamage(sculkCore, 42.0)
        );

        AdversaryFoxTower breeze = new AdversaryFoxTower(
                AdversaryTowers.typeFor(FoxForm.BREEZE), OWNER, TeamId.RED, 1, POSITION
        );
        assertEquals(
                "<#ec8d34>🪓 피해</#ec8d34><white>: </white><#ec8d34>42</#ec8d34>",
                SemionDialogService.formatTowerDamage(breeze, 42.0)
        );
    }

    @Test
    void placementAndUpgradeTooltipsUseTowerTypePrimaryDamageFormat() {
        assertEquals(
                "<#796CFF>🔥 피해</#796CFF><white>: </white><#796CFF>5</#796CFF>",
                SemionDialogService.formatTowerTypePrimaryDamage(AncientCityTowers.SENSOR_T1)
        );
        assertEquals(
                "<#796CFF>🔥 피해</#796CFF><white>: </white><#796CFF>800</#796CFF>",
                SemionDialogService.formatTowerTypePrimaryDamage(AdversaryTowers.typeFor(FoxForm.SCULK_CORE))
        );
        assertEquals(
                "<#ec8d34>🪓 피해</#ec8d34><white>: </white><#ec8d34>26</#ec8d34>",
                SemionDialogService.formatTowerTypePrimaryDamage(AdversaryTowers.typeFor(FoxForm.BREEZE))
        );
    }

    @Test
    void installedTowerDetailsUseTheSameBaseDamageAsTooltips() {
        AncientCityTower sensor = new AncientCityTower(
                AncientCityTowers.SENSOR_T1, OWNER, TeamId.RED, 1, POSITION
        );
        assertEquals(5.0, SemionDialogService.towerPrimaryDamage(sensor));
        assertEquals(5.0, SemionDialogService.currentTowerPrimaryDamage(sensor, null));
        assertEquals(
                SemionDialogService.formatTowerTypePrimaryDamage(AncientCityTowers.SENSOR_T1),
                SemionDialogService.formatTowerDamage(
                        sensor,
                        SemionDialogService.currentTowerPrimaryDamage(sensor, null)
                )
        );

        AdversaryFoxTower sculkCore = new AdversaryFoxTower(
                AdversaryTowers.typeFor(FoxForm.SCULK_CORE), OWNER, TeamId.RED, 1, POSITION
        );
        assertEquals(800.0, SemionDialogService.towerPrimaryDamage(sculkCore));
        assertEquals(800.0, SemionDialogService.currentTowerPrimaryDamage(sculkCore, null));
        assertEquals(
                SemionDialogService.formatTowerTypePrimaryDamage(AdversaryTowers.typeFor(FoxForm.SCULK_CORE)),
                SemionDialogService.formatTowerDamage(
                        sculkCore,
                        SemionDialogService.currentTowerPrimaryDamage(sculkCore, null)
                )
        );
    }

    private static boolean containsStrikethrough(Component component) {
        return component.getStyle().isStrikethrough()
                || component.getSiblings().stream().anyMatch(TowerRuntimeDetailsTest::containsStrikethrough);
    }

}
