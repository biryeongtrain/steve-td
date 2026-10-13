package kim.biryeong.semiontd.vfx;

import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.Identifier;

/**
 * 연출 한 조각의 그림: 직접 그린 텍스처 한 장과 그것을 붙일 평면 모양.
 *
 * <p>텍스처는 {@code resourceDir}/{@code name}.png로 모드 안에 들어 있고,
 * {@link DisplaySpriteResourcePack}이 리소스팩의 {@code textures/item/vfx/}, {@code models/item/vfx/},
 * {@code items/vfx/}로 옮겨 아이템 모델로 만듭니다. 디스플레이 엔티티는 종이 아이템에 이 모델을 입혀 띄웁니다.
 *
 * <ul>
 *   <li>{@link Shape#FLAT}: 바닥에 눕는 1×1 판(XZ). 마법진·충격파·균열·칼날 궤적. 텍스처 위쪽이 북(-Z)입니다.</li>
 *   <li>{@link Shape#UPRIGHT}: 서 있는 1×1 판(XY). 날개·단두대·방벽판. 앞뒤 어느 쪽에서 봐도 월드 기준으로 같은 방향으로 보입니다.</li>
 *   <li>{@link Shape#CROSS}: XY·ZY 판 두 장을 교차. 빛줄기·가시·사슬처럼 어느 각도에서도 보여야 하는 가늘고 긴 것.</li>
 * </ul>
 * {@code billboard}이면 UPRIGHT 판을 항상 카메라 쪽으로 돌립니다(불꽃·룬·파편·섬광).
 */
public record DisplaySprite(String name, Shape shape, boolean billboard, String resourceDir) {
    public enum Shape {
        FLAT,
        UPRIGHT,
        CROSS,
        /** 여섯 면이 모두 같은 텍스처인 1×1×1 정육면체. 오른쪽 회전과 크기로 입체 가시 등을 만듭니다. */
        CUBE,
        /**
         * 지름 1, 높이 1의 속이 빈 원기둥 벽(16각). 텍스처는 옆으로 한 바퀴 감기고(가로 16등분), 위쪽이 기둥 꼭대기입니다.
         * 바닥 원형 연출에 겹쳐 반투명한 입체 벽을 세웁니다. 크기는 (지름, 높이, 지름)입니다.
         */
        CYLINDER
    }

    public static DisplaySprite flat(String name, String resourceDir) {
        return new DisplaySprite(name, Shape.FLAT, false, resourceDir);
    }

    public static DisplaySprite upright(String name, String resourceDir) {
        return new DisplaySprite(name, Shape.UPRIGHT, false, resourceDir);
    }

    public static DisplaySprite cross(String name, String resourceDir) {
        return new DisplaySprite(name, Shape.CROSS, false, resourceDir);
    }

    public static DisplaySprite cube(String name, String resourceDir) {
        return new DisplaySprite(name, Shape.CUBE, false, resourceDir);
    }

    public static DisplaySprite cylinder(String name, String resourceDir) {
        return new DisplaySprite(name, Shape.CYLINDER, false, resourceDir);
    }

    public static DisplaySprite billboard(String name, String resourceDir) {
        return new DisplaySprite(name, Shape.UPRIGHT, true, resourceDir);
    }

    /** 아이템의 {@code item_model} 컴포넌트 값. 리소스팩의 {@code items/vfx/<name>.json}을 가리킵니다. */
    public Identifier itemModel() {
        return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "vfx/" + name);
    }

    public String texturePath() {
        return "/" + resourceDir + "/" + name + ".png";
    }
}
