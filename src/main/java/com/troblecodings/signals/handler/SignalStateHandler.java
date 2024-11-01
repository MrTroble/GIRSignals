package com.troblecodings.signals.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.troblecodings.core.WriteBuffer;
import com.troblecodings.core.interfaces.INetworkSync;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.SEProperty;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.LoadHolder;
import com.troblecodings.signals.core.PathGetter;
import com.troblecodings.signals.core.SignalStateListener;
import com.troblecodings.signals.core.SignalStateLoadHoler;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.enums.ChangedState;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CCustomPayloadPacket;
import net.minecraft.network.play.server.SCustomPayloadPlayPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.event.EventNetworkChannel;

public final class SignalStateHandler implements INetworkSync {

    private static ExecutorService writeService = Executors.newFixedThreadPool(5);
    private static final ExecutorService THREAD_SERVICE = Executors.newCachedThreadPool();
    private static final Map<SignalStateInfo, Map<SEProperty, String>> CURRENTLY_LOADED_STATES = new HashMap<>();
    private static final Map<World, SignalStateFileV2> ALL_LEVEL_FILES = new HashMap<>();
    private static final Map<SignalStateInfo, List<LoadHolder<?>>> SIGNAL_COUNTER = new HashMap<>();
    private static final Map<SignalStateInfo, List<SignalStateListener>> ALL_LISTENERS = new HashMap<>();
    private static final Map<SignalStateInfo, List<SignalStateListener>> TASKS_WHEN_LOAD = new HashMap<>();
    private static EventNetworkChannel channel;
    private static ResourceLocation channelName;

    private SignalStateHandler() {
    }

    public static void init() {
        channelName = new ResourceLocation(OpenSignalsMain.MODID, "signalstatehandler");
        channel = NetworkRegistry.newEventChannel(channelName, () -> OpenSignalsMain.MODID,
                OpenSignalsMain.MODID::equalsIgnoreCase, OpenSignalsMain.MODID::equalsIgnoreCase);
        channel.registerObject(new SignalStateHandler());
        MinecraftForge.EVENT_BUS.register(SignalStateHandler.class);
    }

    @SubscribeEvent
    public static void onServerStop(final FMLServerStoppingEvent event) {
        Map<SignalStateInfo, Map<SEProperty, String>> maps;
        synchronized (CURRENTLY_LOADED_STATES) {
            maps = ImmutableMap.copyOf(CURRENTLY_LOADED_STATES);
        }
        writeService.execute(() -> maps.entrySet()
                .forEach(entry -> createToFile(entry.getKey(), entry.getValue())));
        writeService.shutdown();
        try {
            writeService.awaitTermination(10, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        writeService = null;
    }

    public static void registerToNetworkChannel(final Object object) {
        channel.registerObject(object);
    }

    public static void createStates(final SignalStateInfo info,
            final Map<SEProperty, String> states, final PlayerEntity creator) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return;
        synchronized (CURRENTLY_LOADED_STATES) {
            CURRENTLY_LOADED_STATES.put(info, ImmutableMap.copyOf(states));
        }
        updateListeners(info, states, ChangedState.ADDED_TO_FILE);
        THREAD_SERVICE.execute(() -> {
            final List<LoadHolder<?>> list = new ArrayList<>();
            list.add(new LoadHolder<>(creator));
            synchronized (SIGNAL_COUNTER) {
                SIGNAL_COUNTER.put(info, list);
            }
            sendToAll(info, states);
            createToFile(info, states);
        });
    }

    public static boolean isSignalLoaded(final SignalStateInfo info) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return false;
        synchronized (CURRENTLY_LOADED_STATES) {
            return CURRENTLY_LOADED_STATES.containsKey(info);
        }
    }

    public static void runTaskWhenSignalLoaded(final SignalStateInfo info,
            final SignalStateListener listener) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return;
        if (isSignalLoaded(info)) {
            final Map<SEProperty, String> properties;
            synchronized (CURRENTLY_LOADED_STATES) {
                properties = CURRENTLY_LOADED_STATES.get(info);
            }
            listener.update(info, properties, ChangedState.UPDATED);
        } else {
            synchronized (TASKS_WHEN_LOAD) {
                final List<SignalStateListener> list = TASKS_WHEN_LOAD.computeIfAbsent(info,
                        _u -> new ArrayList<>());
                if (!list.contains(listener)) {
                    list.add(listener);
                }
            }
        }
    }

    public static void addListener(final SignalStateInfo info, final SignalStateListener listener) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return;
        synchronized (ALL_LISTENERS) {
            final List<SignalStateListener> listeners = ALL_LISTENERS.computeIfAbsent(info,
                    _u -> new ArrayList<>());
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    public static void removeListener(final SignalStateInfo info,
            final SignalStateListener listener) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return;
        final List<SignalStateListener> listeners;
        synchronized (ALL_LISTENERS) {
            listeners = ALL_LISTENERS.get(info);
        }
        if (listeners == null)
            return;
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            synchronized (ALL_LISTENERS) {
                ALL_LISTENERS.remove(info);
            }
        }
    }

    private static void updateListeners(final SignalStateInfo info,
            final Map<SEProperty, String> changedProperties, final ChangedState changedState) {
        if (changedProperties == null || changedProperties.isEmpty())
            return;
        final List<SignalStateListener> listeners;
        synchronized (ALL_LISTENERS) {
            listeners = ALL_LISTENERS.get(info);
        }
        if (listeners == null)
            return;
        info.world.getServer().execute(() -> listeners
                .forEach(listener -> listener.update(info, changedProperties, changedState)));
    }

    private static void statesToBuffer(final Signal signal, final Map<SEProperty, String> states,
            final byte[] readData) {
        states.forEach((property, string) -> {
            if (property.equals(Signal.CUSTOMNAME))
                return;
            readData[signal.getIDFromProperty(
                    property)] = (byte) (property.getParent().getIDFromValue(string) + 1);
        });
    }

    private static void createToFile(final SignalStateInfo info,
            final Map<SEProperty, String> states) {
        if (states == null)
            return;
        SignalStateFileV2 file;
        synchronized (ALL_LEVEL_FILES) {
            file = ALL_LEVEL_FILES.get(info.world);
            if (file == null)
                return;
        }
        SignalStatePosV2 pos = file.find(info.pos);
        if (pos == null) {
            pos = file.create(info.pos);
        }
        synchronized (file) {
            final ByteBuffer buffer = file.read(pos);
            statesToBuffer(info.signal, states, buffer.array());
            file.write(pos, buffer);
        }
    }

    public static void setStates(final SignalStateInfo info, final Map<SEProperty, String> states) {
        if (!info.isValid() || info.isWorldNullOrClientSide() || states.isEmpty())
            return;
        final AtomicBoolean contains = new AtomicBoolean(false);
        final Map<SEProperty, String> changedProperties = new HashMap<>();
        synchronized (CURRENTLY_LOADED_STATES) {
            if (CURRENTLY_LOADED_STATES.containsKey(info)) {
                contains.set(true);
                final Map<SEProperty, String> oldStates = new HashMap<>(
                        CURRENTLY_LOADED_STATES.get(info));
                states.entrySet().stream().filter(entry -> {
                    final String oldState = oldStates.get(entry.getKey());
                    return !entry.getValue().equals(oldState);
                }).forEach(entry -> changedProperties.put(entry.getKey(), entry.getValue()));
                oldStates.putAll(states);
                CURRENTLY_LOADED_STATES.put(info, ImmutableMap.copyOf(oldStates));
            } else {
                changedProperties.putAll(states);
            }
        }
        updateListeners(info, changedProperties, ChangedState.UPDATED);
        THREAD_SERVICE.execute(() -> {
            sendToAll(info, changedProperties);
            info.signal.getUpdate(info.world, info.pos);
            if (!contains.get()) {
                createToFile(info, changedProperties);
            }
        });
        info.world.getServer().execute(() -> info.world.updateNeighborsAt(info.pos, info.signal));
    }

    public static Map<SEProperty, String> getStates(final SignalStateInfo info) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return new HashMap<>();
        final Map<SEProperty, String> states;
        synchronized (CURRENTLY_LOADED_STATES) {
            final Map<SEProperty, String> stateVolitile = CURRENTLY_LOADED_STATES.get(info);
            states = stateVolitile == null ? null : ImmutableMap.copyOf(stateVolitile);
        }
        if (states != null)
            return states;
        else {
            if (info.world.isClientSide)
                return new HashMap<>();
            return readAndSerialize(info);
        }
    }

    private static void migrateWorldFilesToV2(final World world) {
        final Path oldPath = Paths.get("osfiles/signalfiles/"
                + world.getServer().getWorldData().getLevelName().replace(":", "").replace("/", "")
                        .replace("\\", "")
                + "/" + world.dimension().location().toString().replace(":", ""));
        if (!Files.exists(oldPath))
            return;
        OpenSignalsMain.getLogger()
                .info("Starting Migration from SignalStateFileV1 to SignalStateFileV2...");
        final SignalStateFile oldFile = new SignalStateFile(oldPath);
        SignalStateFileV2 newFile;
        synchronized (ALL_LEVEL_FILES) {
            newFile = ALL_LEVEL_FILES.get(world);
        }
        oldFile.getAllEntries().forEach((pos, buffer) -> newFile.create(pos, buffer.array()));
        OpenSignalsMain.getLogger()
                .info("Finished Migration from SignalStateFileV1 to SignalStateFileV2!");
        try {
            Files.list(oldPath).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            });
            Files.delete(oldPath);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public static void setState(final SignalStateInfo info, final SEProperty property,
            final String value) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return;
        final Map<SEProperty, String> map = new HashMap<>();
        synchronized (CURRENTLY_LOADED_STATES) {
            final Map<SEProperty, String> savedProperties = CURRENTLY_LOADED_STATES.get(info);
            map.putAll(savedProperties == null ? new HashMap<>() : savedProperties);
        }
        map.put(property, value);
        setStates(info, map);
        info.signal.getUpdate(info.world, info.pos);
    }

    public static Optional<String> getState(final SignalStateInfo info, final SEProperty property) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return Optional.empty();
        final Map<SEProperty, String> properties = getStates(info);
        return Optional.ofNullable(properties.get(property));
    }

    private static Map<SEProperty, String> readAndSerialize(final SignalStateInfo stateInfo) {
        final Map<SEProperty, String> map = new HashMap<>();
        SignalStateFileV2 file;
        synchronized (ALL_LEVEL_FILES) {
            file = ALL_LEVEL_FILES.get(stateInfo.world);
        }
        SignalStatePosV2 pos = file.find(stateInfo.pos);
        if (pos == null) {
            if (stateInfo.world.isClientSide) {
                OpenSignalsMain.getLogger()
                        .warn("Position [" + stateInfo + "] not found on client!");
                return map;
            } else {
                OpenSignalsMain.getLogger()
                        .warn("Position [" + stateInfo + "] not found in file, recovering!");
                pos = file.create(stateInfo.pos);
            }
        }
        ByteBuffer buffer;
        synchronized (file) {
            buffer = file.read(pos);
        }
        final List<SEProperty> properties = stateInfo.signal.getProperties();
        final byte[] byteArray = buffer.array();
        for (int i = 0; i < properties.size(); i++) {
            final SEProperty property = properties.get(i);
            if (property.equals(Signal.CUSTOMNAME)) {
                continue;
            }
            final int typeID = Byte.toUnsignedInt(byteArray[i]);
            if (typeID <= 0) {
                continue;
            }
            final String value = property.getObjFromID(typeID - 1);
            map.put(property, value);
        }
        if (NameHandler.isNameLoaded(stateInfo)) {
            final String customName = NameHandler.getName(stateInfo);
            if (customName == null || customName.isEmpty()
                    || customName.equals(stateInfo.signal.getSignalTypeName())) {
                map.put(Signal.CUSTOMNAME, "false");
            } else {
                map.put(Signal.CUSTOMNAME, "true");
            }
        } else {
            NameHandler.runTaskWhenNameLoaded(new StateInfo(stateInfo.world, stateInfo.pos),
                    (info, name, changed) -> {
                        if (name == null || name.isEmpty()
                                || name.equals(stateInfo.signal.getSignalTypeName())) {
                            runTaskWhenSignalLoaded(stateInfo, (_u1, _u2,
                                    _u3) -> setState(stateInfo, Signal.CUSTOMNAME, "false"));
                        } else {
                            runTaskWhenSignalLoaded(stateInfo, (_u1, _u2,
                                    _u3) -> setState(stateInfo, Signal.CUSTOMNAME, "true"));
                        }
                    });
        }
        return map;
    }

    @SubscribeEvent
    public static void onWorldLoad(final WorldEvent.Load load) {
        final World world = (World) load.getWorld();
        if (world.isClientSide)
            return;
        final Path path = PathGetter.getNewPathForFiles(world, "signalfiles");
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.put(world, new SignalStateFileV2(path));
        }
        migrateWorldFilesToV2(world);
        if (writeService != null)
            return;
        writeService = Executors.newFixedThreadPool(5);
    }

    @SubscribeEvent
    public static void onWorldSave(final WorldEvent.Save save) {
        final World world = (World) save.getWorld();
        if (world.isClientSide())
            return;
        final Map<SignalStateInfo, Map<SEProperty, String>> maps;
        synchronized (CURRENTLY_LOADED_STATES) {
            maps = ImmutableMap.copyOf(CURRENTLY_LOADED_STATES);
        }
        if (writeService != null) {
            writeService.execute(() -> maps.entrySet().stream()
                    .filter(entry -> entry.getKey().world.equals(world))
                    .forEach(entry -> createToFile(entry.getKey(), entry.getValue())));
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(final WorldEvent.Unload unload) {
        if (unload.getWorld().isClientSide())
            return;
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.remove(unload.getWorld());
        }
    }

    public static void setRemoved(final SignalStateInfo info) {
        final Map<SEProperty, String> removedProperties;
        synchronized (CURRENTLY_LOADED_STATES) {
            removedProperties = CURRENTLY_LOADED_STATES.remove(info);
        }
        SignalStateFileV2 file;
        synchronized (ALL_LEVEL_FILES) {
            file = ALL_LEVEL_FILES.get(info.world);
        }
        synchronized (file) {
            file.deleteIndex(info.pos);
        }
        synchronized (SIGNAL_COUNTER) {
            SIGNAL_COUNTER.remove(info);
        }
        sendRemoved(info);
        updateListeners(info, removedProperties, ChangedState.REMOVED_FROM_FILE);
        synchronized (ALL_LISTENERS) {
            ALL_LISTENERS.remove(info);
        }
    }

    private static void sendRemoved(final SignalStateInfo info) {
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putBlockPos(info.pos);
        buffer.putInt(info.signal.getID());
        buffer.putByte((byte) 255);
        info.world.players().forEach(player -> sendTo(player, buffer.getBuildedBuffer()));
    }

    public static ByteBuffer packToByteBuffer(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties) {
        if (properties.size() > 254)
            throw new IllegalStateException("Too many SEProperties!");
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putBlockPos(stateInfo.pos);
        buffer.putInt(stateInfo.signal.getID());
        buffer.putByte((byte) properties.size());
        properties.forEach((property, value) -> {
            buffer.putByte((byte) stateInfo.signal.getIDFromProperty(property));
            buffer.putByte((byte) property.getParent().getIDFromValue(value));
        });
        return buffer.build();
    }

    private static void sendTo(final SignalStateInfo info, final Map<SEProperty, String> properties,
            final @Nullable PlayerEntity player) {
        if (player == null) {
            sendToAll(info, properties);
        } else {
            sendToPlayer(info, properties, player);
        }
    }

    private static void sendToPlayer(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties, final PlayerEntity player) {
        if (properties == null || properties.isEmpty())
            return;
        sendTo(player, packToByteBuffer(stateInfo, properties));
    }

    private static void sendToAll(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties) {
        if (properties == null || properties.isEmpty())
            return;
        final ByteBuffer buffer = packToByteBuffer(stateInfo, properties);
        stateInfo.world.players().forEach(playerEntity -> sendTo(playerEntity, buffer));
    }

    @SubscribeEvent
    public static void onChunkWatch(final ChunkWatchEvent.Watch event) {
        final ServerWorld world = event.getWorld();
        if (world.isClientSide)
            return;
        final IChunk chunk = world.getChunk(event.getPos().getWorldPosition());
        final ServerPlayerEntity player = event.getPlayer();
        final List<SignalStateLoadHoler> states = new ArrayList<>();
        chunk.getBlockEntitiesPos().forEach(pos -> {
            final Block block = chunk.getBlockState(pos).getBlock();
            if (block instanceof Signal) {
                final SignalStateInfo info = new SignalStateInfo(world, pos, (Signal) block);
                states.add(new SignalStateLoadHoler(info, new LoadHolder<>(player)));
            }
        });
        loadSignals(states, player);
    }

    @SubscribeEvent
    public static void onChunkUnWatch(final ChunkWatchEvent.UnWatch event) {
        final ServerWorld world = event.getWorld();
        if (world.isClientSide)
            return;
        final IChunk chunk = world.getChunk(event.getPos().getWorldPosition());
        final List<SignalStateLoadHoler> states = new ArrayList<>();
        chunk.getBlockEntitiesPos().forEach(pos -> {
            final Block block = chunk.getBlockState(pos).getBlock();
            if (block instanceof Signal) {
                states.add(new SignalStateLoadHoler(new SignalStateInfo(world, pos, (Signal) block),
                        new LoadHolder<>(event.getPlayer())));
            }
        });
        unloadSignals(states);
    }

    @SubscribeEvent
    public static void onPlayerJoin(final PlayerEvent.PlayerLoggedInEvent event) {
        final PlayerEntity player = event.getPlayer();
        Map<SignalStateInfo, Map<SEProperty, String>> map;
        synchronized (CURRENTLY_LOADED_STATES) {
            map = ImmutableMap.copyOf(CURRENTLY_LOADED_STATES);
        }
        map.forEach((state, properties) -> sendToPlayer(state, properties, player));
    }

    public static void loadSignal(final SignalStateLoadHoler info) {
        loadSignal(info, null);
    }

    public static void loadSignals(final List<SignalStateLoadHoler> signals) {
        loadSignals(signals, null);
    }

    public static void loadSignal(final SignalStateLoadHoler info,
            final @Nullable PlayerEntity player) {
        loadSignals(ImmutableList.of(info), player);
    }

    public static void loadSignals(final List<SignalStateLoadHoler> signals,
            final @Nullable PlayerEntity player) {
        if (signals == null || signals.isEmpty())
            return;
        THREAD_SERVICE.execute(() -> {
            signals.forEach(info -> {
                boolean isLoaded = false;
                synchronized (SIGNAL_COUNTER) {
                    final List<LoadHolder<?>> holders = SIGNAL_COUNTER.computeIfAbsent(info.info,
                            _u -> new ArrayList<>());
                    if (holders.size() > 0) {
                        isLoaded = true;
                    }
                    if (!holders.contains(info.holder)) {
                        holders.add(info.holder);
                    }
                }
                if (isLoaded) {
                    Map<SEProperty, String> sendProperties;
                    synchronized (CURRENTLY_LOADED_STATES) {
                        sendProperties = CURRENTLY_LOADED_STATES.get(info.info);
                    }
                    sendTo(info.info, sendProperties, player);
                    return;
                }
                final Map<SEProperty, String> properties = readAndSerialize(info.info);
                synchronized (CURRENTLY_LOADED_STATES) {
                    CURRENTLY_LOADED_STATES.put(info.info, properties);
                }
                sendTo(info.info, properties, player);
                updateListeners(info.info, properties, ChangedState.ADDED_TO_CACHE);
                final List<SignalStateListener> tasks;
                synchronized (TASKS_WHEN_LOAD) {
                    tasks = TASKS_WHEN_LOAD.remove(info.info);
                }
                if (tasks != null) {
                    tasks.forEach(listener -> listener.update(info.info, properties,
                            ChangedState.ADDED_TO_CACHE));
                }
            });
        });
    }

    public static void unloadSignal(final SignalStateLoadHoler info) {
        unloadSignals(ImmutableList.of(info));
    }

    public static void unloadSignals(final List<SignalStateLoadHoler> signals) {
        if (signals == null || signals.isEmpty())
            return;
        writeService.execute(() -> {
            signals.forEach(info -> {
                synchronized (SIGNAL_COUNTER) {
                    final List<LoadHolder<?>> holders = SIGNAL_COUNTER.getOrDefault(info.info,
                            new ArrayList<>());
                    holders.remove(info.holder);
                    if (!holders.isEmpty())
                        return;
                    SIGNAL_COUNTER.remove(info.info);
                }
                Map<SEProperty, String> properties;
                synchronized (CURRENTLY_LOADED_STATES) {
                    properties = CURRENTLY_LOADED_STATES.remove(info.info);
                }
                if (properties == null)
                    return;
                createToFile(info.info, properties);
                updateListeners(info.info, properties, ChangedState.REMOVED_FROM_CACHE);
            });
        });
    }

    private static void sendTo(final PlayerEntity player, final ByteBuffer buf) {
        final PacketBuffer buffer = new PacketBuffer(Unpooled.copiedBuffer(buf.array()));
        if (player instanceof ServerPlayerEntity) {
            final ServerPlayerEntity server = (ServerPlayerEntity) player;
            server.connection.send(new SCustomPayloadPlayPacket(channelName, buffer));
        } else {
            final Minecraft mc = Minecraft.getInstance();
            mc.getConnection().send(new CCustomPayloadPacket(channelName, buffer));
        }
    }

    @SubscribeEvent
    public void clientEvent(final ClientCustomPayloadEvent event) {
        deserializeServer(event.getPayload().nioBuffer());
        event.getSource().get().setPacketHandled(true);
    }
}
