package com.troblecodings.signals.guis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.troblecodings.guilib.ecs.GuiElements;
import com.troblecodings.guilib.ecs.entitys.UIComponent;
import com.troblecodings.guilib.ecs.entitys.UIComponentEntity;
import com.troblecodings.guilib.ecs.entitys.UIEntity;
import com.troblecodings.guilib.ecs.entitys.UIEntity.UpdateEvent;
import com.troblecodings.guilib.ecs.entitys.render.UILines;
import com.troblecodings.guilib.ecs.entitys.transform.UIIndependentTranslate;
import com.troblecodings.guilib.ecs.entitys.transform.UIRotate;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.config.ConfigHandler;
import com.troblecodings.signals.core.TrainNumber;
import com.troblecodings.signals.enums.EnumGuiMode;
import com.troblecodings.signals.signalbox.MainSignalIdentifier;
import com.troblecodings.signals.signalbox.MainSignalIdentifier.SignalState;
import com.troblecodings.signals.signalbox.ModeSet;
import com.troblecodings.signals.signalbox.Point;
import com.troblecodings.signals.signalbox.SignalBoxNode;
import com.troblecodings.signals.signalbox.entrys.PathEntryType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public class UISignalBoxTile extends UIComponentEntity {

    public static final ResourceLocation ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/symbols.png");
    public static final ResourceLocation ARROW_ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/arrow.png");
    public static final ResourceLocation INCOMING_ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/connection_in.png");
    public static final ResourceLocation OUTGOING_ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/connection_out.png");
    public static final ResourceLocation SIGNALS = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/signals.png");
    public static final ResourceLocation NE1_ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/ne1.png");
    public static final ResourceLocation NE5_ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/ne5.png");
    public static final ResourceLocation ZS3_ICON = new ResourceLocation(OpenSignalsMain.MODID,
            "gui/textures/zs3.png");

    private SignalBoxNode node;
    private final Map<ModeSet, UIEntity> setToEntity = new HashMap<>();
    private final Map<ModeSet, SignalState> greenSignals = new HashMap<>();

    public UISignalBoxTile(final SignalBoxNode node) {
        super(new UIEntity());
        this.node = node;
        if (this.node != null) {
            this.node.forEach(this::localAdd);
        }
    }

    public void setGreenSignals(final List<MainSignalIdentifier> list) {
        greenSignals.clear();
        list.forEach(identifier -> {
            greenSignals.put(identifier.getModeSet(), identifier.state);
            updateModeSet(identifier.getModeSet());
        });
    }

    public void updateModeSet(final ModeSet mode) {
        localRemove(mode);
        localAdd(mode);
        if (hasParent()) {
            update();
        }
    }

    public void setNode(final SignalBoxNode node) {
        if (this.node != null) {
            this.node.forEach(this::localRemove);
        }
        this.node = node;
        if (this.node != null) {
            this.node.forEach(this::localAdd);
        }
    }

    private void localAdd(final ModeSet modeSet) {
        final UIEntity entity = new UIEntity();
        if (!modeSet.rotation.equals(Rotation.NONE)) {
            final UIRotate rotation = new UIRotate();
            rotation.setRotateZ(modeSet.rotation.ordinal() * ((float) Math.PI / 2.0f));
            entity.add(rotation);
        }
        entity.add(new UIIndependentTranslate(0, 0, modeSet.mode.translation + 1));

        SignalState state = greenSignals.getOrDefault(modeSet, SignalState.RED);
        if (modeSet.mode.equals(EnumGuiMode.RS)) {
            final ModeSet hpMode = new ModeSet(EnumGuiMode.HP, modeSet.rotation);
            SignalState hpState = greenSignals.getOrDefault(hpMode, SignalState.RED);
            if (setToEntity.containsKey(hpMode)) {
                if (hpState.equals(SignalState.RED)) {
                    switch (state) {
                        case SUBSIDIARY_RED: {
                            hpState = SignalState.SUBSIDIARY_RED;
                            break;
                        }
                        case GREEN:
                        case SUBSIDIARY_OFF: {
                            hpState = SignalState.SUBSIDIARY_OFF;
                            break;
                        }
                        default: {
                            hpState = SignalState.RED;
                            break;
                        }
                    }
                }
                greenSignals.put(hpMode, hpState);
                localRemove(modeSet);
                updateModeSet(hpMode);
                return;
            }
        }

        if (modeSet.mode.equals(EnumGuiMode.TRAIN_NUMBER)) {
            final TrainNumber number = node.getOption(modeSet).get()
                    .getEntry(PathEntryType.TRAINNUMBER).orElse(TrainNumber.DEFAULT);
            if (!number.equals(TrainNumber.DEFAULT)) {
                final UIEntity label = GuiElements.createLabel(number.trainNumber,
                        ConfigHandler.CLIENT.signalboxTrainNumberColor.get(), 0.5f);
                label.setX(6);
                label.setY(3);
                entity.add(label);
            }
        }

        entity.add((UIComponent) modeSet.mode.consumer.get(state));
        this.entity.add(entity);
        setToEntity.put(modeSet, entity);
        this.entity.setVisible(!setToEntity.isEmpty());
    }

    @Override
    public void update() {
        super.update();
        this.entity.setX(parent.getWidth() / 2.0);
        this.entity.setY(parent.getHeight() / 2.0);
        setToEntity.values().forEach(e -> {
            e.setHeight(entity.getHeight());
            e.setWidth(entity.getWidth());
            e.update();
        });
        this.entity.findRecursive(UIIndependentTranslate.class).forEach(translate -> {
            translate.setX(-this.entity.getX());
            translate.setY(-this.entity.getY());
        });
    }

    @Override
    public void updateEvent(final UpdateEvent event) {
        super.updateEvent(event);
        this.update();
    }

    private void localRemove(final ModeSet modeSet) {
        this.entity.remove(setToEntity.remove(modeSet));
        this.entity.setVisible(!setToEntity.isEmpty());
    }

    public void add(final ModeSet modeSet) {
        this.node.add(modeSet);
        localAdd(modeSet);
        this.update();
    }

    public boolean has(final ModeSet modeSet) {
        return this.node.has(modeSet);
    }

    public void remove(final ModeSet modeSet) {
        this.node.remove(modeSet);
        this.localRemove(modeSet);
    }

    public boolean isValidStart() {
        return this.node.isValidStart();
    }

    public boolean isValidEnd() {
        return this.node.isValidEnd();
    }

    public Point getPoint() {
        return this.node.getPoint();
    }

    public SignalBoxNode getNode() {
        return this.node;
    }

    public void setColor(final ModeSet mode, final int color) {
        final UIEntity entity = setToEntity.get(mode);
        if (entity != null) {
            entity.findRecursive(UILines.class).forEach(lines -> lines.setColor(color));
        }
    }
}