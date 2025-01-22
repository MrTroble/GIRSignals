package com.troblecodings.signals.contentpacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.animation.SignalAnimation;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.parser.FunctionParsingInfo;

public class SignalAnimationConfigParser {

    private Map<String, ModelAnimationConfig> animations;

    private static final Gson GSON = new Gson();

    public static final Map<Signal, Map<Entry<String, VectorWrapper>, List<SignalAnimation>>>//
    ALL_ANIMATIONS = new HashMap<>();

    public static void loadAllAnimations() {
        if (!ALL_ANIMATIONS.isEmpty())
            return;
        OpenSignalsMain.contentPacks.getFiles("animations").forEach(entry -> {
            final String signalName = entry.getKey().replace(".json", "").toLowerCase();
            final Signal signal = Signal.SIGNALS.get(signalName);
            if (signal == null)
                OpenSignalsMain
                        .exitMinecraftWithMessage("There doesn't exists a signal with the name '"
                                + signalName + "'! Valid Signals are: " + Signal.SIGNALS.keySet());
            if (ALL_ANIMATIONS.containsKey(signal))
                OpenSignalsMain.exitMinecraftWithMessage(
                        "There are already existing animations for " + signal + "!");
            final FunctionParsingInfo info = new FunctionParsingInfo(signal);

            final SignalAnimationConfigParser parser = GSON.fromJson(entry.getValue(),
                    SignalAnimationConfigParser.class);
            final Map<Entry<String, VectorWrapper>, List<SignalAnimation>> modelToAnimation = new HashMap<>();
            parser.animations.forEach((modelName, configs) -> {
                final VectorWrapper vec = new VectorWrapper(configs.translationX,
                        configs.translationY, configs.translationZ);
                final List<SignalAnimation> animatinos = new ArrayList<>();
                for (final SignalAnimationConfig config : configs.animationConfigs) {
                    animatinos.add(config.createAnimation(info,
                            new VectorWrapper(configs.pivotX, configs.pivotY, configs.pivotZ)));
                }
                if (!animatinos.isEmpty())
                    modelToAnimation.put(Maps.immutableEntry(modelName, vec), animatinos);
            });
            if (!modelToAnimation.isEmpty())
                ALL_ANIMATIONS.put(signal, modelToAnimation);
        });
    }

    private class ModelAnimationConfig {

        private float translationX = 0;
        private float translationY = 0;
        private float translationZ = 0;

        private float pivotX = 0;
        private float pivotY = 0;
        private float pivotZ = 0;

        private List<SignalAnimationConfig> animationConfigs;

    }

}