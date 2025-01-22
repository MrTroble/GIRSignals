package com.troblecodings.signals.tileentitys;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.RenderAnimationInfo;
import com.troblecodings.signals.core.RenderOverlayInfo;
import com.troblecodings.signals.core.SignalAngel;
import com.troblecodings.signals.models.SignalCustomModel;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class SignalSpecialRenderer implements BlockEntityRenderer<SignalTileEntity> {

    private final BlockEntityRendererProvider.Context context;

    public SignalSpecialRenderer(final BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(final SignalTileEntity tile, final float tick, final PoseStack stack,
            final MultiBufferSource source, final int rand1, final int rand2) {
        if (tile.hasCustomName()) {
            tile.renderOverlay(new RenderOverlayInfo(stack, 0, 0, 0, context.getFont()));
        }
        if (tile.getSignal().hasAnimation()) {
            tile.getAnimationHandler().render(new RenderAnimationInfo(stack,
                    context.getBlockRenderDispatcher(), source, rand1, rand2).with(tile));
        }
    }

    // @SuppressWarnings("deprecation")
    public void renderAnimation(final RenderAnimationInfo info, final SignalTileEntity tile) {

        final BlockState state = tile.getBlockState();
        final SignalAngel angle = state.getValue(Signal.ANGEL);

        info.stack.pushPose(); // erst Berechnungen ausführen, dann Block rendern

        info.stack.translate(0.5f, 0.5f, 0.5f);

        info.stack.mulPose(angle.getQuaternion());
        info.stack.translate(0.15f, 7f, -0.5f); // Block verschieben

        info.stack.mulPose(Quaternion.fromXYZ(0, 0, -tile.animProgress * 0.005f));
        tile.updateAnim(); // Progress aktualisieren

        info.stack.translate(-0.7f, -4.5f, 0f); // Pivot Punkt verschieben

        // info.dispatcher.renderSingleBlock(Blocks.GLASS.defaultBlockState(),
        // info.stack, info.source,
        // info.lightColor, info.overlayTexture); // Block render
        final BakedModel model = SignalCustomModel.getModelFromLocation(
                new ResourceLocation(OpenSignalsMain.MODID, "semaphore_signals/sema_main_wing1"));
        info.dispatcher.getModelRenderer().renderModel(info.stack.last(),
                info.source.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)), state,
                model, 0, 0, 0, info.lightColor, info.overlayTexture, tile.getModelData());

        info.stack.popPose();

        if (tile.animProgress > 158) {
            tile.animProgress = 0;
        }
    }
}