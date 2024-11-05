package com.troblecodings.signals.animation;

import java.util.Objects;
import java.util.function.Predicate;

import javax.vecmath.Vector3f;

import com.troblecodings.core.QuaternionWrapper;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.models.ModelInfoWrapper;

public class SignalAnimationRotation implements SignalAnimation {

    private AnimationRotionCalc calc;

    private final Predicate<ModelInfoWrapper> predicate;
    private final float animationSpeed;
    private final RotationAxis axis;
    private final float rotation;
    private final VectorWrapper pivot;
    private final float finalRotationValue;

    public SignalAnimationRotation(final Predicate<ModelInfoWrapper> predicate,
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
        final Vector3f vec = QuaternionWrapper.toYXZ(currentTranslation.getQuaternion());
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
    public boolean test(final ModelInfoWrapper wrapper) {
        return predicate.test(wrapper);
    }

    @Override
    public SignalAnimation copy() {
        return new SignalAnimationRotation(predicate, animationSpeed, axis, rotation, pivot);
    }

    @Override
    public Predicate<ModelInfoWrapper> getPredicate() {
        return predicate;
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