package com.troblecodings.signals.animation;

import java.util.Objects;

import javax.vecmath.Vector3f;

import org.lwjgl.util.vector.Quaternion;

public class AnimationRotionCalc {

    private float step;
    private final RotationAxis axis;
    private float progress;
    private float max;

    public AnimationRotionCalc(final Vector3f startPosition, final Vector3f finalPosition,
            final float animationSpeed, final RotationAxis axis) {
        this.step = 0.005f * animationSpeed;
        this.axis = axis;
        calculateWayAndValues(startPosition, finalPosition);
    }

    private void calculateWayAndValues(final Vector3f start, final Vector3f end) {
        switch (axis) {
            case X: {
                progress = start.getX();
                max = end.getX();
                break;
            }
            case Y: {
                progress = start.getY();
                max = end.getY();
                break;
            }
            case Z: {
                progress = start.getZ();
                max = end.getZ();
                break;
            }
        }
        if (max < progress) {
            this.step = -step;
        }
    }

    public void updateAnimation() {
        progress += step;
    }

    public boolean isAnimationFinished() {
        if (step > 0) {
            if (progress < max)
                return false;
        } else {
            if (max < progress)
                return false;
        }
        return true;
    }

    public Quaternion getQuaternion() {
        return axis.getForAxis(progress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(axis, max, progress, step);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final AnimationRotionCalc other = (AnimationRotionCalc) obj;
        return axis == other.axis && Float.floatToIntBits(max) == Float.floatToIntBits(other.max)
                && Float.floatToIntBits(progress) == Float.floatToIntBits(other.progress)
                && Float.floatToIntBits(step) == Float.floatToIntBits(other.step);
    }

}