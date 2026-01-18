package is.meh.minecraft.lan_announcer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LANAnnouncer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lan-announcer");
    private ServerAnnouncer ipv4Announcer;
    private ServerAnnouncer ipv6Announcer;
    private final java.util.List<ServerAnnouncer> extraAnnouncers = new java.util.ArrayList<>();
    private ModConfig config;

    @Override
    public void onInitialize() {
        config = ModConfig.load();
        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER) {
            ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
            ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        } else {
            LOGGER.warn(
                    "This mod is only intended for dedicated multiplayer" +
                            " servers, not clients. Clients can start their own built-in" +
                            " LAN broadcast by using `Open to LAN` (Dedicated Server only feature will be disabled)");
        }
    }

    // Prevent conflicts with the packet payload
    private String sanitizeMOTD(String motd) {
        return motd.replace("[", "").replace("]", "").replace("\n", "-");
    }

    private void onServerStarted(MinecraftServer server) {
        String motd = "[MOTD]" + sanitizeMOTD(server.getServerMotd()) + "[/MOTD]";
        String port = "[AD]" + server.getServerPort() + "[/AD]";

        byte[] message = (motd + port).getBytes();

        InetAddress broadcastAddress = getBroadcastAddress();
        if (broadcastAddress != null) {
            ipv4Announcer = new ServerAnnouncer(broadcastAddress, message);
            ipv4Announcer.startAnnouncing();
        } else {
            LOGGER.warn("Skipping IPv4 announcement due to missing broadcast address.");
        }

        try {
            InetAddress multicastAddress = InetAddress.getByName("ff75:230::60");
            ipv6Announcer = new ServerAnnouncer(multicastAddress, message);
            ipv6Announcer.startAnnouncing();
        } catch (UnknownHostException e) {
            LOGGER.error("Failed to resolve IPv6 multicast address", e);
        }

        if (config != null && config.extraAddresses != null) {
            for (String addressStr : config.extraAddresses) {
                try {
                    InetAddress address = InetAddress.getByName(addressStr);
                    ServerAnnouncer announcer = new ServerAnnouncer(address, message);
                    announcer.startAnnouncing();
                    extraAnnouncers.add(announcer);
                    LOGGER.info("Started extra announcer for: {}", addressStr);
                } catch (UnknownHostException e) {
                    LOGGER.error("Failed to resolve extra address: {}", addressStr, e);
                }
            }
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (ipv4Announcer != null) {
            ipv4Announcer.stopAnnouncing();
        }
        if (ipv6Announcer != null) {
            ipv6Announcer.stopAnnouncing();
        }
        for (ServerAnnouncer announcer : extraAnnouncers) {
            announcer.stopAnnouncing();
        }
        extraAnnouncers.clear();
    }

    private InetAddress getBroadcastAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp())
                    continue;

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        LOGGER.info("Found broadcast address: {}", broadcast.getHostAddress());
                        return broadcast;
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.warn("Error retrieving network interfaces, using default broadcast address.", e);
        }

        try {
            return InetAddress.getByName("224.0.2.60");
        } catch (UnknownHostException e) {
            LOGGER.error("Failed to resolve default broadcast address", e);
            return null;
        }
    }

    static class ServerAnnouncer {
        private final byte[] message;
        private DatagramSocket socket;
        private ScheduledExecutorService executorService;
        private boolean running;
        private final InetAddress address;
        private boolean lastExecutionFailed = false;

        public ServerAnnouncer(InetAddress address, byte[] message) {
            this.address = address;
            this.message = message;

            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize socket", e);
            }
        }

        public void startAnnouncing() {
            if (socket == null) {
                LOGGER.error("Socket not initialized, cannot start announcing.");
                return;
            }

            running = true;
            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(this::announce, 0, 1500, TimeUnit.MILLISECONDS);
        }

        private void announce() {
            if (!running)
                return;

            try {
                DatagramPacket packet = new DatagramPacket(message, message.length, address, 4445);
                socket.send(packet);
                if (lastExecutionFailed) {
                    LOGGER.info("Successfully resumed broadcasting to {}", address.getCanonicalHostName());
                    lastExecutionFailed = false;
                }
            } catch (SocketException e) {
                if (!lastExecutionFailed) {
                    LOGGER.error(
                            "Error sending broadcast packet to {}: {}. This often happens in Docker when not using host networking.",
                            address.getCanonicalHostName(), e.getMessage());
                    lastExecutionFailed = true;
                }
            } catch (IOException e) {
                LOGGER.error("Error sending broadcast packet", e);
            }
        }

        public void stopAnnouncing() {
            running = false;

            if (executorService != null) {
                executorService.shutdownNow();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
