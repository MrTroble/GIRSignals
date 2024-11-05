package com.troblecodings.signals.animation;

import java.util.Objects;

import com.troblecodings.core.VectorWrapper;

public class AnimationTranslationCalc {

    private float stepX;
    private float stepY;
    private float stepZ;
    private float progressX;
    private float progressY;
    private float progressZ;
    private float maxX;
    private float maxY;
    private float maxZ;

    private boolean finishedX = false;
    private boolean finishedY = false;
    private boolean finishedZ = false;

    public AnimationTranslationCalc(final VectorWrapper startPosition,
            final VectorWrapper finalPosition, final float animationSpeed) {
        this.stepX = 0.005f * animationSpeed;
        this.stepY = 0.005f * animationSpeed;
        this.stepZ = 0.005f * animationSpeed;
        calculateWayAndValues(startPosition, finalPosition);
    }

    private void calculateWayAndValues(final VectorWrapper start, final VectorWrapper end) {
        this.progressX = start.getX();
        this.progressY = start.getY();
        this.progressZ = start.getZ();

        this.maxX = end.getX();
        this.maxY = end.getY();
        this.maxZ = end.getZ();

        if (maxX < progressX) {
            this.stepX = -stepX;
        }
        if (maxY < progressY) {
            this.stepY = -stepY;
        }
        if (maxZ < progressZ) {
            this.stepZ = -stepZ;
        }
    }

    public void updateAnimation() {
        if (!finishedX) {
            progressX += stepX;
            this.finishedX = isAnimationOnAxisIsFinished(stepX, progressX, maxX);
        }
        if (!finishedY) {
            progressY += stepY;
            this.finishedY = isAnimationOnAxisIsFinished(stepY, progressY, maxY);
        }
        if (!finishedZ) {
            progressZ += stepZ;
            this.finishedZ = isAnimationOnAxisIsFinished(stepZ, progressZ, maxZ);
        }
    }

    public boolean isAnimationFinished() {
        return finishedX && finishedY && finishedZ;
    }

    private static boolean isAnimationOnAxisIsFinished(final float step, final float progress,
            final float max) {
        if (step > 0) {
            if (progress < max) {
                return false;
            }
        } else {
            if (max < progress) {
                return false;
            }
        }
        return true;
    }

    public VectorWrapper getTranslation() {
        return new VectorWrapper(progressX, progressY, progressZ);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxX, maxY, maxZ, progressX, progressY, progressZ);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final AnimationTranslationCalc other = (AnimationTranslationCalc) obj;
        return Float.floatToIntBits(maxX) == Float.floatToIntBits(other.maxX)
                && Float.floatToIntBits(maxY) == Float.floatToIntBits(other.maxY)
                && Float.floatToIntBits(maxZ) == Float.floatToIntBits(other.maxZ)
                && Float.floatToIntBits(progressX) == Float.floatToIntBits(other.progressX)
                && Float.floatToIntBits(progressY) == Float.floatToIntBits(other.progressY)
                && Float.floatToIntBits(progressZ) == Float.floatToIntBits(other.progressZ);
    }

}