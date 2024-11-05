package com.troblecodings.signals.animation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Quaternion;

import com.google.common.collect.Maps;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.contentpacks.SignalAnimationConfigParser;
import com.troblecodings.signals.core.SignalAngel;
import com.troblecodings.signals.models.ModelInfoWrapper;
import com.troblecodings.signals.models.SignalCustomModel;
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.pipeline.LightUtil;

public class SignalAnimationHandler {

    private final SignalTileEntity tile;

    public SignalAnimationHandler(final SignalTileEntity tile) {
        this.tile = tile;
    }

    private final Map<Entry<IBakedModel, BufferBuilder>, Entry<ModelTranslation, List<SignalAnimation>>>//
    animationPerModel = new HashMap<>();

    public void render(final VectorWrapper vector) {
        final World world = tile.getWorld();
        final BlockPos pos = tile.getPos();
        final IBlockState state = world.getBlockState(pos);
        final SignalAngel angle = state.getValue(Signal.ANGEL);

        animationPerModel.forEach((first, entry) -> {
            final ModelTranslation translation = entry.getKey();
            if (!translation.shouldRenderModel())
                return;
            GlStateManager.enableAlpha();
            GlStateManager.pushMatrix();
            GlStateManager.translate(vector.getX(), vector.getY(), vector.getZ());
            translation.translate();
            GlStateManager.rotate(angle.getDregree(), 0, 1, 0);
            drawBuffer(first.getValue());
            GlStateManager.popMatrix();

            if (translation.isAnimationAssigned()) {
                updateAnimation(translation);
            }
        });
    }

    public void drawBuffer(final BufferBuilder buffer) {
        if (buffer.getVertexCount() > 0) {
            final VertexFormat vertexformat = buffer.getVertexFormat();
            final int i = vertexformat.getNextOffset();
            final ByteBuffer bytebuffer = buffer.getByteBuffer();
            final List<VertexFormatElement> list = vertexformat.getElements();

            for (int j = 0; j < list.size(); ++j) {
                final VertexFormatElement vertexformatelement = list.get(j);
                bytebuffer.position(vertexformat.getOffset(j));
                vertexformatelement.getUsage().preDraw(vertexformat, j, i, bytebuffer);
            }

            GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
            int i1 = 0;

            for (final int j1 = list.size(); i1 < j1; ++i1) {
                final VertexFormatElement vertexformatelement1 = list.get(i1);
                vertexformatelement1.getUsage().postDraw(vertexformat, i1, i, bytebuffer);
            }
        }
    }

    private void updateAnimation(final ModelTranslation translation) {
        final SignalAnimation animation = translation.getAssigendAnimation();
        if (animation.isFinished()) {
            translation.setUpNewTranslation(animation.getFinalModelTranslation());
            translation.removeAnimation();
            animation.reset();
            return;
        }
        animation.updateAnimation();
        translation.setUpNewTranslation(animation.getModelTranslation());
    }

    public void updateStates(final Map<SEProperty, String> properties, final boolean firstLoad) {
        final ModelInfoWrapper wrapper = new ModelInfoWrapper(tile.getBlockType(), properties);
        if (firstLoad) {
            updateToFinalizedAnimations(wrapper);
        } else {
            updateAnimations(wrapper);
        }
    }

    private void updateAnimations(final ModelInfoWrapper wrapper) {
        animationPerModel.values().forEach(entry -> {
            entry.getKey().setRenderModel(false);
            for (final SignalAnimation animation : entry.getValue()) {
                if (animation.test(wrapper)) {
                    final ModelTranslation translation = entry.getKey();
                    translation.setRenderModel(true);
                    if (translation.isAnimationAssigned()) {
                        final SignalAnimation other = translation.getAssigendAnimation();
                        other.reset();
                    }
                    animation.setUpAnimationValues(translation);
                    translation.setUpNewTranslation(animation.getModelTranslation());
                    translation.assignAnimation(animation);
                }
            }
        });
    }

    private void updateToFinalizedAnimations(final ModelInfoWrapper wrapper) {
        animationPerModel.values().forEach((entry) -> {
            for (final SignalAnimation animation : entry.getValue()) {
                if (animation.test(wrapper)) {
                    final ModelTranslation translation = entry.getKey();
                    translation.setUpNewTranslation(animation.getFinalModelTranslation());
                    translation.setRenderModel(true);
                }
            }
        });
    }

    public void updateAnimationListFromBlock() {
        animationPerModel.clear();
        final Map<Entry<String, VectorWrapper>, List<SignalAnimation>> map = //
                SignalAnimationConfigParser.ALL_ANIMATIONS.get(tile.getSignal());
        map.forEach((entry, animations) -> {
            final IBakedModel model = SignalCustomModel.getModelFromLocation(
                    new ResourceLocation(OpenSignalsMain.MODID, entry.getKey()));
            final ModelTranslation translation = new ModelTranslation(VectorWrapper.ZERO,
                    new Quaternion(0, 0, 0, 0));
            translation.setModelTranslation(entry.getValue().copy());
            final BufferBuilder buffer = getBufferFromModel(model, entry.getValue().copy());
            animationPerModel.put(Maps.immutableEntry(model, buffer),
                    Maps.immutableEntry(translation, animations.stream()
                            .map(animation -> animation.copy()).collect(Collectors.toList())));
        });
    }

    private BufferBuilder getBufferFromModel(final IBakedModel model, final VectorWrapper vec) {
        final BufferBuilder buffer = new BufferBuilder(500);
        final IBlockState ebs = tile.getWorld().getBlockState(tile.getPos());
        assert ebs != null;
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(vec.getX(), vec.getY(), vec.getZ());
        final List<BakedQuad> lst = new ArrayList<>();
        lst.addAll(model.getQuads(ebs, null, 0));
        for (final EnumFacing face : EnumFacing.VALUES)
            lst.addAll(model.getQuads(ebs, face, 0));

        final BlockColors blockColors = Minecraft.getMinecraft().getBlockColors();
        for (final BakedQuad quad : lst) {
            final int k = quad.hasTintIndex()
                    ? (blockColors.colorMultiplier(tile.getBlockType().getDefaultState(), null,
                            null, quad.getTintIndex()) + 0xFF000000)
                    : 0xFFFFFFFF;
            LightUtil.renderQuadColor(buffer, quad, k);
        }
        buffer.finishDrawing();
        return buffer;
    }

}