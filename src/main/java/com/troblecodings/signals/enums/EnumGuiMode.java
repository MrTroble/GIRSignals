package com.troblecodings.signals.enums;

import java.util.function.Supplier;

import com.troblecodings.guilib.ecs.entitys.UIComponent;
import com.troblecodings.guilib.ecs.entitys.render.UILines;
import com.troblecodings.guilib.ecs.entitys.render.UITexture;
import com.troblecodings.signals.core.BufferFactory;
import com.troblecodings.signals.guis.UISignalBoxTile;

public enum EnumGuiMode {

    STRAIGHT(new float[] {
            0, 0.5f, 1, 0.5f
    }), CORNER(new float[] {
            0, 0.5f, 0.5f, 1
    }), END(new float[] {
            1, 0.30f, 1, 0.70f
    }), PLATFORM(() -> new UITexture(UISignalBoxTile.ICON, 0, 0, 1, 1)),
    BUE(() -> new UITexture(UISignalBoxTile.ICON, 0, 0, 1, 1)), HP(0), VP(1), RS(2), RA10(3);

    /**
     * Naming
     */

    public final Supplier<UIComponent> consumer;

    private EnumGuiMode(final int id) {
        this(() -> new UITexture(UISignalBoxTile.ICON, id * 0.25, 0, id * 0.25 + 0.25, 0.5));
    }

    private EnumGuiMode(final float[] array) {
        this(() -> new UILines(array, 2));
    }

    private EnumGuiMode(final Supplier<UIComponent> consumer) {
        this.consumer = consumer;
    }

    public static EnumGuiMode of(final BufferFactory buffer) {
        return values()[buffer.getByteAsInt()];
    }
}