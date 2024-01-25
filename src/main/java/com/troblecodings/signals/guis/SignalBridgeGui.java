package com.troblecodings.signals.guis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.google.common.collect.Maps;
import com.mojang.math.Quaternion;
import com.troblecodings.core.I18Wrapper;
import com.troblecodings.core.WriteBuffer;
import com.troblecodings.guilib.ecs.DrawUtil.NamedEnumIntegerable;
import com.troblecodings.guilib.ecs.DrawUtil.SizeIntegerables;
import com.troblecodings.guilib.ecs.GuiBase;
import com.troblecodings.guilib.ecs.GuiElements;
import com.troblecodings.guilib.ecs.GuiInfo;
import com.troblecodings.guilib.ecs.entitys.UIBlockRender;
import com.troblecodings.guilib.ecs.entitys.UIBlockRenderInfo;
import com.troblecodings.guilib.ecs.entitys.UIBox;
import com.troblecodings.guilib.ecs.entitys.UIEntity;
import com.troblecodings.guilib.ecs.entitys.UIMultiBlockRender;
import com.troblecodings.guilib.ecs.entitys.UIScrollBox;
import com.troblecodings.guilib.ecs.entitys.UITextInput;
import com.troblecodings.guilib.ecs.entitys.input.UIClickable;
import com.troblecodings.guilib.ecs.entitys.input.UIDrag;
import com.troblecodings.guilib.ecs.entitys.input.UIScroll;
import com.troblecodings.guilib.ecs.entitys.render.UIBorder;
import com.troblecodings.guilib.ecs.entitys.render.UIButton;
import com.troblecodings.guilib.ecs.entitys.render.UIColor;
import com.troblecodings.guilib.ecs.entitys.render.UILabel;
import com.troblecodings.guilib.ecs.entitys.render.UIScissor;
import com.troblecodings.guilib.ecs.entitys.render.UIToolTip;
import com.troblecodings.guilib.ecs.entitys.transform.UIScale;
import com.troblecodings.guilib.ecs.interfaces.IIntegerable;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.BasicBlock;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.contentpacks.SignalBridgeBlockParser;
import com.troblecodings.signals.core.JsonEnum;
import com.troblecodings.signals.enums.ChangeableStage;
import com.troblecodings.signals.enums.SignalBridgeNetwork;
import com.troblecodings.signals.enums.SignalBridgeType;
import com.troblecodings.signals.models.ModelInfoWrapper;
import com.troblecodings.signals.signalbox.Point;
import com.troblecodings.signals.signalbridge.SignalBridgeBasicBlock;
import com.troblecodings.signals.signalbridge.SignalBridgeBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;

public class SignalBridgeGui extends GuiBase {

    private static final List<Signal> SIGNALS_FOR_BRIDGE = new ArrayList<>();
    private static final UIBorder SELECTED_BORDER = new UIBorder(0xFF00FF00, 1);
    private static final int TILE_WIDTH = 20;
    private static final int TILE_COUNT = 10;

    private final UIEntity leftEntity = new UIEntity();
    private final UIEntity middleEntity = new UIEntity();
    private final UIEntity rightEntity = new UIEntity();
    private final Map<SignalBridgeBasicBlock, UIEntity> blockForEntity = new HashMap<>();
    private final Map<String, UIEntity> signalNameForEntity = new HashMap<>();
    private final Map<String, Map<SEProperty, String>> nameForRenderProperties = new HashMap<>();
    private final SignalBridgeContainer container;
    private final Player player;
    private final PreviewSideBar previewSidebar = new PreviewSideBar(-5);
    private final UIEntity signalPropertiesList = new UIEntity();
    private SignalBridgeBasicBlock currentBlock;
    private String currentSignal;
    private boolean loaded = false;

    static {
        Signal.SIGNAL_IDS.stream().filter(signal -> signal.isForSignalBridge())
                .forEach(SIGNALS_FOR_BRIDGE::add);
    }

    public SignalBridgeGui(final GuiInfo info) {
        super(info);
        this.container = (SignalBridgeContainer) info.base;
        this.player = info.player;
    }

    private void initInternal() {
        container.builder.setFunctionForModelData(name -> new ModelInfoWrapper(
                nameForRenderProperties.getOrDefault(name, new HashMap<>())));
        this.entity.add(new UIBox(UIBox.VBOX, 5));
        final UIEntity lowerEntity = new UIEntity();
        lowerEntity.setInherits(true);
        lowerEntity.add(new UIBox(UIBox.HBOX, 15));
        lowerEntity.add(leftEntity);
        lowerEntity.add(middleEntity);
        lowerEntity.add(rightEntity);
        final UIEntity header = new UIEntity();
        header.setInheritWidth(true);
        header.setHeight(20);
        header.add(new UIBox(UIBox.HBOX, 5));
        header.add(GuiElements.createLabel(I18Wrapper.format("gui.signalbridge.title"),
                header.getBasicTextColor(), 1.1f));
        header.add(GuiElements.createSpacerH(10));
        final UIEntity editButton = GuiElements
                .createButton(I18Wrapper.format("gui.signalbridge.edit"), e -> {
                    updateAvailableBridgeParts(SignalBridgeType.BASE);
                    buildGrid();
                    resetSelection(e);
                });
        editButton.add(new UIToolTip(I18Wrapper.format("gui.signalbridge.edit.desc")));
        header.add(editButton);
        resetSelection(editButton);
        final UIEntity preview = GuiElements
                .createButton(I18Wrapper.format("gui.signalbride.preview"), e -> {
                    buildBridgePreview();
                    buildBridgeList();
                    resetSelection(e);
                });
        preview.add(new UIToolTip(I18Wrapper.format("gui.signalbride.preview.desc")));
        header.add(preview);
        header.add(GuiElements.createButton("?", 20, e -> {
            final UIEntity screen = GuiElements.createScreen(screenEntity -> {
                screenEntity.add(GuiElements.createButton(I18Wrapper.format("gui.return"), _u -> {
                    pop();
                    enableMultiRenderer();
                }));
                screenEntity.add(GuiElements.createSpacerV(50));
                screenEntity.add(GuiElements.createLabel(I18Wrapper.format("gui.signalbridge.info"),
                        screenEntity.getBasicTextColor(), 1.1f));
            });
            disableMultiRenderer();
            push(screen);
        }));
        entity.add(header);
        entity.add(lowerEntity);
        updateAvailableBridgeParts(SignalBridgeType.BASE);
        buildGrid();
    }

    private void updateAvailableBridgeParts(final SignalBridgeType type) {
        currentBlock = null;
        leftEntity.clear();
        leftEntity.setInheritHeight(true);
        leftEntity.setWidth(80);
        leftEntity.add(new UIBox(UIBox.VBOX, 1));

        final IIntegerable<SignalBridgeType> integerable = new NamedEnumIntegerable<>(
                I18Wrapper.format("gui.bridge.type"), SignalBridgeType.class);
        leftEntity.add(GuiElements.createEnumElement(integerable,
                i -> updateAvailableBridgeParts(integerable.getObjFromID(i)), type.ordinal()));

        final UIEntity scroll = new UIEntity();
        scroll.setInherits(true);
        scroll.add(new UIBox(UIBox.HBOX, 1));
        scroll.add(new UIScissor());
        leftEntity.add(scroll);
        final UIEntity list = new UIEntity();
        scroll.add(list);
        list.setInherits(true);
        list.add(new UIBox(UIBox.VBOX, 1));

        final List<SignalBridgeBasicBlock> typeBlocks = SignalBridgeBlockParser.SIGNAL_BRIDGE_BLOCKS
                .getOrDefault(type, new ArrayList<>());
        typeBlocks.forEach(block -> {
            final UIEntity blockEntity = createPreviewForBlock(block, 14, -2, 1.9f, 80, 60, true, 0,
                    0, true, SignalBridgeBuilder.EMPTY_WRAPPER);
            blockEntity.add(new UIClickable(e -> {
                if (currentBlock != null)
                    removeUISelection(currentBlock);
                if (currentBlock == block) {
                    currentBlock = null;
                    return;
                }
                addUISelection(block);
                currentBlock = block;
            }));
            list.add(blockEntity);
            blockForEntity.put(block, blockEntity);
        });

        final UIScrollBox scrollBox = new UIScrollBox(UIBox.VBOX, 2);
        final UIScroll scrolling = new UIScroll();
        final UIEntity scrollBar = GuiElements.createScrollBar(scrollBox, 7, scrolling);
        scrollBox.setConsumer(size -> {
            if (size > list.getHeight()) {
                scroll.add(scrolling);
                scroll.add(scrollBar);
            } else {
                scroll.remove(scrollBar);
                scroll.remove(scrolling);
            }
        });
        list.add(scrollBox);
        entity.update();
    }

    private void buildGrid() {
        rightEntity.clear();
        rightEntity.setWidth(0);
        middleEntity.clear();
        middleEntity.setHeight(200);
        middleEntity.setWidth(200);
        middleEntity.add(new UIBox(UIBox.HBOX, 1));
        final UIEntity plane = new UIEntity();
        plane.setHeight(TILE_COUNT * TILE_WIDTH);
        plane.setWidth(TILE_COUNT * TILE_WIDTH);
        plane.add(new UIBorder(GuiSignalBox.GRID_COLOR, 2));
        plane.add(new UIColor(GuiSignalBox.BACKGROUND_COLOR));
        plane.add(new UIBox(UIBox.VBOX, 0));
        for (int x = 0; x < TILE_COUNT; x++) {
            final UIEntity row = new UIEntity();
            final UIBox hbox = new UIBox(UIBox.HBOX, 0);
            hbox.setPageable(false);
            row.add(hbox);
            row.setHeight(TILE_WIDTH);
            row.setWidth(TILE_WIDTH);
            for (int y = 0; y < TILE_COUNT; y++) {
                final Point point = new Point(y, x);
                final UIEntity tile = new UIEntity();
                tile.setHeight(TILE_WIDTH);
                tile.setWidth(TILE_WIDTH);
                tile.add(new UIBorder(GuiSignalBox.GRID_COLOR, 0.5f));
                row.add(tile);
                final SignalBridgeBasicBlock savedBlock = container.builder.getBlockOnPoint(point);
                if (savedBlock != null) {
                    final UIEntity blockEntity = createPreviewForBlock(savedBlock, 15, -1, 1,
                            TILE_WIDTH, TILE_WIDTH, false, -12.5f, 3, false,
                            SignalBridgeBuilder.EMPTY_WRAPPER);
                    tile.add(blockEntity);
                }
                if (point.equals(container.builder.getStartPoint())) {
                    tile.add(new UIBorder(0xFF0000FF, 2));
                }
                tile.add(new UIClickable(e -> {
                    if (currentBlock == null) {
                        return;
                    }
                    final SignalBridgeBasicBlock block = container.builder.getBlockOnPoint(point);
                    final UIEntity blockEntity = createPreviewForBlock(currentBlock, 15, -1, 1,
                            TILE_WIDTH, TILE_WIDTH, false, -12.5f, 3, false,
                            SignalBridgeBuilder.EMPTY_WRAPPER);
                    if (block == null) {
                        tile.add(blockEntity);
                        container.builder.addBlock(point, currentBlock);
                        sendNewBlock(point, currentBlock);
                    } else if (block == currentBlock) {
                        tile.clearChildren();
                        container.builder.removeBridgeBlock(point);
                        sendRemoveBlock(point);
                    }
                    tile.update();
                }));
                tile.add(new UIClickable(e -> {
                    if (Screen.hasControlDown()) {
                        container.builder.changeStartPoint(point);
                        sendNewStartPoint(point);
                        buildGrid();
                    }
                }, 1));
            }
            plane.add(row);
        }
        middleEntity.add(plane);
        middleEntity.getParent().update();
    }

    private UIEntity renderEntity = new UIEntity();
    private UIMultiBlockRender multiRenderer = new UIMultiBlockRender(20, -10);

    private void buildBridgePreview() {
        middleEntity.clear();
        middleEntity.setWidth(200);
        middleEntity.setHeight(200);
        final UIEntity entity = new UIEntity();
        entity.setHeight(200);
        entity.setWidth(200);
        entity.add(new UIBorder(GuiSignalBox.GRID_COLOR, 2));
        entity.add(new UIColor(GuiSignalBox.BACKGROUND_COLOR));
        renderEntity = new UIEntity();
        renderEntity.setX(140);
        renderEntity.setY(-17);
        multiRenderer = new UIMultiBlockRender(20, -10);
        final List<UIBlockRenderInfo> list = container.builder.getRenderPosAndBlocks();
        if (list.isEmpty()) {
            middleEntity.add(new UILabel(I18Wrapper.format("gui.signalbridge.nostartblock")));
            return;
        }
        updateMultiRenderer(list);
        renderEntity.add(multiRenderer);
        entity.add(renderEntity);
        entity.add(new UIDrag((x, y) -> multiRenderer
                .updateRotation(Quaternion.fromXYZ(0, (float) x * 0.1f, 0))));
        entity.add(new UIScroll(s -> {
            final float newScale = (float) (renderEntity.getScaleX() + s * 0.05f);
            if (newScale <= 0)
                return;
            renderEntity.setScaleX(newScale);
            renderEntity.setScaleY(newScale);
            renderEntity.update();
        }));
        middleEntity.add(entity);
        entity.update();
    }

    private void updateMultiRenderer(List<UIBlockRenderInfo> list) {
        multiRenderer.clear();
        list.forEach(info -> multiRenderer.setBlockState(info));
    }

    private void buildBridgeList() {
        leftEntity.clear();
        leftEntity.setInheritHeight(true);
        leftEntity.setWidth(80);
        leftEntity.add(new UIBox(UIBox.VBOX, 1));

        final UIEntity scroll = new UIEntity();
        scroll.setInherits(true);
        scroll.add(new UIBox(UIBox.HBOX, 1));
        scroll.add(new UIScissor());
        leftEntity.add(scroll);
        final UIEntity list = new UIEntity();
        scroll.add(list);
        list.setInherits(true);
        final UIBox vbox = new UIBox(UIBox.VBOX, 1);
        vbox.setPageable(false);
        list.add(vbox);
        final IIntegerable<Signal> availableSignals = SizeIntegerables.of(
                I18Wrapper.format("gui.signalbridge.signals"), SIGNALS_FOR_BRIDGE.size(),
                i -> SIGNALS_FOR_BRIDGE.get(i));
        final UIEntity addButton = GuiElements.createButton("+", e -> {
            disableMultiRenderer();
            push(GuiElements.createScreen(searchPanel -> {
                final UIEntity searchBar = new UIEntity();
                searchBar.setInheritWidth(true);
                searchBar.setHeight(20);
                final UITextInput input = new UITextInput("");
                searchBar.add(input);
                searchPanel.add(searchBar);

                final UIEntity listWithScroll = new UIEntity();
                listWithScroll.setInherits(true);
                listWithScroll.add(new UIBox(UIBox.HBOX, 2));
                listWithScroll.add(new UIScissor());
                listWithScroll.add(new UIBorder(0xFF00FFFF));
                searchPanel.add(listWithScroll);

                final UIEntity signalList = new UIEntity();
                listWithScroll.add(signalList);
                signalList.setInherits(true);

                final UIScrollBox scrollbox = new UIScrollBox(UIBox.VBOX, 2);
                signalList.add(scrollbox);
                final Map<String, UIEntity> nameToUIEntity = new HashMap<>();
                for (int i = 0; i < availableSignals.count(); i++) {
                    final int index = i;
                    final String name = availableSignals.getNamedObj(i);
                    final UIEntity button = GuiElements.createButton(name, e2 -> {
                        final UIEntity nameEntity = new UIEntity();
                        nameEntity.setInherits(true);
                        nameEntity.add(new UIBox(UIBox.VBOX, 5));
                        nameEntity.add(createSpacerLine(
                                GuiElements.createButton(I18Wrapper.format("btn.return"), e1 -> {
                                    pop();
                                    enableMultiRenderer();
                                })));
                        nameEntity.add(GuiElements.createSpacerV(10));
                        nameEntity.add(
                                GuiElements.createLabel(I18Wrapper.format("gui.signalbridge.name"),
                                        nameEntity.getBasicTextColor(), 1.2f));

                        final UIEntity infoEntity = new UIEntity();
                        infoEntity.setInheritWidth(true);
                        infoEntity.setHeight(20);

                        final UITextInput textInput = new UITextInput("");
                        textInput.setOnTextUpdate(s -> infoEntity.clear());
                        nameEntity.add(createSpacerWithTextInput(textInput));

                        nameEntity.add(createSpacerLine(GuiElements
                                .createButton(I18Wrapper.format("gui.signalbridge.create"), e1 -> {
                                    final String signalName = textInput.getText();
                                    if (signalName.isBlank()
                                            || container.allSignals.containsKey(signalName)) {
                                        infoEntity.add(getCenterdXLabel(
                                                I18Wrapper.format("gui.signalbridge.namenotvalid"),
                                                infoEntity.getErrorTextColor()));
                                        return;
                                    }
                                    pop();
                                    final Signal signal = availableSignals.getObjFromID(index);
                                    container.allSignals.put(signalName,
                                            Maps.immutableEntry(signal, new HashMap<>()));
                                    buildSignalPropertiesSelection(signal, signalName);
                                    sendCreateSignal(signalName, signal);
                                    final Map<SEProperty, Integer> properties = new HashMap<>();
                                    fillRenderPropertiesUp(signal, properties);
                                    updateRenderProperties(name, properties);
                                })));
                        nameEntity.add(GuiElements.createSpacerV(7));
                        nameEntity.add(infoEntity);
                        pop();
                        push(GuiElements.createScreen(screen -> screen.add(nameEntity)));
                    });
                    nameToUIEntity.put(name.toLowerCase(), button);
                    signalList.add(button);
                }
                final UIScroll signalScroll = new UIScroll();
                final UIEntity scrollBar = GuiElements.createScrollBar(scrollbox, 10, signalScroll);
                scrollbox.setConsumer(size -> {
                    if (size > list.getHeight()) {
                        listWithScroll.add(signalScroll);
                        listWithScroll.add(scrollBar);
                    } else {
                        listWithScroll.remove(scrollBar);
                        listWithScroll.remove(signalScroll);
                    }
                });
                input.setOnTextUpdate(string -> {
                    nameToUIEntity.forEach((name, entity) -> {
                        if (!name.contains(string.toLowerCase())) {
                            signalList.remove(entity);
                        } else {
                            signalList.add(entity);
                        }
                    });
                });
            }));
        });
        addButton.add(new UIToolTip(I18Wrapper.format("gui.signalbridge.plusbutton.desc")));
        list.add(addButton);
        container.allSignals.forEach((name, entry) -> {
            final UIEntity blockEntity = createPreviewForBlock(entry.getKey(), 14, -3.5f, 1.9f, 80,
                    100, true, 0, 0, true, name, new ModelInfoWrapper(
                            nameForRenderProperties.getOrDefault(name, new HashMap<>())),
                    100);
            blockEntity.add(new UIClickable(e -> {
                addUISelection(name);
                currentSignal = name;
                addButtonsToEditSignal(list, blockEntity, name);
            }));
            list.add(blockEntity);
            signalNameForEntity.put(name, blockEntity);
        });

        final UIScrollBox scrollBox = new UIScrollBox(UIBox.VBOX, 2);
        final UIScroll scrolling = new UIScroll();
        final UIEntity scrollBar = GuiElements.createScrollBar(scrollBox, 7, scrolling);
        scrollBox.setConsumer(size -> {
            if (size > list.getHeight()) {
                scroll.add(scrolling);
                scroll.add(scrollBar);
            } else {
                scroll.remove(scrollBar);
                scroll.remove(scrolling);
            }
        });
        list.add(scrollBox);
        entity.update();
    }

    private void addButtonsToEditSignal(final UIEntity list, final UIEntity blockEntity,
            final String name) {
        final Signal signal = container.allSignals.get(name).getKey();
        final Consumer<UIEntity> consumer = e -> {
            removeUISelection(currentSignal);
            currentSignal = null;
            pop();
            enableMultiRenderer();
        };
        push(GuiElements.createScreen(screen -> {
            disableMultiRenderer();
            final UIEntity inner = new UIEntity();
            inner.setInherits(true);
            inner.add(new UIBox(UIBox.VBOX, 5));
            inner.add(GuiElements.createSpacerV(15));
            inner.add(createSpacerLine(
                    GuiElements.createButton(I18Wrapper.format("btn.return"), consumer)));
            inner.add(createSpacerLine(GuiElements
                    .createButton(I18Wrapper.format("btn.gui.editsignal"), consumer.andThen(e -> {
                        disableMultiRenderer();
                        buildSignalPropertiesSelection(signal, name);
                    }))));
            inner.add(createSpacerLine(GuiElements.createButton(I18Wrapper.format("btn.remove"),
                    consumer.andThen(e -> {
                        list.remove(blockEntity);
                        sendSignalRemovedFromList(name);
                        sendRemoveSignal(signal, name);
                        container.allSignals.remove(name);
                        container.builder.removeSignal(Maps.immutableEntry(name, signal));
                        updateMultiRenderer(container.builder.getRenderPosAndBlocks());
                    }))));
            inner.add(createSpacerLine(GuiElements.createButton(
                    I18Wrapper.format("btn.signalbridge.editonplane"), consumer.andThen(e -> {
                        final Point startPoint = container.builder.getStartPoint();
                        final Vec3i vec = container.builder.addSignal(
                                new Vec3i(startPoint.getX(), startPoint.getY(), 1), signal, name);
                        sendSignalPos(vec, signal, name);
                        updateMultiRenderer(container.builder.getRenderPosAndBlocks());
                        buildSystemToAddSignal(name, signal, vec);
                    }))));
            inner.add(createSpacerLine(GuiElements.createButton(
                    I18Wrapper.format("btn.signalbridge.rename"), consumer.andThen(e -> {
                        disableMultiRenderer();
                        final UIEntity nameEntity = new UIEntity();
                        nameEntity.setInherits(true);
                        nameEntity.add(new UIBox(UIBox.VBOX, 5));
                        nameEntity.add(createSpacerLine(
                                GuiElements.createButton(I18Wrapper.format("btn.return"), e1 -> {
                                    pop();
                                    enableMultiRenderer();
                                })));
                        nameEntity.add(GuiElements.createSpacerV(10));
                        nameEntity.add(GuiElements.createLabel(
                                I18Wrapper.format("gui.signalbridge.rename"),
                                nameEntity.getBasicTextColor(), 1.2f));

                        final UIEntity infoEntity = new UIEntity();
                        infoEntity.setInherits(true);

                        final UITextInput textInput = new UITextInput("");
                        textInput.setOnTextUpdate(s -> infoEntity.clear());
                        nameEntity.add(createSpacerWithTextInput(textInput));

                        nameEntity.add(createSpacerLine(GuiElements
                                .createButton(I18Wrapper.format("gui.signalbridge.rename"), e1 -> {
                                    final String signalName = textInput.getText();
                                    if (signalName.isBlank()
                                            || container.allSignals.containsKey(signalName)) {
                                        infoEntity.add(getCenterdXLabel(
                                                I18Wrapper.format("gui.signalbridge.namenotvalid"),
                                                infoEntity.getErrorTextColor()));
                                        return;
                                    }
                                    sendNameChange(name, signalName);
                                    container.allSignals.put(signalName,
                                            container.allSignals.remove(name));
                                    container.builder.updateSignalName(name, signalName, signal);
                                    pop();
                                    buildBridgeList();
                                    enableMultiRenderer();
                                })));
                        nameEntity.add(GuiElements.createSpacerV(7));
                        nameEntity.add(infoEntity);
                        push(GuiElements
                                .createScreen(screenEntity -> screenEntity.add(nameEntity)));
                    }))));
            screen.add(inner);
        }));
    }

    private final Map<String, UIEntity> nameForButton = new HashMap<>();

    private void buildSystemToAddSignal(final String name, final Signal signal,
            final Vec3i startVec) {
        nameForButton.clear();
        addUISelection(name);
        final Entry<String, Signal> entry = Maps.immutableEntry(name, signal);
        rightEntity.clear();
        rightEntity.setInheritHeight(true);
        rightEntity.setWidth(30);
        rightEntity.setX(100);
        rightEntity.add(new UIBox(UIBox.VBOX, 5));
        rightEntity.add(GuiElements.createSpacerV(5));
        rightEntity.add(GuiElements.createButton(I18Wrapper.format("btn.return"), e -> {
            removeUISelection(name);
            rightEntity.clear();
            rightEntity.setWidth(0);
            rightEntity.getParent().update();
        }));
        for (final Axis axis : Direction.Axis.values()) {
            for (final AxisDirection axisDirection : Direction.AxisDirection.values()) {
                final String buttonName = axis.getName()
                        + (axisDirection == AxisDirection.POSITIVE ? "+" : "-");
                final UIEntity button = GuiElements.createButton(buttonName, e -> {
                    Vec3i vector = container.builder.getVecForSignal(entry);
                    final int step = axisDirection == AxisDirection.POSITIVE ? -1 : 1;
                    switch (axis) {
                        case X: {
                            vector = new Vec3i(vector.getX() + step, vector.getY(), vector.getZ());
                            break;
                        }
                        case Y: {
                            vector = new Vec3i(vector.getX(), vector.getY() + step, vector.getZ());
                            break;
                        }
                        case Z: {
                            vector = new Vec3i(vector.getX(), vector.getY(), vector.getZ() + step);
                        }
                        default:
                            break;
                    }
                    container.builder.setNewSignalPos(signal, name, vector);
                    updateMultiRenderer(container.builder.getRenderPosAndBlocks());
                    sendSignalPos(vector, signal, name);
                    checkMaxAndMins(vector);
                });
                nameForButton.put(buttonName, button);
                rightEntity.add(button);
            }
        }
        checkMaxAndMins(startVec);
        this.entity.update();
    }

    private void checkMaxAndMins(final Vec3i vector) {
        for (final Axis axis : Axis.values()) {
            for (final AxisDirection axisDirection : AxisDirection.values()) {
                final String buttonName = axis.getName()
                        + (axisDirection == AxisDirection.POSITIVE ? "+" : "-");
                final UIEntity button = nameForButton.get(buttonName);
                switch (axis) {
                    case X: {
                        checkEnableAndDisable(axisDirection.opposite(), 0, 9, vector.getX(),
                                button);
                        break;
                    }
                    case Y: {
                        checkEnableAndDisable(axisDirection.opposite(), 0, 9, vector.getY(),
                                button);
                        break;
                    }
                    case Z: {
                        checkEnableAndDisable(axisDirection.opposite(), -5, 5, vector.getZ(),
                                button);
                        break;
                    }
                    default:
                        break;
                }
            }
        }
    }

    private static void checkEnableAndDisable(final AxisDirection axisDirection, final int min,
            final int max, final int value, final UIEntity button) {
        if (value >= max && axisDirection == AxisDirection.POSITIVE) {
            disableSelection(button);
        } else if (value <= min && axisDirection == AxisDirection.NEGATIVE) {
            disableSelection(button);
        } else {
            enableSelection(button);
        }
    }

    private void buildSignalPropertiesSelection(final Signal signal, final String name) {
        final UIBox vbox = new UIBox(UIBox.VBOX, 5);
        signalPropertiesList.clear();
        signalPropertiesList.add(vbox);
        signalPropertiesList.setInherits(true);

        final UIEntity lowerEntity = new UIEntity();
        lowerEntity.add(GuiElements.createSpacerH(10));

        final UIEntity leftSide = new UIEntity();
        leftSide.setInherits(true);
        leftSide.add(new UIBox(UIBox.VBOX, 5));

        leftSide.add(GuiElements.createButton(I18Wrapper.format("btn.save"), 50, e -> {
            pop();
            enableMultiRenderer();
            buildBridgeList();
        }));
        leftSide.add(signalPropertiesList);
        leftSide.add(GuiElements.createPageSelect(vbox));

        lowerEntity.add(new UIBox(UIBox.HBOX, 5));

        lowerEntity.add(leftSide);
        lowerEntity.add(previewSidebar.get());
        lowerEntity.setInherits(true);

        final Map.Entry<Signal, Map<SEProperty, Integer>> entry = container.allSignals.get(name);
        signal.getProperties().forEach(property -> {
            final int value = entry.getValue().containsKey(property)
                    ? entry.getValue().get(property)
                    : property.getParent().getIDFromValue(property.getDefault());
            of(property, inp -> applyPropertyChanges(name, property, inp), value);
        });
        previewSidebar.update(signal);
        final UIEntity screenEntity = GuiElements.createScreen(screen -> screen.add(lowerEntity));
        disableMultiRenderer();
        push(screenEntity);
    }

    private void of(final SEProperty property, final IntConsumer consumer, final int value) {
        if (property == null)
            return;
        previewSidebar.addToRenderList(property, value);
        if (property.isChangabelAtStage(ChangeableStage.GUISTAGE)) {
            if (property.getParent().equals(JsonEnum.BOOLEAN)) {
                signalPropertiesList.add(GuiElements.createBoolElement(property, consumer, value));
                return;
            }
            signalPropertiesList.add(GuiElements.createEnumElement(property, consumer, value));
        } else if (property.isChangabelAtStage(ChangeableStage.APISTAGE)) {
            signalPropertiesList.add(GuiElements.createBoolElement(property, consumer, value));
        }
    }

    private static UIEntity createPreviewForBlock(final BasicBlock block, final float renderScale,
            final float renderHeight, final float previewScale, final float width,
            final float height, final boolean showName, final double previewX,
            final double previewY, final boolean enableRotation, final ModelInfoWrapper modelInfo) {
        return createPreviewForBlock(block, renderScale, renderHeight, previewScale, width, height,
                showName, previewX, previewY, enableRotation, "", modelInfo, 60);
    }

    private static UIEntity createPreviewForBlock(final BasicBlock block, final float renderScale,
            final float renderHeight, final float previewScale, final float width,
            final float height, final boolean showName, final double previewX,
            final double previewY, final boolean enableRotation, final String customName,
            final ModelInfoWrapper modelInfo, final float previewHeight) {
        final UIEntity blockEntity = new UIEntity();
        blockEntity.setWidth(width);
        blockEntity.setHeight(height);
        blockEntity.add(new UIColor(GuiSignalBox.BACKGROUND_COLOR));
        if (showName) {
            final UILabel label = new UILabel(customName.isEmpty()
                    ? I18Wrapper.format("block." + block.delegate.name().getPath() + ".name")
                    : customName);
            label.setCenterY(false);
            label.setTextColor(blockEntity.getBasicTextColor());
            blockEntity.add(label);
        }
        final UIEntity preview = new UIEntity();
        final UIBlockRender renderer = new UIBlockRender(renderScale, renderHeight);
        renderer.setBlockState(new UIBlockRenderInfo(block.defaultBlockState(), modelInfo));
        preview.setWidth(60);
        preview.setHeight(previewHeight);
        preview.setX(previewX);
        preview.setY(previewY);
        preview.add(new UIScale(previewScale, previewScale, previewScale));

        if (enableRotation)
            preview.add(new UIDrag(
                    (x, y) -> renderer.updateRotation(Quaternion.fromXYZ(0, (float) x * 0.1f, 0)),
                    1));

        preview.add(new UIScissor());
        preview.add(renderer);

        blockEntity.add(preview);
        return blockEntity;
    }

    private static void resetSelection(final UIEntity entity) {
        final UIEntity parent = entity.getParent();
        enableSelection(parent);
        disableSelection(entity);
    }

    private static void disableSelection(final UIEntity entity) {
        entity.findRecursive(UIButton.class).forEach(btn -> btn.setEnabled(false));
        entity.findRecursive(UIClickable.class).forEach(click -> click.setVisible(false));
    }

    private static void enableSelection(final UIEntity entity) {
        entity.findRecursive(UIButton.class).forEach(btn -> btn.setEnabled(true));
        entity.findRecursive(UIClickable.class).forEach(click -> click.setVisible(true));
    }

    private void removeUISelection(final SignalBridgeBasicBlock block) {
        final UIEntity blockEntity = blockForEntity.get(block);
        blockEntity.remove(SELECTED_BORDER);
    }

    private void addUISelection(final SignalBridgeBasicBlock block) {
        final UIEntity blockEntity = blockForEntity.get(block);
        blockEntity.add(SELECTED_BORDER);
    }

    private void removeUISelection(final String signalName) {
        final UIEntity blockEntity = signalNameForEntity.get(signalName);
        blockEntity.remove(SELECTED_BORDER);
    }

    private void addUISelection(final String signalName) {
        final UIEntity blockEntity = signalNameForEntity.get(signalName);
        blockEntity.add(SELECTED_BORDER);
    }

    private void disableMultiRenderer() {
        renderEntity.remove(multiRenderer);
    }

    private void enableMultiRenderer() {
        renderEntity.add(multiRenderer);
        updateMultiRenderer(container.builder.getRenderPosAndBlocks());
    }

    private void applyPropertyChanges(final String signalName, final SEProperty property,
            final int valueId) {
        if (!loaded)
            return;
        final Map.Entry<Signal, Map<SEProperty, Integer>> entry = container.allSignals
                .get(signalName);
        final Signal signal = entry.getKey();
        final int propertyId = signal.getIDFromProperty(property);
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.SEND_PROPERTY);
        buffer.putString(signalName);
        buffer.putByte((byte) propertyId);
        buffer.putByte((byte) valueId);
        OpenSignalsMain.network.sendTo(player, buffer);

        final Map<SEProperty, Integer> properties = entry.getValue();
        previewSidebar.addToRenderList(property, valueId);
        previewSidebar.update(signal);
        properties.put(property, valueId);
        updateRenderProperties(signalName, properties);
    }

    private void updateRenderProperties(final String name,
            final Map<SEProperty, Integer> properties) {
        final Map<SEProperty, String> renderProperties = nameForRenderProperties
                .computeIfAbsent(name, _u -> new HashMap<>());
        properties.forEach(
                (property, valueID) -> addToRenderNormal(renderProperties, property, valueID));
    }

    private static void addToRenderNormal(final Map<SEProperty, String> properties,
            final SEProperty property, final int valueID) {
        if (valueID < 0) {
            properties.remove(property);
            return;
        }
        if (property.isChangabelAtStage(ChangeableStage.GUISTAGE)) {
            properties.put(property, property.getObjFromID(valueID));
        } else if (property.isChangabelAtStage(ChangeableStage.APISTAGE)) {
            if (valueID > 0) {
                properties.put(property, property.getDefault());
            } else {
                properties.remove(property);
            }
        } else if (property.isChangabelAtStage(ChangeableStage.APISTAGE_NONE_CONFIG)) {
            properties.put(property, property.getDefault());
        }
    }

    private static UIEntity createSpacerLine(final UIEntity middle) {
        final UIEntity line = new UIEntity();
        line.setInheritWidth(true);
        line.setHeight(20);
        line.add(new UIBox(UIBox.HBOX, 5));
        line.add(GuiElements.createSpacerH(25));
        line.add(middle);
        line.add(GuiElements.createSpacerH(25));
        return line;
    }

    private static UIEntity createSpacerWithTextInput(final UITextInput input) {
        final UIEntity inputEntity = new UIEntity();
        inputEntity.setInheritWidth(true);
        inputEntity.setHeight(20);
        inputEntity.add(input);
        return createSpacerLine(inputEntity);
    }

    private static UIEntity getCenterdXLabel(final String name, final int color) {
        final UIEntity labelEntity = new UIEntity();
        final UILabel label = new UILabel(name);
        label.setCenterY(false);
        label.setCenterY(false);
        label.setTextColor(color);
        labelEntity.setHeight(20);
        labelEntity.setInheritWidth(true);
        labelEntity.add(label);
        labelEntity.setX(150);
        labelEntity.setScaleY(1.2f);
        labelEntity.setScaleX(1.2f);
        return labelEntity;
    }

    private void sendNewBlock(final Point point, final SignalBridgeBasicBlock block) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.SET_BLOCK);
        point.writeNetwork(buffer);
        buffer.putInt(block.getID());
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendRemoveBlock(final Point point) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.REMOVE_BLOCK);
        point.writeNetwork(buffer);
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendNewStartPoint(final Point point) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.SEND_START_POINT);
        point.writeNetwork(buffer);
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendCreateSignal(final String name, final Signal signal) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.SEND_CREATE_SIGNAL);
        buffer.putString(name);
        buffer.putInt(signal.getID());
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendSignalRemovedFromList(final String name) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.REMOVE_SIGNAL_FROM_LIST);
        buffer.putString(name);
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendNameChange(final String previousName, final String newName) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.CHANGE_NAME);
        buffer.putString(previousName);
        buffer.putString(newName);
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendSignalPos(final Vec3i vec, final Signal signal, final String name) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.SET_SIGNAL);
        buffer.putInt(vec.getX());
        buffer.putInt(vec.getY());
        buffer.putInt(vec.getZ());
        buffer.putInt(signal.getID());
        buffer.putString(name);
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    private void sendRemoveSignal(final Signal signal, final String name) {
        if (!loaded)
            return;
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBridgeNetwork.REMOVE_SIGNAL);
        buffer.putInt(signal.getID());
        buffer.putString(name);
        OpenSignalsMain.network.sendTo(player, buffer);
    }

    @Override
    public void updateFromContainer() {
        initInternal();
        prepareRenderData();
        loaded = true;
    }

    private void prepareRenderData() {
        container.allSignals.forEach((name, entry) -> {
            final Map<SEProperty, Integer> properties = new HashMap<>(entry.getValue());
            fillRenderPropertiesUp(entry.getKey(), properties);
            updateRenderProperties(name, properties);
        });
    }

    private void fillRenderPropertiesUp(final Signal signal,
            final Map<SEProperty, Integer> properties) {
        signal.getProperties().forEach(property -> {
            if (!properties.containsKey(property))
                properties.put(property,
                        property.getParent().getIDFromValue(property.getDefault()));
        });
    }
}