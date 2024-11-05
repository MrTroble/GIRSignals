package com.troblecodings.signals.signalbridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.troblecodings.core.NBTWrapper;
import com.troblecodings.core.ReadBuffer;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.core.WriteBuffer;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.BasicBlock;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.signalbox.Point;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

public class SignalBridgeBuilder {

    public static final String SIGNALBRIDGE_BLOCKS = "signalBridgeBlocks";
    public static final String SIGNALS_ON_BRIDGE = "signalsOnBridge";
    public static final String START_POINT = "startPoint";
    public static final String CUSTOMNAME = "signalCustomName";

    private final Map<Point, SignalBridgeBasicBlock> pointForBlocks = new HashMap<>();
    private final Map<Entry<String, Signal>, VectorWrapper> vecForSignal = new HashMap<>();
    private List<Entry<VectorWrapper, BasicBlock>> relativesToStart = ImmutableList.of();
    private Point startPoint = new Point(13, 13);

    public void changeStartPoint(final Point newPoint) {
        if (newPoint.equals(startPoint))
            return;
        this.startPoint = newPoint;
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }

    public Point getStartPoint() {
        return startPoint;
    }

    public void addBlock(final Point point, final SignalBridgeBasicBlock block) {
        pointForBlocks.put(point, block);
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }

    public void removeBridgeBlock(final Point point) {
        pointForBlocks.remove(point);
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }

    public VectorWrapper addSignal(final VectorWrapper vec, final Signal signal,
            final String name) {
        final Entry<String, Signal> entry = Maps.immutableEntry(name, signal);
        if (vecForSignal.containsKey(entry))
            return vecForSignal.get(entry);
        vecForSignal.put(entry, vec);
        this.relativesToStart = calculateRelativesToPoint(startPoint);
        return vec;
    }

    public void setNewSignalPos(final Signal signal, final String name, final VectorWrapper vec) {
        final Entry<String, Signal> entry = Maps.immutableEntry(name, signal);
        vecForSignal.put(entry, vec);
    }

    public void removeSignal(final Entry<String, Signal> entry) {
        vecForSignal.remove(entry);
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }

    public SignalBridgeBasicBlock getBlockOnPoint(final Point point) {
        return pointForBlocks.get(point);
    }

    public VectorWrapper getVecForSignal(final Entry<String, Signal> entry) {
        return vecForSignal.get(entry);
    }

    public boolean hasBlockOn(final VectorWrapper vec, final Entry<String, Signal> entry) {
        final boolean isCollidingWithBlock = vec.getZ() == 0
                ? pointForBlocks.containsKey(new Point((int) vec.getX(), (int) vec.getY()))
                : false;
        final VectorWrapper signalVec = vecForSignal.get(entry);
        return isCollidingWithBlock || (!vec.equals(signalVec) && vecForSignal.containsValue(vec));
    }

    public void updateSignalName(final String oldName, final String newName, final Signal signal) {
        final Entry<String, Signal> oldEntry = Maps.immutableEntry(oldName, signal);
        final Entry<String, Signal> newEntry = Maps.immutableEntry(newName, signal);
        if (!vecForSignal.containsKey(oldEntry))
            return;
        vecForSignal.put(newEntry, vecForSignal.remove(oldEntry));
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }

    private List<Entry<VectorWrapper, BasicBlock>> calculateRelativesToPoint(
            final Point startPoint) {
        if (startPoint == null) {
            return new ArrayList<>();
        }
        final Builder<Map.Entry<VectorWrapper, BasicBlock>> builder = ImmutableList.builder();
        final VectorWrapper startVec = new VectorWrapper(startPoint.getX(), startPoint.getY(), 0);
        pointForBlocks.forEach((point, block) -> {
            final VectorWrapper vector = new VectorWrapper(point.getX(), point.getY(), 0);
            builder.add(Maps.immutableEntry(startVec.subtract(vector), block));
        });
        vecForSignal.forEach((entry, vec) -> builder
                .add(Maps.immutableEntry(startVec.subtract(vec), entry.getValue())));
        return builder.build();
    }

    public Map<Entry<String, Signal>, VectorWrapper> getAllSignalsInRelativeToStart() {
        if (startPoint == null) {
            return new HashMap<>();
        }
        final VectorWrapper startVec = new VectorWrapper(startPoint.getX(), startPoint.getY(), 0);
        final Map<Entry<String, Signal>, VectorWrapper> map = new HashMap<>();
        vecForSignal.forEach((entry, vec) -> map.put(entry, startVec.subtract(vec)));
        return map;
    }

    protected Map<Point, SignalBridgeBasicBlock> getPointsForBlocks() {
        return ImmutableMap.copyOf(pointForBlocks);
    }

    protected Map<Entry<String, Signal>, VectorWrapper> getVecsForSignals() {
        return ImmutableMap.copyOf(vecForSignal);
    }

    public List<Entry<VectorWrapper, BasicBlock>> getRelativesToStart() {
        return relativesToStart;
    }

    public Map<Entry<String, Signal>, VectorWrapper> getAllSignals() {
        return ImmutableMap.copyOf(vecForSignal);
    }

    public void write(final NBTWrapper wrapper) {
        final List<NBTWrapper> blockList = new ArrayList<>();
        pointForBlocks.forEach((point, block) -> {
            final NBTWrapper tag = new NBTWrapper();
            point.write(tag);
            tag.putString(SIGNALBRIDGE_BLOCKS, block.getRegistryName().getResourcePath());
            blockList.add(tag);
        });
        final List<NBTWrapper> signalList = new ArrayList<>();
        vecForSignal.forEach((entry, vec) -> {
            final NBTWrapper tag = new NBTWrapper();
            vec.writeNBT(tag);
            tag.putString(SIGNALS_ON_BRIDGE, entry.getValue().getRegistryName().getResourcePath());
            tag.putString(CUSTOMNAME, entry.getKey());
            signalList.add(tag);
        });
        wrapper.putList(SIGNALBRIDGE_BLOCKS, blockList);
        wrapper.putList(SIGNALS_ON_BRIDGE, signalList);
        if (startPoint != null) {
            final NBTWrapper startPointNBT = new NBTWrapper();
            startPoint.write(startPointNBT);
            wrapper.putWrapper(START_POINT, startPointNBT);
        }
    }

    public void read(final NBTWrapper wrapper) {
        pointForBlocks.clear();
        vecForSignal.clear();
        if (wrapper.isTagNull())
            return;
        wrapper.getList(SIGNALBRIDGE_BLOCKS).forEach(tag -> {
            final Point point = new Point();
            point.read(tag);
            pointForBlocks.put(point,
                    (SignalBridgeBasicBlock) Block.REGISTRY.getObject(new ResourceLocation(
                            OpenSignalsMain.MODID, tag.getString(SIGNALBRIDGE_BLOCKS))));
        });
        wrapper.getList(SIGNALS_ON_BRIDGE).forEach(tag -> {
            vecForSignal.put(
                    Maps.immutableEntry(tag.getString(CUSTOMNAME),
                            (Signal) Block.REGISTRY.getObject(new ResourceLocation(
                                    OpenSignalsMain.MODID, tag.getString(SIGNALS_ON_BRIDGE)))),
                    VectorWrapper.of(tag));
        });
        final NBTWrapper startPointWrapper = wrapper.getWrapper(START_POINT);
        if (!startPointWrapper.isTagNull()) {
            this.startPoint = new Point();
            this.startPoint.read(startPointWrapper);
        }
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }

    public void writeNetwork(final WriteBuffer buffer) {
        buffer.putByte((byte) pointForBlocks.size());
        pointForBlocks.forEach((point, block) -> {
            point.writeNetwork(buffer);
            buffer.putInt(block.getID());
        });
        buffer.putByte((byte) vecForSignal.size());
        vecForSignal.forEach((entry, vec) -> {
            buffer.putString(entry.getKey());
            buffer.putInt(entry.getValue().getID());
            vec.writeNetwork(buffer);
        });
        startPoint.writeNetwork(buffer);
    }

    public void readNetwork(final ReadBuffer buffer) {
        pointForBlocks.clear();
        vecForSignal.clear();
        final int blockSize = buffer.getByteToUnsignedInt();
        for (int i = 0; i < blockSize; i++) {
            pointForBlocks.put(Point.of(buffer),
                    SignalBridgeBasicBlock.ALL_SIGNALBRIDGE_BLOCKS.get(buffer.getInt()));
        }
        final int signalsSize = buffer.getByteToUnsignedInt();
        for (int i = 0; i < signalsSize; i++) {
            vecForSignal.put(
                    Maps.immutableEntry(buffer.getString(), Signal.SIGNAL_IDS.get(buffer.getInt())),
                    VectorWrapper.of(buffer));
        }
        this.startPoint = Point.of(buffer);
        this.relativesToStart = calculateRelativesToPoint(startPoint);
    }
}