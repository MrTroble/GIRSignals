package com.troblecodings.signals.core;

import org.lwjgl.util.vector.Quaternion;

import com.troblecodings.signals.tileentitys.SignalTileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class RenderAnimationInfo {

    public final double x;
    public final double y;
    public final double z;
    public SignalTileEntity tileEntity;

    public RenderAnimationInfo(final double x, final double y, final double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public RenderAnimationInfo with(final SignalTileEntity tileEntity) {
        this.tileEntity = tileEntity;
        return this;
    }

    public void push() {
        GlStateManager.pushMatrix();
    }

    public void pop() {
        GlStateManager.popMatrix();
    }

    public void translate(final double x, final double y, final double z) {
        GlStateManager.translate(x, y, z);
    }

    public void scale(final double x, final double y, final double z) {
        GlStateManager.scale(x, y, z);
    }

    public void rotate(final Quaternion quaternion) {
        GlStateManager.rotate(quaternion);
    }

    public void rotate(final float angle, final float x, final float y, final float z) {
        GlStateManager.rotate(angle, x, y, z);
    }

    public void applyTexture(final ResourceLocation location) {
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
    }

    public void depthOn() {
        GlStateManager.enableDepth();
    }

    public void depthOff() {
        GlStateManager.disableDepth();
    }

    public void alphaOn() {
        GlStateManager.enableAlpha();
    }

    public void alphaOff() {
        GlStateManager.disableAlpha();
    }

}
