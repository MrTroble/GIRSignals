package com.troblecodings.signals.tileentitys;

import java.util.ArrayList;

import com.troblecodings.core.NBTWrapper;
import com.troblecodings.guilib.ecs.interfaces.UIClientSync;
import com.troblecodings.signals.core.TileEntityInfo;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SyncableTileEntity extends BasicBlockEntity {

    public SyncableTileEntity(final TileEntityType<?> info) {
        super(info);
    }

    protected final ArrayList<UIClientSync> clientSyncs = new ArrayList<>();

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(final Connection net, final ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
    }

    @Override
    public CompoundNBT getUpdateTag() {
        final NBTWrapper wrapper = new NBTWrapper();
        saveWrapper(wrapper);
        return wrapper.tag;
    }

    public void syncClient() {
        syncClient(getLevel(), getBlockPos());
    }

    public void syncClient(final World world, final BlockPos pos) {
        world.setBlockAndUpdate(pos, getBlockState());
    }

    public boolean add(final UIClientSync sync) {
        return this.clientSyncs.add(sync);
    }

    public boolean remove(final UIClientSync sync) {
        return this.clientSyncs.removeIf(s -> s.getPlayer().equals(sync.getPlayer()));
    }

    public UIClientSync get(final int id) {
        return clientSyncs.get(id);
    }
}