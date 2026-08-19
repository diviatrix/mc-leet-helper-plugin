package com.leet.helper.resource;

import com.leet.helper.Core;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds and distributes the Cooking resource pack.
 *
 * <p>Reads the {@code resource_pack/index} manifest from the JAR, zips every
 * listed file into an in-memory byte array, then serves it via a tiny
 * embedded HTTP server and pushes it to joining players during the
 * configuration phase, exactly like Vane.
 */
public final class ResourcePackService implements Listener {

    private static final String INDEX_PATH = "resource_pack/index";
    private static final String ROUTE = "/cooking-pack.zip";

    private final Core plugin;
    private final boolean enabled;
    private final boolean require;
    private final String urlOverride;
    private final int port;

    private byte[] packBytes;
    private String sha1Hex;
    private UUID packUuid;
    private volatile boolean running;
    private volatile String servedUrl;
    private ServerSocket serverSocket;
    private ExecutorService acceptor;

    private final ConcurrentHashMap<UUID, CountDownLatch> latches = new ConcurrentHashMap<>();

    public ResourcePackService(Core plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        this.require = plugin.getConfig().getBoolean("resource-pack.require", false);
        this.urlOverride = plugin.getConfig().getString("resource-pack.url", "");
        this.port = plugin.getConfig().getInt("resource-pack.port", 8043);
    }

    public void start() {
        if (running) return;
        if (!enabled) {
            plugin.getLogger().info("Cooking resource-pack distribution disabled.");
            return;
        }
        if (!buildPack()) {
            plugin.getLogger().warning("Failed to build cooking resource pack; dish icons will not be distributed.");
            return;
        }
        // Always start the embedded HTTP server — the pack is served from here
        // regardless of how the client reaches it (direct or proxied).
        startEmbeddedServer();
        if (urlOverride != null && !urlOverride.isBlank()) {
            // The client-facing URL is overridden (e.g. a proxy address like
            // selfed.top:8043); use that for ResourcePackInfo so clients
            // download from the external address.
            servedUrl = urlOverride;
        }
        if (servedUrl == null) {
            plugin.getLogger().warning("No resource-pack URL available. Set resource-pack.url in config.yml.");
            return;
        }
        running = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Serving cooking dish icons from " + servedUrl);
    }

    public void stop() {
        running = false;
        HandlerList.unregisterAll(this);
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (acceptor != null) {
            acceptor.shutdownNow();
            acceptor = null;
        }
    }

    private boolean buildPack() {
        List<String> files;
        try (InputStream in = plugin.getResource(INDEX_PATH)) {
            if (in == null) return false;
            files = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            return false;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {
            for (String path : files) {
                try (InputStream fileIn = plugin.getResource("resource_pack/" + path)) {
                    if (fileIn == null) continue;
                    zip.putNextEntry(new ZipEntry(path));
                    fileIn.transferTo(zip);
                    zip.closeEntry();
                }
            }
            zip.finish();
            packBytes = baos.toByteArray();
        } catch (IOException e) {
            return false;
        }

        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(packBytes);
            sha1Hex = HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ignored) {
            sha1Hex = null;
        }
        packUuid = UUID.nameUUIDFromBytes(packBytes);

        try {
            Path dest = Path.of(plugin.getDataFolder().getAbsolutePath(), "resource-pack", "cooking.zip");
            Files.createDirectories(dest.getParent());
            Files.write(dest, packBytes);
        } catch (IOException ignored) {}

        return true;
    }

    private void startEmbeddedServer() {
        String serverIp = plugin.getServer().getIp();
        boolean hasExplicitIp = serverIp != null && !serverIp.isBlank();
        String bindIp = hasExplicitIp ? serverIp : "0.0.0.0";
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName(bindIp));
            acceptor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LeetHelper-resourcepack");
                t.setDaemon(true);
                return t;
            });
            acceptor.submit(this::acceptLoop);
            if (hasExplicitIp) {
                servedUrl = "http://" + serverIp + ":" + port + ROUTE;
            } else {
                servedUrl = "http://localhost:" + port + ROUTE;
                plugin.getLogger().warning(
                    "server-ip is empty — resource pack URL uses localhost. "
                    + "Remote players won't be able to download it. "
                    + "Set resource-pack.url in config.yml or server-ip in server.properties.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not start resource-pack server on port " + port + ": " + e.getMessage());
        }
    }

    private void acceptLoop() {
        plugin.getLogger().info("[RP-HTTP] acceptLoop started, listening on " + serverSocket.getLocalSocketAddress());
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread.startVirtualThread(() -> handle(socket));
            } catch (IOException e) {
                if (running) plugin.getLogger().warning("Resource-pack accept failure: " + e.getMessage());
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            int state = 0;
            while (state < 4) {
                int b = in.read();
                if (b == -1) return;
                req.write(b);
                state = (b == '\r' || b == '\n') ? state + 1 : 0;
            }
            String raw = req.toString(StandardCharsets.ISO_8859_1);
            String requestLine = raw.indexOf('\r') >= 0 ? raw.substring(0, raw.indexOf('\r')) : raw;
            boolean hit = requestLine.startsWith("GET ") && requestLine.contains(" " + ROUTE + " ");
            plugin.getLogger().info("[RP-HTTP] " + requestLine.trim() + " -> " + (hit ? "200 OK (" + packBytes.length + "b)" : "404"));
            byte[] response;
            if (hit) {
                byte[] head = ("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: " + packBytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                ByteArrayOutputStream out = new ByteArrayOutputStream(packBytes.length + head.length);
                out.writeBytes(head);
                out.writeBytes(packBytes);
                response = out.toByteArray();
            } else {
                String body = "not found";
                response = ("HTTP/1.1 404 Not Found\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII);
            }
            socket.getOutputStream().write(response);
            socket.getOutputStream().flush();
        } catch (IOException ignored) {}
    }

    /**
     * Sends the resource pack during the configuration phase, blocks until the client
     * confirms the pack was processed (via callback), then completes reconfiguration.
     * Exactly mirrors Vane's {@code on_player_async_connection_configure}.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!running || servedUrl == null || packBytes == null) return;
        var connection = event.getConnection();
        var profileId = connection.getProfile().getId();
        if (profileId == null) return;

        var latch = new CountDownLatch(1);
        var old = latches.put(profileId, latch);
        if (old != null) old.countDown();

        try {
            var info = ResourcePackInfo.resourcePackInfo(packUuid, URI.create(servedUrl), sha1Hex);
            var request = ResourcePackRequest.resourcePackRequest()
                .required(require).replace(true)
                .packs(info)
                .callback((uuid, status, audience) -> {
                    plugin.getLogger().info("[RP] Callback fired: " + status + " (intermediate=" + status.intermediate() + ")");
                    if (!status.intermediate()) {
                        Optional.ofNullable(latches.remove(profileId)).ifPresent(CountDownLatch::countDown);
                    }
                })
                .build();
            connection.getAudience().sendResourcePacks(request);
            plugin.getLogger().info("[RP] Sent resource pack, waiting for callback...");
        } catch (Exception e) {
            plugin.getLogger().warning("[RP] Failed to send resource pack: " + e.getMessage());
            Optional.ofNullable(latches.remove(profileId)).ifPresent(CountDownLatch::countDown);
            return;
        }

        try {
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                plugin.getLogger().warning("[RP] Callback timed out after 10s, proceeding anyway.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        connection.completeReconfiguration();
        plugin.getLogger().info("[RP] completeReconfiguration() called for " + profileId);
    }
}
