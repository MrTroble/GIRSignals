package com.troblecodings.signals.animation;

import java.util.Arrays;

public enum AnimationMode {

    ROTATION("ROTATION"), TRANSLATION("TRANSLATION");

    private String mode;

    private AnimationMode(final String mode) {
        this.mode = mode;
    }

    public static AnimationMode of(final String mode) {
        return Arrays.stream(values())
                .filter(animationMode -> animationMode.mode.equalsIgnoreCase(mode)).findFirst()
                .orElse(null);
    }

}