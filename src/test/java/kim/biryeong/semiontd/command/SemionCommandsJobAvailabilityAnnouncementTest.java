package kim.biryeong.semiontd.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.job.SemionJob;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SemionCommandsJobAvailabilityAnnouncementTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void announcementsAreRedAndUseDisplayNameInsteadOfJobId() {
        SemionJob job = JobRegistry.find(NetherTowerJob.ID).orElseThrow();

        Component enabled = SemionCommands.jobAvailabilityAnnouncement(job, true);
        Component disabled = SemionCommands.jobAvailabilityAnnouncement(job, false);

        assertTrue(enabled.getString().contains(job.displayName().getString()));
        assertTrue(enabled.getString().endsWith(" 직업이 활성화되었습니다."));
        assertTrue(disabled.getString().endsWith(" 직업이 비활성화되었습니다."));
        assertFalse(enabled.getString().contains(job.id().toString()));
        assertFalse(disabled.getString().contains(job.id().toString()));
        assertEquals(ChatFormatting.RED.getColor(), enabled.getSiblings().getLast().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.RED.getColor(), disabled.getSiblings().getLast().getStyle().getColor().getValue());
    }
}
