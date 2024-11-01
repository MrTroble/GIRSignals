package com.troblecodings.signals.signalbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.collect.Maps;
import com.troblecodings.core.NBTWrapper;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.BlockPosSignalHolder;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.enums.EnumGuiMode;
import com.troblecodings.signals.enums.EnumPathUsage;
import com.troblecodings.signals.handler.SignalBoxHandler;
import com.troblecodings.signals.handler.SignalStateInfo;
import com.troblecodings.signals.signalbox.MainSignalIdentifier.SignalState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class InterSignalBoxPathway extends SignalBoxPathway {

    private static final String PATHWAY_TO_BLOCK = "pathwayToBlock";
    private static final String PATHWAY_TO_RESET = "pathwayToReset";
    private static final String END_POINT = "endPoint";
    private static final String TILE_POS = "signalBoxPos";

    protected InterSignalBoxPathway pathwayToBlock;
    protected InterSignalBoxPathway pathwayToReset;

    public InterSignalBoxPathway(final PathwayData data) {
        super(data);
    }

    @Override
    public void write(final NBTWrapper tag) {
        if (pathwayToBlock != null) {
            final NBTWrapper blockWrapper = new NBTWrapper();
            blockWrapper.putBlockPos(TILE_POS, pathwayToBlock.tile.getBlockPos());
            final NBTWrapper pointWrapper = new NBTWrapper();
            pathwayToBlock.getLastPoint().write(pointWrapper);
            blockWrapper.putWrapper(END_POINT, pointWrapper);
            tag.putWrapper(PATHWAY_TO_BLOCK, blockWrapper);
        }
        if (pathwayToReset != null) {
            final NBTWrapper resetWrapper = new NBTWrapper();
            resetWrapper.putBlockPos(TILE_POS, pathwayToReset.tile.getBlockPos());
            final NBTWrapper pointWrapper = new NBTWrapper();
            pathwayToReset.getLastPoint().write(pointWrapper);
            resetWrapper.putWrapper(END_POINT, pointWrapper);
            tag.putWrapper(PATHWAY_TO_RESET, resetWrapper);
        }
        super.write(tag);
    }

    private Map.Entry<BlockPos, Point> blockPW = null;
    private Map.Entry<BlockPos, Point> resetPW = null;

    @Override
    public void postRead(final NBTWrapper tag) {
        final NBTWrapper blockWrapper = tag.getWrapper(PATHWAY_TO_BLOCK);
        if (!blockWrapper.isTagNull()) {
            final Point end = new Point();
            end.read(blockWrapper.getWrapper(END_POINT));
            final BlockPos otherPos = blockWrapper.getBlockPos(TILE_POS);
            final Level world = tile.getLevel();
            if (world == null || world.isClientSide) {
                blockPW = Maps.immutableEntry(otherPos, end);
            } else {
                final AtomicReference<SignalBoxGrid> otherGrid = new AtomicReference<>();
                otherGrid.set(SignalBoxHandler.getGrid(new StateInfo(world, otherPos)));
                if (otherGrid.get() == null)
                    loadTileAndExecute(otherPos, tile -> otherGrid.set(tile.getSignalBoxGrid()));

                final SignalBoxPathway otherPathway = otherGrid.get().getPathwayByLastPoint(end);
                pathwayToBlock = (InterSignalBoxPathway) otherPathway;
            }
        }
        final NBTWrapper resetWrapper = tag.getWrapper(PATHWAY_TO_RESET);
        if (!resetWrapper.isTagNull()) {
            final Point end = new Point();
            end.read(resetWrapper.getWrapper(END_POINT));
            final BlockPos otherPos = resetWrapper.getBlockPos(TILE_POS);
            final Level world = tile.getLevel();
            if (world == null || world.isClientSide) {
                resetPW = Maps.immutableEntry(otherPos, end);
            } else {
                final AtomicReference<SignalBoxGrid> otherGrid = new AtomicReference<>();
                otherGrid.set(SignalBoxHandler.getGrid(new StateInfo(world, otherPos)));
                if (otherGrid.get() == null)
                    loadTileAndExecute(otherPos, tile -> otherGrid.set(tile.getSignalBoxGrid()));

                final SignalBoxPathway otherPathway = otherGrid.get().getPathwayByLastPoint(end);
                pathwayToReset = (InterSignalBoxPathway) otherPathway;
            }
        }
        super.postRead(tag);
    }

    @Override
    public void onLoad() {
        final Level world = tile.getLevel();
        if (world == null || world.isClientSide)
            return;
        if (blockPW != null) {
            final AtomicReference<SignalBoxGrid> otherGrid = new AtomicReference<>();
            otherGrid.set(SignalBoxHandler.getGrid(new StateInfo(world, blockPW.getKey())));
            if (otherGrid.get() == null)
                loadTileAndExecute(blockPW.getKey(),
                        tile -> otherGrid.set(tile.getSignalBoxGrid()));

            if (otherGrid.get() != null) {
                final SignalBoxPathway otherPathway = otherGrid.get()
                        .getPathwayByLastPoint(blockPW.getValue());
                pathwayToBlock = (InterSignalBoxPathway) otherPathway;
                blockPW = null;
            }
        }
        if (resetPW != null) {
            final AtomicReference<SignalBoxGrid> otherGrid = new AtomicReference<>();
            otherGrid.set(SignalBoxHandler.getGrid(new StateInfo(world, resetPW.getKey())));
            if (otherGrid.get() == null)
                loadTileAndExecute(resetPW.getKey(),
                        tile -> otherGrid.set(tile.getSignalBoxGrid()));

            if (otherGrid.get() != null) {
                final SignalBoxPathway otherPathway = otherGrid.get()
                        .getPathwayByLastPoint(resetPW.getValue());
                pathwayToReset = (InterSignalBoxPathway) otherPathway;
                resetPW = null;
            }
        }
        super.onLoad();
    }

    @Override
    protected SignalStateInfo getLastSignalInfo() {
        if (pathwayToBlock != null) {
            final MainSignalIdentifier otherLastSignal = pathwayToBlock.data.getEndSignal();
            if (otherLastSignal != null) {
                final Signal nextSignal = SignalBoxHandler
                        .getSignal(new StateInfo(pathwayToBlock.tile.getLevel(),
                                pathwayToBlock.tile.getBlockPos()), otherLastSignal.pos);
                if (nextSignal != null)
                    lastSignalInfo = new SignalStateInfo(tile.getLevel(), otherLastSignal.pos,
                            nextSignal);
            }
        }
        return super.getLastSignalInfo();
    }

    @Override
    protected void setSignals(final SignalStateInfo lastSignal) {
        if (tile == null || isExecutingSignalSet)
            return;
        final StateInfo identifier = new StateInfo(tile.getLevel(), tile.getBlockPos());
        if (lastSignal != null && pathwayToReset != null) {
            final Signal signal = SignalBoxHandler.getSignal(identifier, lastSignal.pos);
            if (signal == null)
                return;
            pathwayToReset.setSignals(new SignalStateInfo(tile.getLevel(), lastSignal.pos, signal));
        }
        super.setSignals(lastSignal);
    }

    @Override
    public void resetPathway(final Point point) {
        super.resetPathway(point);
        if (data.totalPathwayReset(point) && pathwayToReset != null) {
            pathwayToReset.loadTileAndExecute(
                    tile -> tile.getSignalBoxGrid().resetPathway(pathwayToReset.getFirstPoint()));
        }
    }

    @Override
    public boolean tryBlock(final BlockPos position) {
        final boolean result = super.tryBlock(position);
        if (result && pathwayToBlock != null) {
            pathwayToBlock.loadTileAndExecute(otherTile -> {
                final SignalBoxGrid otherGrid = otherTile.getSignalBoxGrid();
                pathwayToBlock = (InterSignalBoxPathway) otherGrid
                        .getPathwayByLastPoint(pathwayToBlock.getLastPoint());
                pathwayToBlock.setPathStatus(EnumPathUsage.BLOCKED);
                pathwayToBlock.updateTrainNumber(trainNumber);
                otherGrid.updateToNet(pathwayToBlock);
            });
        }
        return result;
    }

    @Override
    protected void updateSignalStates() {
        final List<MainSignalIdentifier> redSignals = new ArrayList<>();
        final List<MainSignalIdentifier> greenSignals = new ArrayList<>();
        final MainSignalIdentifier lastSignal = data.getEndSignal();
        if (lastSignal != null) {
            if (isBlocked)
                return;
            final SignalState previous = lastSignal.state;
            lastSignal.state = SignalState.GREEN;
            if (!lastSignal.state.equals(previous))
                greenSignals.add(lastSignal);
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
            if (lastSignal != null && next != null && !next.isEmptyOrBroken()) {
                if (!next.isExecutingSignalSet)
                    position.state = SignalState.GREEN;
            } else if (pathwayToBlock != null) {
                final SignalBoxPathway otherNext = pathwayToBlock.getNextPathway();
                if (otherNext != null && !otherNext.isEmptyOrBroken()) {
                    if (!otherNext.isExecutingSignalSet)
                        position.state = SignalState.GREEN;
                } else {
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
                } else if (position.state.equals(SignalState.GREEN)) {
                    greenSignals.add(position);
                }
            }
        });
        updateSignalsOnClient(redSignals, greenSignals);
    }

    public void setOtherPathwayToBlock(final InterSignalBoxPathway pathway) {
        this.pathwayToBlock = pathway;
    }

    public void setOtherPathwayToReset(final InterSignalBoxPathway pathway) {
        this.pathwayToReset = pathway;
    }

    @Override
    public String toString() {
        return "InterSignalBoxPathway [start=" + getFirstPoint() + ", end=" + getLastPoint() + "]";
    }
}