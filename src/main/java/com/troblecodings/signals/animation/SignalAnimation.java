package com.troblecodings.signals.animation;

import java.util.function.Predicate;

import com.troblecodings.signals.models.ModelInfoWrapper;

public interface SignalAnimation extends Predicate<ModelInfoWrapper> {

    public void updateAnimation();

    public ModelTranslation getModelTranslation();

    public ModelTranslation getFinalModelTranslation();

    public boolean isFinished();

    public void reset();

    public void setUpAnimationValues(final ModelTranslation currentTranslation);

    public SignalAnimation copy();

    public Predicate<ModelInfoWrapper> getPredicate();

}