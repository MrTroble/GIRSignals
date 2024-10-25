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
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

public final class SignalStateHandler implements INetworkSync {

    private SignalStateHandler() {
    }

    private static ExecutorService writeService = Executors.newFixedThreadPool(5);
    private static final ExecutorService THREAD_SERVICE = Executors.newCachedThreadPool();
    private static final Map<SignalStateInfo, Map<SEProperty, String>> CURRENTLY_LOADED_STATES = new HashMap<>();
    private static final Map<World, SignalStateFileV2> ALL_LEVEL_FILES = new HashMap<>();
    private static final Map<SignalStateInfo, List<LoadHolder<?>>> SIGNAL_COUNTER = new HashMap<>();
    private static final Map<SignalStateInfo, List<SignalStateListener>> ALL_LISTENERS = new HashMap<>();
    private static final Map<SignalStateInfo, List<SignalStateListener>> TASKS_WHEN_LOAD = new HashMap<>();
    private static final String CHANNELNAME = "statehandlernet";
    private static FMLEventChannel channel;

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNELNAME);
    }

    public static void registerToNetworkChannel(final Object object) {
        channel.register(object);
    }

    public static void onServerStop(final FMLServerStoppingEvent event) {
        writeService.shutdown();
        try {
            writeService.awaitTermination(10, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        writeService = Executors.newFixedThreadPool(5);
    }

    public static void createStates(final SignalStateInfo info,
            final Map<SEProperty, String> states, final EntityPlayer creator) {
        if (!info.isValid() || info.worldNullOrClientSide())
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
        if (!info.isValid() || info.worldNullOrClientSide())
            return false;
        return CURRENTLY_LOADED_STATES.containsKey(info);
    }

    public static void runTaskWhenSignalLoaded(final SignalStateInfo info,
            final SignalStateListener listener) {
        if (!info.isValid() || info.worldNullOrClientSide())
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
        if (!info.isValid() || info.worldNullOrClientSide())
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
        if (!info.isValid() || info.worldNullOrClientSide())
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
        info.world.getMinecraftServer().addScheduledTask(() -> listeners
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

    private static void migrateWorldFilesToV2(final World world) {
        final Path oldPath = Paths.get("osfiles/signalfiles/"
                + ((WorldServer) world).getMinecraftServer().getName().replace(":", "")
                        .replace("/", "").replace("\\", "")
                + "/"
                + ((WorldServer) world).provider.getDimensionType().getName().replace(":", ""));
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

    public static void setStates(final SignalStateInfo info, final Map<SEProperty, String> states) {
        if (!info.isValid() || info.worldNullOrClientSide() || states.isEmpty())
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
            info.world.getMinecraftServer()
                    .addScheduledTask(() -> info.signal.getUpdate(info.world, info.pos));
            if (!contains.get()) {
                createToFile(info, changedProperties);
            }
        });
        info.world.getMinecraftServer().addScheduledTask(
                () -> info.world.notifyNeighborsOfStateChange(info.pos, info.signal, true));
    }

    public static Map<SEProperty, String> getStates(final SignalStateInfo info) {
        if (!info.isValid() || info.worldNullOrClientSide())
            return new HashMap<>();
        final Map<SEProperty, String> states;
        synchronized (CURRENTLY_LOADED_STATES) {
            final Map<SEProperty, String> stateVolitile = CURRENTLY_LOADED_STATES.get(info);
            states = stateVolitile == null ? null : ImmutableMap.copyOf(stateVolitile);
        }
        if (states != null)
            return states;
        else {
            if (info.world.isRemote)
                return new HashMap<>();
            return readAndSerialize(info);
        }
    }

    public static void setState(final SignalStateInfo info, final SEProperty property,
            final String value) {
        if (!info.isValid() || info.worldNullOrClientSide())
            return;
        final Map<SEProperty, String> map = new HashMap<>();
        synchronized (CURRENTLY_LOADED_STATES) {
            final Map<SEProperty, String> savedProperties = CURRENTLY_LOADED_STATES.get(info);
            map.putAll(savedProperties == null ? new HashMap<>() : savedProperties);
        }
        map.put(property, value);
        setStates(info, map);
    }

    public static Optional<String> getState(final SignalStateInfo info, final SEProperty property) {
        if (!info.isValid() || info.worldNullOrClientSide())
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
        ByteBuffer buffer;
        synchronized (file) {
            SignalStatePosV2 pos = file.find(stateInfo.pos);
            if (pos == null) {
                if (stateInfo.world.isRemote) {
                    OpenSignalsMain.getLogger()
                            .warn("Position [" + stateInfo + "] not found on client!");
                    return map;
                } else {
                    OpenSignalsMain.getLogger()
                            .warn("Position [" + stateInfo + "] not found in file, recovering!");
                    pos = file.create(stateInfo.pos);
                }
            }
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
    public static void onWorldLoad(final WorldEvent.Load event) {
        final World world = event.getWorld();
        if (world.isRemote)
            return;
        final Path path = PathGetter.getNewPathForFiles(world, "signalfiles");
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.put(world, new SignalStateFileV2(path));
        }
        migrateWorldFilesToV2(world);
    }

    @SubscribeEvent
    public static void onWorldSave(final WorldEvent.Save save) {
        final World world = save.getWorld();
        if (world.isRemote)
            return;
        final Map<SignalStateInfo, Map<SEProperty, String>> maps;
        synchronized (CURRENTLY_LOADED_STATES) {
            maps = ImmutableMap.copyOf(CURRENTLY_LOADED_STATES);
        }
        writeService.execute(() -> {
            maps.entrySet().stream().filter(entry -> entry.getKey().world.equals(world))
                    .forEach(entry -> createToFile(entry.getKey(), entry.getValue()));
        });
    }

    @SubscribeEvent
    public static void onWorldUnload(final WorldEvent.Unload unload) {
        if (unload.getWorld().isRemote)
            return;
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.remove(unload.getWorld());
        }
    }

    public static void setRemoved(final SignalStateInfo info) {
        Map<SEProperty, String> removedProperties;
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
        updateListeners(info, removedProperties, ChangedState.REMOVED_FROM_FILE);
        synchronized (ALL_LISTENERS) {
            ALL_LISTENERS.remove(info);
        }
    }

    public static void sendRemoved(final SignalStateInfo info) {
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putBlockPos(info.pos);
        buffer.putInt(info.signal.getID());
        buffer.putByte((byte) 255);
        info.world.playerEntities.forEach(player -> sendTo(player, buffer.getBuildedBuffer()));
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
            final @Nullable EntityPlayer player) {
        if (player == null) {
            sendToAll(info, properties);
        } else {
            sendToPlayer(info, properties, player);
        }
    }

    private static void sendToPlayer(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties, final EntityPlayer player) {
        if (properties == null || properties.isEmpty())
            return;
        sendTo(player, packToByteBuffer(stateInfo, properties));
    }

    private static void sendToAll(final SignalStateInfo stateInfo,
            final Map<SEProperty, String> properties) {
        if (properties == null || properties.isEmpty())
            return;
        final ByteBuffer buffer = packToByteBuffer(stateInfo, properties);
        final List<EntityPlayer> players = ImmutableList.copyOf(stateInfo.world.playerEntities);
        players.forEach(player -> sendTo(player, buffer));
    }

    @SubscribeEvent
    public static void onPlayerJoin(final PlayerLoggedInEvent event) {
        final EntityPlayer player = event.player;
        final Map<SignalStateInfo, Map<SEProperty, String>> properties;
        synchronized (CURRENTLY_LOADED_STATES) {
            properties = ImmutableMap.copyOf(CURRENTLY_LOADED_STATES);
        }
        properties.forEach((info, map) -> sendTo(info, map, player));
    }

    @SubscribeEvent
    public static void onChunkWatch(final ChunkWatchEvent.Watch event) {
        final Chunk chunk = event.getChunkInstance();
        final World world = chunk.getWorld();
        if (world.isRemote)
            return;

        final EntityPlayer player = event.getPlayer();
        final List<SignalStateLoadHoler> states = new ArrayList<>();
        ImmutableMap.copyOf(chunk.getTileEntityMap()).forEach((pos, tile) -> {
            if (tile instanceof SignalTileEntity) {
                final SignalTileEntity signalTile = (SignalTileEntity) tile;
                final SignalStateInfo info = new SignalStateInfo(world, pos,
                        signalTile.getSignal());
                states.add(new SignalStateLoadHoler(info, new LoadHolder<>(player)));
            }
        });
        loadSignals(states, player);
    }

    @SubscribeEvent
    public static void onChunkUnWatch(final ChunkWatchEvent.UnWatch event) {
        final Chunk chunk = event.getChunkInstance();
        final World world = chunk.getWorld();
        if (world.isRemote)
            return;
        final List<SignalStateLoadHoler> states = new ArrayList<>();
        ImmutableMap.copyOf(chunk.getTileEntityMap()).forEach((pos, tile) -> {
            if (tile instanceof SignalTileEntity) {
                final SignalTileEntity signalTile = (SignalTileEntity) tile;
                states.add(new SignalStateLoadHoler(
                        new SignalStateInfo(world, pos, signalTile.getSignal()),
                        new LoadHolder<>(event.getPlayer())));
            }
        });
        unloadSignals(states);
    }

    public static void loadSignal(final SignalStateLoadHoler info) {
        loadSignal(info, null);
    }

    public static void loadSignals(final List<SignalStateLoadHoler> signals) {
        loadSignals(signals, null);
    }

    public static void loadSignal(final SignalStateLoadHoler info,
            final @Nullable EntityPlayer player) {
        loadSignals(ImmutableList.of(info), player);
    }

    public static void loadSignals(final List<SignalStateLoadHoler> signals,
            final @Nullable EntityPlayer player) {
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
                sendToAll(info.info, properties);
                updateListeners(info.info, properties, ChangedState.ADDED_TO_CACHE);
                synchronized (TASKS_WHEN_LOAD) {
                    final List<SignalStateListener> tasks = TASKS_WHEN_LOAD.remove(info.info);
                    if (tasks != null) {
                        tasks.forEach(listener -> listener.update(info.info, properties,
                                ChangedState.ADDED_TO_CACHE));
                    }
                }
            });
        });
    }

    public static void unloadSignal(final SignalStateLoadHoler info) {
        unloadSignals(ImmutableList.of(info));
    }

    public static void unloadSignals(final List<SignalStateLoadHoler> signals) {
        if (signals == null || signals.isEmpty() || writeService.isShutdown())
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

    private static void sendTo(final EntityPlayer player, final ByteBuffer buf) {
        final PacketBuffer buffer = new PacketBuffer(
                Unpooled.copiedBuffer((ByteBuffer) buf.position(0)));
        if (player instanceof EntityPlayerMP) {
            final EntityPlayerMP server = (EntityPlayerMP) player;
            channel.sendTo(new FMLProxyPacket(buffer, CHANNELNAME), server);
        } else {
            channel.sendToServer(new FMLProxyPacket(new CPacketCustomPayload(CHANNELNAME, buffer)));
        }
    }

    @SubscribeEvent
    public void clientEvent(final ClientCustomPacketEvent event) {
        deserializeServer(event.getPacket().payload().nioBuffer());
    }
}
