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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.troblecodings.core.WriteBuffer;
import com.troblecodings.core.interfaces.INetworkSync;
import com.troblecodings.signals.OpenSignalsMain;
import com.troblecodings.signals.blocks.RedstoneIO;
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.LoadHolder;
import com.troblecodings.signals.core.NameStateListener;
import com.troblecodings.signals.core.PathGetter;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.core.StateLoadHolder;
import com.troblecodings.signals.enums.ChangedState;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent.ClientCustomPayloadEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

public final class NameHandler implements INetworkSync {

    private static final Map<StateInfo, String> ALL_NAMES = new HashMap<>();
    private static final Map<Level, NameHandlerFileV2> ALL_LEVEL_FILES = new HashMap<>();
    private static final Map<StateInfo, List<NameStateListener>> TASKS_WHEN_LOAD = new HashMap<>();
    private static final Map<StateInfo, List<LoadHolder<?>>> LOAD_COUNTER = new HashMap<>();
    private static ExecutorService writeService = Executors.newFixedThreadPool(5);
    private static final ExecutorService THREAD_SERVICE = Executors.newCachedThreadPool();
    private static EventNetworkChannel channel;
    private static ResourceLocation channelName;

    public static void init() {
        channelName = new ResourceLocation(OpenSignalsMain.MODID, "namehandler");
        channel = NetworkRegistry.newEventChannel(channelName, () -> OpenSignalsMain.MODID,
                OpenSignalsMain.MODID::equalsIgnoreCase, OpenSignalsMain.MODID::equalsIgnoreCase);
        channel.registerObject(new NameHandler());
    }

    @SubscribeEvent
    public static void onServerStop(final ServerStoppingEvent event) {
        Map<StateInfo, String> map;
        synchronized (ALL_NAMES) {
            map = ImmutableMap.copyOf(ALL_NAMES);
        }
        writeService.execute(() -> map.entrySet()
                .forEach(entry -> createToFile(entry.getKey(), entry.getValue())));
        writeService.shutdown();
        try {
            writeService.awaitTermination(10, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        writeService = null;
    }

    public static void registerToNetworkChannel(final Object obj) {
        channel.registerObject(obj);
    }

    public static void createName(final StateInfo info, final String name, final Player creator) {
        if (info.world.isClientSide || name == null)
            return;
        THREAD_SERVICE.execute(() -> {
            setNameForNonSignal(info, name);
            final List<LoadHolder<?>> list = new ArrayList<>();
            list.add(new LoadHolder<>(creator));
            synchronized (LOAD_COUNTER) {
                LOAD_COUNTER.put(info, list);
            }
            createToFile(info, name);
        });
    }

    public static void setNameForSignal(final StateInfo info, final String name) {
        if (info.world.isClientSide || name == null)
            return;
        setNameForNonSignal(info, name);
        final Block block = info.world.getBlockState(info.pos).getBlock();
        if (block instanceof Signal) {
            SignalStateHandler.setState(new SignalStateInfo(info.world, info.pos, (Signal) block),
                    Signal.CUSTOMNAME, "true");
        }
    }

    public static void setNameForNonSignal(final StateInfo info, final String name) {
        if (info.world.isClientSide || name == null)
            return;
        THREAD_SERVICE.execute(() -> {
            synchronized (ALL_NAMES) {
                ALL_NAMES.put(info, name);
            }
            sendToAll(info, name);
        });
    }

    public static String getName(final StateInfo info) {
        if (info.world.isClientSide)
            return "";
        synchronized (ALL_NAMES) {
            final String name = ALL_NAMES.get(info);
            if (name == null)
                return "";
            return name;
        }
    }

    private static void sendToAll(final StateInfo info, final String name) {
        final ByteBuffer buffer = packToBuffer(info.pos, name);
        info.world.players().forEach(player -> sendTo(player, buffer));
    }

    private static ByteBuffer packToBuffer(final BlockPos pos, final String name) {
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putBlockPos(pos);
        buffer.putBoolean(false);
        buffer.putString(name);
        return buffer.build();
    }

    public static boolean isNameLoaded(final StateInfo info) {
        return ALL_NAMES.containsKey(info);
    }

    public static void runTaskWhenNameLoaded(final StateInfo info,
            final NameStateListener listener) {
        if (!info.isValid() || info.isWorldNullOrClientSide())
            return;
        if (isNameLoaded(info)) {
            final String name;
            synchronized (ALL_NAMES) {
                name = ALL_NAMES.get(info);
            }
            listener.update(info, name, ChangedState.UPDATED);
        } else {
            synchronized (TASKS_WHEN_LOAD) {
                final List<NameStateListener> list = TASKS_WHEN_LOAD.computeIfAbsent(info,
                        _u -> new ArrayList<>());
                if (!list.contains(listener)) {
                    list.add(listener);
                }
            }
        }
    }

    public static void setRemoved(final StateInfo info) {
        synchronized (ALL_NAMES) {
            ALL_NAMES.remove(info);
        }
        NameHandlerFileV2 file;
        synchronized (ALL_LEVEL_FILES) {
            file = ALL_LEVEL_FILES.get(info.world);
        }
        if (file == null)
            return;
        synchronized (file) {
            file.deleteIndex(info.pos);
        }
        sendRemoved(info);
    }

    private static void sendRemoved(final StateInfo info) {
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putBlockPos(info.pos);
        buffer.putBoolean(true);
        info.world.players().forEach(player -> sendTo(player, buffer.getBuildedBuffer()));
    }

    private static void migrateWorldFilesToV2(final Level world) {
        final Path oldPath = Paths.get("osfiles/namefiles/"
                + world.getServer().getWorldData().getLevelName().replace(":", "").replace("/", "")
                        .replace("\\", "")
                + "/" + world.dimension().location().toString().replace(":", ""));
        if (!Files.exists(oldPath))
            return;
        OpenSignalsMain.getLogger()
                .info("Starting Migration from NameHandlerFileV1 to NameHandlerFileV2...");
        final NameHandlerFile oldFile = new NameHandlerFile(oldPath);
        NameHandlerFileV2 newFile;
        synchronized (ALL_LEVEL_FILES) {
            newFile = ALL_LEVEL_FILES.get(world);
        }
        oldFile.getAllEntries().forEach((pos, buffer) -> newFile.create(pos, buffer.array()));
        OpenSignalsMain.getLogger()
                .info("Finished Migration from NameHandlerFileV1 to NameHandlerFileV2!");
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

    @SubscribeEvent
    public static void onWorldLoad(final WorldEvent.Load load) {
        final Level world = (Level) load.getWorld();
        if (world.isClientSide)
            return;
        final Path path = PathGetter.getNewPathForFiles(world, "namefiles");
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.put(world, new NameHandlerFileV2(path));
        }
        migrateWorldFilesToV2(world);
        if (writeService != null)
            return;
        writeService = Executors.newFixedThreadPool(5);
    }

    @SubscribeEvent
    public static void onWorldSave(final WorldEvent.Save event) {
        final Level world = (Level) event.getWorld();
        if (world.isClientSide)
            return;
        Map<StateInfo, String> map;
        synchronized (ALL_NAMES) {
            map = ImmutableMap.copyOf(ALL_NAMES);
        }
        if (writeService != null)
            writeService.execute(() -> map.entrySet().stream()
                    .filter(entry -> entry.getKey().world.equals(world))
                    .forEach(entry -> createToFile(entry.getKey(), entry.getValue())));
    }

    @SubscribeEvent
    public static void onWorldUnload(final WorldEvent.Unload unload) {
        if (unload.getWorld().isClientSide())
            return;
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.remove(unload.getWorld());
        }
    }

    private static void createToFile(final StateInfo info, final String name) {
        NameHandlerFileV2 file;
        synchronized (ALL_LEVEL_FILES) {
            file = ALL_LEVEL_FILES.get(info.world);
        }
        if (file == null)
            return;
        SignalStatePosV2 posInFile = file.find(info.pos);
        synchronized (file) {
            if (posInFile == null) {
                posInFile = file.createState(info.pos, name);
                return;
            }
            file.writeString(posInFile, name);
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(final ChunkWatchEvent.Watch event) {
        final ServerLevel world = event.getWorld();
        if (world.isClientSide)
            return;
        final ChunkAccess chunk = world.getChunk(event.getPos().getWorldPosition());
        final Player player = event.getPlayer();
        final List<StateLoadHolder> states = new ArrayList<>();
        chunk.getBlockEntitiesPos().forEach(pos -> {
            final Block block = chunk.getBlockState(pos).getBlock();
            if (block instanceof Signal || block instanceof RedstoneIO) {
                final StateInfo info = new StateInfo(world, pos);
                states.add(new StateLoadHolder(info, new LoadHolder<>(player)));
            }
        });
        loadNames(states, player);
    }

    @SubscribeEvent
    public static void onChunkUnWatch(final ChunkWatchEvent.UnWatch event) {
        final ServerLevel world = event.getWorld();
        if (world.isClientSide)
            return;
        final ChunkAccess chunk = world.getChunk(event.getPos().getWorldPosition());
        final List<StateLoadHolder> states = new ArrayList<>();
        final Player player = event.getPlayer();
        chunk.getBlockEntitiesPos().forEach(pos -> {
            final Block block = chunk.getBlockState(pos).getBlock();
            if (block instanceof Signal || block instanceof RedstoneIO) {
                states.add(
                        new StateLoadHolder(new StateInfo(world, pos), new LoadHolder<>(player)));
            }
        });
        unloadNames(states);
    }

    @SubscribeEvent
    public static void onPlayerJoin(final PlayerEvent.PlayerLoggedInEvent event) {
        final Player player = event.getPlayer();
        Map<StateInfo, String> map;
        synchronized (ALL_NAMES) {
            map = ImmutableMap.copyOf(ALL_NAMES);
        }
        map.forEach((state, name) -> sendTo(player, packToBuffer(state.pos, name)));
    }

    public static void loadName(final StateLoadHolder holder) {
        loadName(holder, null);
    }

    public static void loadNames(final List<StateLoadHolder> holders) {
        loadNames(holders, null);
    }

    public static void loadName(final StateLoadHolder holder, final @Nullable Player player) {
        loadNames(ImmutableList.of(holder), player);
    }

    public static void loadNames(final List<StateLoadHolder> infos, final @Nullable Player player) {
        if (infos == null || infos.isEmpty())
            return;
        THREAD_SERVICE.execute(() -> {
            infos.forEach(info -> {
                boolean isLoaded = false;
                synchronized (LOAD_COUNTER) {
                    final List<LoadHolder<?>> holders = LOAD_COUNTER.computeIfAbsent(info.info,
                            _u -> new ArrayList<>());
                    if (holders.size() > 0) {
                        isLoaded = true;
                    }
                    if (!holders.contains(info.holder))
                        holders.add(info.holder);
                }
                if (isLoaded) {
                    if (player == null)
                        return;
                    String name;
                    synchronized (ALL_NAMES) {
                        name = ALL_NAMES.getOrDefault(info.info, "");
                    }
                    if (name.isEmpty())
                        return;
                    sendTo(player, packToBuffer(info.info.pos, name));
                    return;
                }
                NameHandlerFileV2 file;
                synchronized (ALL_LEVEL_FILES) {
                    file = ALL_LEVEL_FILES.get(info.info.world);
                }
                if (file == null)
                    return;
                String name;
                synchronized (file) {
                    name = file.getString(info.info.pos);
                }
                synchronized (ALL_NAMES) {
                    ALL_NAMES.put(info.info, name);
                }
                sendToAll(info.info, name);
                final List<NameStateListener> tasks;
                synchronized (TASKS_WHEN_LOAD) {
                    tasks = TASKS_WHEN_LOAD.remove(info.info);
                }
                if (tasks != null) {
                    tasks.forEach(listener -> listener.update(info.info, name,
                            ChangedState.ADDED_TO_CACHE));
                }
            });
        });
    }

    public static void unloadName(final StateLoadHolder holder) {
        unloadNames(ImmutableList.of(holder));
    }

    public static void unloadNames(final List<StateLoadHolder> infos) {
        if (infos == null || infos.isEmpty() || writeService == null)
            return;
        writeService.execute(() -> {
            infos.forEach(info -> {
                synchronized (LOAD_COUNTER) {
                    final List<LoadHolder<?>> holders = LOAD_COUNTER.getOrDefault(info.info,
                            new ArrayList<>());
                    holders.remove(info.holder);
                    if (!holders.isEmpty())
                        return;
                    LOAD_COUNTER.remove(info.info);
                }
                String name;
                synchronized (ALL_NAMES) {
                    name = ALL_NAMES.remove(info.info);
                }
                if (name == null)
                    return;
                createToFile(info.info, name);
            });
        });
    }

    private static void sendTo(final Player player, final ByteBuffer buf) {
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
}