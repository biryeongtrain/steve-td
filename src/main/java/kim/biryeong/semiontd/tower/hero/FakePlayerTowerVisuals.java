package kim.biryeong.semiontd.tower.hero;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.cosmetic.SemionCosmeticItems;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.mixin.accessor.PlayerInfoUpdatePacketAccessor;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.pet.PetTowers;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class FakePlayerTowerVisuals {
    static final String SUCCUBUS_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc4NzI5OTU3OTMyNCwKICAicHJvZmlsZUlkIiA6ICI1MzE4YWJhNDJiMTk0ODNiODFiMWY2N2Y1ODVjNDdkNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJocHllZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kOTA1ODkzNzlmNDAyZTkwZmM2ZTQ0OTYwOGNjNjVjYjFlYzRlODk2Zjc1ZGU1MmMwYjE3MWQ2YjY4NGVmMDRmIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
    static final String SUCCUBUS_TEXTURE_SIGNATURE = "B4/+wtiHS8FUBBiTS46yCQPb568wY+oU9PYQ0Ri5kv1r3rPfWV+/EYEuDHLRTpzwGP6Dmr7GHV5WCHfdCyZ/MqaZx/Qht85Liy9cDCzqVOvJUhaiHAZucKvl2ziAbgQWy9sLBjFNZyEgTi+j1ZZhPzLUfhrD4OT5N9K3uHMKU2/ATa6YXSTupFBPPGwKa4139B8Q1Pd6URc+PSomiZvorVWdw8/rQMd0BnrEg5INwiQ386XuwtmbOeQS93LjFHqv815J8PtF69MB3rHvnTVRYAquqqTOgd7wUYD0aoYOdN8WwPgibLKkKesWd4TWNyY6WlKhNvlZ6RKsp1rIWnGjPyObbNuU125DzdeoayfjxvOqs1X3v7VClypYKayhQTzr+Yo0NAWSaCMdbipW2kHWB/j8na+6fGyJSqhSl2L187TuNEBBc29Rym4D1AtImJvrd5iT54vt9C1jUjAOHfq+xTgYJIyUg3BTGFNUd3qghpKBT853snuopIvRiFZp65naAmfYtOw3DNEvx51OcHVkmRjcAtScfg9QGwEBfvCwH1/p4Bsel5IpUFatnI8S+JrrLY6Pq7Wcc2zZFGWhpwWbBolvtqPgxjLqmnjyEVf3KEBZ7a8uW/d+57M+DlW7htnqII3uZbMP69bOJWlLvx//Hx/lCbDe+aOu2NTYCq34Z1w=";
    static final String PET_OWNER_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc4NzYwODkwMzg1MSwKICAicHJvZmlsZUlkIiA6ICI4ZmZhYTYzODk0YmQ0YmQ1YjM1MjY3NWY0ZjM5ZTgwZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJzb2h4eCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85NjMzNDEwODJlYzIyZmJiMTdkZWZhNzk1YzQ1M2EwYmQzZTlkNDI0MTA0NjJiNjg5MzQyMmE2ZDM4ZDA2MDM3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
    static final String PET_OWNER_TEXTURE_SIGNATURE = "AyzeuDPRUVVnwaLe5+lmwy1vL44GgIM7MfwrzdhZGYgSYXmnTDCeQoY8KC+OAIw09zhlfSn1Jm9Mz569z/XOFifqVSRdjmiaR+4h1o+FNf1icwIS2GkZdpThI1jdfVXcx943YYtfw8KF3WIQfuckX90TwrF1dX/OYKLEPkMRLF273gJ7S5DSIytwSPFEWsJzFlGO/p28oU6Juu6Pz0yulxLzAci+6LS+jyzdg1DnlG0gRcDibqsUkjzil7zQsyGttoa3z9bNnxtcA0Cqr8XaOYq4uryMmOpuwpX5kc9TjTLW22bbCGceATBpvLZLowW6QyW55Fqoyj1RqySjZab/RMZzgNW3RFm3U+4QR857brmoGfXTy5l7sSJnaODQdTl8x5mt5qpmQgld/PJqdnhhSMmyNY6ptWsdIsz5SS2D/3sfFpBj8FjMhUn9tjaECA6tkHa0Ym0qdyzCOO7Osbi3PctLYxTXb+k+W2V9J7tWpD0jytKa6zKVpW20uepmDN5/gwpPkXXThFXY3Rp5VUVsHX8GGJZCH8FvTdFfnySHS8A2hdOi7bg5657CdZuvI5LlViNnhb5YG/nwu87x6fBIXBJzN47tszjEiggUsAJFSflF+cUC6BDKZJMKafgFNobCu3SiMcS0Jb858HTn/OIMgqM+FYGls31DiN8JnZcmsDE=";
    private static final String PIRATE_ADMIRAL_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTcyODI3NjUyMzI4MiwKICAicHJvZmlsZUlkIiA6ICIwNTAzNzZmZjAxY2I0OGVjOTUwM2NhMjhjMWU2MzlkMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJKb25haDU1OTAiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY2ZGE5YmI3YmY0NWQxZjAyNDMyMDYzZjNhZjlkY2FmOWFiYzkwOGFhMGM3NjE3NzIxMGJmM2U2Njc1ZDAyYyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String PIRATE_ADMIRAL_TEXTURE_SIGNATURE = "jZBB60NMLiPmyf7eZwiSANzZXt2/Jz12fnCRdndMW+jeMgXeUmB7c0VWIDVF4eUNBstn3Jbxg9Oq7U8qJbvrEVAZipHzqpGFIACoVAOGWCTJDv09dsHWjHgCqOu9mqLXHzKHxaUc8mMTfgTxIrZcNPPBpzAwad7PqLRshHgXolqGHHwtS85Xs9BiLVRWCLgKVQySENXxKbXJa3ZkIYHA3E5zIrlMlUzjILW2esSzL4krf4ioWrH3+Gq4U6ygIXTHyI3XmKx/6ISEFFVg5DfsH6Xg8o5+QU8J31+M0fA705uNeB18ymPPJzxCnMHXWdqQ1dw8GjR+zm/o4m3FsYUAutMflJaXxo58HNpoVtLuhu7LqfChHaXiSJcOqZomWfefiiS+MIPeRgxtZYFRQImAYEEuj8rn5uC5YUK9EtTh9HJKnHbI2+Iv/AIGsDK7K+qa9he746suDz3USMGr93tASbP2mWwuwUoNF4bnH8HerqbQVorHuSwul/vlYP6zEtNA5MCj6FHqQ336zzb8nnJzPeWvswH0WH/AiJcFlNpML6KYJVCSaVklpjm0XulWGpeWGVUibh56/7Fz+818ppJgYsXYW62VK+fxmN/o+AAEptPu+7ParfiB6xMRBgmR+Qwv2yrZnxQS0P56Ukcf/sJbY1WHK2kVVSFXAjUMzA32kCw=";
    private static final String PIRATE_SWORDSMAN_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc3NDU1NTY5NTI4NiwKICAicHJvZmlsZUlkIiA6ICJlODE1MGY1MjlmZGU0YzdkYjI3OTAyZjJjNmU3NTc5ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJCcmFkQm90XzIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmNkYWJlZmYwZDc0YjgxNTYwZjY4YWMyZmE0YTA5ZTRiN2Q5MWE4N2MwYWRkNDNmODliMjg3YjBlZGNkODg1YyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String PIRATE_SWORDSMAN_TEXTURE_SIGNATURE = "hCdOrcsHY/ucYvVpSD7qzPcTykHIQrPKaNFCxwHhsOLvon3sBdS0ORdYA6Q77ot2syvphmUB/cY4BYCiTT/XENAiYiFTcVmp0+Br/suZOANCvXNlLaRB89dSURtpu9fjUPeNLjl+ytZMDWvvOWJegWWN6McxM5liLQU8h9yPZR1HTQlJHtWgsxwSlu9epKafUFUx+HRalFhSNMf9qyocop2u71hFdhJd+RSGQD3wuLE6SyacXkQcz4mpfRNu6/jJMR3JUZko/0oQF3okypKAqn/3toFziHHiIG8NNz6tze21iH7CP+VkGxK4203a3OiJtYtkAAJCjPTc+4rbrfUMbZNk9Lvx260W7Zs6bCeOh8/1X/HRSa50uiQNyXy4kGWJ02DS7VUB+XeQ0pXulfCbspoei5J495aMXfumQ7z6oomxypoS/XgyfQwUyTJuZedIwdbAX13mN4FoFADuRgU0hsOXH3ljBJlLbFt9IidhsZCkNrnIpJGe+6vWCvA3NQ+IVmlyFmfhnY+DFxB3pJs/3dlX1xiJ8gx4mIxGGlvm9x9C1gjcc39JypsJ8xHhgFiaoY47UVc/iaSWjcqAIkdvn30NTrQf3M7qOUfNh9ekMYX9Njh+btuAzkDZDkx8Xq9wV44Hrvmju8Nc3chvy28kRiiIfigLtNnLOHma75SsHHI=";
    private static final String PIRATE_FERRYMAN_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTYyMTM2NDgwMzAxNSwKICAicHJvZmlsZUlkIiA6ICIyM2YxYTU5ZjQ2OWI0M2RkYmRiNTM3YmZlYzEwNDcxZiIsCiAgInByb2ZpbGVOYW1lIiA6ICIyODA3IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlkYjU5ZWIxNTI5ZTE2ZjcyZGU1NzNjYjZmNWU0NDkxZTJhOWUxOWRmOWEwNDhhYTRlMDZlZmYwM2YyYmY4ZTYiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";
    private static final String PIRATE_FERRYMAN_TEXTURE_SIGNATURE = "dvS6s6WzpSa+h8UqQcifMArLJvahyDk4WmwF4y8OY77erHT4CXYzGDYYDOjkXsRT6F3XzuI/3ukUtOgwX4eCYILaP37UFTSXm7DXN3B3FLMBvt3v/0sOx3ElXsj3N5njKUCTpHWm7+NbBVwv9OZIz3VChP0RAYY1epzyCAHLFP+e1+XxdHtlT/MSvGH7mNlF4zbTwdcyhwoA/AMIRdBRE6k45YWzRWVDkhrgZ83lw72CwPTotQzwFoeKbhkji4PO8Q7I6iUATtaTSLTgyjEAltlmXrzDyGUytF+FDsw76zcsESI/z+RuprRaTpvnDX8yQyi55WXbZ1soX3dfPpqyR2V1D96rOKhpVc8jKzPUn7uAAZjqeKaqd580sm4sIwGzGz9bhe3f1/X0ugDQK5z8T30Fj2a3j9ofvwua9WLn+p1Jjrpgjfty+MZJno+ImHGzB7pPpsl/hqrL5o9ShrBWSMGeiUbfrgo9xSUBPkmdN0QXv6zkN5C2Jr+6StYyquqTUhzVZp5M7yPCV1dnDKq0p1yZlfeUGNaWHb1//fcp/cth9CFq+tN4U3ppHI0OwJWlADDOnlwPgUrc9dXBqL691ZxYrDvPeUywF8lrB1w9iR0/YBGsM38+gRSvot4jtckg7CXroW4GjAMZskyUC99iGnaTQuhaTB1+OWS7QWkap9U=";
    private static final String PIRATE_BOAT_SINGER_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc0MDk1MjQ3NzE2MywKICAicHJvZmlsZUlkIiA6ICI3MDYwMDk0OTgyZDc0MTczYTNjZjg1Zjc1NjQ3MGE5YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJpbmV4YWN0b3N0ZW50YXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmExODc1YTE5N2QxMWY1ZDg2N2NkY2JlZWIzNTdhYjlhNjczNjQ5OWFhMjkxYWEyNDM3MDcyNTUxMmY1NWUzOCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String PIRATE_BOAT_SINGER_TEXTURE_SIGNATURE = "xwvg6hUIt3JC5GXch/mZkegsKvcPBpjNViSxSXB/PpmzxifYayKZVM1LFDA26p1PSeFeO9BYSE495h2RkvlAad5emznm3kFVe8Y+oo/t9y0ktHelI8GnQPHtO5NYIxubVf49Ce+3ONcSZaqiG0R7Pz/qRNfqgo4J6uSci5UIdReLuCW3Y5x/8Ad51WWecmQnykDVddJoylvUUwGSIpRjT9cq0Rz/S2I7WoZpqywJlH27W/Yv1tNy/UNQ48P/chlq1wfnmadnydwrKklK3nxEjg674TSY6YKoHegO7RQhT4NMsC/zp1qjtdeBMvsRyCAyNi04q3q0n5Fg6jDqLlhER0LgoWChSfk/VjiJtWQeLrSM7VEelIFJe1mthaoMFAYXaIYHW/ZSV/dfJOsGmnpNn8pE2H/M+VFhJZf6LjBN6XcWwvvoa/bEqSZAd9o5p6O5vQbRj8qReIEBNGwfi8QQrLf3boeuJgNgwtUPfVzvvDyYVap8uiPydvVLV6ZSd+czdkka89BhmQRXqUt8a8YnjvhsXlW35Qd43ZzYhnswd3D1PkVBDW9krQ5f4aIhsbLTK4qxG1QY2OSS/v+7mHyRDxlnTKSn+s8/288OS67V/ud3f1W3oN5k5vPAaqQee88MeY9bycngTB3YTeL2M+zsLIJT2U+mxZe+aoxVNkWuUL8=";
    private static final String PIRATE_DECKHAND_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTcxNzcwNDY5MjE2NiwKICAicHJvZmlsZUlkIiA6ICJlZDUzZGQ4MTRmOWQ0YTNjYjRlYjY1MWRjYmE3N2U2NiIsCiAgInByb2ZpbGVOYW1lIiA6ICI0MTQxNDE0MWgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzYxNzY5OTNhZjNlYmJiZmUyZDFiYjViMzhhZGNkYjMyZDkyMWFiODM1NDNhYTc0NzBlZGY0ODNmMjQ3MWZkZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String PIRATE_DECKHAND_TEXTURE_SIGNATURE = "iliXzK2eR7HdcVUAfFZPedP9WYR1kiuJMJ7Vg4CP0lht7RuHm8KOcLjEe5Y89yZG8gNgIjAdhvWwKoTNefeXRP2pErzs9BwI+M4E2JiLBR2gHDe1AB9qGWJk1jl8TLt1HMcwdYdgEfQQlcrVHOCdXaVY14MCcwgyguyTvSJT2b+aG1/7vrNOLXJ4LbM3c/gKoMMYbbtJM4D+nGQGp6uzPGC59beoPCe8docvtkLLi+buQDWIhq8+NrIz46xExu+eh1GulsrXhj5amzsH6uYjmifT+fdiLOgBMWGnuzJuuX0Y5OVpKAmgC+e3vGO/l+7e+V6AfJgM4ltWwJpFWKbYaByBUEYS/EZ1UbZ/xvrfbJs60dgOCpso9NAkUOeBOd9M/rG3z34wgawQh9Xu93WiVR0/M/AXZP1TJY8ISZ0LKRIAcDSIOL1QUeM0nT5U/R4KyAgANgvEY5lt9Kn4T2uTqUbjZLY++2v8sCWXzTPV0T2QzNY5AAl6PB/+Br6iRldiwaaQidBP77YfAC4MF+Csk3u3m6c2S2jbOgZH+JVoo4cU372yJXsa6x7GEHzPW7pdzXzlP4Qs5qVz0SIVoMPTdPgUnpvftNR0I+/HEZn02Mut0zhqfqOlS2OMqYWvYVg2LMyrrIpiFXyMd9h9R87ni5+STWqrgJFTbLtHe9lHads=";
    private static final String PIRATE_HELMSMAN_TEXTURE_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTcxMTc1NzA5Njg4MywKICAicHJvZmlsZUlkIiA6ICJlZjVjZjkzYWFhMTY0ZTMyODQ4NDYxYjIzNGQ1YWJhNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJfXzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjBjZDcxNWQ1YzcwYTEzMDliNGM1YjlkYThjYTQ0MDE3ZTc1NjA1OTM5MzMwOWIwZGJkZDdhMzdmNWIxN2ZlMSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String PIRATE_HELMSMAN_TEXTURE_SIGNATURE = "YlXMRW2a8oJZxDMlX+c9xZmVorNPKAsH/egMqWyQMgNAP6Ry5how3riuKwdTsnJpkvGRNgKkpxbSngvVb15o4w3hwdYWbsLM3E84yfblOSkG/plxG/lxHwHWuDg1mdOc4B4CWJspoQ1g2NYejSf7CAIGT7qLo7EUPm9TJGM/wacf3UJM82pPNmyfPSjJzgZk/b54j9Uza/nwmKNkxa1deZDl4Fj2f2aY7eqO9W4bwIRKs+oNv/BuU/hpK5vl/kYifemgrcvcsuDH89ichfjGBmQpEdfHpkesQiOPqqszE23xwzUqamlZDBQmWcpimGJUwRiTtN3ZBtLefqpPUbTbejb8ytT9lMJS8MbHyGnyc6vPSM4dEv6y0Nz+Eg+7vUurn0nI2MP4oTqVfhdwrMuQqOm2TIoDIqqkk/74wltx0sn/jbklVdKplyP3U5SmUw/Otrpruyz254e3QpbDX5J0a3g+3bvzCGF2rNYh0YxvPm4jT+E3/Y3P1giZWAthOcFC9gHuNMy+ksEZpMeQVXyiakrwb9cFL1PcDvRhpydsAjq+jMzIDxlvMKx6q+s9ffxDoHutOerFqJoX0PO0xzMQGyYOd/8s0iw2lqxL3Lgrf9B45NKTfMpsY4B17IIlNmbzD6q20Tjs0jiDcrXWGHa+MjlVJ1AVsNZeRC3c0x4xQsw=";
    private static final double TRACKING_DISTANCE_SQR = 128.0 * 128.0;
    private static final float COMBAT_PITCH_CORRECTION = 37.5F;
    private static final Map<EntityBackedTower, Visual> VISUALS = new IdentityHashMap<>();

    private FakePlayerTowerVisuals() {
    }

    public static synchronized void attach(SemionTowerEntity anchor, EntityBackedTower tower) {
        if (anchor == null || tower == null || !(anchor.level() instanceof ServerLevel level)) {
            return;
        }
        remove(tower);
        GameProfile visualProfile = uniqueVisualProfile(profile(level, tower), anchor);
        ServerPlayer fakePlayer = new ServerPlayer(
                level.getServer(),
                level,
                visualProfile,
                clientInformation()
        );
        fakePlayer.snapTo(anchor.getX(), anchor.getY(), anchor.getZ(), fakePlayer.getYRot(), fakePlayer.getXRot());
        equip(fakePlayer, tower);
        Visual visual = new Visual(level, anchor, tower, fakePlayer);
        VISUALS.put(tower, visual);
        visual.tick(true);
    }

    public static synchronized void tick(EntityBackedTower tower) {
        Visual visual = VISUALS.get(tower);
        if (visual != null) {
            visual.tick(false);
        }
    }

    public static synchronized void refresh(EntityBackedTower tower) {
        Visual visual = VISUALS.get(tower);
        if (visual == null) {
            return;
        }
        equip(visual.fakePlayer, tower);
        visual.refreshEquipment();
        visual.tick(true);
    }

    public static synchronized void refreshOwner(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        VISUALS.forEach((tower, visual) -> {
            if (ownerId.equals(tower.ownerPlayer())) {
                equip(visual.fakePlayer, tower);
                visual.refreshEquipment();
            }
        });
    }

    public static synchronized void refreshSkin(UUID ownerId, HeroCompanionRole role) {
        if (ownerId == null || role == null) {
            return;
        }
        List<Visual> matching = VISUALS.entrySet().stream()
                .filter(entry -> ownerId.equals(entry.getKey().ownerPlayer()))
                .filter(entry -> HeroPartyTowers.role(entry.getKey().type()).filter(role::equals).isPresent())
                .map(Map.Entry::getValue)
                .toList();
        for (Visual visual : matching) {
            VISUALS.remove(visual.tower);
            visual.remove();
            attach(visual.anchor, visual.tower);
        }
    }

    public static synchronized void playAttack(EntityBackedTower tower) {
        Visual visual = VISUALS.get(tower);
        if (visual == null) {
            return;
        }
        ClientboundAnimatePacket packet = new ClientboundAnimatePacket(
                visual.fakePlayer,
                ClientboundAnimatePacket.SWING_MAIN_HAND
        );
        visual.viewers().forEach(viewer -> viewer.connection.send(packet));
    }

    public static synchronized void remove(EntityBackedTower tower) {
        Visual visual = VISUALS.remove(tower);
        if (visual != null) {
            visual.remove();
        }
    }

    static synchronized Set<UUID> activeProfileIdsForTesting() {
        return VISUALS.values().stream()
                .map(visual -> visual.fakePlayer.getUUID())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static synchronized List<ServerPlayer> activeFakePlayersForTesting() {
        return VISUALS.values().stream()
                .map(visual -> visual.fakePlayer)
                .toList();
    }

    public static synchronized Entity resolveInteractionAnchor(ServerLevel level, int entityId) {
        if (level == null) {
            return null;
        }
        return VISUALS.values().stream()
                .filter(visual -> visual.fakePlayer.getId() == entityId)
                .map(visual -> visual.anchor)
                .filter(anchor -> anchor.level() == level && !anchor.isRemoved())
                .findFirst()
                .orElse(null);
    }

    private static GameProfile profile(ServerLevel level, EntityBackedTower tower) {
        Property pirateTexture = pirateTexture(tower.type());
        if (pirateTexture != null) {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("semion-td:pirate-skin-v2:" + tower.type().id()).getBytes(StandardCharsets.UTF_8)), displayProfileName(tower.type()));
            profile.getProperties().put("textures", pirateTexture);
            return profile;
        }
        if (SuccubusTowers.isSuccubus(tower.type())) {
            return succubusProfile(tower.ownerPlayer());
        }
        if (PetTowers.isOwner(tower.type())) {
            return petOwnerProfile(tower.ownerPlayer(), tower.type());
        }
        if (HeroPartyTowers.isHero(tower.type())) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tower.ownerPlayer());
            if (owner != null) {
                UUID visualId = UUID.nameUUIDFromBytes(
                        ("semion-td:hero:" + tower.ownerPlayer()).getBytes(StandardCharsets.UTF_8)
                );
                GameProfile profile = new GameProfile(visualId, displayProfileName(tower.type()));
                profile.getProperties().putAll(owner.getGameProfile().getProperties());
                return profile;
            }
        }
        HeroCompanionRole role = HeroPartyTowers.role(tower.type()).orElse(null);
        if (role != null) {
            GameProfile skinProfile = HeroCompanionSkins.profile(tower.ownerPlayer(), role);
            GameProfile profile = new GameProfile(skinProfile.getId(), displayProfileName(tower.type()));
            profile.getProperties().putAll(skinProfile.getProperties());
            return profile;
        }
        UUID uuid = UUID.nameUUIDFromBytes("semion-td:hero-party:unknown".getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, "용사 타워");
    }

    /**
     * The client keys player-list entries by profile UUID.  A separate UUID is therefore
     * required for every placed tower, even when multiple towers intentionally share one skin.
     */
    private static GameProfile uniqueVisualProfile(GameProfile skinProfile, SemionTowerEntity anchor) {
        UUID visualId = UUID.nameUUIDFromBytes(
                ("semion-td:tower-visual:" + anchor.getUUID()).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(visualId, skinProfile.getName());
        profile.getProperties().putAll(skinProfile.getProperties());
        return profile;
    }

    private static Property pirateTexture(TowerType type) {
        if (PirateTowers.isAdmiral(type)) return signedTexture(PIRATE_ADMIRAL_TEXTURE_VALUE, PIRATE_ADMIRAL_TEXTURE_SIGNATURE);
        if (PirateTowers.isSwordsman(type)) return signedTexture(PIRATE_SWORDSMAN_TEXTURE_VALUE, PIRATE_SWORDSMAN_TEXTURE_SIGNATURE);
        if (PirateTowers.isBoatSinger(type)) return signedTexture(PIRATE_BOAT_SINGER_TEXTURE_VALUE, PIRATE_BOAT_SINGER_TEXTURE_SIGNATURE);
        if (PirateTowers.isFerrymanFamily(type)) return signedTexture(PIRATE_FERRYMAN_TEXTURE_VALUE, PIRATE_FERRYMAN_TEXTURE_SIGNATURE);
        if (PirateTowers.isDeckhand(type)) return signedTexture(PIRATE_DECKHAND_TEXTURE_VALUE, PIRATE_DECKHAND_TEXTURE_SIGNATURE);
        return PirateTowers.isHelmsman(type) ? signedTexture(PIRATE_HELMSMAN_TEXTURE_VALUE, PIRATE_HELMSMAN_TEXTURE_SIGNATURE) : null;
    }

    private static Property signedTexture(String value, String signature) {
        return new Property("textures", value, signature);
    }

    static GameProfile petOwnerProfile(UUID ownerId, TowerType type) {
        UUID visualId = UUID.nameUUIDFromBytes(
                ("semion-td:pet-owner:" + type.id() + ":" + ownerId).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(visualId, displayProfileName(type));
        profile.getProperties().put("textures", new Property(
                "textures", PET_OWNER_TEXTURE_VALUE, PET_OWNER_TEXTURE_SIGNATURE));
        return profile;
    }

    static GameProfile succubusProfile(UUID ownerId) {
        UUID visualId = UUID.nameUUIDFromBytes(("semion-td:succubus:" + ownerId).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(visualId, displayProfileName(SuccubusTowers.SUCCUBUS));
        profile.getProperties().put("textures", new Property(
                "textures", SUCCUBUS_TEXTURE_VALUE, SUCCUBUS_TEXTURE_SIGNATURE));
        return profile;
    }

    static UUID companionProfileId(HeroCompanionRole role) {
        return HeroCompanionSkins.roleDefaultProfileId(role);
    }

    static String displayProfileName(TowerType type) {
        String displayName = type == null ? "용사" : type.displayName();
        String name = displayName.endsWith("타워") ? displayName : displayName + " 타워";
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    private static ClientInformation clientInformation() {
        return new ClientInformation(
                "ko_kr",
                10,
                ChatVisiblity.FULL,
                true,
                0x7F,
                HumanoidArm.RIGHT,
                false,
                false,
                net.minecraft.server.level.ParticleStatus.ALL
        );
    }

    private static void equip(ServerPlayer player, EntityBackedTower tower) {
        if (PetTowers.isOwner(tower.type())) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, PetTowers.hatStack(tower.type()));
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            return;
        }
        ItemStack mainHand = mainHand(tower);
        player.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
        int armor = displayedArmorLevel(tower);
        player.setItemSlot(EquipmentSlot.HEAD, SuccubusTowers.isSuccubus(tower.type())
                ? SemionCosmeticItems.succubusHead().getDefaultInstance()
                : armorStack(armor, EquipmentSlot.HEAD));
        player.setItemSlot(EquipmentSlot.CHEST, armorStack(armor, EquipmentSlot.CHEST));
        player.setItemSlot(EquipmentSlot.LEGS, armorStack(armor, EquipmentSlot.LEGS));
        player.setItemSlot(EquipmentSlot.FEET, armorStack(armor, EquipmentSlot.FEET));
    }

    static int displayedArmorLevel(EntityBackedTower tower) {
        if (!(tower instanceof HeroTower)) {
            return 0;
        }
        HeroPartyState state = HeroPartyStates.state(tower.ownerPlayer());
        return state.armorVisible() ? state.armorLevel() : 0;
    }

    private static ItemStack mainHand(EntityBackedTower tower) {
        if (SuccubusTowers.isSuccubus(tower.type())) {
            return Items.AMETHYST_SHARD.getDefaultInstance();
        }
        if (tower instanceof HeroTower) {
            return HeroPartyStates.state(tower.ownerPlayer()).equippedWeapon().item().getDefaultInstance();
        }
        return HeroPartyTowers.role(tower.type()).map(role -> switch (role) {
            case KNIGHT -> Items.IRON_SWORD.getDefaultInstance();
            case ARCHER -> Items.BOW.getDefaultInstance();
            case MAGE -> Items.BLAZE_ROD.getDefaultInstance();
            case PRIEST -> Items.ENCHANTED_BOOK.getDefaultInstance();
            case ROGUE -> Items.IRON_SWORD.getDefaultInstance();
            case BARD -> Items.NOTE_BLOCK.getDefaultInstance();
        }).orElse(ItemStack.EMPTY);
    }

    private static ItemStack armorStack(int level, EquipmentSlot slot) {
        if (level <= 0) {
            return ItemStack.EMPTY;
        }
        int tier = Math.max(1, Math.min(5, level));
        return switch (slot) {
            case HEAD -> switch (tier) {
                case 1 -> Items.LEATHER_HELMET.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_HELMET.getDefaultInstance();
                case 3 -> Items.IRON_HELMET.getDefaultInstance();
                case 4 -> Items.DIAMOND_HELMET.getDefaultInstance();
                default -> Items.NETHERITE_HELMET.getDefaultInstance();
            };
            case CHEST -> switch (tier) {
                case 1 -> Items.LEATHER_CHESTPLATE.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_CHESTPLATE.getDefaultInstance();
                case 3 -> Items.IRON_CHESTPLATE.getDefaultInstance();
                case 4 -> Items.DIAMOND_CHESTPLATE.getDefaultInstance();
                default -> Items.NETHERITE_CHESTPLATE.getDefaultInstance();
            };
            case LEGS -> switch (tier) {
                case 1 -> Items.LEATHER_LEGGINGS.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_LEGGINGS.getDefaultInstance();
                case 3 -> Items.IRON_LEGGINGS.getDefaultInstance();
                case 4 -> Items.DIAMOND_LEGGINGS.getDefaultInstance();
                default -> Items.NETHERITE_LEGGINGS.getDefaultInstance();
            };
            case FEET -> switch (tier) {
                case 1 -> Items.LEATHER_BOOTS.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_BOOTS.getDefaultInstance();
                case 3 -> Items.IRON_BOOTS.getDefaultInstance();
                case 4 -> Items.DIAMOND_BOOTS.getDefaultInstance();
                default -> Items.NETHERITE_BOOTS.getDefaultInstance();
            };
            default -> ItemStack.EMPTY;
        };
    }

    private static List<Pair<EquipmentSlot, ItemStack>> equipment(ServerPlayer fakePlayer) {
        ArrayList<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        )) {
            equipment.add(Pair.of(slot, fakePlayer.getItemBySlot(slot).copy()));
        }
        return List.copyOf(equipment);
    }

    private static final class Visual {
        private final ServerLevel level;
        private final SemionTowerEntity anchor;
        private final EntityBackedTower tower;
        private final ServerPlayer fakePlayer;
        private final Set<UUID> trackingViewers = new java.util.HashSet<>();
        private int ticks;
        private double lastX = Double.NaN;
        private double lastY = Double.NaN;
        private double lastZ = Double.NaN;
        private float lastYaw = Float.NaN;
        private float lastPitch = Float.NaN;

        private Visual(ServerLevel level, SemionTowerEntity anchor, EntityBackedTower tower, ServerPlayer fakePlayer) {
            this.level = level;
            this.anchor = anchor;
            this.tower = tower;
            this.fakePlayer = fakePlayer;
        }

        private void tick(boolean forceMove) {
            if (anchor.isRemoved() || anchor.level() != level) {
                remove();
                return;
            }
            ticks++;
            Set<UUID> visible = new java.util.HashSet<>();
            for (ServerPlayer viewer : level.players()) {
                if (viewer.distanceToSqr(anchor) > TRACKING_DISTANCE_SQR) {
                    continue;
                }
                visible.add(viewer.getUUID());
                if (trackingViewers.add(viewer.getUUID())) {
                    spawn(viewer);
                }
            }
            for (UUID viewerId : Set.copyOf(trackingViewers)) {
                if (!visible.contains(viewerId)) {
                    ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(viewerId);
                    if (viewer != null) {
                        viewer.connection.send(new ClientboundRemoveEntitiesPacket(fakePlayer.getId()));
                        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(fakePlayer.getUUID())));
                    }
                    trackingViewers.remove(viewerId);
                }
            }

            double x = anchor.getX();
            double y = anchor.getY();
            double z = anchor.getZ();
            float yaw = anchor.getYHeadRot();
            float pitch = visualPitch(tower.type(), anchor.getXRot(), anchor.currentAttackTarget() != null);
            if (forceMove || ticks % 2 == 0 && (x != lastX || y != lastY || z != lastZ
                    || yaw != lastYaw || pitch != lastPitch)) {
                fakePlayer.snapTo(x, y, z, yaw, pitch);
                fakePlayer.setYHeadRot(yaw);
                ClientboundTeleportEntityPacket packet = ClientboundTeleportEntityPacket.teleport(
                        fakePlayer.getId(),
                        PositionMoveRotation.of(fakePlayer),
                        Set.of(),
                        true
                );
                viewers().forEach(viewer -> viewer.connection.send(packet));
                ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(
                        fakePlayer,
                        (byte) (yaw * 256.0F / 360.0F)
                );
                viewers().forEach(viewer -> viewer.connection.send(headPacket));
                lastX = x;
                lastY = y;
                lastZ = z;
                lastYaw = yaw;
                lastPitch = pitch;
            }
        }

        private List<ServerPlayer> viewers() {
            return trackingViewers.stream()
                    .map(id -> level.getServer().getPlayerList().getPlayer(id))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        private void spawn(ServerPlayer viewer) {
            ClientboundPlayerInfoUpdatePacket playerInfo = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                    List.of(viewer)
            );
            ((PlayerInfoUpdatePacketAccessor) (Object) playerInfo).semiontd$setEntries(List.of(
                    new ClientboundPlayerInfoUpdatePacket.Entry(
                            fakePlayer.getUUID(),
                            fakePlayer.getGameProfile(),
                            false,
                            0,
                            GameType.ADVENTURE,
                            null,
                            true,
                            0,
                            null
                    )
            ));
            viewer.connection.send(playerInfo);
            viewer.connection.send(new ClientboundAddEntityPacket(fakePlayer, 0, fakePlayer.blockPosition()));
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = fakePlayer.getEntityData().getNonDefaultValues();
            if (data != null && !data.isEmpty()) {
                viewer.connection.send(new ClientboundSetEntityDataPacket(fakePlayer.getId(), data));
            }
            viewer.connection.send(new ClientboundSetEquipmentPacket(fakePlayer.getId(), equipment(fakePlayer)));
        }

        private void refreshEquipment() {
            ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(fakePlayer.getId(), equipment(fakePlayer));
            viewers().forEach(viewer -> viewer.connection.send(packet));
        }

        private void remove() {
            ClientboundRemoveEntitiesPacket removeEntity = new ClientboundRemoveEntitiesPacket(fakePlayer.getId());
            ClientboundPlayerInfoRemovePacket removeInfo = new ClientboundPlayerInfoRemovePacket(List.of(fakePlayer.getUUID()));
            Set<ServerPlayer> recipients = new java.util.HashSet<>(level.players());
            trackingViewers.stream()
                    .map(id -> level.getServer().getPlayerList().getPlayer(id))
                    .filter(java.util.Objects::nonNull)
                    .forEach(recipients::add);
            recipients.forEach(viewer -> {
                viewer.connection.send(removeEntity);
                viewer.connection.send(removeInfo);
            });
            trackingViewers.clear();
        }
    }

    static float correctedPitch(float pitch, boolean hasTarget) {
        return hasTarget ? Mth.clamp(pitch + COMBAT_PITCH_CORRECTION, -90.0F, 90.0F) : pitch;
    }

    static float visualPitch(TowerType type, float pitch, boolean hasTarget) {
        return SuccubusTowers.isSuccubus(type) ? 0.0F : correctedPitch(pitch, hasTarget);
    }
}
