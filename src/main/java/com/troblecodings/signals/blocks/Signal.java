package com.troblecodings.signals.blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.config.ConfigHandler;
import com.troblecodings.signals.contentpacks.SignalAnimationConfigParser;
import com.troblecodings.signals.core.DestroyHelper;
import com.troblecodings.signals.core.JsonEnum;
import com.troblecodings.signals.core.RenderOverlayInfo;
import com.troblecodings.signals.core.SignalAngel;
import com.troblecodings.signals.core.SignalProperties;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.core.TileEntitySupplierWrapper;
import com.troblecodings.signals.enums.ChangeableStage;
import com.troblecodings.signals.handler.ClientSignalStateHandler;
import com.troblecodings.signals.handler.NameHandler;
import com.troblecodings.signals.handler.SignalBoxHandler;
import com.troblecodings.signals.handler.SignalStateHandler;
import com.troblecodings.signals.handler.SignalStateInfo;
import com.troblecodings.signals.init.OSItems;
import com.troblecodings.signals.items.Placementtool;
import com.troblecodings.signals.parser.ValuePack;
import com.troblecodings.signals.properties.PredicatedPropertyBase.PredicateProperty;
import com.troblecodings.signals.properties.SoundProperty;
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
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
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class Signal extends BasicBlock {

    public static Consumer<List<SEProperty>> nextConsumer = _u -> {
    };

    public static final Map<String, Signal> SIGNALS = new HashMap<>();
    public static final List<Signal> SIGNAL_IDS = new ArrayList<>();
    public static final PropertyEnum<SignalAngel> ANGEL = PropertyEnum.create("angel",
            SignalAngel.class);
    public static final SEProperty CUSTOMNAME = new SEProperty("customname", JsonEnum.BOOLEAN,
            "false", ChangeableStage.AUTOMATICSTAGE, t -> true, 0);
    public static final TileEntitySupplierWrapper SUPPLIER = SignalTileEntity::new;

    protected final SignalProperties prop;
    private final int id;
    private List<SEProperty> signalProperties;
    private final Map<SEProperty, Integer> signalPropertiesToInt = new HashMap<>();

    public Signal(final SignalProperties prop) {
        super(Material.ROCK);
        this.prop = prop;
        this.id = SIGNAL_IDS.size();
        SIGNAL_IDS.add(this);
        this.setDefaultState(getDefaultState().withProperty(ANGEL, SignalAngel.ANGEL0));
        prop.placementtool.addSignal(this);
        for (int i = 0; i < signalProperties.size(); i++) {
            final SEProperty property = signalProperties.get(i);
            signalPropertiesToInt.put(property, i);
        }
        setLightLevel(ConfigHandler.lightEmission / 15.0f);
    }

    public int getID() {
        return id;
    }

    @Override
    public boolean shouldHaveItem() {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(final IBlockState state, final IBlockAccess source,
            final BlockPos pos) {
        final TileEntity te = source.getTileEntity(pos);
        if (te == null || !(te instanceof SignalTileEntity))
            return FULL_BLOCK_AABB;
        final World world = te.getWorld();
        final SignalStateInfo info = new SignalStateInfo(world, pos, this);
        final Map<SEProperty, String> properties = world.isRemote
                ? ClientSignalStateHandler.getClientStates(new StateInfo(info.world, info.pos))
                : ((SignalTileEntity) te).getProperties();
        return FULL_BLOCK_AABB.expand(0, getHeight(properties), 0);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(final IBlockState blockState,
            final IBlockAccess worldIn, final BlockPos pos) {
        return getBoundingBox(blockState, worldIn, pos);
    }

    public static ItemStack pickBlock(final EntityPlayer player, final Item item) {
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
    public ItemStack getPickBlock(final IBlockState state, final RayTraceResult target,
            final World world, final BlockPos pos, final EntityPlayer player) {
        return pickBlock(player, prop.placementtool);
    }

    @Override
    public IBlockState getStateForPlacement(final World world, final BlockPos pos,
            final EnumFacing facing, final float hitX, final float hitY, final float hitZ,
            final int meta, final EntityLivingBase placer, final EnumHand hand) {
        final int index = 15
                - (MathHelper.floor(placer.getRotationYawHead() * 16.0F / 360.0F - 0.5D) & 15);
        return getDefaultState().withProperty(ANGEL, SignalAngel.values()[index]);
    }

    @Override
    public IBlockState getStateFromMeta(final int meta) {
        return getDefaultState().withProperty(ANGEL, SignalAngel.values()[meta]);
    }

    @Override
    public int getMetaFromState(final IBlockState state) {
        return state.getValue(ANGEL).ordinal();
    }

    @Override
    public IBlockState withRotation(final IBlockState state, final Rotation rot) {
        return state.withRotation(rot);
    }

    @Override
    public IBlockState withMirror(final IBlockState state, final Mirror mirrorIn) {
        return state.withMirror(mirrorIn);
    }

    @Override
    public boolean canRenderInLayer(final IBlockState state, final BlockRenderLayer layer) {
        return layer.equals(BlockRenderLayer.CUTOUT_MIPPED);
    }

    @Override
    public IBlockState getExtendedState(final IBlockState state, final IBlockAccess acess,
            final BlockPos pos) {
        final AtomicReference<IExtendedBlockState> blockState = new AtomicReference<>(
                (IExtendedBlockState) super.getExtendedState(state, acess, pos));
        final SignalTileEntity tile = (SignalTileEntity) acess.getTileEntity(pos);
        if (tile == null)
            return blockState.get();
        final World world = tile.getWorld();
        final SignalStateInfo info = new SignalStateInfo(world, pos, this);
        final Map<SEProperty, String> properties = world.isRemote
                ? ClientSignalStateHandler.getClientStates(new StateInfo(info.world, info.pos))
                : tile.getProperties();
        properties.forEach((property, value) -> {
            if (signalProperties.contains(property)) {
                blockState.getAndUpdate(oldState -> oldState.withProperty(property, value));
            } else {
                OpenSignalsMain.getLogger().error("Tried to set invalid SEProperty [" + property
                        + "] on [" + this + "]. Rejected!");
            }
        });
        return blockState.get();
    }

    @Override
    public boolean isTranslucent(final IBlockState state) {
        return true;
    }

    @Override
    public boolean isOpaqueCube(final IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(final IBlockState state) {
        return false;
    }

    public int getIDFromProperty(final SEProperty property) {
        return this.signalPropertiesToInt.get(property);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        final List<SEProperty> properties = new ArrayList<>();
        nextConsumer.accept(properties);
        nextConsumer = _u -> {
        };
        properties.add(CUSTOMNAME);
        this.signalProperties = ImmutableList.copyOf(properties);
        return new ExtendedBlockState(this, new IProperty<?>[] {
                ANGEL
        }, this.signalProperties.toArray(new IUnlistedProperty[signalProperties.size()]));
    }

    public List<SEProperty> getProperties() {
        return this.signalProperties;
    }

    public String getSignalTypeName() {
        return this.getRegistryName().getResourcePath();
    }

    @Override
    public void breakBlock(final World worldIn, final BlockPos pos, final IBlockState state) {
        super.breakBlock(worldIn, pos, state);
        DestroyHelper.checkAndDestroyBlockInDirection(worldIn, pos, state, new EnumFacing[] {
                EnumFacing.UP, EnumFacing.DOWN
        }, block -> block instanceof GhostBlock);
        if (!worldIn.isRemote) {
            final SignalStateInfo info = new SignalStateInfo(worldIn, pos, this);
            SignalStateHandler.sendRemoved(info);
            NameHandler.sendRemoved(new StateInfo(worldIn, pos));
            new Thread(() -> {
                SignalStateHandler.setRemoved(new SignalStateInfo(worldIn, pos, this));
                NameHandler.setRemoved(new StateInfo(worldIn, pos));
                SignalBoxHandler.onPosRemove(new StateInfo(worldIn, pos));
            }, "Signal:breakBlock").start();
        }
    }

    public int getHeight(final Map<SEProperty, String> map) {
        for (final PredicateProperty<Integer> property : this.prop.signalHeights) {
            if (property.test(map))
                return property.state;
        }
        return this.prop.defaultHeight;
    }

    public boolean canHaveCustomname(final Map<SEProperty, String> map) {
        return this.prop.customNameRenderHeight != -1 || !this.prop.customRenderHeights.isEmpty();
    }

    public final boolean isForSignalBridge() {
        return this.prop.isBridgeSignal;
    }

    @Override
    public Optional<String> getSupplierWrapperName() {
        return Optional.of("signal");
    }

    @Override
    public String toString() {
        return this.getUnlocalizedName();
    }

    public final boolean canBeLinked() {
        return this.prop.canLink;
    }

    @SideOnly(Side.CLIENT)
    public int colorMultiplier(final IBlockState state, final IBlockAccess worldIn,
            final BlockPos pos, final int tintIndex) {
        return this.prop.colors.get(tintIndex);
    }

    @SideOnly(Side.CLIENT)
    public boolean hasCostumColor() {
        return !this.prop.colors.isEmpty();
    }

    @SideOnly(Side.CLIENT)
    public void renderOverlay(final RenderOverlayInfo info) {
        float customRenderHeight = this.prop.customNameRenderHeight;
        final Map<SEProperty, String> map = ClientSignalStateHandler.getClientStates(
                new StateInfo(info.tileEntity.getWorld(), info.tileEntity.getPos()));
        final String customNameState = map.get(CUSTOMNAME);
        if (customNameState == null || customNameState.equalsIgnoreCase("FALSE"))
            return;
        for (final PredicateProperty<Float> property : this.prop.customRenderHeights) {
            if (property.predicate.test(map)) {
                customRenderHeight = property.state;
            }
        }
        if (customRenderHeight == -1)
            return;
        final World world = info.tileEntity.getWorld();
        final BlockPos pos = info.tileEntity.getPos();
        final IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof Signal))
            return;
        boolean doubleSidedText = false;
        for (final PredicateProperty<Boolean> boolProp : this.prop.doubleSidedText) {
            if (boolProp.predicate.test(map)) {
                doubleSidedText = boolProp.state;
            }
        }

        final SignalAngel angle = state.getValue(Signal.ANGEL);

        GlStateManager.enableAlpha();
        GlStateManager.pushMatrix();
        GlStateManager.translate(info.x + 0.5f, info.y + customRenderHeight, info.z + 0.5f);
        GlStateManager.rotate(angle.getDregree(), 0, 1, 0);

        if (!this.prop.autoscale) {
            renderSingleOverlay(info);
        } else {
            renderSingleScaleOverlay(info);
        }

        if (doubleSidedText) {
            GlStateManager.rotate(180, 0, 1, 0);

            if (!this.prop.autoscale) {
                renderSingleOverlay(info);
            } else {
                renderSingleScaleOverlay(info);
            }

        }
        GlStateManager.popMatrix();
    }

    @SideOnly(Side.CLIENT)
    public void renderSingleOverlay(final RenderOverlayInfo info) {
        final String name = info.tileEntity.getNameWrapper();
        final String[] splitNames = name.split("\\[n\\]");
        final float signWidth = this.prop.signWidth;
        final float scale = this.prop.signScale;
        final float offsetX = this.prop.offsetX;
        final float offsetZ = this.prop.offsetY;

        GlStateManager.pushMatrix();
        // TODO Eig erst Translate, dann Scale
        GlStateManager.scale(-0.015f * scale, -0.015f * scale, 0.015f * scale);
        GlStateManager.translate(offsetX, 0, -4.2f + offsetZ);

        for (int j = 0; j < splitNames.length; j++) {
            final String text = splitNames[j];
            final float nameWidth = info.font.getStringWidth(text);
            final float center = (signWidth - nameWidth) / 2;
            info.font.drawSplitString(text, (int) center - 10, j * 10, (int) signWidth,
                    this.prop.textColor);
        }
        GlStateManager.popMatrix();
    }

    @SideOnly(Side.CLIENT)
    private void renderSingleScaleOverlay(final RenderOverlayInfo info) {
        final String name = info.tileEntity.getNameWrapper();
        final float nameWidth = info.font.getStringWidth(name);
        final float scale = Math.min(1 / (22 * (nameWidth / this.prop.signWidth)), 0.1f);
        final float offsetX = this.prop.offsetX;
        final float offsetZ = this.prop.offsetY;

        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX * 0.015f, 0, offsetZ * 0.015f);
        GlStateManager.scale(-scale, -scale, 1);
        info.font.drawString(name, (int) (-nameWidth / 2), 0, this.prop.textColor);
        GlStateManager.popMatrix();
    }

    public Placementtool getPlacementtool() {
        return this.prop.placementtool;
    }

    public int getDefaultDamage() {
        return this.prop.defaultItemDamage;
    }

    @Override
    public boolean onBlockActivated(final World world, final BlockPos pos, final IBlockState state,
            final EntityPlayer player, final EnumHand hand, final EnumFacing facing,
            final float hitX, final float hitY, final float hitZ) {
        final Item item = player.getHeldItemMainhand().getItem();
        if (!(state.getBlock() instanceof Signal)
                || (item.equals(OSItems.LINKING_TOOL) || item.equals(OSItems.MULTI_LINKING_TOOL)))
            return false;
        final SignalStateInfo stateInfo = new SignalStateInfo(world, pos, this);
        final Map<SEProperty, String> states = SignalStateHandler.getStates(stateInfo);
        final boolean customname = canHaveCustomname(states);
        if (player.getHeldItemMainhand().getItem().equals(OSItems.MANIPULATOR)
                && (canBeLinked() || customname)) {
            if (world.isRemote)
                return true;
            OpenSignalsMain.handler.invokeGui(Signal.class, player, world, pos, "signal");
            return true;
        }
        if (loadRedstoneOutput(world, stateInfo, states)) {
            world.setBlockState(pos, state, 3);
            world.notifyNeighborsOfStateChange(pos, this, true);
            world.markAndNotifyBlock(pos, null, state, state, 3);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean loadRedstoneOutput(final World worldIn, final SignalStateInfo info,
            final Map<SEProperty, String> properties) {
        if (!this.prop.redstoneOutputs.isEmpty()) {
            for (final ValuePack pack : this.prop.redstoneOutputs) {
                if (properties.containsKey(pack.property) && pack.predicate.test(properties)) {
                    SignalStateHandler.setState(info, pack.property,
                            Boolean.toString(!Boolean.valueOf(properties.get(pack.property))));
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasRedstoneOut() {
        return !this.prop.redstoneOutputs.isEmpty() || !this.prop.redstoneOutputPacks.isEmpty();
    }

    @Override
    public boolean canProvidePower(final IBlockState state) {
        return hasRedstoneOut();
    }

    @Override
    public int getStrongPower(final IBlockState blockState, final IBlockAccess blockAccess,
            final BlockPos pos, final EnumFacing side) {
        return getWeakPower(blockState, blockAccess, pos, side);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int getWeakPower(final IBlockState blockState, final IBlockAccess blockAccess,
            final BlockPos pos, final EnumFacing side) {
        if (!hasRedstoneOut() || !(blockAccess instanceof World))
            return 0;
        final SignalStateInfo stateInfo = new SignalStateInfo((World) blockAccess, pos, this);
        final Map<SEProperty, String> properties = SignalStateHandler.getStates(stateInfo);
        for (final List<ValuePack> valuePacks : ImmutableList.of(this.prop.redstoneOutputPacks,
                this.prop.redstoneOutputs)) {
            for (final ValuePack pack : valuePacks) {
                if (properties.containsKey(pack.property) && pack.predicate.test(properties)
                        && !properties.get(pack.property)
                                .equalsIgnoreCase(pack.property.getDefault()))
                    return 15;
            }
        }
        return 0;
    }

    public void getUpdate(final World world, final BlockPos pos) {
        if (this.prop.sounds.isEmpty())
            return;

        final SignalStateInfo stateInfo = new SignalStateInfo(world, pos, this);
        SignalStateHandler.runTaskWhenSignalLoaded(stateInfo, (info, properties, _u) -> {
            final SoundProperty sound = getSound(properties);
            if (sound.duration < 1)
                return;

            if (sound.duration == 1) {
                world.playSound(null, pos, sound.state, SoundCategory.BLOCKS, 1.0F, 1.0F);
            } else {
                if (world.isUpdateScheduled(pos, this))
                    return;
                else {
                    if (sound.predicate.test(properties)) {
                        world.scheduleUpdate(pos, this, 1);
                    }
                }
            }
        });
    }

    public SoundProperty getSound(final Map<SEProperty, String> map) {
        for (final SoundProperty property : this.prop.sounds) {
            if (property.predicate.test(map))
                return property;
        }
        return new SoundProperty(null, t -> true, 0);
    }

    @Override
    public void updateTick(final World worldIn, final BlockPos pos, final IBlockState state,
            final Random rand) {
        if (this.prop.sounds.isEmpty() || worldIn.isRemote)
            return;
        final SignalStateInfo stateInfo = new SignalStateInfo(worldIn, pos, this);
        SignalStateHandler.runTaskWhenSignalLoaded(stateInfo, (info, properties, _u) -> {
            final SoundProperty sound = getSound(properties);
            if (sound.duration <= 1)
                return;
            worldIn.playSound(null, pos, sound.state, SoundCategory.BLOCKS, 1.0F, 1.0F);
            worldIn.scheduleUpdate(pos, this, sound.duration);
        });
    }

    @Override
    public Optional<TileEntitySupplierWrapper> getSupplierWrapper() {
        return Optional.of(SUPPLIER);
    }

    @Override
    public TileEntity createNewTileEntity(final World worldIn, final int meta) {
        return new SignalTileEntity();
    }

    public boolean hasAnimation() {
        return SignalAnimationConfigParser.ALL_ANIMATIONS.containsKey(this);
    }
}