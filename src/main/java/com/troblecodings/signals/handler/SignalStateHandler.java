package com.troblecodings.signals.handler;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.collect.ImmutableMap;
import com.troblecodings.core.interfaces.INetworkSync;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent.ClientCustomPayloadEvent;
import net.minecraftforge.network.NetworkEvent.ServerCustomPayloadEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

public final class SignalStateHandler implements INetworkSync {

    private static final Map<SignalStateInfo, Map<SEProperty, String>> currentlyLoadedStates = new HashMap<>();
    private static final Map<ChunkAccess, List<SignalStateInfo>> currentlyLoadedChunks = new HashMap<>();
    private static final Map<Level, SignalStateFile> allLevelFiles = new HashMap<>();
    private static EventNetworkChannel channel;
    private static ResourceLocation channelName;

    public static void init() {
        channelName = new ResourceLocation(OpenSignalsMain.MODID, "signalstatehandler");
        channel = NetworkRegistry.newEventChannel(channelName, () -> OpenSignalsMain.MODID,
                OpenSignalsMain.MODID::equalsIgnoreCase, OpenSignalsMain.MODID::equalsIgnoreCase);
        channel.registerObject(new SignalStateHandler());
    }

    private static final ExecutorService SERVICE = Executors.newFixedThreadPool(3);

    public static void createStates(final SignalStateInfo info,
            final Map<SEProperty, String> states) {
        synchronized (currentlyLoadedStates) {
            currentlyLoadedStates.put(info, states);
        }
        if (info.world.isClientSide)
            return;
        sendPropertiesToClient(info, states);
        createToFile(info, states);
    }

    private static void createToFile(final SignalStateInfo info,
            final Map<SEProperty, String> states) {
        final SignalStateFile file;
        synchronized (allLevelFiles) {
            file = allLevelFiles.get(info.world);
            if (file == null) {
                return;
            }
        }
        SERVICE.submit(() -> {
            SignalStatePos pos = file.find(info.pos);
            if (pos == null) {
                pos = file.create(info.pos);
            }
            final ByteBuffer buffer = file.read(pos);
            final byte[] readData = buffer.array();
            states.forEach((property, string) -> {
                readData[info.signal.getIDFromProperty(property)] = (byte) property.getParent()
                        .getIDFromValue(string);
            });
            file.write(pos, buffer);
        });
    }

    public static void setStates(final SignalStateInfo info, final Map<SEProperty, String> states) {
        if (info.world.isClientSide || states == null || states.isEmpty()) {
            return;
        }
        synchronized (currentlyLoadedStates) {
            if (currentlyLoadedStates.containsKey(info)) {
                currentlyLoadedStates.put(info, states);
                sendPropertiesToClient(info, states);
                return;
            }
        }
        createToFile(info, states);
    }

    public static Map<SEProperty, String> getStates(final SignalStateInfo info) {
        final Map<SEProperty, String> states = currentlyLoadedStates.get(info);
        if (states != null) {
            return states;
        } else {
            if (info.world.isClientSide) {
                return Map.of();
            }
            return readAndSerialize(info);
        }
    }

    public static void setState(final SignalStateInfo info, final SEProperty property,
            final String value) {
        final Map<SEProperty, String> map = new HashMap<>();
        map.put(property, value);
        setStates(info, map);
    }

    public static Optional<String> getState(final SignalStateInfo info, final SEProperty property) {
        final Map<SEProperty, String> properties = getStates(info);
        return Optional.ofNullable(properties.get(property));
    }

    private static Map<SEProperty, String> readAndSerialize(final SignalStateInfo stateInfo) {
        Map<SEProperty, String> map = new HashMap<>();
        SignalStateFile file;
        synchronized (allLevelFiles) {
            file = allLevelFiles.get(stateInfo.world);
        }
        SignalStatePos pos = file.find(stateInfo.pos);
        if (pos == null) {
            if (stateInfo.world.isClientSide) {
                OpenSignalsMain.getLogger().warn("Position not found on client!");
                return map;
            } else {
                OpenSignalsMain.getLogger().warn("Position not found in file, recovering!");
                pos = file.create(stateInfo.pos);
            }
        }
        final ByteBuffer buffer = file.read(pos);
        final List<SEProperty> properties = stateInfo.signal.getProperties();
        for (int i = 0; i < properties.size(); i++) {
            final SEProperty property = properties.get(i);
            final String value = property.getObjFromID(Byte.toUnsignedInt(buffer.get()));
            map.put(property, value);
        }
        return map;
    }

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        final ChunkAccess chunk = event.getChunk();
        if (chunk.getWorldForge().isClientSide())
            return;
        final Level world = (Level) chunk.getWorldForge();
        if (!allLevelFiles.containsKey(world)) {
            allLevelFiles.put(world,
                    new SignalStateFile(Paths
                            .get("ossignalfiles/" + world.dimension().getRegistryName().toString()
                                    .replace(":", "").replace("/", "").replace("\\", ""))));
        }
        SERVICE.submit(() -> {
            final List<SignalStateInfo> states = new ArrayList<>();
            chunk.getBlockEntitiesPos().forEach(pos -> {
                final Block block = chunk.getBlockState(pos).getBlock();
                if (!(block instanceof Signal)) {
                    return;
                }
                final SignalStateInfo stateInfo = new SignalStateInfo(world, pos);
                final Map<SEProperty, String> map = readAndSerialize(stateInfo);
                synchronized (currentlyLoadedStates) {
                    currentlyLoadedStates.put(stateInfo, map);
                }
                states.add(stateInfo);
                sendPropertiesToClient(stateInfo, map);
            });
            synchronized (currentlyLoadedChunks) {
                currentlyLoadedChunks.put(chunk, states);
            }
        });
    }

    @SubscribeEvent
    public static void onChunkUnload(final ChunkEvent.Unload event) {
        final ChunkAccess chunk = event.getChunk();
        final Level level = (Level) chunk.getWorldForge();
        if (level.isClientSide())
            return;
        SignalStateFile file;
        synchronized (allLevelFiles) {
            file = allLevelFiles.get(level);
        }
        SERVICE.submit(() -> {
            List<SignalStateInfo> states;
            synchronized (currentlyLoadedChunks) {
                states = currentlyLoadedChunks.remove(chunk);
            }
            states.forEach(stateInfo -> {
                Map<SEProperty, String> properties;
                synchronized (currentlyLoadedStates) {
                    properties = currentlyLoadedStates.remove(stateInfo);
                }
                final SignalStatePos pos = file.find(stateInfo.pos);
                final ByteBuffer buffer = ByteBuffer.allocate(properties.size());
                properties.forEach((property, value) -> {
                    buffer.put((byte) property.getParent().getIDFromValue(value));
                });
                file.write(pos, buffer);
                unRenderClients(stateInfo);
            });
        });
    }

    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save save) {
        SERVICE.execute(() -> {
            final Map<SignalStateInfo, Map<SEProperty, String>> maps;
            synchronized (currentlyLoadedStates) {
                maps = ImmutableMap.copyOf(currentlyLoadedStates);
            }
            maps.forEach(SignalStateHandler::createToFile);
        });
    }

    @SubscribeEvent
    public static void onPlayerJoin(final PlayerEvent.PlayerLoggedInEvent event) {
        final Player player = event.getPlayer();
        Map<SignalStateInfo, Map<SEProperty, String>> map;
        synchronized (currentlyLoadedStates) {
            map = ImmutableMap.copyOf(currentlyLoadedStates);
        }
        map.forEach((state, properites) -> {
            final ByteBuffer buffer = packToByteBuffer(state, properites);
            sendTo(player, buffer);
        });
    }

    private static void unRenderClients(final SignalStateInfo stateInfo) {
        final ByteBuffer buffer = ByteBuffer.allocate(13);
        buffer.putInt(stateInfo.pos.getX());
        buffer.putInt(stateInfo.pos.getY());
        buffer.putInt(stateInfo.pos.getZ());
        buffer.put((byte) 255);
        stateInfo.world.players().forEach(player -> {
            if (checkInRange(player.blockPosition(), stateInfo.pos)) {
                sendTo(player, buffer);
            }
        });
    }

    private static ByteBuffer packToByteBuffer(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties) {
        final ByteBuffer buffer = ByteBuffer.allocate(13 + properties.size() * 2);
        buffer.putInt(stateInfo.pos.getX());
        buffer.putInt(stateInfo.pos.getY());
        buffer.putInt(stateInfo.pos.getZ());
        if (properties.size() > 254) {
            throw new IllegalStateException("To many SEProperties!");
        }
        buffer.put((byte) properties.size());
        properties.forEach((property, value) -> {
            if (property.equals(Signal.CUSTOMNAME)) {
                // TODO send SignalName
                return;
            }
            buffer.put((byte) stateInfo.signal.getIDFromProperty(property));
            buffer.put((byte) property.getParent().getIDFromValue(value));
        });
        return buffer;
    }

    private static void sendPropertiesToClient(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        final ByteBuffer buffer = packToByteBuffer(stateInfo, properties);
        stateInfo.world.players().forEach(player -> {
            if (checkInRange(player.blockPosition(), stateInfo.pos)) {
                sendTo(player, buffer);
            }
        });
    }

    /**
     * This is the distance in blocks in which signals get rendered
     */
    private static final int RENDER_DISTANCE = 512;

    private static boolean checkInRange(final BlockPos playerPos, final BlockPos signalPos) {
        return playerPos.distManhattan(signalPos) <= RENDER_DISTANCE;
    }

    @Override
    public void deserializeClient(final ByteBuffer buf) {
        final BlockPos signalPos = new BlockPos(buf.getInt(), buf.getInt(), buf.getInt());
        final int propertiesSize = Byte.toUnsignedInt(buf.get());
        if (propertiesSize == 255) {
            // TODO inform client to unrender
            return;
        }
        final int[] propertyIDs = new int[propertiesSize];
        final int[] valueIDs = new int[propertiesSize];
        for (int i = 0; i < propertiesSize; i++) {
            propertyIDs[i] = Byte.toUnsignedInt(buf.get());
            valueIDs[i] = Byte.toUnsignedInt(buf.get());
        }
        SERVICE.execute(() -> {
            final Minecraft mc = Minecraft.getInstance();
            BlockState state;
            while ((state = mc.player.level.getBlockState(signalPos)) == null
                    || state.equals(Blocks.VOID_AIR.defaultBlockState())) {
                continue;
            }
            final SignalStateInfo stateInfo = new SignalStateInfo(mc.player.level, signalPos);
            final List<SEProperty> signalProperties = stateInfo.signal.getProperties();
            Map<SEProperty, String> properties = new HashMap<>();
            synchronized (currentlyLoadedStates) {
                if (currentlyLoadedStates.containsKey(stateInfo)) {
                    properties = currentlyLoadedStates.get(stateInfo);
                }
            }
            for (int i = 0; i < propertiesSize; i++) {
                final SEProperty property = signalProperties.get(propertyIDs[i]);
                final String value = property.getObjFromID(valueIDs[i]);
                properties.put(property, value);
            }
            synchronized (currentlyLoadedStates) {
                currentlyLoadedStates.put(stateInfo, properties);
            }
            mc.player.level.setBlocksDirty(signalPos, state, state);
        });
    }

    public static void sendTo(final Player player, final ByteBuffer buf) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.copiedBuffer(buf.position(0)));
        if (player instanceof ServerPlayer) {
            final ServerPlayer server = (ServerPlayer) player;
            server.connection.send(new ClientboundCustomPayloadPacket(channelName, buffer));
        } else {
            final Minecraft mc = Minecraft.getInstance();
            mc.getConnection().send(new ServerboundCustomPayloadPacket(channelName, buffer));
        }
    }

    @SubscribeEvent
    public void clientEvent(final ClientCustomPayloadEvent event) {
        deserializeServer(event.getPayload().nioBuffer());
        event.getSource().get().setPacketHandled(true);
    }

    @SubscribeEvent
    public void serverEvent(final ServerCustomPayloadEvent event) {
        deserializeClient(event.getPayload().nioBuffer());
        event.getSource().get().setPacketHandled(true);
    }
}
