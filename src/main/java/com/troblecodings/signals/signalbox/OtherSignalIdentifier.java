package com.troblecodings.signals.signalbox;

import java.util.Objects;

import net.minecraft.core.BlockPos;

public class OtherSignalIdentifier extends MainSignalIdentifier {

    public final boolean isRepeater;

    public OtherSignalIdentifier(Point point, ModeSet mode, BlockPos pos,
            final boolean isRepeater) {
        super(point, mode, pos);
        this.isRepeater = isRepeater;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(isRepeater);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        OtherSignalIdentifier other = (OtherSignalIdentifier) obj;
        return isRepeater == other.isRepeater && super.equals(obj);
    }
}