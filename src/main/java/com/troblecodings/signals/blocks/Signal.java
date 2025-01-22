package com.troblecodings.signals.blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.mojang.math.Quaternion;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class Signal extends BasicBlock {

    public static Consumer<List<SEProperty>> nextConsumer = _u -> {
    };

    public static final Map<String, Signal> SIGNALS = new HashMap<>();
    public static final List<Signal> SIGNAL_IDS = new ArrayList<>();
    public static final EnumProperty<SignalAngel> ANGEL = EnumProperty.create("angel",
            SignalAngel.class);
    public static final SEProperty CUSTOMNAME = new SEProperty("customname", JsonEnum.BOOLEAN,
            "false", ChangeableStage.AUTOMATICSTAGE, t -> true, 0);
    public static final TileEntitySupplierWrapper SUPPLIER = SignalTileEntity::new;

    protected final SignalProperties prop;
    private final int id;
    private List<SEProperty> signalProperties;
    private final Map<SEProperty, Integer> signalPropertiesToInt = new HashMap<>();

    public Signal(final SignalProperties prop) {
        super(Properties.of(Material.STONE).noOcclusion()
                .lightLevel(u -> ConfigHandler.GENERAL.lightEmission.get())
                .isRedstoneConductor((_u1, _u2, _u3) -> false));
        this.prop = prop;
        this.id = SIGNAL_IDS.size();
        SIGNAL_IDS.add(this);
        registerDefaultState(defaultBlockState().setValue(ANGEL, SignalAngel.ANGEL0));
        prop.placementtool.addSignal(this);
        for (int i = 0; i < signalProperties.size(); i++) {
            final SEProperty property = signalProperties.get(i);
            signalPropertiesToInt.put(property, i);
        }
    }

    public int getID() {
        return id;
    }

    @Override
    public boolean shouldHaveItem() {
        return false;
    }

    @Override
    public boolean propagatesSkylightDown(final BlockState state, final BlockGetter getter,
            final BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(final BlockState state, final BlockGetter getter,
            final BlockPos pos) {
        return 1.0F;
    }

    @Override
    public VoxelShape getBlockSupportShape(final BlockState stat, final BlockGetter getter,
            final BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final int angel = Integer
                .valueOf(Mth.floor(context.getRotation() * 16.0F / 360.0F + 0.5D) & 15);
        return defaultBlockState().setValue(ANGEL, SignalAngel.values()[angel]);
    }

    public int getIDFromProperty(final SEProperty property) {
        return this.signalPropertiesToInt.get(property);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter source, final BlockPos pos,
            final CollisionContext context) {
        final SignalTileEntity te = (SignalTileEntity) source.getBlockEntity(pos);
        if (te == null)
            return Shapes.block();
        final Level world = te.getLevel();
        final SignalStateInfo info = new SignalStateInfo(world, pos, this);
        final Map<SEProperty, String> properties = world.isClientSide
                ? ClientSignalStateHandler.getClientStates(new StateInfo(info.world, info.pos))
                : te.getProperties();
        return Shapes.create(Shapes.block().bounds().expandTowards(0, getHeight(properties), 0));
    }

    @Override
    public VoxelShape getCollisionShape(final BlockState blockState, final BlockGetter worldIn,
            final BlockPos pos, final CollisionContext context) {
        return getShape(blockState, worldIn, pos, context);
    }

    @Override
    public ItemStack getCloneItemStack(final BlockState state, final HitResult target,
            final BlockGetter level, final BlockPos pos, final Player player) {
        return getPlacementtool().getDefaultInstance();
    }

    @Override
    protected void createBlockStateDefinition(final Builder<Block, BlockState> builder) {
        final List<SEProperty> properties = new ArrayList<>();
        nextConsumer.accept(properties);
        nextConsumer = _u -> {
        };
        properties.add(CUSTOMNAME);
        this.signalProperties = ImmutableList.copyOf(properties);
        builder.add(ANGEL);
    }

    public List<SEProperty> getProperties() {
        return this.signalProperties;
    }

    public String getSignalTypeName() {
        return this.delegate.name().getPath();
    }

    @Override
    public void destroy(final LevelAccessor worldIn, final BlockPos pos, final BlockState state) {
        super.destroy(worldIn, pos, state);
        DestroyHelper.checkAndDestroyBlockInDirection(worldIn, pos, state, new Direction[] {
                Direction.UP, Direction.DOWN
        }, block -> block instanceof GhostBlock);
        if (!worldIn.isClientSide() && worldIn instanceof Level) {
            SignalStateHandler.setRemoved(new SignalStateInfo((Level) worldIn, pos, this));
            NameHandler.setRemoved(new StateInfo((Level) worldIn, pos));
            SignalBoxHandler.onPosRemove(new StateInfo((Level) worldIn, pos));
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

    @Override
    public Optional<String> getSupplierWrapperName() {
        return Optional.of("signal");
    }

    @Override
    public String toString() {
        return this.getDescriptionId();
    }

    public final boolean canBeLinked() {
        return this.prop.canLink;
    }

    public final boolean isForSignalBridge() {
        return this.prop.isBridgeSignal;
    }

    @OnlyIn(Dist.CLIENT)
    public int colorMultiplier(final int tintIndex) {
        return this.prop.colors.get(tintIndex);
    }

    @OnlyIn(Dist.CLIENT)
    public boolean hasCostumColor() {
        return !this.prop.colors.isEmpty();
    }

    @OnlyIn(Dist.CLIENT)
    public void renderOverlay(final RenderOverlayInfo info) {
        float customRenderHeight = this.prop.customNameRenderHeight;

        final Map<SEProperty, String> map = ClientSignalStateHandler.getClientStates(
                new StateInfo(info.tileEntity.getLevel(), info.tileEntity.getBlockPos()));
        final String customNameState = map.get(CUSTOMNAME);
        if (customNameState == null || customNameState.equalsIgnoreCase("FALSE"))
            return;
        for (final PredicateProperty<Float> property : this.prop.customRenderHeights) {
            if (property.test(map)) {
                customRenderHeight = property.state;
            }
        }
        if (customRenderHeight == -1)
            return;
        final Level world = info.tileEntity.getLevel();
        final BlockPos pos = info.tileEntity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof Signal))
            return;
        boolean doubleSidedText = false;
        for (final PredicateProperty<Boolean> boolProp : this.prop.doubleSidedText) {
            if (boolProp.test(map)) {
                doubleSidedText = boolProp.state;
            }
        }

        final SignalAngel face = state.getValue(Signal.ANGEL);
        final Quaternion angle = face.getQuaternion();

        info.stack.pushPose();
        info.stack.translate(info.x + 0.5f, info.y + customRenderHeight, info.z + 0.5f);
        info.stack.mulPose(angle);

        if (!this.prop.autoscale) {
            renderSingleOverlay(info);
        } else {
            renderSingleScaleOverlay(info);
        }

        if (doubleSidedText) {
            final Quaternion quad = new Quaternion(
                    Quaternion.fromXYZ(0, (float) (-face.getRadians() + Math.PI), 0));
            info.stack.mulPose(quad);
            info.stack.mulPose(face.getQuaternion());

            if (!this.prop.autoscale) {
                renderSingleOverlay(info);
            } else {
                renderSingleScaleOverlay(info);
            }
        }
        info.stack.popPose();
    }

    @OnlyIn(Dist.CLIENT)
    private void renderSingleOverlay(final RenderOverlayInfo info) {
        final String name = info.tileEntity.getNameWrapper();
        final String[] splitNames = name.split("\\[n\\]");
        final float signWidth = this.prop.signWidth;
        final float scale = this.prop.signScale;
        final float offsetX = this.prop.offsetX;
        final float offsetZ = this.prop.offsetY;

        info.stack.pushPose();
        // TODO Eig erst Translate, dann Scale
        info.stack.scale(-0.015f * scale, -0.015f * scale, 0.015f * scale);
        info.stack.translate(offsetX, 0, -4.2f + offsetZ);

        for (int j = 0; j < splitNames.length; j++) {
            final String text = splitNames[j];
            final float textWidth = info.font.width(text);
            final float center = (signWidth - textWidth) / 2;
            info.font.draw(info.stack, text, (int) center - 10, j * 10, this.prop.textColor);
        }
        info.stack.popPose();
    }

    @OnlyIn(Dist.CLIENT)
    private void renderSingleScaleOverlay(final RenderOverlayInfo info) {
        final String name = info.tileEntity.getNameWrapper();
        final float nameWidth = info.font.width(name);
        final float scale = Math.min(1 / (22 * (nameWidth / this.prop.signWidth)), 0.1f);
        final float offsetX = this.prop.offsetX;
        final float offsetZ = this.prop.offsetY;

        info.stack.pushPose();
        info.stack.translate(offsetX * 0.015f, 0, offsetZ * 0.015f);
        info.stack.scale(-scale, -scale, 1);
        info.font.draw(info.stack, name, -nameWidth / 2, 0, this.prop.textColor);
        info.stack.popPose();
    }

    public Placementtool getPlacementtool() {
        return this.prop.placementtool;
    }

    public int getDefaultDamage() {
        return this.prop.defaultItemDamage;
    }

    @Override
    public InteractionResult use(final BlockState blockstate, final Level level,
            final BlockPos blockPos, final Player placer, final InteractionHand hand,
            final BlockHitResult blockHit) {
        if (!(blockstate.getBlock() instanceof Signal))
            return InteractionResult.FAIL;
        final SignalStateInfo stateInfo = new SignalStateInfo(level, blockPos, this);
        final boolean customname = canHaveCustomname(SignalStateHandler.getStates(stateInfo));
        final Item item = placer.getItemInHand(InteractionHand.MAIN_HAND).getItem();
        if (item.equals(OSItems.MANIPULATOR) && (canBeLinked() || customname)) {
            OpenSignalsMain.handler.invokeGui(Signal.class, placer, level, blockPos, "signal");
            return InteractionResult.SUCCESS;
        }
        if (loadRedstoneOutput(level, stateInfo)) {
            level.blockUpdated(blockPos, this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @SuppressWarnings("unchecked")
    private boolean loadRedstoneOutput(final Level worldIn, final SignalStateInfo info) {
        if (worldIn.isClientSide)
            return true;
        if (!this.prop.redstoneOutputs.isEmpty()) {
            final Map<SEProperty, String> properties = SignalStateHandler.getStates(info);
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
    public boolean isSignalSource(final BlockState state) {
        return hasRedstoneOut();
    }

    @Override
    public int getSignal(final BlockState state, final BlockGetter getter, final BlockPos pos,
            final Direction direction) {
        return getDirectSignal(state, getter, pos, direction);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int getDirectSignal(final BlockState blockState, final BlockGetter blockAccess,
            final BlockPos pos, final Direction side) {
        if (!hasRedstoneOut() || !(blockAccess instanceof Level))
            return 0;
        final SignalStateInfo stateInfo = new SignalStateInfo((Level) blockAccess, pos, this);
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

    public void getUpdate(final Level world, final BlockPos pos) {
        if (this.prop.sounds.isEmpty())
            return;

        final SignalStateInfo stateInfo = new SignalStateInfo(world, pos, this);
        SignalStateHandler.runTaskWhenSignalLoaded(stateInfo, (info, properties, _u) -> {
            final SoundProperty sound = getSound(properties);
            if (sound.duration < 1)
                return;

            if (sound.duration == 1) {
                world.playSound(null, pos, sound.state, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                if (world.getBlockTicks().hasScheduledTick(pos, this))
                    return;
                else {
                    if (sound.predicate.test(properties)) {
                        world.scheduleTick(pos, this, 1);
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
        return new SoundProperty(t -> true, null, 0);
    }

    @Override
    public void tick(final BlockState state, final ServerLevel world, final BlockPos pos,
            final Random rand) {
        if (this.prop.sounds.isEmpty() || world.isClientSide)
            return;
        final SignalStateInfo stateInfo = new SignalStateInfo(world, pos, this);
        SignalStateHandler.runTaskWhenSignalLoaded(stateInfo, (info, properties, _u) -> {
            final SoundProperty sound = getSound(properties);
            if (sound.duration <= 1)
                return;
            world.playSound(null, pos, sound.state, SoundSource.BLOCKS, 1.0F, 1.0F);
            world.scheduleTick(pos, this, sound.duration);
        });
    }

    @Override
    public Optional<TileEntitySupplierWrapper> getSupplierWrapper() {
        return Optional.of(SUPPLIER);
    }

    public boolean hasAnimation() {
        return SignalAnimationConfigParser.ALL_ANIMATIONS.containsKey(this);
    }
}
