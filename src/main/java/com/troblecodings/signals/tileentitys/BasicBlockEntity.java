package com.troblecodings.signals.tileentitys;

import java.util.ArrayList;
import java.util.List;

import com.troblecodings.core.NBTWrapper;
import com.troblecodings.core.interfaces.NamableWrapper;
import com.troblecodings.signals.handler.NameHandler;
import com.troblecodings.signals.handler.NameStateInfo;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;

public class BasicBlockEntity extends TileEntity implements NamableWrapper {

    public static final String GUI_TAG = "guiTag";
    public static final String POS_TAG = "posTag";
    protected final ArrayList<BlockPos> linkedPositions = new ArrayList<>();
    protected String customName = null;

    public BasicBlockEntity(final TileEntityType<?> info) {
        super(new TileEntityType<>(null, null, null));
    }

    public void saveWrapper(final NBTWrapper wrapper) {
    }

    public void loadWrapper(final NBTWrapper wrapper) {
    }

    @Override
    public void deserializeNBT(final CompoundNBT nbt) {
        super.deserializeNBT(nbt);
        saveWrapper(new NBTWrapper(nbt));
    }

    @Override
    public CompoundNBT serializeNBT() {
        final NBTWrapper wrapper = new NBTWrapper(super.serializeNBT());
        this.loadWrapper(wrapper);
        return wrapper.tag;
    }

    public List<BlockPos> getLinkedPos() {
        return linkedPositions;
    }

    @Override
    public String getNameWrapper() {
        if (customName == null)
            customName = NameHandler.getName(new NameStateInfo(level, worldPosition));
        return customName;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null;
    }
}