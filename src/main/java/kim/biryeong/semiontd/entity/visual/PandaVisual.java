package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.Panda;

/**
 * 판다의 겉모습. 판다는 크기가 아니라 <b>유전자</b>로 종류가 갈립니다 - 갈색 판다, 화난 판다,
 * 약한 판다처럼 생김새가 아예 다릅니다.
 */
public final class PandaVisual {
    private PandaVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:panda");

        /**
         * 겉으로 드러날 유전자. 주 유전자와 숨은 유전자를 <b>둘 다</b> 채웁니다.
         *
         * <p>바닐라는 {@code Gene.getVariantFromGenes} 로 보이는 종류를 정하는데, 갈색과 약함은
         * 열성이라 두 유전자가 같을 때만 드러납니다. 주 유전자만 갈색으로 두면 화면에는 평범한
         * 판다가 섭니다.
         */
        public Builder gene(Panda.Gene gene) {
            visual.propertyValue(EntityVisualProperties.PANDA_MAIN_GENE, gene);
            visual.propertyValue(EntityVisualProperties.PANDA_HIDDEN_GENE, gene);
            return this;
        }

        /** 새끼 판다. 크기 배율과 달리 얼굴 비율까지 바뀌어 한눈에 어린 개체로 보입니다. */
        public Builder baby(boolean baby) {
            visual.propertyValue(EntityVisualProperties.BABY, baby);
            return this;
        }

        public Builder scale(double scale) {
            visual.scale(scale);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
