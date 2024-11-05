package com.troblecodings.signals.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.contentpacks.SoundPropertyParser;
import com.troblecodings.signals.init.OSItems;
import com.troblecodings.signals.init.OSSounds;
import com.troblecodings.signals.items.Placementtool;
import com.troblecodings.signals.parser.FunctionParsingInfo;
import com.troblecodings.signals.parser.LogicParser;
import com.troblecodings.signals.parser.LogicalParserException;
import com.troblecodings.signals.parser.ValuePack;
import com.troblecodings.signals.properties.PredicatedPropertyBase.PredicateProperty;
import com.troblecodings.signals.properties.SoundProperty;

import net.minecraft.sounds.SoundEvent;

public class SignalPropertiesBuilder {

    private transient Placementtool placementtool = null;
    private String placementToolName = null;
    private int defaultHeight = 1;
    private Map<String, Integer> signalHeights;
    private float customNameRenderHeight = -1;
    private Map<String, Float> renderHeights;
    private float signWidth = 22;
    private boolean autoscale = false;
    private float offsetX = 0;
    private float offsetY = 0;
    private float signScale = 1;
    private Map<String, Boolean> doubleSidedText;
    private int textColor = 0;
    private boolean canLink = true;
    private List<Integer> colors;
    private Map<String, SoundPropertyParser> sounds;
    private Map<String, String> redstoneOutputs;
    private Map<String, String> remoteRedstoneOutputs;
    private int defaultItemDamage = 1;
    private boolean isBridgeSignal = false;

    public SignalProperties build(final FunctionParsingInfo info) {
        if (placementToolName != null) {
            for (int i = 0; i < OSItems.placementtools.size(); i++) {
                final Placementtool tool = OSItems.placementtools.get(i);
                if (tool.getRegistryName().getPath().equalsIgnoreCase(placementToolName)) {
                    placementtool = tool;
                    break;
                }
            }
        }
        if (placementtool == null) {
            OpenSignalsMain.exitMinecraftWithMessage(
                    "There doesn't exists a placementtool with the name '" + placementToolName
                            + "'! Valid Placementtools: " + OSItems.placementtools);
        }

        final List<PredicateProperty<Integer>> signalheights = new ArrayList<>();
        if (signalHeights != null) {
            signalHeights.forEach((property, height) -> {
                try {
                    signalheights.add(new PredicateProperty<Integer>(
                            LogicParser.predicate(property, info), height));
                } catch (final LogicalParserException e) {
                    OpenSignalsMain.getLogger()
                            .error("Something went wrong during the registry of a predicate in "
                                    + info.signalName + "!\nWith statement:" + property);
                    e.printStackTrace();
                }
            });
        }
        final List<PredicateProperty<Float>> renderheights = new ArrayList<>();
        if (renderHeights != null) {
            renderHeights.forEach((property, height) -> {
                try {
                    renderheights.add(new PredicateProperty<Float>(
                            LogicParser.predicate(property, info), height));
                } catch (final LogicalParserException e) {
                    OpenSignalsMain.getLogger()
                            .error("Something went wrong during the registry of a predicate in "
                                    + info.signalName + "!\nWith statement:" + property);
                    e.printStackTrace();
                }
            });
        }
        final List<SoundProperty> soundProperties = new ArrayList<>();
        if (sounds != null) {
            for (final Map.Entry<String, SoundPropertyParser> soundProperty : sounds.entrySet()) {
                final SoundPropertyParser soundProp = soundProperty.getValue();
                final SoundEvent sound = OSSounds.SOUNDS.get(soundProp.getName().toLowerCase());
                if (sound == null) {
                    OpenSignalsMain.getLogger().error(
                            "The sound with the name " + soundProp.getName() + " doesn't exists!");
                    continue;
                }
                try {
                    soundProperties.add(
                            new SoundProperty(LogicParser.predicate(soundProperty.getKey(), info),
                                    sound, soundProp.getLength()));
                } catch (final LogicalParserException e) {
                    OpenSignalsMain.getLogger()
                            .error("Something went wrong during the registry of a predicate in "
                                    + info.signalName + "!\nWith statement:"
                                    + soundProperty.getKey());
                    e.printStackTrace();
                }
            }
        }
        final List<ValuePack> redstoneValuePacks = new ArrayList<>();
        final List<ValuePack> remoteRedstoneValuePacks = new ArrayList<>();

        ImmutableList
                .of(Maps.immutableEntry(redstoneOutputs, redstoneValuePacks),
                        Maps.immutableEntry(remoteRedstoneOutputs, remoteRedstoneValuePacks))
                .forEach((entry) -> {
                    if (entry.getKey() != null) {
                        entry.getKey().forEach((key, value) -> {
                            final Predicate<Map<SEProperty, String>> predicate = LogicParser
                                    .predicate(key, info);
                            final SEProperty property = (SEProperty) info.getProperty(value);
                            entry.getValue().add(new ValuePack(property, predicate));
                        });
                    }
                });

        final List<PredicateProperty<Boolean>> doubleText = new ArrayList<>();
        if (doubleSidedText != null) {
            doubleSidedText.forEach((property, bool) -> {
                try {
                    doubleText.add(new PredicateProperty<Boolean>(
                            LogicParser.predicate(property, info), bool));
                } catch (final LogicalParserException e) {
                    OpenSignalsMain.getLogger()
                            .error("Something went wrong during the registry of a predicate in "
                                    + info.signalName + "!\nWith statement:" + property);
                    e.printStackTrace();
                }
            });
        }
        this.colors = this.colors == null ? new ArrayList<>() : this.colors;

        return new SignalProperties(placementtool, customNameRenderHeight, defaultHeight,
                ImmutableList.copyOf(signalheights), signWidth, offsetX, offsetY, signScale,
                autoscale, ImmutableList.copyOf(doubleText), textColor, canLink, colors,
                ImmutableList.copyOf(renderheights), ImmutableList.copyOf(soundProperties),
                ImmutableList.copyOf(redstoneValuePacks), defaultItemDamage,
                ImmutableList.copyOf(remoteRedstoneValuePacks), isBridgeSignal);
    }
}
