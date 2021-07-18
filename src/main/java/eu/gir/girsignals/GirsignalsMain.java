package eu.gir.girsignals;

import static eu.gir.girsignals.debug.Debug.DEBUG;
import static eu.gir.girsignals.debug.Debug.INSTANCE;
import static eu.gir.girsignals.debug.Debug.SUBCOMMANDS;

import org.apache.logging.log4j.Logger;

import eu.gir.girsignals.debug.NetworkDebug;
import eu.gir.girsignals.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = GirsignalsMain.MODID, dependencies = "after:opencomputers", acceptedMinecraftVersions = "[1.12.2]")
public class GirsignalsMain {

	@Instance
	private static GirsignalsMain instance;
	public static final String MODID = "girsignals";

	public static GirsignalsMain getInstance() {
		return instance;
	}

	@SidedProxy(serverSide = "eu.gir.girsignals.proxy.CommonProxy", clientSide = "eu.gir.girsignals.proxy.ClientProxy")
	public static CommonProxy PROXY;
	public static Logger LOG;

	@EventHandler
	public void preinit(FMLPreInitializationEvent event) {
		LOG =event.getModLog();
		PROXY.preinit(event);
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		PROXY.init(event);
	}

	@EventHandler
	public void postinit(FMLPostInitializationEvent event) {
		PROXY.postinit(event);
	}
	
	@EventHandler
	public static void register(FMLServerStartingEvent event) {
		if (!DEBUG)
			return;
		SUBCOMMANDS.clear();
		SUBCOMMANDS.put("network", NetworkDebug::trigger);
		SUBCOMMANDS.put("networkmark", NetworkDebug::mark);
		event.registerServerCommand(INSTANCE);
	}

}
