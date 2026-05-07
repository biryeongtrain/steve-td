package kim.biryeong.semiontd.mixin;

import de.tomalbrc.bil.file.extra.interpolation.BezierInterpolator;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = BezierInterpolator.class, remap = false)
public abstract class BezierInterpolatorMixin {
    /**
     * @author Codex
     * @reason BIL 1.7.0 accepts the "bezier" interpolation token but leaves the
     * implementation as a stub. Semion TD models use it for recoil and idle easing.
     */
    @Overwrite
    public Vector3f interpolate(float t, Vector3f beforePlus, Vector3f before, Vector3f after, Vector3f afterPlus) {
        Vector3f start = new Vector3f(before);
        Vector3f end = new Vector3f(after);
        Vector3f span = end.sub(start, new Vector3f());

        Vector3f controlA = start.add(
                beforePlus == null ? span.mul(1.0f / 3.0f, new Vector3f()) : after.sub(beforePlus, new Vector3f()).mul(1.0f / 6.0f),
                new Vector3f()
        );
        Vector3f controlB = end.sub(
                afterPlus == null ? span.mul(1.0f / 3.0f, new Vector3f()) : afterPlus.sub(before, new Vector3f()).mul(1.0f / 6.0f),
                new Vector3f()
        );

        float inverse = 1.0f - t;
        return start.mul(inverse * inverse * inverse, new Vector3f())
                .add(controlA.mul(3.0f * inverse * inverse * t, new Vector3f()))
                .add(controlB.mul(3.0f * inverse * t * t, new Vector3f()))
                .add(end.mul(t * t * t, new Vector3f()));
    }
}
