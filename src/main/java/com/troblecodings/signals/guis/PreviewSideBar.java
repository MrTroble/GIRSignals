package com.troblecodings.signals.guis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import com.troblecodings.core.QuaternionWrapper;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.guilib.ecs.entitys.UIBlockRender;
import com.troblecodings.guilib.ecs.entitys.UIBlockRenderInfo;
import com.troblecodings.guilib.ecs.entitys.UIEntity;
import com.troblecodings.guilib.ecs.entitys.UIMultiBlockRender;
import com.troblecodings.guilib.ecs.entitys.input.UIDrag;
import com.troblecodings.guilib.ecs.entitys.render.UIColor;
import com.troblecodings.guilib.ecs.entitys.render.UIScissor;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.animation.ModelTranslation;
import com.troblecodings.signals.animation.SignalAnimation;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.contentpacks.SignalAnimationConfigParser;
import com.troblecodings.signals.enums.ChangeableStage;
import com.troblecodings.signals.models.ModelInfoWrapper;
import com.troblecodings.signals.models.SignalCustomModel;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;

public class PreviewSideBar {
    public static final float MODIFIER = 0.1f;

    private UIBlockRender blockRender;
    private final Map<SEProperty, String> properties = new HashMap<>();
    private final UIEntity blockRenderEntity = new UIEntity();
    private final float height;
    private final List<UIBlockRenderInfo> animationInfos = new ArrayList<>();

    public PreviewSideBar(final float height) {
        this.height = height;
        blockRender = new UIBlockRender(20, height);
        blockRenderEntity.setInheritHeight(true);
        blockRenderEntity.setWidth(60);

        blockRenderEntity.add(new UIDrag((x, y) -> blockRender
                .updateRotation(QuaternionWrapper.fromXYZ(0, (float) x * MODIFIER, 0))));

        blockRenderEntity.add(new UIScissor());
        blockRenderEntity.add(new UIColor(0xFF8B8B8B));
        blockRenderEntity.add(blockRender);
    }

    public UIEntity get() {
        return blockRenderEntity;
    }

    public void addToRenderList(final SEProperty property, final int valueId) {
        if (valueId < 0) {
            properties.remove(property);
            return;
        }
        if (property.isChangabelAtStage(ChangeableStage.GUISTAGE)) {
            properties.put(property, property.getObjFromID(valueId));
        } else if (property.isChangabelAtStage(ChangeableStage.APISTAGE)) {
            if (valueId > 0) {
                properties.put(property, property.getDefault());
            } else {
                properties.remove(property);
            }
        } else if (property.isChangabelAtStage(ChangeableStage.APISTAGE_NONE_CONFIG)) {
            properties.put(property, property.getDefault());
        }
    }

    public void addToRenderNormal(final SEProperty property, final int valueId) {
        if (valueId < 0) {
            properties.put(property, property.getDefault());
            return;
        }
        properties.put(property, property.getObjFromID(valueId));
    }

    public void clear() {
        properties.clear();
    }

    private void buildRenderListForAnimations(final Signal signal, final ModelInfoWrapper wrapper) {
        animationInfos.clear();
        final Map<Entry<String, VectorWrapper>, List<SignalAnimation>> //
        map = SignalAnimationConfigParser.ALL_ANIMATIONS.get(signal);
        map.forEach((entry, list) -> {
            for (final SignalAnimation animation : list) {
                final Predicate<ModelInfoWrapper> predicate = animation.getPredicate();
                final ModelTranslation modelTranslation = animation.getFinalModelTranslation();
                modelTranslation.setModelTranslation(entry.getValue().copy());

                final IBakedModel model = SignalCustomModel.getModelFromLocation(
                        new ResourceLocation(OpenSignalsMain.MODID, entry.getKey()));

                final UIBlockRenderInfo info = new UIBlockRenderInfo(model,
                        signal.getDefaultState(), wrapper, new VectorWrapper(0.5f, 0.5f, 0.5f));
                info.predicate = p -> predicate.test(wrapper);
                info.consumer = d -> {
                    modelTranslation.translate();
                };
                animationInfos.add(info);
            }
        });
    }

    public void update(final Signal signal) {
        final ModelInfoWrapper wrapper = new ModelInfoWrapper(signal, properties);
        blockRenderEntity.remove(blockRender);
        if (signal.hasAnimation()) {
            buildRenderListForAnimations(signal, wrapper);
            blockRender = new UIMultiBlockRender(20, height);
            animationInfos.forEach(blockRender::setBlockState);
        } else {
            blockRender = new UIBlockRender(20, height);
        }
        blockRender.setBlockState(new UIBlockRenderInfo(signal.getDefaultState(), wrapper));
        blockRenderEntity.add(blockRender);
    }
}
