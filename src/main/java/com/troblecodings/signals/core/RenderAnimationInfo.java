package com.troblecodings.signals.core;

import com.troblecodings.signals.tileentitys.SignalTileEntity;

public class RenderAnimationInfo {

    public final double x;
    public final double y;
    public final double z;
    public SignalTileEntity tileEntity;

    public RenderAnimationInfo(final double x, final double y, final double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public RenderAnimationInfo with(final SignalTileEntity tileEntity) {
        this.tileEntity = tileEntity;
        return this;
    }
}
