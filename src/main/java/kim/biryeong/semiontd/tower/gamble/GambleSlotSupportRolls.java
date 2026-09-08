package kim.biryeong.semiontd.tower.gamble;

import java.util.EnumMap;
import java.util.List;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.util.RandomSource;

/** Round-scoped slot buffs; paid gambler slot bets use GambleSlots instead. */
final class GambleSlotSupportRolls {
    private GambleSlotSupportRolls() {
    }

    static Result roll(TowerType type, RandomSource random) {
        var symbols = GambleSlots.Symbol.values();
        return resolve(type, List.of(symbols[random.nextInt(symbols.length)],
                symbols[random.nextInt(symbols.length)], symbols[random.nextInt(symbols.length)]));
    }

    static Result resolve(TowerType type, List<GambleSlots.Symbol> symbols) {
        if (symbols.size() != 3) throw new IllegalArgumentException("Slot support requires three symbols.");
        var counts = new EnumMap<GambleSlots.Symbol, Integer>(GambleSlots.Symbol.class);
        symbols.forEach(symbol -> counts.merge(symbol, 1, Integer::sum));
        var magnitudes = new EnumMap<GambleSupportStat, Double>(GambleSupportStat.class);
        double damage = TowerBalanceRuntime.ability(type.id(), "slotBaseDamage");
        counts.forEach((symbol, count) -> {
            double multiplier = count == 3 ? 5.0 : count == 2 ? 2.5 : 1.0;
            switch (symbol) {
                case IRON_NUGGET -> { }
                case IRON -> magnitudes.merge(GambleSupportStat.MAX_HEALTH,
                        TowerBalanceRuntime.ability(type.id(), "slotBaseHealth") * multiplier, Double::sum);
                case COPPER -> magnitudes.merge(GambleSupportStat.REGENERATION,
                        TowerBalanceRuntime.ability(type.id(), "slotBaseRegeneration") * multiplier, Double::sum);
                case GOLD -> magnitudes.merge(GambleSupportStat.DAMAGE, damage * multiplier, Double::sum);
                case EMERALD -> magnitudes.merge(GambleSupportStat.MAGIC_DAMAGE, damage * multiplier, Double::sum);
                case DIAMOND -> {
                    magnitudes.merge(GambleSupportStat.DAMAGE, damage * multiplier / 2, Double::sum);
                    magnitudes.merge(GambleSupportStat.MAGIC_DAMAGE, damage * multiplier / 2, Double::sum);
                }
            }
        });
        return new Result(symbols, magnitudes.entrySet().stream()
                .map(entry -> new GambleSupportEffect(entry.getKey(), true, entry.getValue())).toList());
    }

    record Result(List<GambleSlots.Symbol> symbols, List<GambleSupportEffect> effects) {
        Result { symbols = List.copyOf(symbols); effects = List.copyOf(effects); }
        boolean jackpot() { return symbols.get(0) == symbols.get(1) && symbols.get(1) == symbols.get(2); }
    }
}
