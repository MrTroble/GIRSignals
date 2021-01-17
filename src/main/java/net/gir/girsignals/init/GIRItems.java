package net.gir.girsignals.init;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import net.gir.girsignals.GirsignalsMain;
import net.gir.girsignals.items.Linkingtool;
import net.gir.girsignals.items.Placementtool;
import net.gir.girsignals.tileentitys.SignalTileEnity;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

public class GIRItems {

	public static final Linkingtool LINKING_TOOL = new Linkingtool();
	public static final Placementtool PLACEMENT_TOOL = new Placementtool();
	public static final Item DEBUG_ITEM = new Item() {
		public net.minecraft.util.EnumActionResult onItemUse(net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World worldIn, net.minecraft.util.math.BlockPos pos, net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing facing, float hitX, float hitY, float hitZ) {
			TileEntity ent = worldIn.getTileEntity(pos);
			if(ent != null && ent instanceof SignalTileEnity) {
				SignalTileEnity tile = (SignalTileEnity) ent;
				tile.setCustomName(" AA ");
			}
			return EnumActionResult.PASS;
		};
	};
	
	public static ArrayList<Item> registeredItems = new ArrayList<>();
	
	public static void init() {
		Field[] fields = GIRItems.class.getFields();
		for(Field field : fields) {
			int modifiers = field.getModifiers();
			if(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers)) {
				String name = field.getName().toLowerCase().replace("_", "");
				try {
					Item item = (Item) field.get(null);
					item.setRegistryName(new ResourceLocation(GirsignalsMain.MODID, name));
					item.setUnlocalizedName(name);
					registeredItems.add(item);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@SubscribeEvent
	public static void registerItem(RegistryEvent.Register<Item> event) {
		IForgeRegistry<Item> registry = event.getRegistry();
		registeredItems.forEach(registry::register);
	}

}
