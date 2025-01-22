package com.troblecodings.signals.animation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Quaternion;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.contentpacks.SignalAnimationConfigParser;
import com.troblecodings.signals.core.RenderAnimationInfo;
import com.troblecodings.signals.core.SignalAngel;
import com.troblecodings.signals.models.ModelInfoWrapper;
import com.troblecodings.signals.models.SignalCustomModel;
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.IModelData;

public class SignalAnimationHandler {

    private final SignalTileEntity tile;

    public SignalAnimationHandler(final SignalTileEntity tile) {
        this.tile = tile;
    }

    private final Map<BakedModel, Entry<ModelTranslation, List<SignalAnimation>>>//
    animationPerModel = new HashMap<>();

    public void render(final RenderAnimationInfo info) {
        final BlockState state = tile.getBlockState();
        final SignalAngel angle = state.getValue(Signal.ANGEL);
        final ModelBlockRenderer renderer = info.dispatcher.getModelRenderer();
        final VertexConsumer vertex = info.source
                .getBuffer(ItemBlockRenderTypes.getRenderType(state, false));
        final IModelData data = tile.getModelData();

        animationPerModel.forEach((model, entry) -> {
            final ModelTranslation translation = entry.getKey();
            if (!translation.shouldRenderModel())
                return;

            info.stack.pushPose();
            info.stack.translate(0.5f, 0.5f, 0.5f);
            info.stack.mulPose(angle.getQuaternion());
            translation.translate(info.stack);
            renderer.renderModel(info.stack.last(), vertex, state, model, 0, 0, 0, info.lightColor,
                    info.overlayTexture, data);
            info.stack.popPose();

            if (translation.isAnimationAssigned()) {
                updateAnimation(translation);
            }
        });
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

    public void updateStates(final Map<SEProperty, String> newProperties,
            final Map<SEProperty, String> oldProperties) {
        if (oldProperties.isEmpty()) {
            updateToFinalizedAnimations(new ModelInfoWrapper(newProperties));
        } else {
            updateAnimations(new ModelInfoWrapper(newProperties));
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
            final BakedModel model = SignalCustomModel.getModelFromLocation(
                    new ResourceLocation(OpenSignalsMain.MODID, entry.getKey()));
            final ModelTranslation translation = new ModelTranslation(VectorWrapper.ZERO,
                    new Quaternion(0, 0, 0, 0));
            translation.setModelTranslation(entry.getValue().copy());
            animationPerModel.put(model, Maps.immutableEntry(translation, animations.stream()
                    .map(animation -> animation.copy()).collect(Collectors.toList())));
        });
    }

}
