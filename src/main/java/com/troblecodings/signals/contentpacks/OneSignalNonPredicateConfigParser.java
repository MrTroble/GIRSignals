package com.troblecodings.signals.contentpacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.parser.FunctionParsingInfo;
import com.troblecodings.signals.parser.LogicalParserException;
import com.troblecodings.signals.properties.PredicatedPropertyBase.ConfigProperty;

public class OneSignalNonPredicateConfigParser {

    private String currentSignal;
    private List<String> values;

    public static final Map<Signal, List<ConfigProperty>> SHUNTINGCONFIGS = new HashMap<>();

    public static final Map<Signal, List<ConfigProperty>> RESETCONFIGS = new HashMap<>();

    private static final Gson GSON = new Gson();

    public static void loadOneSignalConfigs() {
        loadResetConfigs();
        loadOneSignalNonPredicateConfig(SHUNTINGCONFIGS, "signalconfigs/shunting");
    }

    private static void loadResetConfigs() {
        final List<Map.Entry<String, String>> list = OpenSignalsMain.contentPacks
                .getFiles("signalconfigs/reset");
        list.forEach(entry -> {
            try {
                OneSignalPredicateConfigParser.loadOneSignalPredicateConfigEntry(RESETCONFIGS,
                        entry, "signalconfigs/reset");
            } catch (final Exception e) {
                OpenSignalsMain.getLogger()
                        .error("Reset Config '" + entry.getKey() + "' is still in old "
                                + "ResetConfig system. Please update with Predicate system!");
                loadOneSignalNonPredicateConfig(RESETCONFIGS, entry, "signalconfigs/reset");
            }
        });
    }

    public static void loadOneSignalNonPredicateConfig(final Map<Signal, List<ConfigProperty>> map,
            final String internal) {
        final List<Map.Entry<String, String>> list = OpenSignalsMain.contentPacks
                .getFiles(internal);
        list.forEach(entry -> loadOneSignalNonPredicateConfig(map, entry, internal));
    }

    public static void loadOneSignalNonPredicateConfig(final Map<Signal, List<ConfigProperty>> map,
            final Map.Entry<String, String> files, final String path) {
        try {
            final OneSignalNonPredicateConfigParserV2 parser = GSON.fromJson(files.getValue(),
                    OneSignalNonPredicateConfigParserV2.class);
            for (final String currentSignal : parser.currentSignals) {
                loadConfig(map, files.getKey(), currentSignal, parser.values, path);
            }
        } catch (final Exception e) {
            OpenSignalsMain.getLogger().error("Please update your config [" + files.getKey()
                    + "] located in [" + path + "]!");
            final OneSignalNonPredicateConfigParser parser = GSON.fromJson(files.getValue(),
                    OneSignalNonPredicateConfigParser.class);
            loadConfig(map, files.getKey(), parser.currentSignal, parser.values, path);
        }

    }

    private static void loadConfig(final Map<Signal, List<ConfigProperty>> map,
            final String fileName, final String currentSignal, final List<String> values,
            final String path) {
        try {
            final Signal signal = checkSignal(currentSignal, fileName);
            if (signal == null)
                return;

            if (map.containsKey(signal)) {
                throw new LogicalParserException("A signalconfig with the signals ["
                        + signal.getSignalTypeName() + "] does alredy exists! '" + fileName
                        + "' tried to register the same signalconfig!");
            }

            final FunctionParsingInfo info = new FunctionParsingInfo(signal);
            final List<ConfigProperty> propertes = new ArrayList<>();
            for (final String property : values) {
                final String[] value = property.split("\\.");
                propertes.add(new ConfigProperty(t -> true,
                        ImmutableMap.of((SEProperty) info.getProperty(value[0]), value[1])));
            }
            map.put(signal, propertes);
        } catch (final Exception e) {
            OpenSignalsMain.getLogger().error("There was a problem loading the config [" + fileName
                    + "] located in [" + path + "]! Please check the file!");
            e.printStackTrace();
        }
    }

    private static Signal checkSignal(final String signalName, final String filename) {
        final Signal signal = Signal.SIGNALS.get(signalName.toLowerCase());
        if (signal == null) {
            OpenSignalsMain.exitMinecraftWithMessage(
                    "The signal '" + signalName + "' doesn't exists! " + "Please check " + filename
                            + " where to problem is! Valid Signals: " + Signal.SIGNALS.keySet());
        }
        return signal;
    }

    private static class OneSignalNonPredicateConfigParserV2 {

        private String[] currentSignals;
        private List<String> values;

    }
}