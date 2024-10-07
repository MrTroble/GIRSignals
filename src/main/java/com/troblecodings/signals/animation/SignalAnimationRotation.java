package com.troblecodings.signals.animation;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import com.mojang.math.Vector3f;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.SEProperty;

public class SignalAnimationRotation implements SignalAnimation {

    private AnimationRotionCalc calc;

    private final Predicate<Map<SEProperty, String>> predicate;
    private final float animationSpeed;
    private final RotationAxis axis;
    private final float rotation;
    private final VectorWrapper pivot;
    private final float finalRotationValue;

    public SignalAnimationRotation(final Predicate<Map<SEProperty, String>> predicate,
            final float animationSpeed, final RotationAxis axis, final float rotation,
            final VectorWrapper pivot) {
        this.predicate = predicate;
        this.animationSpeed = animationSpeed;
        this.axis = axis;
        this.rotation = rotation;
        this.pivot = pivot;
        this.finalRotationValue = rotation * 0.005f * 3.49065f;
    }

    @Override
    public void updateAnimation() {
        calc.updateAnimation();
    }

    @Override
    public void setUpAnimationValues(final ModelTranslation currentTranslation) {
        final Vector3f vec = currentTranslation.getQuaternion().toYXZ();
        Vector3f maxPos = new Vector3f(0, 0, 0);
        switch (axis) {
            case X: {
                maxPos = new Vector3f(finalRotationValue, 0, 0);
                break;
            }
            case Y: {
                maxPos = new Vector3f(0, finalRotationValue, 0);
                break;
            }
            case Z: {
                maxPos = new Vector3f(0, 0, finalRotationValue);
                break;
            }
            default:
                break;
        }
        this.calc = new AnimationRotionCalc(vec, maxPos, animationSpeed, axis);
    }

    @Override
    public ModelTranslation getFinalModelTranslation() {
        return new ModelTranslation(pivot, axis.getForAxis(finalRotationValue));
    }

    @Override
    public ModelTranslation getModelTranslation() {
        return new ModelTranslation(pivot, calc.getQuaternion());
    }

    @Override
    public boolean isFinished() {
        if (calc == null)
            return true;
        return calc.isAnimationFinished();
    }

    @Override
    public void reset() {
        calc = null;
    }

    @Override
    public boolean test(final Map<SEProperty, String> properties) {
        return predicate.test(properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(animationSpeed, axis, pivot, calc, rotation);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final SignalAnimationRotation other = (SignalAnimationRotation) obj;
        return Float.floatToIntBits(animationSpeed) == Float.floatToIntBits(other.animationSpeed)
                && axis == other.axis && Objects.equals(pivot, other.pivot)
                && Objects.equals(calc, other.calc) && Objects.equals(rotation, other.rotation);
    }

}