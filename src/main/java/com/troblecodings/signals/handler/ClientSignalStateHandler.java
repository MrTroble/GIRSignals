package com.troblecodings.signals.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.troblecodings.core.ReadBuffer;
import com.troblecodings.core.interfaces.INetworkSync;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;

public class ClientSignalStateHandler implements INetworkSync {

    private static final Map<StateInfo, Map<SEProperty, String>> CURRENTLY_LOADED_STATES = new HashMap<>();

    public static final Map<SEProperty, String> getClientStates(final StateInfo info) {
        synchronized (CURRENTLY_LOADED_STATES) {
            return ImmutableMap
                    .copyOf(CURRENTLY_LOADED_STATES.computeIfAbsent(info, _u -> new HashMap<>()));
        }
    }

    @Override
    public void deserializeClient(final ReadBuffer buffer) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient level = mc.world;
        if (level == null)
            return;
        final BlockPos signalPos = buffer.getBlockPos();
        final int signalID = buffer.getInt();
        final int propertiesSize = buffer.getByteToUnsignedInt();
        if (propertiesSize == 255) {
            setRemoved(signalPos);
            return;
        }
        final int[] propertyIDs = new int[propertiesSize];
        final int[] valueIDs = new int[propertiesSize];
        for (int i = 0; i < propertiesSize; i++) {
            propertyIDs[i] = buffer.getByteToUnsignedInt();
            valueIDs[i] = buffer.getByteToUnsignedInt();
        }
        final List<SEProperty> signalProperties = Signal.SIGNAL_IDS.get(signalID).getProperties();
        final StateInfo stateInfo = new StateInfo(level, signalPos);
        final boolean contains;
        final Map<SEProperty, String> properties;
        synchronized (CURRENTLY_LOADED_STATES) {
            contains = CURRENTLY_LOADED_STATES.containsKey(stateInfo);
            properties = CURRENTLY_LOADED_STATES.computeIfAbsent(stateInfo, _u -> new HashMap<>());

            for (int i = 0; i < propertiesSize; i++) {
                final SEProperty property = signalProperties.get(propertyIDs[i]);
                final String value = property.getObjFromID(valueIDs[i]);
                properties.put(property, value);
            }
            CURRENTLY_LOADED_STATES.put(stateInfo, properties);
        }
        mc.addScheduledTask(() -> {
            final TileEntity tile = level.getTileEntity(signalPos);
            if (tile != null && tile instanceof SignalTileEntity) {
                ((SignalTileEntity) tile).updateAnimationStates(properties, !contains);
            }
            final Chunk chunk = level.getChunkFromBlockCoords(signalPos);
            if (chunk == null)
                return;
            chunk.markDirty();
            final IBlockState state = level.getBlockState(signalPos);
            if (state == null)
                return;
            mc.renderGlobal.notifyLightSet(signalPos);
            mc.renderGlobal.notifyBlockUpdate(level, signalPos, state, state, 8);
            level.notifyBlockUpdate(signalPos, state, state, 3);
        });
    }

    private static void setRemoved(final BlockPos pos) {
        final Minecraft mc = Minecraft.getMinecraft();
        synchronized (CURRENTLY_LOADED_STATES) {
            CURRENTLY_LOADED_STATES.remove(new StateInfo(mc.world, pos));
        }
    }

    @SubscribeEvent
    public void serverEvent(final ClientCustomPacketEvent event) {
        deserializeClient(event.getPacket().payload().nioBuffer());
    }
}