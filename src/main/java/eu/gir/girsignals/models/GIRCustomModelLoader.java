package eu.gir.girsignals.models;

import static eu.gir.girsignals.models.parser.PredicateHolder.hasAndIs;
import static eu.gir.girsignals.models.parser.PredicateHolder.hasAndIsNot;
import static eu.gir.girsignals.models.parser.PredicateHolder.with;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;

import eu.gir.girsignals.EnumSignals.WNCross;
import eu.gir.girsignals.EnumSignals.WNNormal;
import eu.gir.girsignals.GirsignalsMain;
import eu.gir.girsignals.blocks.Signal;
import eu.gir.girsignals.blocks.Signal.SignalAngel;
import eu.gir.girsignals.blocks.boards.SignalWN;
import eu.gir.girsignals.models.parser.FunctionParsingInfo;
import eu.gir.girsignals.models.parser.LogicParser;
import eu.gir.girsignals.models.parser.LogicalParserException;
import net.minecraft.client.renderer.block.model.BuiltInModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GIRCustomModelLoader implements ICustomModelLoader {

    private static HashMap<String, Consumer<SignalCustomModel>> registeredModels = new HashMap<>();

    private static final Map<String, Signal> TRANSLATION_TABLE = new HashMap<>();

    private static final List<Signal> SIGNALS = new ArrayList<>(Signal.SIGNALLIST);

    static {

        SIGNALS.forEach(signal -> {

            TRANSLATION_TABLE.put(signal.getSignalTypeName(), signal);
        });
    }

    @Override
    public void onResourceManagerReload(final IResourceManager resourceManager) {

        registeredModels.clear();

        final Map<String, ModelExtention> extentions = new HashMap<>();

        final Map<String, Object> modelmap = ModelStats
                .getfromJson("/assets/girsignals/modeldefinitions");

        modelmap.forEach((filename, content) -> {

            if (filename.endsWith(".extention.json")) {

                final ModelExtention ext = (ModelExtention) content;
                extentions.put(filename.replace(".extention", ""), ext);
            }
        });

        for (final Entry<String, Object> modelstatemap : modelmap.entrySet()) {

            final String filename = modelstatemap.getKey();

            if (!filename.endsWith(".extention.json")) {

                final ModelStats content = (ModelStats) modelstatemap.getValue();

                Signal signaltype = null;

                for (final Map.Entry<String, Signal> entry : TRANSLATION_TABLE.entrySet()) {

                    final String signalname = entry.getKey();
                    final Signal signal = entry.getValue();

                    if (filename.replace(".json", "").equalsIgnoreCase(signalname)) {

                        signaltype = signal;
                    }
                }

                if (signaltype == null) {

                    GirsignalsMain.log.error("There doesn't exists a signalsystem named "
                            + filename.replace(".json", "") + "!");
                    return;
                }

                final FunctionParsingInfo parsinginfo = new FunctionParsingInfo(signaltype);

                registeredModels.put(
                        signaltype.getRegistryName().toString().replace("girsignals:", ""), cm -> {

                            for (final Map.Entry<String, Models> modelsmap : content.getModels()
                                    .entrySet()) {

                                final String modelname = modelsmap.getKey();
                                final Models modelstats = modelsmap.getValue();

                                for (int i = 0; i < modelstats.getTexture().size(); i++) {

                                    final TextureStats texturestate = modelstats.getTexture()
                                            .get(i);

                                    final String blockstate = texturestate.getBlockstate();

                                    Predicate<IExtendedBlockState> state = null;

                                    boolean extentionloaded = false;

                                    if (!texturestate.isautoBlockstate()) {

                                        final Map<String, Map<String, String>> texturemap = texturestate
                                                .getExtentions();

                                        if (texturemap != null && !texturemap.isEmpty()) {

                                            cm.loadExtention(texturestate, extentions, modelname,
                                                    content, modelstats, parsinginfo);

                                            extentionloaded = true;
                                        }

                                        if (!extentionloaded) {

                                            try {
                                                state = LogicParser.predicate(blockstate,
                                                        parsinginfo);
                                            } catch (final LogicalParserException e) {
                                                GirsignalsMain.log.error(
                                                        "There was an problem during loading "
                                                                + modelname
                                                                + " with the blockstate '"
                                                                + texturestate.getBlockstate()
                                                                + " '!");
                                                e.printStackTrace();
                                            }
                                        }
                                    }

                                    if (texturestate.isautoBlockstate()) {

                                        cm.register(modelname, new ImplAutoBlockstatePredicate(),
                                                modelstats.getX(texturestate.getOffsetX()),
                                                modelstats.getY(texturestate.getOffsetY()),
                                                modelstats.getZ(texturestate.getOffsetZ()),
                                                ModelStats.createRetexture(
                                                        texturestate.getRetextures(),
                                                        content.getTextures()));

                                    } else if (state != null && !texturestate.isautoBlockstate()
                                            && !extentionloaded) {

                                        cm.register(modelname, state,
                                                modelstats.getX(texturestate.getOffsetX()),
                                                modelstats.getY(texturestate.getOffsetY()),
                                                modelstats.getZ(texturestate.getOffsetZ()),
                                                ModelStats.createRetexture(
                                                        texturestate.getRetextures(),
                                                        content.getTextures()));

                                    } else if (state == null && !texturestate.isautoBlockstate()
                                            && !extentionloaded) {
                                        GirsignalsMain.log.warn(
                                                "The predicate of " + modelname + " in " + filename
                                                        + " is null! This shouldn´t be the case!");
                                    }
                                }
                            }
                        });
            }
        }
        
        registeredModels.put("wnsignal", cm -> {
            cm.register("wn/wn1_2", hasAndIsNot(SignalWN.WNTYPE)
                    .and(with(SignalWN.WNNORMAL, wn -> wn.equals(WNNormal.OFF))), 0);
            cm.register("wn/wn1_2",
                    hasAndIsNot(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNNORMAL, wn -> wn.equals(WNNormal.WN1))),
                    0, "lamp_1", "girsignals:blocks/lamps/lamp_white", "lamp_3",
                    "girsignals:blocks/lamps/lamp_white");
            cm.register("wn/wn1_2",
                    hasAndIsNot(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNNORMAL, wn -> wn.equals(WNNormal.WN2))),
                    0, "lamp_2", "girsignals:blocks/lamps/lamp_white", "lamp_3",
                    "girsignals:blocks/lamps/lamp_white");
            cm.register("wn/wn1_2",
                    hasAndIsNot(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNNORMAL, wn -> wn.equals(WNNormal.BLINK))),
                    0, "lamp_3", "girsignals:blocks/lamps/lamp_white_blink");
            cm.register("wn/wn3_6", hasAndIs(SignalWN.WNTYPE)
                    .and(with(SignalWN.WNCROSS, wn -> wn.equals(WNCross.OFF))), 0);
            cm.register("wn/wn3_6",
                    hasAndIs(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNCROSS, wn -> wn.equals(WNCross.WN3))),
                    0, "lamp_1", "girsignals:blocks/lamps/lamp_white", "lamp_3",
                    "girsignals:blocks/lamps/lamp_white", "lamp_5",
                    "girsignals:blocks/lamps/lamp_white");
            cm.register("wn/wn3_6",
                    hasAndIs(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNCROSS, wn -> wn.equals(WNCross.WN4))),
                    0, "lamp_1", "girsignals:blocks/lamps/lamp_white", "lamp_2",
                    "girsignals:blocks/lamps/lamp_white", "lamp_4",
                    "girsignals:blocks/lamps/lamp_white");
            cm.register("wn/wn3_6",
                    hasAndIs(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNCROSS, wn -> wn.equals(WNCross.WN5))),
                    0, "lamp_1", "girsignals:blocks/lamps/lamp_white", "lamp_3",
                    "girsignals:blocks/lamps/lamp_white", "lamp_4",
                    "girsignals:blocks/lamps/lamp_white");
            cm.register("wn/wn3_6",
                    hasAndIs(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNCROSS, wn -> wn.equals(WNCross.WN6))),
                    0, "lamp_1", "girsignals:blocks/lamps/lamp_white", "lamp_2",
                    "girsignals:blocks/lamps/lamp_white", "lamp_5",
                    "girsignals:blocks/lamps/lamp_white");
            cm.register("wn/wn3_6",
                    hasAndIs(SignalWN.WNTYPE)
                            .and(with(SignalWN.WNCROSS, wn -> wn.equals(WNCross.BLINK))),
                    0, "lamp_1", "girsignals:blocks/lamps/lamp_white_blink");
        });
    }

    @Override
    public boolean accepts(final ResourceLocation modelLocation) {
        if (!modelLocation.getResourceDomain().equals(GirsignalsMain.MODID))
            return false;
        return registeredModels.containsKey(modelLocation.getResourcePath())
                || modelLocation.getResourcePath().equals("ghostblock");
    }

    @Override
    public IModel loadModel(final ResourceLocation modelLocation) throws Exception {
        if (modelLocation.getResourcePath().equals("ghostblock"))
            return (state, format, bak) -> new BuiltInModel(ItemCameraTransforms.DEFAULT,
                    ItemOverrideList.NONE);
        final ModelResourceLocation mrl = (ModelResourceLocation) modelLocation;
        final String[] strs = mrl.getVariant().split("=");
        if (strs.length < 2)
            return new SignalCustomModel(registeredModels.get(modelLocation.getResourcePath()),
                    SignalAngel.ANGEL0);
        return new SignalCustomModel(registeredModels.get(modelLocation.getResourcePath()),
                SignalAngel.valueOf(strs[1].toUpperCase()));
    }
}
