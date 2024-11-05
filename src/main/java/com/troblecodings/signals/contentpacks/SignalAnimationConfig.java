package com.troblecodings.signals.contentpacks;

import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.animation.AnimationMode;
import com.troblecodings.signals.animation.RotationAxis;
import com.troblecodings.signals.animation.SignalAnimation;
import com.troblecodings.signals.animation.SignalAnimationRotation;
import com.troblecodings.signals.animation.SignalAnimationTranslation;
import com.troblecodings.signals.parser.FunctionParsingInfo;
import com.troblecodings.signals.parser.LogicParser;

public class SignalAnimationConfig {

    private String predicate;
    private float animationSpeed = 1.0f;
    private String mode;
    private String rotationAxis;
    private float rotation = 0;

    private float pivotX = 0;
    private float pivotY = 0;
    private float pivotZ = 0;

    private float destX = 0;
    private float destY = 0;
    private float destZ = 0;

    public SignalAnimation createAnimation(final FunctionParsingInfo info,
            final VectorWrapper pivot) {
        final AnimationMode mode = AnimationMode.of(this.mode);
        switch (mode) {
            case ROTATION: {
                final RotationAxis axis = RotationAxis.of(rotationAxis);
                return new SignalAnimationRotation(LogicParser.predicate(predicate, info),
                        animationSpeed, axis, rotation, new VectorWrapper(pivotX + pivot.getX(),
                                pivotY + pivot.getY(), pivotZ + pivot.getZ()));
            }
            case TRANSLATION: {
                return new SignalAnimationTranslation(LogicParser.predicate(predicate, info),
                        animationSpeed, new VectorWrapper(destX, destY, destZ));
            }
            default:
                return null;
        }
    }
}