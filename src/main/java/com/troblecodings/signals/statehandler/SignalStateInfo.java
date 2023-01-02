package com.troblecodings.signals.statehandler;

import java.util.Objects;

import com.troblecodings.signals.blocks.Signal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

public class SignalStateInfo {

    public final BlockGetter world;
    public final BlockPos pos;
    public final Signal signal;

    public SignalStateInfo(final BlockGetter world, final BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.signal = (Signal) world.getBlockState(pos).getBlock();
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, signal, world);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SignalStateInfo other = (SignalStateInfo) obj;
        return Objects.equals(pos, other.pos) && Objects.equals(signal, other.signal)
                && Objects.equals(world, other.world);
    }
}