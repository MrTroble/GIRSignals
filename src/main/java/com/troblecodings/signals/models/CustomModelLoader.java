package com.troblecodings.signals.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import com.troblecodings.core.VectorWrapper;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.animation.SignalAnimation;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.contentpacks.SignalAnimationConfigParser;
import com.troblecodings.signals.core.SignalAngel;
import com.troblecodings.signals.parser.FunctionParsingInfo;
import com.troblecodings.signals.parser.LogicParser;
import com.troblecodings.signals.parser.LogicalParserException;

import net.minecraft.client.renderer.block.model.BuiltInModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class CustomModelLoader implements ICustomModelLoader {

    private static HashMap<String, List<SignalModelLoaderInfo>> registeredModels = new HashMap<>();

    public static final CustomModelLoader INSTANCE = new CustomModelLoader();

    private CustomModelLoader() {
    }

    private static void loadExtention(final TextureStats texturestate,
            final Map<String, ModelExtention> extention, final String modelname,
            final ModelStats states, final Models models, final FunctionParsingInfo info,
            final List<SignalModelLoaderInfo> accumulator) {

        final String blockstate = texturestate.getBlockstate();

        final Map<String, String> retexture = texturestate.getRetextures();

        for (final Map.Entry<String, Map<String, String>> extentions : texturestate.getExtentions()
                .entrySet()) {

            final String extentionName = extentions.getKey();
            final Map<String, String> extentionProperties = extentions.getValue();
            final ModelExtention extentionValues = extention.get(extentionName);
            if (extentionValues == null)
                OpenSignalsMain.exitMinecraftWithMessage(
                        String.format("There doesn't exists an extention named [%s]!",
                                extentionName) + " Valid Extentions: " + extention.keySet());

            for (final Map.Entry<String, String> entry : extentionValues.getExtention()
                    .entrySet()) {

                final String enumValue = entry.getKey();
                final String retextureValue = entry.getValue();

                for (final Map.Entry<String, String> extProperties : extentionProperties
                        .entrySet()) {
                    final String seProperty = extProperties.getKey();
                    final String retextureKey = extProperties.getValue();

                    final boolean load = texturestate.appendExtention(seProperty, enumValue,
                            retextureKey, retextureValue);

                    if (load) {

                        try {
                            accumulator
                                    .add(new SignalModelLoaderInfo(modelname,
                                            LogicParser.predicate(texturestate.getBlockstate(),
                                                    info),
                                            models.getX(texturestate.getOffsetX()),
                                            models.getY(texturestate.getOffsetY()),
                                            models.getZ(texturestate.getOffsetZ()),
                                            states.createRetexture(texturestate.getRetextures())));
                        } catch (final LogicalParserException e) {
                            OpenSignalsMain.exitMinecraftWithMessage(
                                    "There was an problem during loading an extention into "
                                            + modelname + " with the blockstate '"
                                            + texturestate.getBlockstate() + "'!" + e);
                        } finally {
                            texturestate.resetStates(blockstate, retexture);
                        }
                    }
                }
            }

        }
    }

    @Override
    public void onResourceManagerReload(final IResourceManager manager) {
        registeredModels.clear();
        final Map<String, ModelExtention> extentions = new HashMap<>();
        final Map<String, Object> modelmap = ModelStats.getfromJson("modeldefinitions");

        modelmap.forEach((filename, content) -> {
            if (filename.endsWith(".extention.json")) {
                final ModelExtention ext = (ModelExtention) content;
                extentions.put(filename.replace(".extention", ""), ext);
            }
        });

        for (final Entry<String, Object> modelstatemap : modelmap.entrySet()) {

            final String filename = modelstatemap.getKey();

            if (!filename.endsWith(".extention.json")) {
                final List<SignalModelLoaderInfo> accumulator = new ArrayList<>();

                final ModelStats content = (ModelStats) modelstatemap.getValue();
                final String lowercaseName = filename.replace(".json", "").toLowerCase();
                final Signal signaltype = Signal.SIGNALS.get(lowercaseName);

                if (signaltype == null) {
                    throw new IllegalArgumentException("There doesn't exists a signalsystem named "
                            + lowercaseName + "! Allowed Signals: " + Signal.SIGNALS.keySet());
                }

                final FunctionParsingInfo parsinginfo = new FunctionParsingInfo(signaltype);

                for (final Map.Entry<String, Models> modelsmap : content.getModels().entrySet()) {

                    final String modelname = modelsmap.getKey();
                    final Models modelstats = modelsmap.getValue();

                    for (int i = 0; i < modelstats.getTexture().size(); i++) {

                        final TextureStats texturestate = modelstats.getTexture().get(i);

                        final String blockstate = texturestate.getBlockstate();

                        Predicate<ModelInfoWrapper> state = null;

                        boolean extentionloaded = false;

                        if (!texturestate.isautoBlockstate()) {

                            final Map<String, Map<String, String>> texturemap = texturestate
                                    .getExtentions();

                            if (texturemap != null && !texturemap.isEmpty()) {

                                loadExtention(texturestate, extentions, modelname, content,
                                        modelstats, parsinginfo, accumulator);

                                extentionloaded = true;
                            }

                            if (!extentionloaded) {

                                try {
                                    state = LogicParser.predicate(blockstate, parsinginfo);
                                } catch (final LogicalParserException e) {
                                    OpenSignalsMain.getLogger()
                                            .error("There was an problem during loading "
                                                    + modelname + " with the blockstate '"
                                                    + texturestate.getBlockstate() + " '!");
                                    e.printStackTrace();
                                    return;
                                }
                            }
                        }

                        if ((state != null || texturestate.isautoBlockstate())
                                && !extentionloaded) {
                            accumulator.add(new SignalModelLoaderInfo(modelname,
                                    texturestate.isautoBlockstate()
                                            ? new ImplAutoBlockStatePredicate()
                                            : state,
                                    modelstats.getX(texturestate.getOffsetX()),
                                    modelstats.getY(texturestate.getOffsetY()),
                                    modelstats.getZ(texturestate.getOffsetZ()),
                                    content.createRetexture(texturestate.getRetextures())));
                        } else if (state == null && !texturestate.isautoBlockstate()
                                && !extentionloaded) {
                            OpenSignalsMain.getLogger().error("The predicate of " + modelname
                                    + " in " + filename + " is null! This shouldn't be the case!");
                            return;
                        }
                    }
                }
                SignalAnimationConfigParser.loadAllAnimations();
                final Map<Entry<String, VectorWrapper>, List<SignalAnimation>> animations = //
                        SignalAnimationConfigParser.ALL_ANIMATIONS.get(signaltype);
                if (animations != null) {
                    animations.keySet()
                            .forEach(entry -> accumulator.add(
                                    new SignalModelLoaderInfo(entry.getKey(), predicate -> false, 0,
                                            0, 0, new HashMap<>()).setOnAnimation()));
                }
                registeredModels.put(lowercaseName, accumulator);
            }
        }
    }

    @Override
    public boolean accepts(final ResourceLocation modelLocation) {
        if (!modelLocation.getResourceDomain().equals(OpenSignalsMain.MODID))
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
                Enum.valueOf(SignalAngel.class, strs[1].toUpperCase()));
    }
}