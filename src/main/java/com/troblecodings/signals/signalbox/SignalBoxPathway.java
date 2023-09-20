package com.troblecodings.signals.signalbox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Maps;
import com.troblecodings.core.NBTWrapper;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.RedstoneIO;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.JsonEnumHolder;
import com.troblecodings.signals.core.PosIdentifier;
import com.troblecodings.signals.enums.EnumGuiMode;
import com.troblecodings.signals.enums.EnumPathUsage;
import com.troblecodings.signals.enums.PathType;
import com.troblecodings.signals.handler.SignalBoxHandler;
import com.troblecodings.signals.handler.SignalStateInfo;
import com.troblecodings.signals.signalbox.config.ConfigInfo;
import com.troblecodings.signals.signalbox.config.SignalConfig;
import com.troblecodings.signals.signalbox.entrys.PathEntryType;
import com.troblecodings.signals.signalbox.entrys.PathOptionEntry;

import net.minecraft.block.BlockState;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SignalBoxPathway {

    private final Map<BlockPos, SignalBoxNode> mapOfResetPositions = new HashMap<>();
    private final Map<BlockPos, SignalBoxNode> mapOfBlockingPositions = new HashMap<>();
    private ImmutableList<SignalBoxNode> listOfNodes = ImmutableList.of();
    private PathType type = PathType.NONE;
    private Point firstPoint = new Point();
    private Point lastPoint = new Point();
    private int speed = -1;
    private String zs2Value = "";
    private Optional<Entry<BlockPos, BlockPos>> signalPositions = Optional.empty();
    private Optional<BlockPos> lastSignal = Optional.empty();
    private ImmutableList<BlockPos> distantSignalPositions = ImmutableList.of();
    private Map<Point, SignalBoxNode> modeGrid = null;
    private boolean emptyOrBroken = false;
    private World world;
    private BlockPos tilePos;
    private boolean isBlocked = false;
    private boolean isAutoPathway = false;
    private Point originalFirstPoint = null;

    public SignalBoxPathway(final Map<Point, SignalBoxNode> modeGrid) {
        this.modeGrid = modeGrid;
    }

    public void setWorldAndPos(final World world, final BlockPos tilePos) {
        this.world = world;
        this.tilePos = tilePos;
    }

    public SignalBoxPathway(final Map<Point, SignalBoxNode> modeGrid,
            final List<SignalBoxNode> pNodes, final PathType type) {
        this(modeGrid);
        this.listOfNodes = ImmutableList.copyOf(pNodes);
        this.type = Objects.requireNonNull(type);
        if (listOfNodes.size() < 2)
            throw new IndexOutOfBoundsException();
        if (this.type.equals(PathType.NONE))
            throw new IllegalArgumentException();
        initalize();
        this.originalFirstPoint = new Point(firstPoint);
        updatePathwayToAutomatic();
    }

    private void initalize() {
        final AtomicInteger atomic = new AtomicInteger(Integer.MAX_VALUE);
        final AtomicReference<Byte> zs2Value = new AtomicReference<>((byte) -1);
        final Builder<BlockPos> distantPosBuilder = ImmutableList.builder();
        mapOfBlockingPositions.clear();
        mapOfResetPositions.clear();
        foreachEntry((optionEntry, node) -> {
            optionEntry.getEntry(PathEntryType.SPEED)
                    .ifPresent(value -> atomic.updateAndGet(in -> Math.min(in, value)));
            optionEntry.getEntry(PathEntryType.BLOCKING)
                    .ifPresent(position -> mapOfBlockingPositions.put(position, node));
            optionEntry.getEntry(PathEntryType.RESETING)
                    .ifPresent(position -> mapOfResetPositions.put(position, node));
            optionEntry.getEntry(PathEntryType.ZS2).ifPresent(value -> zs2Value.set(value));
        });
        foreachPath((path, node) -> {
            final Rotation rotation = SignalBoxUtil
                    .getRotationFromDelta(node.getPoint().delta(path.point1));
            for (final EnumGuiMode mode : Arrays.asList(EnumGuiMode.VP, EnumGuiMode.RS)) {
                node.getOption(new ModeSet(mode, rotation))
                        .ifPresent(option -> option.getEntry(PathEntryType.SIGNAL)
                                .ifPresent(position -> distantPosBuilder.add(position)));
            }
        }, null);
        this.distantSignalPositions = distantPosBuilder.build();
        final SignalBoxNode firstNode = this.listOfNodes.get(this.listOfNodes.size() - 1);
        this.firstPoint = firstNode.getPoint();
        final BlockPos firstPos = makeFromNext(type, firstNode,
                this.listOfNodes.get(this.listOfNodes.size() - 2), Rotation.NONE);
        final SignalBoxNode lastNode = this.listOfNodes.get(0);
        this.lastPoint = lastNode.getPoint();
        final BlockPos lastPos = makeFromNext(type, lastNode, this.listOfNodes.get(1),
                Rotation.CLOCKWISE_180);
        if (lastPos != null) {
            lastSignal = Optional.of(lastPos);
        }
        if (firstPos != null) {
            this.signalPositions = Optional.of(Maps.immutableEntry(firstPos, lastPos));
        } else {
            this.signalPositions = Optional.empty();
        }
        this.speed = atomic.get();
        this.zs2Value = JsonEnumHolder.ZS32.getObjFromID(Byte.toUnsignedInt(zs2Value.get()));
    }

    private BlockPos makeFromNext(final PathType type, final SignalBoxNode first,
            final SignalBoxNode next, final Rotation pRotation) {
        final Point delta = first.getPoint().delta(next.getPoint());
        final Rotation rotation = SignalBoxUtil.getRotationFromDelta(delta).getRotated(pRotation);
        for (final EnumGuiMode mode : type.getModes()) {
            final BlockPos possiblePosition = first.getOption(new ModeSet(mode, rotation))
                    .flatMap(option -> option.getEntry(PathEntryType.SIGNAL)).orElse(null);
            if (possiblePosition != null)
                return possiblePosition;
        }
        return null;
    }

    private static final String LIST_OF_NODES = "listOfNodes";
    private static final String PATH_TYPE = "pathType";
    private static final String IS_BLOCKED = "isBlocked";
    private static final String ORIGINAL_FIRST_POINT = "origianlFirstPoint";

    public void write(final NBTWrapper tag) {
        tag.putList(LIST_OF_NODES, listOfNodes.stream().map(node -> {
            final NBTWrapper entry = new NBTWrapper();
            node.getPoint().write(entry);
            return entry;
        })::iterator);
        tag.putString(PATH_TYPE, this.type.name());
        tag.putBoolean(IS_BLOCKED, isBlocked);
        if (originalFirstPoint != null) {
            final NBTWrapper originalFirstPoint = new NBTWrapper();
            this.originalFirstPoint.write(originalFirstPoint);
            tag.putWrapper(ORIGINAL_FIRST_POINT, originalFirstPoint);
        }
    }

    public void read(final NBTWrapper tag) {
        final Builder<SignalBoxNode> nodeBuilder = ImmutableList.builder();
        tag.getList(LIST_OF_NODES).forEach(nodeNBT -> {
            final Point point = new Point();
            point.read(nodeNBT);
            final SignalBoxNode node = modeGrid.get(point);
            if (node == null) {
                OpenSignalsMain.getLogger().error("Detecting broken pathway at {}!",
                        point.toString());
                this.emptyOrBroken = true;
                return;
            }
            nodeBuilder.add(node);
        });
        this.listOfNodes = nodeBuilder.build();
        this.type = PathType.valueOf(tag.getString(PATH_TYPE));
        this.isBlocked = tag.getBoolean(IS_BLOCKED);
        if (this.listOfNodes.size() < 2) {
            OpenSignalsMain.getLogger().error("Detecting pathway with only 2 elements!");
            this.emptyOrBroken = true;
            return;
        }
        this.initalize();
        updatePathwayToAutomatic();
        final NBTWrapper originalFirstPoint = tag.getWrapper(ORIGINAL_FIRST_POINT);
        if (!originalFirstPoint.isTagNull()) {
            this.originalFirstPoint = new Point();
            this.originalFirstPoint.read(originalFirstPoint);
        }
    }

    private void foreachEntry(final Consumer<PathOptionEntry> consumer,
            final @Nullable Point point) {
        foreachEntry((entry, _u) -> consumer.accept(entry), point);
    }

    private void foreachEntry(final BiConsumer<PathOptionEntry, SignalBoxNode> consumer) {
        foreachEntry(consumer, null);
    }

    private void foreachPath(final BiConsumer<Path, SignalBoxNode> consumer,
            final @Nullable Point point) {
        for (int i = listOfNodes.size() - 2; i > 0; i--) {
            final Point oldPos = listOfNodes.get(i - 1).getPoint();
            final Point newPos = listOfNodes.get(i + 1).getPoint();
            final SignalBoxNode current = listOfNodes.get(i);
            consumer.accept(new Path(oldPos, newPos), current);
            if (current.getPoint().equals(point))
                break;
        }

    }

    private void foreachEntry(final BiConsumer<PathOptionEntry, SignalBoxNode> consumer,
            final @Nullable Point point) {
        foreachPath((path, current) -> current.getOption(path)
                .ifPresent(entry -> consumer.accept(entry, current)), point);
    }

    public void setPathStatus(final EnumPathUsage status, final @Nullable Point point) {
        foreachEntry(option -> {
            option.getEntry(PathEntryType.OUTPUT).ifPresent(
                    pos -> SignalBoxHandler.updateRedstoneOutput(new PosIdentifier(pos, world),
                            !status.equals(EnumPathUsage.FREE)));
            option.setEntry(PathEntryType.PATHUSAGE, status);
        }, point);
    }

    public void setPathStatus(final EnumPathUsage status) {
        setPathStatus(status, null);
    }

    public void updatePathwaySignals() {
        if (world == null)
            return;
        final PosIdentifier identifier = new PosIdentifier(tilePos, world);
        SignalStateInfo lastInfo = null;
        if (lastSignal.isPresent()) {
            final Signal nextSignal = SignalBoxHandler.getSignal(identifier, lastSignal.get());
            if (nextSignal != null)
                lastInfo = new SignalStateInfo(world, lastSignal.get(), nextSignal);
        }
        final SignalStateInfo lastSignalInfo = lastInfo;
        this.signalPositions.ifPresent(entry -> {
            if (isBlocked)
                return;
            final Signal first = SignalBoxHandler.getSignal(identifier, entry.getKey());
            if (first == null)
                return;
            final SignalStateInfo firstInfo = new SignalStateInfo(world, entry.getKey(), first);
            SignalConfig.change(new ConfigInfo(firstInfo, lastSignalInfo, speed, zs2Value, type));
        });
        distantSignalPositions.forEach(position -> {
            final Signal current = SignalBoxHandler.getSignal(identifier, position);
            if (current == null)
                return;
            SignalConfig.change(new ConfigInfo(new SignalStateInfo(world, position, current),
                    lastSignalInfo, speed, zs2Value, type));
        });
    }

    public void resetPathway() {
        resetPathway(null);
    }

    private void resetFirstSignal() {
        this.signalPositions.ifPresent(entry -> {
            final Signal current = SignalBoxHandler.getSignal(new PosIdentifier(tilePos, world),
                    entry.getKey());
            if (current == null)
                return;
            SignalConfig.reset(new SignalStateInfo(world, entry.getKey(), current));
        });
    }

    private void resetOther() {
        distantSignalPositions.forEach(position -> {
            final Signal current = SignalBoxHandler.getSignal(new PosIdentifier(tilePos, world),
                    position);
            if (current == null)
                return;
            SignalConfig.reset(new SignalStateInfo(world, position, current));
        });
    }

    public void resetPathway(final @Nullable Point point) {
        this.setPathStatus(EnumPathUsage.FREE, point);
        resetFirstSignal();
        if (point == null || point.equals(this.getLastPoint())
                || point.equals(this.listOfNodes.get(1).getPoint())) {
            this.emptyOrBroken = true;
            this.isBlocked = false;
            resetOther();
        }
    }

    public void compact(final Point point) {
        foreachPath((path, node) -> {
            final Rotation rotation = SignalBoxUtil
                    .getRotationFromDelta(node.getPoint().delta(path.point1));
            for (final EnumGuiMode mode : Arrays.asList(EnumGuiMode.VP, EnumGuiMode.RS)) {
                node.getOption(new ModeSet(mode, rotation)).ifPresent(
                        option -> option.getEntry(PathEntryType.SIGNAL).ifPresent(position -> {
                            final Signal current = SignalBoxHandler
                                    .getSignal(new PosIdentifier(tilePos, world), position);
                            if (current == null)
                                return;
                            SignalConfig.reset(new SignalStateInfo(world, position, current));
                        }));
            }
        }, point);
        this.listOfNodes = ImmutableList.copyOf(this.listOfNodes.subList(0,
                this.listOfNodes.indexOf(this.modeGrid.get(point)) + 1));
        this.initalize();
    }

    public Optional<Point> tryReset(final BlockPos position) {
        final SignalBoxNode node = this.mapOfResetPositions.get(position);
        if (node == null) {
            if (checkReverseReset(position)) {
                return Optional.of(firstPoint);
            } else {
                return Optional.empty();
            }
        }
        final Point point = node.getPoint();
        final AtomicBoolean atomic = new AtomicBoolean(false);
        foreachEntry((option, cNode) -> {
            option.getEntry(PathEntryType.BLOCKING).ifPresent(pos -> {
                if (isPowerd(pos))
                    atomic.set(true);
            });
        }, point);
        if (atomic.get())
            return Optional.empty();
        this.resetPathway(point);
        return Optional.of(point);
    }

    private boolean checkReverseReset(final BlockPos pos) {
        final SignalBoxNode firstNode = listOfNodes.get(listOfNodes.size() - 1);
        for (final Rotation rot : Rotation.values()) {
            if (tryReversReset(pos, firstNode, rot)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryReversReset(final BlockPos pos, final SignalBoxNode node,
            final Rotation rot) {
        final AtomicBoolean canReset = new AtomicBoolean(false);
        for (final EnumGuiMode mode : Arrays.asList(EnumGuiMode.CORNER, EnumGuiMode.STRAIGHT)) {
            node.getOption(new ModeSet(mode, rot)).ifPresent(
                    entry -> entry.getEntry(PathEntryType.RESETING).ifPresent(blockPos -> {
                        if (!blockPos.equals(pos))
                            return;
                        final AtomicBoolean atomic = new AtomicBoolean(false);
                        foreachEntry((option, cNode) -> {
                            option.getEntry(PathEntryType.BLOCKING).ifPresent(blockingPos -> {
                                if (isPowerd(blockingPos)) {
                                    atomic.set(true);
                                }
                            });
                        });
                        if (atomic.get())
                            return;
                        canReset.set(true);
                        this.resetPathway();
                    }));
        }
        return canReset.get();
    }

    private boolean isPowerd(final BlockPos pos) {
        final BlockState state = world.getBlockState(pos);
        if (state == null || !(state.getBlock() instanceof RedstoneIO))
            return false;
        return state.getValue(RedstoneIO.POWER);
    }

    public boolean tryBlock(final BlockPos position) {
        if (!mapOfBlockingPositions.containsKey(position))
            return false;
        resetFirstSignal();
        this.setPathStatus(EnumPathUsage.BLOCKED);
        isBlocked = true;
        return true;
    }

    public void deactivateAllOutputsOnPathway() {
        foreachPath((_u, node) -> {
            final List<BlockPos> outputs = node.clearAllManuellOutputs();
            outputs.forEach(pos -> SignalBoxHandler
                    .updateRedstoneOutput(new PosIdentifier(pos, world), false));
        }, null);
    }

    public void updatePathwayToAutomatic() {
        final SignalBoxNode first = modeGrid.get(originalFirstPoint);
        if (first == null) {
            isAutoPathway = false;
            return;
        }
        this.isAutoPathway = first.isAutoPoint();
    }

    public void checkReRequest() {
        if (isAutoPathway) {
            final PosIdentifier identifier = new PosIdentifier(tilePos, world);
            SignalBoxHandler.requestPathway(identifier, originalFirstPoint, getLastPoint(),
                    modeGrid);
        }
    }

    /**
     * Getter for the first point of this pathway
     *
     * @return the firstPoint
     */
    public Point getFirstPoint() {
        return firstPoint;
    }

    /**
     * @return the lastPoint
     */
    public Point getLastPoint() {
        return lastPoint;
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstPoint, lastPoint, listOfNodes, modeGrid, type);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if ((obj == null) || (getClass() != obj.getClass()))
            return false;
        final SignalBoxPathway other = (SignalBoxPathway) obj;
        return Objects.equals(firstPoint, other.firstPoint)
                && Objects.equals(lastPoint, other.lastPoint)
                && Objects.equals(listOfNodes, other.listOfNodes)
                && Objects.equals(modeGrid, other.modeGrid) && type == other.type;
    }

    @Override
    public String toString() {
        return "SignalBoxPathway [start=" + firstPoint + ", end=" + lastPoint + "]";
    }

    /**
     * @return the listOfNodes
     */
    public ImmutableList<SignalBoxNode> getListOfNodes() {
        return listOfNodes;
    }

    /**
     * @return the emptyOrBroken
     */
    public boolean isEmptyOrBroken() {
        return emptyOrBroken;
    }
}
