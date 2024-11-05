package com.troblecodings.signals.proxy;

import java.util.Map;

import com.troblecodings.core.UIInit;
import com.troblecodings.core.net.NetworkHandler;
import com.troblecodings.guilib.ecs.GuiHandler;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.PathwayRequester;
import com.troblecodings.signals.blocks.RedstoneIO;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.blocks.SignalBox;
import com.troblecodings.signals.blocks.SignalController;
import com.troblecodings.signals.blocks.TrainNumberBlock;
import com.troblecodings.signals.contentpacks.ChangeConfigParser;
import com.troblecodings.signals.contentpacks.OneSignalNonPredicateConfigParser;
import com.troblecodings.signals.contentpacks.OneSignalPredicateConfigParser;
import com.troblecodings.signals.contentpacks.SignalAnimationConfigParser;
import com.troblecodings.signals.contentpacks.SubsidiarySignalParser;
import com.troblecodings.signals.guis.ContainerPathwayRequester;
import com.troblecodings.signals.guis.ContainerPlacementtool;
import com.troblecodings.signals.guis.ContainerSignalBox;
import com.troblecodings.signals.guis.ContainerSignalBridge;
import com.troblecodings.signals.guis.ContainerSignalController;
import com.troblecodings.signals.guis.ContainerTrainNumber;
import com.troblecodings.signals.guis.NamableContainer;
import com.troblecodings.signals.handler.NameHandler;
import com.troblecodings.signals.handler.SignalStateHandler;
import com.troblecodings.signals.init.OSBlocks;
import com.troblecodings.signals.init.OSSounds;
import com.troblecodings.signals.items.Placementtool;
import com.troblecodings.signals.signalbridge.SignalBridgeBasicBlock;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void initModEvent(final FMLPreInitializationEvent event) {
        final Map.Entry<GuiHandler, NetworkHandler> init = UIInit.initCommon(OpenSignalsMain.MODID,
                OpenSignalsMain.getLogger(), OpenSignalsMain.isDebug());
        OpenSignalsMain.handler = init.getKey();
        OpenSignalsMain.network = init.getValue();

        SignalStateHandler.init();
        NameHandler.init();
        OSSounds.init();
        OSBlocks.init();

        OpenSignalsMain.handler.addServer(Placementtool.class, ContainerPlacementtool::new);
        OpenSignalsMain.handler.addServer(SignalController.class, ContainerSignalController::new);
        OpenSignalsMain.handler.addServer(SignalBox.class, ContainerSignalBox::new);
        OpenSignalsMain.handler.addServer(Signal.class, NamableContainer::new);
        OpenSignalsMain.handler.addServer(RedstoneIO.class, NamableContainer::new);
        OpenSignalsMain.handler.addServer(PathwayRequester.class, ContainerPathwayRequester::new);
        OpenSignalsMain.handler.addServer(TrainNumberBlock.class, ContainerTrainNumber::new);
        OpenSignalsMain.handler.addServer(SignalBridgeBasicBlock.class, ContainerSignalBridge::new);
    }

    public void init(final FMLInitializationEvent event) {
        OneSignalNonPredicateConfigParser.loadOneSignalConfigs();
        ChangeConfigParser.loadChangeConfigs();
        OneSignalPredicateConfigParser.loadAllOneSignalPredicateConfigs();
        SubsidiarySignalParser.loadAllSubsidiarySignals();
        SignalAnimationConfigParser.loadAllAnimations();
    }
}