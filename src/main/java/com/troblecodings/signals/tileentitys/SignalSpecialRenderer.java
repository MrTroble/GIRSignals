package com.troblecodings.signals.tileentitys;

import com.troblecodings.signals.core.RenderOverlayInfo;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

public class SignalSpecialRenderer extends TileEntitySpecialRenderer<SignalTileEntity> {

    @Override
    public void render(final SignalTileEntity tile, final double x, final double y, final double z,
            final float partialTicks, final int destroyStage, final float alpha) {
        if (tile.hasCustomName()) {
            tile.renderOverlay(new RenderOverlayInfo(x, y, z, getFontRenderer()));
        }
        if (tile.getSignal().hasAnimation()) {
            tile.getAnimationHandler().render();
        }
    }

    @Override
    public boolean isGlobalRenderer(final SignalTileEntity te) {
        return te.hasCustomName();
    }
}