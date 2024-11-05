package com.troblecodings.signals.animation;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.troblecodings.core.VectorWrapper;

public class ModelTranslation {

    private VectorWrapper pivotTranslation = VectorWrapper.ZERO;
    private Quaternion quaternion = Quaternion.ONE;
    private SignalAnimation animation;
    private VectorWrapper modelTranslation = VectorWrapper.ZERO;
    private VectorWrapper translation = VectorWrapper.ZERO;
    private boolean renderModel = false;

    public ModelTranslation(final VectorWrapper firstTranslation, final Quaternion quaternion) {
        this.pivotTranslation = firstTranslation;
        this.quaternion = quaternion;
    }

    public ModelTranslation(final VectorWrapper translation) {
        this.translation = translation;
    }

    public void translate(final PoseStack stack) {
        stack.translate(modelTranslation.getX() - 0.5f, modelTranslation.getY() - 0.5f,
                modelTranslation.getZ() - 0.5f);

        if (!quaternion.equals(Quaternion.ONE)) {
            stack.mulPose(quaternion);
        }
        if (!translation.equals(VectorWrapper.ZERO)) {
            stack.translate(translation.getX(), translation.getY(), translation.getZ());
        }
        stack.translate(pivotTranslation.getX(), pivotTranslation.getY(), pivotTranslation.getZ());
    }

    public Quaternion getQuaternion() {
        return quaternion;
    }

    public VectorWrapper getTranslation() {
        return translation;
    }

    public boolean shouldRenderModel() {
        return renderModel;
    }

    public ModelTranslation setRenderModel(final boolean renderModel) {
        this.renderModel = renderModel;
        return this;
    }

    public void setUpNewTranslation(final ModelTranslation other) {
        this.pivotTranslation = other.pivotTranslation;
        this.quaternion = other.quaternion;
        this.translation = other.translation;
    }

    public void setModelTranslation(final VectorWrapper translation) {
        this.modelTranslation = translation;
    }

    public void assignAnimation(final SignalAnimation animation) {
        this.animation = animation;
    }

    public void removeAnimation() {
        this.animation = null;
    }

    public boolean isAnimationAssigned() {
        return animation != null;
    }

    public SignalAnimation getAssigendAnimation() {
        return animation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pivotTranslation, quaternion, renderModel);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final ModelTranslation other = (ModelTranslation) obj;
        return Objects.equals(pivotTranslation, other.pivotTranslation)
                && Objects.equals(quaternion, other.quaternion) && renderModel == other.renderModel;
    }

}
