package com.troblecodings.signals.signalbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.troblecodings.core.NBTWrapper;
import com.troblecodings.core.WriteBuffer;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.RedstoneIO;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.BlockPosSignalHolder;
import com.troblecodings.signals.core.ModeIdentifier;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.core.SubsidiaryEntry;
import com.troblecodings.signals.core.TrainNumber;
import com.troblecodings.signals.enums.EnumGuiMode;
import com.troblecodings.signals.enums.EnumPathUsage;
import com.troblecodings.signals.enums.PathType;
import com.troblecodings.signals.enums.SignalBoxNetwork;
import com.troblecodings.signals.handler.SignalBoxHandler;
import com.troblecodings.signals.handler.SignalStateInfo;
import com.troblecodings.signals.signalbox.MainSignalIdentifier.SignalState;
import com.troblecodings.signals.signalbox.config.ConfigInfo;
import com.troblecodings.signals.signalbox.config.ResetInfo;
import com.troblecodings.signals.signalbox.config.SignalConfig;
import com.troblecodings.signals.signalbox.entrys.PathEntryType;
import com.troblecodings.signals.signalbox.entrys.PathOptionEntry;
import com.troblecodings.signals.tileentitys.IChunkLoadable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SignalBoxPathway implements IChunkLoadable {

    protected final PathwayData data;
    protected final SignalConfig config;

    protected boolean isBlocked;
    protected boolean isAutoPathway = false;
    protected Point originalFirstPoint = null;
    protected SignalBoxGrid grid = null;
    protected SignalBoxTileEntity tile;
    protected TrainNumber trainNumber;
    protected boolean isExecutingSignalSet = false;

    public void setTile(final SignalBoxTileEntity tile) {
        this.tile = tile;
    }

    public SignalBoxPathway(final PathwayData data) {
        this.config = new SignalConfig(this);
        this.data = data;
        this.originalFirstPoint = new Point(data.getFirstPoint());
        updatePathwayToAutomatic();
        resetAllTrainNumbers();
    }

    private static final String IS_BLOCKED = "isBlocked";
    private static final String ORIGINAL_FIRST_POINT = "origianlFirstPoint";

    public void write(final NBTWrapper tag) {
        data.write(tag);
        tag.putBoolean(IS_BLOCKED, isBlocked);
        if (originalFirstPoint != null) {
            final NBTWrapper originalFirstPoint = new NBTWrapper();
            this.originalFirstPoint.write(originalFirstPoint);
            tag.putWrapper(ORIGINAL_FIRST_POINT, originalFirstPoint);
        }
        if (trainNumber != null) {
            this.trainNumber.writeTag(tag);
        }
    }

    public void read(final NBTWrapper tag) {
        data.read(tag);
        if (isEmptyOrBroken())
            return;
        this.isBlocked = tag.getBoolean(IS_BLOCKED);
        final NBTWrapper originalFirstPoint = tag.getWrapper(ORIGINAL_FIRST_POINT);
        if (!originalFirstPoint.isTagNull()) {
            this.originalFirstPoint = new Point();
            this.originalFirstPoint.read(originalFirstPoint);
        }
        this.trainNumber = TrainNumber.of(tag);
        updatePathwayToAutomatic();
        updateSignalStates();
    }

    public void postRead(final NBTWrapper tag) {
    }

    public void onLoad() {
    }

    public void setPathStatus(final EnumPathUsage status, final @Nullable Point point) {
        data.foreachEntry(option -> {
            option.getEntry(PathEntryType.OUTPUT)
                    .ifPresent(pos -> SignalBoxHandler.updateRedstoneOutput(
                            new StateInfo(tile.getWorld(), pos),
                            !status.equals(EnumPathUsage.FREE)));
            if (!status.equals(EnumPathUsage.FREE)) {
                option.setEntry(PathEntryType.PATHUSAGE, status);
            } else {
                option.removeEntry(PathEntryType.PATHUSAGE);
            }
        }, point);
        if (status.equals(EnumPathUsage.SELECTED) || status.equals(EnumPathUsage.PREPARED)) {
            setProtectionWay();
        }
    }

    private void setProtectionWay() {
        data.forEachEntryProtectionWay((option, _u) -> {
            option.getEntry(PathEntryType.OUTPUT).ifPresent(pos -> SignalBoxHandler
                    .updateRedstoneOutput(new StateInfo(tile.getWorld(), pos), true));
            option.setEntry(PathEntryType.PATHUSAGE, EnumPathUsage.PROTECTED);
        });
    }

    public boolean checkResetOfProtectionWay(final BlockPos position) {
        if (!data.canResetProtectionWay(position))
            return false;
        return resetProtectionWay();
    }

    public boolean resetProtectionWay() {
        return data.resetProtectionWay();
    }

    public boolean directResetOfProtectionWay() {
        return data.directResetOfProtectionWay();
    }

    public void removeProtectionWay() {
        data.removeProtectionWay();
    }

    public void setUpPathwayStatus() {
        setPathStatus(EnumPathUsage.SELECTED);
    }

    public void setPathStatus(final EnumPathUsage status) {
        setPathStatus(status, null);
    }

    public void updatePrevious() {
        if (grid == null)
            return;
        grid.updatePrevious(this);
    }

    protected SignalStateInfo lastSignalInfo = null;

    protected SignalStateInfo getLastSignalInfo() {
        if (lastSignalInfo != null)
            return lastSignalInfo;
        final World world = tile.getWorld();
        if (world == null || world.isRemote)
            return null;
        final StateInfo identifier = new StateInfo(world, tile.getPos());
        final MainSignalIdentifier lastSignal = data.getEndSignal();
        if (lastSignal != null) {
            final Signal nextSignal = SignalBoxHandler.getSignal(identifier, lastSignal.pos);
            if (nextSignal != null)
                lastSignalInfo = new SignalStateInfo(world, lastSignal.pos, nextSignal);
        }
        return lastSignalInfo;
    }

    public void updatePathwaySignals() {
        final World world = tile.getWorld();
        if (world == null || world.isRemote || isExecutingSignalSet)
            return;
        setSignals();
    }

    protected SignalBoxPathway getNextPathway() {
        return grid.startsToPath.get(getLastPoint());
    }

    protected void setSignals() {
        setSignals(getLastSignalInfo());
    }

    protected void setSignals(final SignalStateInfo lastSignal) {
        if (isExecutingSignalSet || tile == null)
            return;
        final World world = tile.getWorld();
        final StateInfo identifier = new StateInfo(world, tile.getPos());
        final MainSignalIdentifier startSignal = data.getStartSignal();
        if (startSignal != null) {
            if (isBlocked)
                return;
            final Signal first = SignalBoxHandler.getSignal(identifier, startSignal.pos);
            if (first == null)
                return;
            final SignalStateInfo firstInfo = new SignalStateInfo(world, startSignal.pos, first);
            config.change(new ConfigInfo(firstInfo, lastSignal, data));
            updatePreSignals();
        }
        final SignalBoxPathway next = getNextPathway();
        if (next != null && (next.isEmptyOrBroken() || next.isBlocked)) {
            updateSignalStates();
            return;
        }
        final Map<BlockPosSignalHolder, OtherSignalIdentifier> distantSignalPositions = data
                .getOtherSignals();
        distantSignalPositions.forEach((holder, position) -> {
            if (holder.shouldTurnSignalOff())
                return;
            final Signal current = SignalBoxHandler.getSignal(identifier, position.pos);
            if (current == null)
                return;
            final ConfigInfo info = new ConfigInfo(
                    new SignalStateInfo(world, position.pos, current), lastSignal, data,
                    position.isRepeater);
            if (position.guiMode.equals(EnumGuiMode.HP)) {
                config.loadDisable(info);
            } else {
                config.change(info);
            }
        });
        updateSignalStates();
    }

    private void updatePreSignals() {
        final MainSignalIdentifier startSignal = data.getStartSignal();
        if (startSignal == null)
            return;
        final SignalBoxPathway next = getNextPathway();
        if (next != null && (next.isEmptyOrBroken() || next.isBlocked)) {
            return;
        }
        final StateInfo identifier = new StateInfo(tile.getWorld(), tile.getPos());
        final Signal first = SignalBoxHandler.getSignal(identifier, startSignal.pos);
        if (first == null)
            return;
        final SignalStateInfo firstInfo = new SignalStateInfo(tile.getWorld(), startSignal.pos,
                first);
        data.getPreSignals().forEach(posIdent -> {
            final Signal current = SignalBoxHandler.getSignal(identifier, posIdent.pos);
            if (current == null)
                return;
            config.change(
                    new ConfigInfo(new SignalStateInfo(tile.getWorld(), posIdent.pos, current),
                            firstInfo, data, posIdent.isRepeater));
        });
    }

    protected void updateSignalStates() {
        final List<MainSignalIdentifier> redSignals = new ArrayList<>();
        final List<MainSignalIdentifier> greenSignals = new ArrayList<>();
        final MainSignalIdentifier startSignal = data.getStartSignal();
        final MainSignalIdentifier endSignal = data.getEndSignal();
        if (startSignal != null) {
            if (!isBlocked) {
                final SignalState previous = startSignal.state;
                startSignal.state = SignalState.GREEN;
                if (!startSignal.state.equals(previous))
                    greenSignals.add(startSignal);
                data.getPreSignals().forEach(signalIdent -> {
                    signalIdent.state = SignalState.GREEN;
                    greenSignals.add(signalIdent);
                });
            }
        }
        final Map<BlockPosSignalHolder, OtherSignalIdentifier> distantSignalPositions = data
                .getOtherSignals();
        distantSignalPositions.forEach((holder, position) -> {
            if (holder.shouldTurnSignalOff()) {
                position.state = SignalState.OFF;
                greenSignals.add(position);
                return;
            }
            final SignalBoxPathway next = getNextPathway();
            final SignalState previous = position.state;
            if (endSignal != null && next != null && !next.isEmptyOrBroken()) {
                if (!next.isExecutingSignalSet) {
                    position.state = SignalState.GREEN;
                }
                if (next.isBlocked) {
                    position.state = SignalState.RED;
                }
            } else {
                position.state = SignalState.RED;
            }
            if (position.guiMode.equals(EnumGuiMode.RS)) {
                position.state = SignalState.GREEN;
            } else if (position.guiMode.equals(EnumGuiMode.HP)) {
                position.state = SignalState.OFF;
            }
            if (position.state.equals(previous)) {
                return;
            } else {
                if (position.state.equals(SignalState.RED)) {
                    redSignals.add(position);
                } else if (position.state.equals(SignalState.GREEN)
                        || position.state.equals(SignalState.OFF)) {
                    greenSignals.add(position);
                }
            }
        });
        updateSignalsOnClient(redSignals, greenSignals);
    }

    public List<MainSignalIdentifier> getGreenSignals() {
        final List<MainSignalIdentifier> returnList = new ArrayList<>();
        final MainSignalIdentifier startSignal = data.getStartSignal();
        if (startSignal != null) {
            if (startSignal.state.equals(SignalState.GREEN))
                returnList.add(startSignal);
        }
        final Map<BlockPosSignalHolder, OtherSignalIdentifier> distantSignalPositions = data
                .getOtherSignals();
        distantSignalPositions.values().forEach(signal -> {
            if (signal.state.equals(SignalState.GREEN) || signal.state.equals(SignalState.OFF))
                returnList.add(signal);
        });
        data.getPreSignals().forEach(ident -> {
            if (ident.state.equals(SignalState.GREEN))
                returnList.add(ident);
        });
        return returnList;
    }

    protected void updatePathwayOnGrid() {
        grid.updatePrevious(this);
        grid.updateToNet(this);
    }

    protected void setSignalBoxGrid(final SignalBoxGrid grid) {
        this.grid = grid;
    }

    private void updateSignalsOnClient(final List<MainSignalIdentifier> redSignals) {
        updateSignalsOnClient(redSignals, new ArrayList<>());
    }

    protected void updateSignalsOnClient(final List<MainSignalIdentifier> redSignals,
            final List<MainSignalIdentifier> greenSignals) {
        if (redSignals.isEmpty() && greenSignals.isEmpty())
            return;
        final World world = tile.getWorld();
        if (world == null || world.isRemote)
            return;
        world.getMinecraftServer().addScheduledTask(() -> {
            final WriteBuffer buffer = new WriteBuffer();
            buffer.putEnumValue(SignalBoxNetwork.SET_SIGNALS);
            buffer.putByte((byte) redSignals.size());
            redSignals.forEach(signal -> {
                signal.writeNetwork(buffer);
                grid.updateSubsidiarySignal(signal.getPoint(), signal.getModeSet(),
                        new SubsidiaryEntry(null, false));
            });
            buffer.putByte((byte) greenSignals.size());
            greenSignals.forEach(signal -> signal.writeNetwork(buffer));
            if (tile == null || !tile.isBlocked())
                return;
            OpenSignalsMain.network.sendTo(tile.get(0).getPlayer(), buffer);
        });
    }

    public void resetPathway() {
        resetPathway(null);
    }

    public void resetAllSignals() {
        resetFirstSignal();
        resetOther();
        isBlocked = true;
    }

    private void resetFirstSignal() {
        final MainSignalIdentifier startSignal = data.getStartSignal();
        if (startSignal != null) {
            final StateInfo stateInfo = new StateInfo(tile.getWorld(), tile.getPos());
            final List<MainSignalIdentifier> signals = new ArrayList<>();
            final Signal current = SignalBoxHandler.getSignal(stateInfo, startSignal.pos);
            if (current == null)
                return;
            SignalConfig.reset(new ResetInfo(
                    new SignalStateInfo(tile.getWorld(), startSignal.pos, current), false));
            final SignalState previous = startSignal.state;
            startSignal.state = SignalState.RED;
            if (!startSignal.state.equals(previous)) {
                signals.add(startSignal);
            }
            data.getPreSignals().forEach(ident -> {
                final Signal currentPreSignal = SignalBoxHandler.getSignal(stateInfo, ident.pos);
                if (currentPreSignal == null)
                    return;
                SignalConfig.reset(new ResetInfo(
                        new SignalStateInfo(tile.getWorld(), ident.pos, currentPreSignal),
                        ident.isRepeater));
                final SignalState previousState = ident.state;
                ident.state = SignalState.RED;
                if (!ident.state.equals(previousState)) {
                    signals.add(ident);
                }
            });
            updateSignalsOnClient(signals);
        }
    }

    private void resetOther() {
        final List<MainSignalIdentifier> redSignals = new ArrayList<>();
        final Map<BlockPosSignalHolder, OtherSignalIdentifier> distantSignalPositions = data
                .getOtherSignals();
        distantSignalPositions.forEach((holder, position) -> {
            if (holder.shouldTurnSignalOff()) {
                position.state = SignalState.RED;
                redSignals.add(position);
            }
            final Signal current = SignalBoxHandler
                    .getSignal(new StateInfo(tile.getWorld(), tile.getPos()), position.pos);
            if (current == null)
                return;
            SignalConfig.reset(
                    new ResetInfo(new SignalStateInfo(tile.getWorld(), position.pos, current),
                            position.isRepeater));
            final SignalState previous = position.state;
            position.state = SignalState.RED;
            if (!position.state.equals(previous)) {
                redSignals.add(position);
            }
        });
        updateSignalsOnClient(redSignals);
    }

    public void resetPathway(final @Nullable Point point) {
        this.setPathStatus(EnumPathUsage.FREE, point);
        resetFirstSignal();
        if (data.totalPathwayReset(point)) {
            resetOther();
            resetAllTrainNumbers();
            sendTrainNumberUpdates();
            resetProtectionWay();
            final SignalBoxPathway next = getNextPathway();
            if (next != null) {
                next.updatePreSignals();
                next.updateSignalStates();
            }
        }
    }

    public void compact(final Point point) {
        final List<MainSignalIdentifier> redSignals = new ArrayList<>();
        data.foreachPath((path, node) -> {
            final Rotation rotation = SignalBoxUtil
                    .getRotationFromDelta(node.getPoint().delta(path.point1));
            for (final EnumGuiMode mode : Arrays.asList(EnumGuiMode.VP, EnumGuiMode.RS,
                    EnumGuiMode.HP, EnumGuiMode.ZS3)) {
                node.getOption(new ModeSet(mode, rotation)).ifPresent(
                        option -> option.getEntry(PathEntryType.SIGNAL).ifPresent(position -> {
                            final Signal current = SignalBoxHandler.getSignal(
                                    new StateInfo(tile.getWorld(), tile.getPos()), position);
                            if (current == null)
                                return;
                            final Map<BlockPosSignalHolder, OtherSignalIdentifier> distantSignalPositions = data
                                    .getOtherSignals();
                            final OtherSignalIdentifier identifier = distantSignalPositions
                                    .getOrDefault(new BlockPosSignalHolder(position),
                                            new OtherSignalIdentifier(point,
                                                    new ModeSet(mode, rotation), position, false,
                                                    mode));
                            SignalConfig.reset(new ResetInfo(
                                    new SignalStateInfo(tile.getWorld(), position, current),
                                    identifier.isRepeater));
                            final SignalState previous = identifier.state;
                            identifier.state = SignalState.RED;
                            if (!identifier.state.equals(previous)) {
                                redSignals.add(identifier);
                            }
                            final OtherSignalIdentifier otherIdent = distantSignalPositions
                                    .get(new BlockPosSignalHolder(position, true));
                            if (otherIdent != null) {
                                otherIdent.state = SignalState.RED;
                                redSignals.add(otherIdent);
                            }
                        }));
            }
        }, point);
        resetAllTrainNumbers(data.getTrainNumberDisplays());
        sendTrainNumberUpdates();
        data.compact(point);
        updateSignalsOnClient(redSignals);
        updateTrainNumber(trainNumber);
        updateSignalStates();
    }

    public Optional<Point> tryReset(final BlockPos position) {
        final SignalBoxNode node = data.tryReset(position);
        if (node == null) {
            if (checkReverseReset(position)) {
                return Optional.of(getFirstPoint());
            } else {
                return Optional.empty();
            }
        }
        final Point point = node.getPoint();
        final AtomicBoolean atomic = new AtomicBoolean(false);
        data.foreachEntry((option, cNode) -> {
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
        if (!isBlocked || getFirstPoint().equals(originalFirstPoint)) {
            return false;
        }
        final List<SignalBoxNode> listOfNodes = data.getListOfNodes();
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
                        data.foreachEntry((option, cNode) -> {
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
        final IBlockState state = tile.getWorld().getBlockState(pos);
        if (state == null || !(state.getBlock() instanceof RedstoneIO))
            return false;
        return state.getValue(RedstoneIO.POWER);
    }

    public boolean tryBlock(final BlockPos position) {
        if (!data.tryBlock(position))
            return false;
        resetFirstSignal();
        this.setPathStatus(EnumPathUsage.BLOCKED);
        if (!isBlocked) {
            getTrainNumberFromPrevious();
        }
        isBlocked = true;
        return true;
    }

    private void getTrainNumberFromPrevious() {
        final SignalBoxPathway previous = grid.getPathwayByLastPoint(getFirstPoint());
        if (previous != null) {
            updateTrainNumber(previous.trainNumber);
        }
    }

    public void checkTrainNumberUpdate(final TrainNumber number, final SignalBoxNode node) {
        if (!data.getListOfNodes().contains(node))
            return;
        updateTrainNumber(number);
    }

    protected void updateTrainNumber(final TrainNumber number) {
        resetAllTrainNumbers();
        final List<ModeIdentifier> trainNumberDisplays = data.getTrainNumberDisplays();
        trainNumberDisplays.forEach(ident -> grid.getNode(ident.point).getOption(ident.mode)
                .orElse(new PathOptionEntry()).setEntry(PathEntryType.TRAINNUMBER, number));
        this.trainNumber = number;
        sendTrainNumberUpdates();
    }

    private void sendTrainNumberUpdates() {
        if (!this.tile.isBlocked())
            return;
        final List<ModeIdentifier> trainNumberDisplays = data.getTrainNumberDisplays();
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putEnumValue(SignalBoxNetwork.SEND_TRAIN_NUMBER);
        buffer.putInt(trainNumberDisplays.size());
        trainNumberDisplays.forEach(ident -> {
            final SignalBoxNode node = grid.getNode(ident.point);
            node.getPoint().writeNetwork(buffer);
            node.writeNetwork(buffer);
        });
        OpenSignalsMain.network.sendTo(tile.get(0).getPlayer(), buffer);
    }

    private void resetAllTrainNumbers() {
        resetAllTrainNumbers(data.getTrainNumberDisplays());
    }

    private void resetAllTrainNumbers(final List<ModeIdentifier> trainNumberDisplays) {
        if (grid != null && trainNumberDisplays != null) {
            trainNumberDisplays.forEach(ident -> {
                final SignalBoxNode node = grid.getNode(ident.point);
                if (node == null)
                    return;
                node.getOption(ident.mode).orElse(new PathOptionEntry())
                        .removeEntry(PathEntryType.TRAINNUMBER);
            });
        }
    }

    public void deactivateAllOutputsOnPathway() {
        data.foreachPath((_u, node) -> {
            final List<BlockPos> outputs = node.clearAllManuellOutputs();
            outputs.forEach(pos -> SignalBoxHandler
                    .updateRedstoneOutput(new StateInfo(tile.getWorld(), pos), false));
        }, null);
        data.forEachEntryProtectionWay((_u, node) -> {
            final List<BlockPos> outputs = node.clearAllManuellOutputs();
            outputs.forEach(pos -> SignalBoxHandler
                    .updateRedstoneOutput(new StateInfo(tile.getWorld(), pos), false));
        });
    }

    public void updatePathwayToAutomatic() {
        final SignalBoxNode first = data.grid.getModeGrid().get(originalFirstPoint);
        if (first == null) {
            isAutoPathway = false;
            return;
        }
        this.isAutoPathway = first.isAutoPoint();
    }

    protected boolean loadTileAndExecute(final Consumer<SignalBoxTileEntity> consumer) {
        return loadTileAndExecute(this.tile.getPos(), consumer);
    }

    protected boolean loadTileAndExecute(final BlockPos tilePos,
            final Consumer<SignalBoxTileEntity> consumer) {
        return loadChunkAndGetTile(SignalBoxTileEntity.class, tile.getWorld(), tilePos,
                (blockTile, _u) -> consumer.accept(blockTile));
    }

    public void checkReRequest() {
        if (isAutoPathway) {
            grid.requestWay(originalFirstPoint, getLastPoint(), getPathType());
        }
    }

    /**
     * Getter for the first point of this pathway
     *
     * @return the firstPoint
     */
    public Point getFirstPoint() {
        return data.getFirstPoint();
    }

    /**
     * @return the lastPoint
     */
    public Point getLastPoint() {
        return data.getLastPoint();
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if ((obj == null) || (getClass() != obj.getClass()))
            return false;
        final SignalBoxPathway other = (SignalBoxPathway) obj;
        return Objects.equals(data, other.data);
    }

    @Override
    public String toString() {
        return "SignalBoxPathway [start=" + getFirstPoint() + ", end=" + getLastPoint() + "]";
    }

    /**
     * @return the listOfNodes
     */
    public List<SignalBoxNode> getListOfNodes() {
        return data.getListOfNodes();
    }

    public List<SignalBoxNode> getProtectionWayNodes() {
        return data.getProtectionWayNodes();
    }

    /**
     * @return the emptyOrBroken
     */
    public boolean isEmptyOrBroken() {
        return data.isEmptyOrBroken();
    }

    public boolean isShuntingPath() {
        return getPathType().equals(PathType.SHUNTING);
    }

    public PathType getPathType() {
        return data.getPathType();
    }

    public boolean isInterSignalBoxPathway() {
        return this instanceof InterSignalBoxPathway;
    }

    public SignalBoxGrid getGrid() {
        return grid;
    }
}
