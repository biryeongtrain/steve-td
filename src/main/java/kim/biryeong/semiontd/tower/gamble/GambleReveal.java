package kim.biryeong.semiontd.tower.gamble;

import java.util.ArrayList;
import java.util.List;

/** Pure presentation timeline. All outcomes are supplied by the completed bet; frames never roll rewards. */
public record GambleReveal(Kind kind, List<Integer> outcomes, String label, String caption, String result, boolean positive) {
    public enum Kind { CARDS, DICE, SLOTS }
    public enum Cue { NONE, DRAW, ROLL, STOP }
    public record Frame(List<Integer> glyphs, boolean revealed, Cue cue) {
        public Frame { glyphs = List.copyOf(glyphs); }
    }

    public GambleReveal {
        outcomes = List.copyOf(outcomes);
        int limit = kind == Kind.CARDS ? 52 : 6;
        int min = kind == Kind.DICE ? 1 : 0;
        int max = kind == Kind.DICE ? 6 : limit - 1;
        if (outcomes.isEmpty() || outcomes.size() > 3 || (kind != Kind.DICE && outcomes.size() != 3)
                || (kind == Kind.DICE && outcomes.size() > 2)
                || outcomes.stream().anyMatch(value -> value < min || value > max)) {
            throw new IllegalArgumentException("Invalid reveal outcomes");
        }
    }

    public int revealTick() {
        return settleTick(outcomes.size() - 1);
    }

    public int durationTicks() {
        return revealTick() + 40;
    }

    private int settleTick(int index) {
        return switch (kind) {
            case CARDS -> 12 * (index + 1);
            case DICE -> 28 + 10 * index;
            case SLOTS -> 30 + 10 * index;
        };
    }

    public Frame frameAt(int age) {
        List<Integer> glyphs = new ArrayList<>();
        Cue cue = Cue.NONE;
        for (int i = 0; i < outcomes.size(); i++) {
            int settle = settleTick(i);
            if (age >= settle) {
                glyphs.add(outcomes.get(i));
            } else if (kind == Kind.CARDS) {
                glyphs.add(-1);
            } else {
                // Cosmetic cycling is deterministic and never touches the gameplay RNG.
                glyphs.add(Math.floorMod(age / 3 + i * 2, 6) + (kind == Kind.DICE ? 1 : 0));
            }
            if (age == settle) cue = kind == Kind.CARDS ? Cue.DRAW : Cue.STOP;
        }
        if (cue == Cue.NONE && kind != Kind.CARDS && age < revealTick() && age % 6 == 0) cue = Cue.ROLL;
        return new Frame(glyphs, age >= revealTick(), cue);
    }
}
