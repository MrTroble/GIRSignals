package com.troblecodings.signals.animation;

import java.util.Arrays;

import com.mojang.math.Quaternion;
import com.troblecodings.signals.OpenSignalsMain;

public enum RotationAxis {

    X("X"), Y("Y"), Z("Z");

    private final String axis;

    private RotationAxis(final String axis) {
        this.axis = axis;
    }

    public static RotationAxis of(final String axis) {
        return Arrays.stream(values())
                .filter(rotationAxis -> rotationAxis.axis.equalsIgnoreCase(axis)).findFirst()
                .orElseGet(() -> {
                    OpenSignalsMain.exitMinecraftWithMessage("[" + axis
                            + "] is not a valid axis for a RotationAnimation! Valid are: "
                            + values());
                    return null;
                });
    }

    public Quaternion getForAxis(final float value) {
        switch (this) {
            case X:
                return Quaternion.fromXYZ(value, 0, 0);
            case Y:
                return Quaternion.fromXYZ(0, value, 0);
            case Z:
                return Quaternion.fromXYZ(0, 0, value);
            default:
                return Quaternion.fromXYZ(0, 0, 0);
        }
    }

}