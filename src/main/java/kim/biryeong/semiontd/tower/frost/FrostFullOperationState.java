package kim.biryeong.semiontd.tower.frost;

import java.util.EnumMap;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService.TriggerFamily;

final class FrostFullOperationState {
    private final EnumMap<TriggerFamily, Integer> familyActivations =
            new EnumMap<>(TriggerFamily.class);
    private final EnumMap<TriggerFamily, Long> lastActivationTick =
            new EnumMap<>(TriggerFamily.class);
    private int totalActivations;
    private boolean waveActive;
    private boolean ready;
    private boolean usedThisWave;
    private boolean active;
    private long activeUntilTick;
    private long nextChillPulseTick;

    void beginWave() {
        familyActivations.clear();
        lastActivationTick.clear();
        totalActivations = 0;
        waveActive = true;
        ready = false;
        usedThisWave = false;
        active = false;
        activeUntilTick = 0L;
        nextChillPulseTick = 0L;
    }

    void endWave() {
        waveActive = false;
        ready = false;
        active = false;
    }

    boolean record(TriggerFamily family, long gameTime) {
        if (!waveActive || lastActivationTick.getOrDefault(family, Long.MIN_VALUE) == gameTime) {
            return false;
        }
        int count = familyActivations.getOrDefault(family, 0);
        if (count >= FrostBalance.fullOperationMaxActivationsPerFamily()) {
            return false;
        }
        lastActivationTick.put(family, gameTime);
        familyActivations.put(family, count + 1);
        totalActivations++;
        return true;
    }

    void activate(long gameTime) {
        ready = false;
        usedThisWave = true;
        active = true;
        activeUntilTick = gameTime + Math.max(1, FrostBalance.fullOperationDurationTicks());
        nextChillPulseTick = gameTime;
    }

    int totalActivations() {
        return totalActivations;
    }

    int familyActivations(TriggerFamily family) {
        return familyActivations.getOrDefault(family, 0);
    }

    boolean waveActive() {return waveActive;}
    boolean ready() {return ready;}
    boolean usedThisWave() {return usedThisWave;}
    boolean active() {return active;}
    long activeUntilTick() {return activeUntilTick;}
    long nextChillPulseTick() {return nextChillPulseTick;}
    void expire() {active = false;}
    void markReady() {ready = true;}
    void scheduleChillPulse(long tick) {nextChillPulseTick = tick;}
}
