package com.troblecodings.signals.tileentitys;

import com.troblecodings.signals.core.RenderAnimationInfo;
import com.troblecodings.signals.core.RenderOverlayInfo;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

public class SignalSpecialRenderer extends TileEntitySpecialRenderer<SignalTileEntity> {

    @Override
    public void render(final SignalTileEntity tile, final double x, final double y, final double z,
            final float partialTicks, final int destroyStage, final float alpha) {
        if (tile.hasCustomName()) {
            tile.renderOverlay(new RenderOverlayInfo(x, y, z, getFontRenderer()));
        }
        if (tile.getSignal().hasAnimation()) {
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            setLightmapDisabled(true);
            tile.getAnimationHandler().render(new RenderAnimationInfo(x, y, z).with(tile));
        }
    }

    @Override
    public boolean isGlobalRenderer(final SignalTileEntity te) {
        return te.hasCustomName() || te.hasAnimation();
    }
}