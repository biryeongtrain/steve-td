package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class DefaultJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "recruit");

    public DefaultJob() {
        super(
                ID,
                Component.literal("무직"),
                List.of(Component.literal("무직백수. 쓰면 밴"))
        );
    }


}
