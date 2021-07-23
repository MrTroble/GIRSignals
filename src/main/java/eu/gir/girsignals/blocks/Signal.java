package eu.gir.girsignals.blocks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nullable;

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
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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

public class Signal extends Block implements ITileEntityProvider {

	public static enum SignalAngel implements IStringSerializable {
		ANGEL0, ANGEL22P5, ANGEL45,
		ANGEL67P5, ANGEL90, ANGEL112P5,
		ANGEL135, ANGEL157P5, ANGEL180,
		ANGEL202P5, ANGEL225, ANGEL247P5,
		ANGEL270, ANGEL292P5, ANGEL315,
		ANGEL337P5;

		@Override
		public String getName() {
			return this.name().toLowerCase();
		}
		
		public float getAngel() {
			return this.ordinal() * 22.5f;
		}
	}

	public static final ArrayList<Signal> SIGNALLIST = new ArrayList<Signal>();

	public static final PropertyEnum<SignalAngel> ANGEL = PropertyEnum.create("angel", SignalAngel.class);
	public static final SEProperty<Boolean> CUSTOMNAME = SEProperty.of("customname", false,
			ChangeableStage.AUTOMATICSTAGE);

	private final int ID;
	private final int height;
	private final Placementtool placementtool;

	private final String signalTypeName;
	private final float customNameRenderHeight;

	public Signal(final Placementtool placementtool, final String signalTypeName, final int height) {
		this(placementtool, signalTypeName, height, -1);
	}
	
	public Signal(final Placementtool placementtool, final String signalTypeName, final int height, final float customNameRenderHeight) {
		super(Material.ROCK);
		this.signalTypeName = signalTypeName;
		if(hasAngel())
			setDefaultState(getDefaultState().withProperty(ANGEL, SignalAngel.ANGEL0));
		ID = SIGNALLIST.size();
		SIGNALLIST.add(this);
		this.height = height;
		this.customNameRenderHeight = customNameRenderHeight;
		this.placementtool = placementtool;
		this.placementtool.addSignal(this);
	}

	@Override
	public void dropBlockAsItemWithChance(World worldIn, BlockPos pos, IBlockState state, float chance, int fortune) {
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		IExtendedBlockState ebs = (IExtendedBlockState) this.getExtendedState(state, source, pos);
		NBTTagCompound comp = new NBTTagCompound();
		ebs.getUnlistedProperties().forEach((prop, opt) -> comp.setBoolean(prop.getName(), opt.isPresent()));
		return FULL_BLOCK_AABB.expand(0, getHeight(comp), 0);
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
		return pickBlock(player, placementtool);
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		if(!hasAngel())
			return getDefaultState();
		int x = Math.abs((int) (placer.rotationYaw / 22.5f)) % 16;
		return getDefaultState().withProperty(ANGEL, SignalAngel.values()[x]);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return hasAngel() ? getDefaultState().withProperty(ANGEL, SignalAngel.values()[meta]):getDefaultState();
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return hasAngel() ? state.getValue(ANGEL).ordinal():0;
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
		if(customNameRenderHeight != -1)
			prop.add(CUSTOMNAME);
		return new ExtendedBlockState(this, hasAngel() ? new IProperty<?>[] { ANGEL }:new IProperty<?>[] {},
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
		return signalTypeName;
	}

	public int getID() {
		return ID;
	}

	@Override
	public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
		super.breakBlock(worldIn, pos, state);

		if(!worldIn.isRemote)
			GhostBlock.destroyUpperBlock(worldIn, pos);
	}

	public int getHeight(NBTTagCompound comp) {
		return height;
	}
	
	public boolean canHaveCustomname() {
		return customNameRenderHeight != -1;
	}
	
	public float getCustomnameRenderHeight(@Nullable final World world, @Nullable final BlockPos pos, 
			@Nullable final SignalTileEnity te) {
		return customNameRenderHeight;
	}
	
	public float getCustomnameSignWidth(final World world, final BlockPos pos, final SignalTileEnity te) {
		return 22;
	}
	
	public float getCustomnameOffsetX(final World world, final BlockPos pos, final SignalTileEnity te) {
		return 0;
	}
	
	public float getCustomnameOffsetZ(final World world, final BlockPos pos, final SignalTileEnity te) {
		return 0;
	}
	
	public float getCustomnameScale(final World world, final BlockPos pos, final SignalTileEnity te) {
		return 1;
	}

	@Override
	public String toString() {
		return this.getLocalizedName();
	}
	
	public boolean canBeLinked() {
		return true;
	}
	
	public boolean hasAngel() {
		return true;
	}
	
	@SideOnly(Side.CLIENT)
	public int colorMultiplier(IBlockState state, IBlockAccess worldIn, BlockPos pos, int tintIndex) {
		return 0;
	}
	
	@SideOnly(Side.CLIENT)
	public boolean hasCostumColor() {
		return false;
	}

	public Placementtool getPlacementtool() {
		return placementtool;
	}
	
}
