package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.config.JobAvailabilityConfig;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.job.SemionJob;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SemionDialogServiceJobAvailabilityTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetJobAvailability() {
        JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
    }

    @Test
    void disabledJobButtonIsRedExplainsWhyAndCannotRunSelection() {
        SemionJob job = JobRegistry.find(NetherTowerJob.ID).orElseThrow();
        JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig().withEnabled(job.id(), false));

        Component label = SemionDialogService.jobButtonLabel(job, false);

        assertEquals("✕ " + job.displayName().getString() + " (비활성화)", label.getString());
        assertEquals(ChatFormatting.RED.getColor(), label.getStyle().getColor().getValue());
        assertEquals("", SemionDialogService.jobSelectionCommand(job));
        assertTrue(SemionDialogService.jobTooltip(job, false).getString()
                .contains("관리자에 의해 비활성화된 직업입니다."));
    }
}
