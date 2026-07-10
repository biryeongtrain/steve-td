package kim.biryeong.semiontd.entity.tower.vfx;

final class TowerVfxBudget {
    private int tokens;
    private long lastTick;

    TowerVfxBudget(int initialTokens, long gameTime) {
        tokens = Math.max(0, initialTokens);
        lastTick = gameTime;
    }

    int claim(int preferred, int minimum, boolean essential, long gameTime, int refill, int capacity) {
        refill(gameTime, refill, capacity);
        int claimed = Math.min(Math.max(0, preferred), tokens);
        if (claimed < minimum && essential) {
            claimed = Math.min(Math.max(0, preferred), minimum);
        } else if (claimed < minimum) {
            claimed = 0;
        }
        tokens = Math.max(0, tokens - claimed);
        return claimed;
    }

    private void refill(long gameTime, int refill, int capacity) {
        if (gameTime <= lastTick) {
            return;
        }
        long elapsed = gameTime - lastTick;
        long replenished = (long) tokens + elapsed * Math.max(0, refill);
        tokens = (int) Math.min(Math.max(0, capacity), replenished);
        lastTick = gameTime;
    }
}
