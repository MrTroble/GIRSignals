package com.troblecodings.signals.models;

import java.util.Map;
import java.util.function.Predicate;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SignalModelLoaderInfo {

    public final String name;
    public final Predicate<ModelInfoWrapper> state;
    public final float x;
    public final float y;
    public final float z;
    public final Map<String, String> retexture;
    public boolean isAnimation = false;

    public SignalModelLoaderInfo(final String name, final Predicate<ModelInfoWrapper> state,
            final float x, final float y, final float z, final Map<String, String> retexture) {
        this.name = name;
        this.state = state;
        this.x = x;
        this.y = y;
        this.z = z;
        this.retexture = retexture;
    }

    public SignalModelLoaderInfo setOnAnimation() {
        this.isAnimation = true;
        return this;
    }
}