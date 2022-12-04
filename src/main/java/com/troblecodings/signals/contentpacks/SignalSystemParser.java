package com.troblecodings.signals.contentpacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.blocks.Signal.SignalPropertiesBuilder;
import com.troblecodings.signals.models.parser.FunctionParsingInfo;
import com.troblecodings.signals.utils.FileReader;

import net.minecraftforge.common.property.IUnlistedProperty;

public class SignalSystemParser {

    private SignalPropertiesBuilder systemProperties;
    private List<SEPropertyParser> seProperties;

    private static transient final Gson GSON = new Gson();

    public List<SEPropertyParser> getSEProperties() {
        return seProperties;
    }

    public static Map<String, SignalSystemParser> getSignalSystems(final String directory) {

        final Map<String, String> systems = FileReader.readallFilesfromDierectory(directory);

        final Map<String, SignalSystemParser> properties = new HashMap<>();

        if (systems.isEmpty()) {
            OpenSignalsMain.getLogger().warn("No signalsystems found at '" + directory + "'!");
            return properties;
        }

        systems.forEach((name, property) -> {
            if (!name.contains("autogen"))
                properties.put(name, GSON.fromJson(property, SignalSystemParser.class));
        });

        return properties;
    }

    @SuppressWarnings("rawtypes")
    public Signal createSignalSystem(final String fileName) {

        final String name = fileName.replace(".json", "").replace("_", "");

        final List<IUnlistedProperty> properties = new ArrayList<>();

        final FunctionParsingInfo info = new FunctionParsingInfo(name, properties);
        try {
            seProperties.forEach(prop -> properties.add(prop.createSEProperty(info)));
        } catch (final Exception e) {
            throw new ContentPackException(
                    String.format("Error in file %s caused by parsing!", fileName), e);
        }
        Signal.nextConsumer = list -> {
            list.addAll(properties);
        };

        return (Signal) new Signal(systemProperties.typename(name).build(info))
                .setRegistryName(OpenSignalsMain.MODID, name).setUnlocalizedName(name);
    }
}