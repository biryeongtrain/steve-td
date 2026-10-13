package kim.biryeong.semiontd.config;

import net.minecraft.resources.Identifier;

public final class TraitBalanceRuntime {
    private static final TraitBalanceConfig DEFAULT_CONFIG = TraitBalanceConfig.defaultConfig();
    private static TraitBalanceConfig current = DEFAULT_CONFIG;

    private TraitBalanceRuntime() {
    }

    public static TraitBalanceConfig current() {
        return current;
    }

    public static void apply(TraitBalanceConfig config) {
        current = config == null ? DEFAULT_CONFIG : config.withMissingDefaults(DEFAULT_CONFIG);
    }

    public static double value(Identifier traitId, String key) {
        if (traitId == null || key == null) {
            return 0.0;
        }
        String path = traitId.getPath();
        double fallback = DEFAULT_CONFIG.value(path, key, 0.0);
        return current.value(path, key, fallback);
    }
}
