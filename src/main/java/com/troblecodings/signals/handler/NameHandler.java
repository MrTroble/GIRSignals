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
import com.troblecodings.signals.blocks.Signal;
import com.troblecodings.signals.core.LoadHolder;
import com.troblecodings.signals.core.NameStateListener;
import com.troblecodings.signals.core.PathGetter;
import com.troblecodings.signals.core.StateInfo;
import com.troblecodings.signals.core.StateLoadHolder;
import com.troblecodings.signals.enums.ChangedState;
import com.troblecodings.signals.tileentitys.RedstoneIOTileEntity;
import com.troblecodings.signals.tileentitys.SignalTileEntity;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

public final class NameHandler implements INetworkSync {

    private static ExecutorService writeService = Executors.newFixedThreadPool(5);
    private static final ExecutorService THREAD_SERVICE = Executors.newCachedThreadPool();
    private static final Map<StateInfo, String> ALL_NAMES = new HashMap<>();
    private static final Map<World, NameHandlerFileV2> ALL_LEVEL_FILES = new HashMap<>();
    private static final Map<StateInfo, List<NameStateListener>> TASKS_WHEN_LOAD = new HashMap<>();
    private static final Map<StateInfo, List<LoadHolder<?>>> LOAD_COUNTER = new HashMap<>();
    private static final String CHANNELNAME = "namehandlernet";
    private static FMLEventChannel channel;

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNELNAME);
        channel.register(new NameHandler());
    }

    @EventHandler
    public static void onServerStop(final FMLServerStoppingEvent event) {
        writeService.shutdown();
        try {
            writeService.awaitTermination(10, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        writeService = Executors.newFixedThreadPool(5);
    }

    public static void registerToNetworkChannel(final Object obj) {
        channel.register(obj);
    }

    public static void createName(final StateInfo info, final String name,
            final EntityPlayer creator) {
        if (info.world.isRemote || name == null)
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
        if (info.world.isRemote || name == null)
            return;
        setNameForNonSignal(info, name);
        final Block block = info.world.getBlockState(info.pos).getBlock();
        if (block instanceof Signal) {
            SignalStateHandler.setState(new SignalStateInfo(info.world, info.pos, (Signal) block),
                    Signal.CUSTOMNAME, "true");
        }
    }

    public static void setNameForNonSignal(final StateInfo info, final String name) {
        if (info.world.isRemote || name == null)
            return;
        THREAD_SERVICE.execute(() -> {
            synchronized (ALL_NAMES) {
                ALL_NAMES.put(info, name);
            }
            sendToAll(info, name);
        });
    }

    public static String getName(final StateInfo info) {
        if (info.world.isRemote)
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
        final List<EntityPlayer> players = ImmutableList.copyOf(info.world.playerEntities);
        players.forEach(player -> sendTo(player, buffer));
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
        if (!info.isValid() || info.worldNullOrClientSide())
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
    }

    public static void sendRemoved(final StateInfo info) {
        final WriteBuffer buffer = new WriteBuffer();
        buffer.putBlockPos(info.pos);
        buffer.putBoolean(true);
        info.world.playerEntities.forEach(player -> sendTo(player, buffer.getBuildedBuffer()));
    }

    private static void migrateWorldFilesToV2(final World world) {
        final Path oldPath = (Paths.get("osfiles/namefiles/"
                + ((WorldServer) world).getMinecraftServer().getName().replace(":", "")
                        .replace("/", "").replace("\\", "")
                + "/"
                + ((WorldServer) world).provider.getDimensionType().getName().replace(":", "")));
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
    public static void onWorldLoad(final WorldEvent.Load event) {
        final World world = event.getWorld();
        if (world.isRemote)
            return;
        final Path path = PathGetter.getNewPathForFiles(world, "namefiles");
        synchronized (ALL_LEVEL_FILES) {
            ALL_LEVEL_FILES.put(world, new NameHandlerFileV2(path));
        }
        migrateWorldFilesToV2(world);
    }

    @SubscribeEvent
    public static void onWorldSave(final WorldEvent.Save event) {
        final World world = event.getWorld();
        if (world.isRemote)
            return;
        Map<StateInfo, String> map;
        synchronized (ALL_NAMES) {
            map = ImmutableMap.copyOf(ALL_NAMES);
        }
        writeService.execute(() -> {
            map.entrySet().stream().filter(entry -> entry.getKey().world.equals(world))
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
        final Chunk chunk = event.getChunkInstance();
        final World world = chunk.getWorld();
        if (world == null || world.isRemote)
            return;
        final EntityPlayer player = event.getPlayer();
        final List<StateLoadHolder> states = new ArrayList<>();
        ImmutableMap.copyOf(chunk.getTileEntityMap()).forEach((pos, tile) -> {
            if (tile instanceof SignalTileEntity || tile instanceof RedstoneIOTileEntity) {
                final StateInfo info = new StateInfo(world, pos);
                states.add(new StateLoadHolder(info, new LoadHolder<>(player)));
            }
        });
        loadNames(states, player);
    }

    @SubscribeEvent
    public static void onChunkUnWatch(final ChunkWatchEvent.UnWatch event) {
        final Chunk chunk = event.getChunkInstance();
        final World world = chunk.getWorld();
        final List<StateLoadHolder> states = new ArrayList<>();
        final EntityPlayer player = event.getPlayer();
        ImmutableMap.copyOf(chunk.getTileEntityMap()).forEach((pos, tile) -> {
            if (tile instanceof SignalTileEntity || tile instanceof RedstoneIOTileEntity) {
                states.add(
                        new StateLoadHolder(new StateInfo(world, pos), new LoadHolder<>(player)));
            }
        });
        unloadNames(states);
    }

    public static void loadName(final StateLoadHolder holder) {
        loadName(holder, null);
    }

    public static void loadNames(final List<StateLoadHolder> holders) {
        loadNames(holders, null);
    }

    public static void loadName(final StateLoadHolder holder, final @Nullable EntityPlayer player) {
        loadNames(ImmutableList.of(holder), player);
    }

    public static void loadNames(final List<StateLoadHolder> infos,
            final @Nullable EntityPlayer player) {
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
        if (infos == null || infos.isEmpty() || writeService.isShutdown())
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