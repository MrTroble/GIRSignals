package com.troblecodings.signals.signalbox.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.contentpacks.ChangeConfigParser;
import com.troblecodings.signals.contentpacks.OneSignalNonPredicateConfigParser;
import com.troblecodings.signals.contentpacks.OneSignalPredicateConfigParser;
import com.troblecodings.signals.core.LoadHolder;
import com.troblecodings.signals.core.SignalStateListener;
import com.troblecodings.signals.core.SignalStateLoadHoler;
import com.troblecodings.signals.enums.PathType;
import com.troblecodings.signals.handler.SignalStateHandler;
import com.troblecodings.signals.handler.SignalStateInfo;
import com.troblecodings.signals.properties.PredicatedPropertyBase.ConfigProperty;
import com.troblecodings.signals.signalbox.SignalBoxPathway;

public final class SignalConfig {

    private static final LoadHolder<Class<SignalConfig>> LOAD_HOLDER = new LoadHolder<>(
            SignalConfig.class);

    private final SignalBoxPathway pathway;

    public SignalConfig(final SignalBoxPathway pathway) {
        this.pathway = pathway;
    }

    public void change(final ConfigInfo info) {
        final Signal currentSignal = info.currentinfo.signal;
        if (info.type.equals(PathType.NORMAL)) {
            if (info.nextinfo != null && info.nextinfo.isValid()) {
                final Signal nextSignal = info.nextinfo.signal;
                final List<ConfigProperty> values = ChangeConfigParser.CHANGECONFIGS
                        .get(Maps.immutableEntry(currentSignal, nextSignal));
                if (values != null) {
                    changeIfPresent(values, info);
                } else {
                    loadDefault(info);
                }
            } else {
                loadDefault(info);
            }
        } else if (info.type.equals(PathType.SHUNTING)) {
            final List<ConfigProperty> shuntingValues = OneSignalNonPredicateConfigParser.SHUNTINGCONFIGS
                    .get(currentSignal);
            if (shuntingValues != null && info.currentinfo.isValid()) {
                loadWithoutPredicate(shuntingValues, info.currentinfo);
            }
        }
    }

    private void loadDefault(final ConfigInfo info) {
        if (!info.currentinfo.isValid())
            return;
        final List<ConfigProperty> defaultValues = OneSignalPredicateConfigParser.DEFAULTCONFIGS
                .get(info.currentinfo.signal);
        if (defaultValues != null) {
            changeIfPresent(defaultValues, info);
        }
    }

    public static void reset(final ResetInfo info) {
        final List<ConfigProperty> resetValues = OneSignalNonPredicateConfigParser.RESETCONFIGS
                .get(info.current.signal);
        if (resetValues == null)
            return;
        loadSignalAndRunTask(info.current, (stateInfo, oldProperties, _u) -> {
            final Map<Class<?>, Object> object = new HashMap<>();
            object.put(Boolean.class, info.isRepeater);
            object.put(Map.class, oldProperties);

            final Map<SEProperty, String> propertiesToSet = new HashMap<>();
            resetValues.forEach(property -> {
                if (property.test(object)) {
                    propertiesToSet.putAll(property.state.entrySet().stream()
                            .filter(entry -> oldProperties.containsKey(entry.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                }
            });
            if (!propertiesToSet.isEmpty())
                SignalStateHandler.setStates(info.current, propertiesToSet);
        });
    }

    public void loadDisable(final ConfigInfo info) {
        final List<ConfigProperty> disableValues = OneSignalPredicateConfigParser.DISABLECONFIGS
                .get(info.currentinfo.signal);
        if (disableValues != null) {
            changeIfPresent(disableValues, info);
        }
    }

    private void changeIfPresent(final List<ConfigProperty> values, final ConfigInfo info) {
        loadSignalAndRunTask(info.currentinfo, (stateInfo, oldProperties, _u) -> {
            if (info.nextinfo != null) {
                loadSignalAndRunTask(info.nextinfo, (nextInfo, nextProperties, _u2) -> {
                    changeSignals(values, info, oldProperties, nextProperties);
                });
            } else {
                changeSignals(values, info, oldProperties, null);
            }
        });
    }

    private void changeSignals(final List<ConfigProperty> values, final ConfigInfo info,
            final Map<SEProperty, String> oldProperties,
            final Map<SEProperty, String> nextProperties) {
        final Map<Class<?>, Object> object = new HashMap<>();
        object.put(Map.class, nextProperties != null ? nextProperties : new HashMap<>());
        object.put(Integer.class, info.speed);
        object.put(String.class, info.zs2Value);
        object.put(Boolean.class, info.isSignalRepeater);
        final Map<SEProperty, String> propertiesToSet = new HashMap<>();
        values.forEach(property -> {
            if (property.test(object)) {
                propertiesToSet.putAll(property.state.entrySet().stream()
                        .filter(entry -> oldProperties.containsKey(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
        });
        if (!propertiesToSet.isEmpty()) {
            SignalStateHandler.setStates(info.currentinfo, propertiesToSet);
            updatePreviousPathway(info.currentinfo);
        }
    }

    private void loadWithoutPredicate(final List<ConfigProperty> values,
            final SignalStateInfo current) {
        if (values != null) {
            loadSignalAndRunTask(current, (info, oldProperties, _u) -> {
                final Map<SEProperty, String> propertiesToSet = new HashMap<>();
                values.forEach(property -> {
                    propertiesToSet.putAll(property.state.entrySet().stream()
                            .filter(entry -> oldProperties.containsKey(entry.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                });
                if (!propertiesToSet.isEmpty()) {
                    SignalStateHandler.setStates(current, propertiesToSet);
                    updatePreviousPathway(current);
                }
            });
        }
    }

    private void updatePreviousPathway(final SignalStateInfo info) {
        // info.world.getMinecraftServer().addScheduledTask(() ->
        // pathway.updatePrevious());
    }

    private static void loadSignalAndRunTask(final SignalStateInfo info,
            final SignalStateListener task) {
        final boolean isSignalLoaded = SignalStateHandler.isSignalLoaded(info);
        if (!isSignalLoaded) {
            SignalStateHandler.loadSignal(new SignalStateLoadHoler(info, LOAD_HOLDER));
            task.andThen((_u1, _u2, _u3) -> SignalStateHandler
                    .unloadSignal(new SignalStateLoadHoler(info, LOAD_HOLDER)));
        }
        SignalStateHandler.runTaskWhenSignalLoaded(info, task);
    }
}
