package com.troblecodings.signals.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;

public class RenderAnimationInfo {

    public final PoseStack stack;
    public SignalTileEntity tileEntity;
    public final BlockRenderDispatcher dispatcher;
    public final MultiBufferSource source;
    public final int lightColor;
    public final int overlayTexture;

    public RenderAnimationInfo(final PoseStack stack, final BlockRenderDispatcher dispatcher,
            final MultiBufferSource source, final int lightColor, final int overlayTexture) {
        this.stack = stack;
        this.dispatcher = dispatcher;
        this.source = source;
        this.lightColor = lightColor;
        this.overlayTexture = overlayTexture;
    }

    public RenderAnimationInfo with(final SignalTileEntity tileEntity) {
        this.tileEntity = tileEntity;
        return this;
    }

}
