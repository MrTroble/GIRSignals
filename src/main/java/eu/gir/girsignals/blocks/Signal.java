package eu.gir.girsignals.blocks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import eu.gir.girsignals.GIRSignalsConfig;
import eu.gir.girsignals.SEProperty;
import eu.gir.girsignals.SEProperty.ChangeableStage;
import eu.gir.girsignals.items.Placementtool;
import eu.gir.girsignals.tileentitys.SignalTileEnity;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class Signal extends Block implements ITileEntityProvider, IConfigUpdatable {

	public static enum SignalAngel implements IStringSerializable {
		ANGEL0, ANGEL22P5, ANGEL45, ANGEL67P5, ANGEL90, ANGEL112P5, ANGEL135, ANGEL157P5, ANGEL180, ANGEL202P5,
		ANGEL225, ANGEL247P5, ANGEL270, ANGEL292P5, ANGEL315, ANGEL337P5;

		@Override
		public String getName() {
			return this.name().toLowerCase();
		}

		public float getAngel() {
			return this.ordinal() * 22.5f;
		}
	}

	public static class SignalProperties {
		public final Placementtool placementtool;
		public final String signalTypeName;
		public final float customNameRenderHeight;
		public final int height;
		public final float signWidth;
		public final float offsetX;
		public final float offsetY;
		public final float signScale;
		public final boolean canLink;

		public SignalProperties(final Placementtool placementtool, final String signalTypeName,
				final float customNameRenderHeight, final int height, final float signWidth, final float offsetX,
				final float offsetY, final float signScale, final boolean canLink) {
			this.placementtool = placementtool;
			this.signalTypeName = signalTypeName;
			this.customNameRenderHeight = customNameRenderHeight;
			this.height = height;
			this.signWidth = signWidth;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.signScale = signScale;
			this.canLink = canLink;
		}
	}

	public static class SignalPropertiesBuilder {
		private Placementtool placementtool = null;
		private String signalTypeName = null;
		private int height = 1;
		private float customNameRenderHeight = -1;
		private float signWidth = 22;
		private float offsetX = 0;
		private float offsetY = 0;
		private float signScale = 1;
		private boolean canLink = true;

		public SignalPropertiesBuilder(final Placementtool placementtool, final String signalTypeName) {
			this.placementtool = placementtool;
			this.signalTypeName = signalTypeName;
		}

		public SignalProperties build() {
			return new SignalProperties(placementtool, signalTypeName, customNameRenderHeight, height, signWidth,
					offsetX, offsetY, signScale, canLink);
		}

		public SignalPropertiesBuilder signWidth(float signWidth) {
			this.signWidth = signWidth;
			return this;
		}

		public SignalPropertiesBuilder offsetX(float offsetX) {
			this.offsetX = offsetX;
			return this;
		}

		public SignalPropertiesBuilder offsetY(float offsetY) {
			this.offsetY = offsetY;
			return this;
		}

		public SignalPropertiesBuilder signScale(float signScale) {
			this.signScale = signScale;
			return this;
		}

		public SignalPropertiesBuilder height(int height) {
			this.height = height;
			return this;
		}

		public SignalPropertiesBuilder signHeight(float customNameRenderHeight) {
			this.customNameRenderHeight = customNameRenderHeight;
			return this;
		}

		public SignalPropertiesBuilder noLink() {
			this.canLink = false;
			return this;
		}

	}

	public static final SignalPropertiesBuilder builder(final Placementtool placementtool,
			final String signalTypeName) {
		return new SignalPropertiesBuilder(placementtool, signalTypeName);
	}

	public static final ArrayList<Signal> SIGNALLIST = new ArrayList<Signal>();

	public static final PropertyEnum<SignalAngel> ANGEL = PropertyEnum.create("angel", SignalAngel.class);
	public static final SEProperty<Boolean> CUSTOMNAME = SEProperty.of("customname", false,
			ChangeableStage.AUTOMATICSTAGE);

	private final int ID;
	protected final SignalProperties prop;

	public Signal(final SignalProperties prop) {
		super(Material.ROCK);
		this.prop = prop;
		setDefaultState(getDefaultState().withProperty(ANGEL, SignalAngel.ANGEL0));
		ID = SIGNALLIST.size();
		SIGNALLIST.add(this);
		prop.placementtool.addSignal(this);
	}

	@Override
	public void dropBlockAsItemWithChance(World worldIn, BlockPos pos, IBlockState state, float chance, int fortune) {
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		final SignalTileEnity te = (SignalTileEnity) source.getTileEntity(pos);
		if (te == null)
			return FULL_BLOCK_AABB;
		return FULL_BLOCK_AABB.expand(0, getHeight(te.getProperties()), 0);
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
		return getBoundingBox(blockState, worldIn, pos);
	}

	public static ItemStack pickBlock(EntityPlayer player, Item item) {
		// Compatibility issues with other mods ...
		if (!Minecraft.getMinecraft().gameSettings.keyBindPickBlock.isKeyDown())
			return new ItemStack(item);
		for (int k = 0; k < InventoryPlayer.getHotbarSize(); ++k) {
			if (player.inventory.getStackInSlot(k).getItem().equals(item)) {
				player.inventory.currentItem = k;
				return ItemStack.EMPTY;
			}
		}
		return new ItemStack(item);
	}

	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos,
			EntityPlayer player) {
		return pickBlock(player, prop.placementtool);
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		int x = Math.abs((int) (Math.abs(placer.rotationYaw) / 22.5f)) % 16;
		return getDefaultState().withProperty(ANGEL, SignalAngel.values()[x]);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(ANGEL, SignalAngel.values()[meta]);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(ANGEL).ordinal();
	}

	@Override
	public IBlockState withRotation(IBlockState state, Rotation rot) {
		return state.withRotation(rot);
	}

	@Override
	public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
		return state.withMirror(mirrorIn);
	}

	@Override
	public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
		return layer.equals(BlockRenderLayer.CUTOUT_MIPPED);
	}

	@SuppressWarnings("unchecked")
	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
		IExtendedBlockState ebs = (IExtendedBlockState) super.getExtendedState(state, world, pos);
		SignalTileEnity entity = (SignalTileEnity) world.getTileEntity(pos);
		if (entity != null)
			return entity.accumulate((b, p, o) -> b.withProperty(p, o), ebs);
		return ebs;
	}

	@Override
	public boolean isTranslucent(IBlockState state) {
		return true;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}

	private IUnlistedProperty<?>[] propcache = null;

	private void buildCacheIfNull() {
		if (propcache == null) {
			Collection<IUnlistedProperty<?>> props = ((ExtendedBlockState) this.getBlockState())
					.getUnlistedProperties();
			propcache = props.toArray(new IUnlistedProperty[props.size()]);
		}
	}

	public int getIDFromProperty(final IUnlistedProperty<?> propertyIn) {
		buildCacheIfNull();
		for (int i = 0; i < propcache.length; i++)
			if (propcache[i].equals(propertyIn))
				return i;
		return -1;
	}

	public IUnlistedProperty<?> getPropertyFromID(int id) {
		buildCacheIfNull();
		return propcache[id];
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected BlockStateContainer createBlockState() {
		ArrayList<IUnlistedProperty> prop = new ArrayList<>();
		if (!this.getClass().equals(Signal.class)) {
			for (Field f : this.getClass().getDeclaredFields()) {
				int mods = f.getModifiers();
				if (Modifier.isFinal(mods) && Modifier.isStatic(mods) && Modifier.isPublic(mods)) {
					try {
						prop.add((IUnlistedProperty) f.get(null));
					} catch (IllegalArgumentException | IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
		}
		prop.add(CUSTOMNAME);
		return new ExtendedBlockState(this, new IProperty<?>[] { ANGEL },
				prop.toArray(new IUnlistedProperty[prop.size()]));
	}

	@Override
	public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int id, int param) {
		return true;
	}

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new SignalTileEnity();
	}

	public String getSignalTypeName() {
		return this.prop.signalTypeName;
	}

	public int getID() {
		return ID;
	}

	@Override
	public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
		super.breakBlock(worldIn, pos, state);

		if (!worldIn.isRemote)
			GhostBlock.destroyUpperBlock(worldIn, pos);
	}

	public int getHeight(final HashMap<SEProperty<?>, Object> map) {
		return this.prop.height;
	}

	public boolean canHaveCustomname(final HashMap<SEProperty<?>, Object> map) {
		return this.prop.customNameRenderHeight != -1;
	}

	@Override
	public String toString() {
		return this.getUnlocalizedName();
	}

	public final boolean canBeLinked() {
		return this.prop.canLink;
	}

	@SideOnly(Side.CLIENT)
	public int colorMultiplier(IBlockState state, IBlockAccess worldIn, BlockPos pos, int tintIndex) {
		return 0;
	}

	@SideOnly(Side.CLIENT)
	public boolean hasCostumColor() {
		return false;
	}

	@SideOnly(Side.CLIENT)
	public void renderOverlay(final double x, final double y, final double z, final SignalTileEnity te,
			final FontRenderer font) {
		this.renderOverlay(x, y, z, te, font, this.prop.customNameRenderHeight);
	}

	@SideOnly(Side.CLIENT)
	public void renderOverlay(final double x, final double y, final double z, final SignalTileEnity te,
			final FontRenderer font, final float renderHeight) {
		if (renderHeight == -1)
			return;
		final World world = te.getWorld();
		final BlockPos pos = te.getPos();
		final IBlockState state = world.getBlockState(pos);
		final SignalAngel face = state.getValue(Signal.ANGEL);
		final float angel = face.getAngel();

		final String[] display = te.getDisplayName().getFormattedText().split("\\[n\\]");
		final float width = this.prop.signWidth;
		final float offsetX = this.prop.offsetX;
		final float offsetZ = this.prop.offsetY;
		final float scale = this.prop.signScale;

		GlStateManager.enableAlpha();
		GlStateManager.pushMatrix();
		GlStateManager.translate(x + 0.5f, y + renderHeight, z + 0.5f);
		GlStateManager.scale(0.015f * scale, -0.015f * scale, 0.015f * scale);
		GlStateManager.rotate(angel, 0, 1, 0);
		GlStateManager.translate(width / 2 + offsetX, 0, -4.2f + offsetZ);
		GlStateManager.scale(-1f, 1f, 1f);
		for (int i = 0; i < display.length; i++) {
			font.drawSplitString(display[i], 0, (int) (i * scale * 2.8f), (int) width, 0);
		}
		GlStateManager.popMatrix();
	}

	public Placementtool getPlacementtool() {
		return this.prop.placementtool;
	}

	@Override
	public void updateConfigValues() {
		setLightLevel(GIRSignalsConfig.signalLightValue / 15.0f);
	}

}
