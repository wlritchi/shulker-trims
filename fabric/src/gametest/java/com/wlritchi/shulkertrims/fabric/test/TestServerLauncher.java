package com.wlritchi.shulkertrims.fabric.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Utility for launching test servers (Fabric dedicated or Paper) from game tests.
 * Servers are started in separate processes and cleaned up automatically.
 */
public class TestServerLauncher implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("TestServerLauncher");

    public enum ServerType {
        FABRIC,
        PAPER
    }

    private final ServerType serverType;
    private final int port;
    private final Path serverDir;
    private Process serverProcess;
    private final ExecutorService outputReader = Executors.newSingleThreadExecutor();
    private volatile boolean serverReady = false;
    private volatile boolean serverFailed = false;
    private final StringBuilder serverLog = new StringBuilder();

    /**
     * Create a new test server launcher.
     *
     * @param serverType Type of server to launch (FABRIC or PAPER)
     * @param port Port to run the server on
     */
    public TestServerLauncher(ServerType serverType, int port) {
        this.serverType = serverType;
        this.port = port;
        try {
            this.serverDir = Files.createTempDirectory("shulker-trims-test-server-");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp server directory", e);
        }
    }

    /**
     * Start the server and wait for it to be ready.
     *
     * @param timeoutSeconds Maximum time to wait for server startup
     * @throws Exception if server fails to start
     */
    public void start(int timeoutSeconds) throws Exception {
        LOGGER.info("Starting {} server on port {}...", serverType, port);
        LOGGER.info("Server directory: {}", serverDir);

        switch (serverType) {
            case FABRIC -> startFabricServer();
            case PAPER -> startPaperServer();
        }

        // Wait for server to be ready
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (!serverReady && !serverFailed && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

        if (serverFailed) {
            throw new RuntimeException("Server failed to start. Log:\n" + serverLog);
        }

        if (!serverReady) {
            // Include server log in error message for debugging
            String lastLog = serverLog.length() > 2000
                ? serverLog.substring(serverLog.length() - 2000)
                : serverLog.toString();
            throw new TimeoutException("Server did not start within " + timeoutSeconds + " seconds. Last log output:\n" + lastLog);
        }

        // Wait for game port to actually be listening (server "Done" appears before port is bound)
        if (!waitForPort(port, 10)) {
            throw new RuntimeException("Server port " + port + " not listening after server claimed ready");
        }

        // Wait for RCON port to be ready (RCON starts after the game port)
        int rconPort = port + RCON_PORT_OFFSET;
        if (!waitForPort(rconPort, 15)) {
            LOGGER.warn("RCON port {} not listening after 15s - RCON commands may fail", rconPort);
        } else {
            LOGGER.info("RCON port {} is ready", rconPort);
            // Give RCON a moment to fully initialize after port is listening
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.info("{} server is ready on port {}", serverType, port);
    }

    private void startFabricServer() throws Exception {
        // Setup server directory
        setupFabricServerFiles();

        // Build command
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-Xmx1G", "-Xms512M",
                "-Dfabric.gametest.eula=true",
                "-jar", "fabric-server-launch.jar",
                "--nogui"
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);

        serverProcess = pb.start();
        startOutputReader("Done");
    }

    private void startPaperServer() throws Exception {
        // Download Paper if needed and setup server
        setupPaperServerFiles();

        // Build command
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-Xmx1G", "-Xms512M",
                "-jar", "paper.jar",
                "--nogui"
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);

        serverProcess = pb.start();
        startOutputReader("Done");
    }

    private void setupFabricServerFiles() throws Exception {
        // Copy the built mod JAR to mods folder
        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);

        // Find the fabric mod JAR
        Path fabricJar = findFabricModJar();
        if (fabricJar != null) {
            Files.copy(fabricJar, modsDir.resolve(fabricJar.getFileName()));
            LOGGER.info("Copied mod JAR: {}", fabricJar.getFileName());
        }

        // Find Fabric API JAR (needed for server)
        // For now, we'll rely on the server-launch.jar to handle this

        // Create server properties
        writeServerProperties();

        // Accept EULA
        Files.writeString(serverDir.resolve("eula.txt"), "eula=true\n");

        // Create fabric-server-launcher.properties to specify loader/game versions
        // The server launch JAR needs to be present - this is typically handled
        // by running `./gradlew :fabric:runServer` once to download dependencies
        LOGGER.warn("Fabric server launcher not fully implemented - using in-process server instead");
    }

    private void setupPaperServerFiles() throws Exception {
        // Download latest Paper build
        String mcVersion = "1.21.10";
        Path paperJar = downloadPaperServer(mcVersion);

        // Create server properties
        writeServerProperties();

        // Accept EULA
        Files.writeString(serverDir.resolve("eula.txt"), "eula=true\n");

        // Create plugins directory and copy plugin (throws if not found)
        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        Path pluginJar = findBukkitPluginJar();
        Files.copy(pluginJar, pluginsDir.resolve(pluginJar.getFileName()));
        LOGGER.info("Copied plugin JAR: {}", pluginJar.getFileName());

        // Rename paper jar for simpler command
        Files.move(paperJar, serverDir.resolve("paper.jar"));
    }

    private static final String RCON_PASSWORD = "shulkertest";
    private static final int RCON_PORT_OFFSET = 1000;

    private void writeServerProperties() throws IOException {
        int rconPort = port + RCON_PORT_OFFSET;
        // Use a void-like flat world with a single barrier layer at y=0
        // This prevents mobs from spawning and gives us a clean background
        String generatorSettings = """
                {"layers":[{"block":"minecraft:barrier","height":1}],"biome":"minecraft:the_void","features":false}""".trim();

        String properties = String.format("""
                # Test server configuration
                server-port=%d
                online-mode=false
                spawn-protection=0
                max-players=2
                view-distance=10
                simulation-distance=10
                level-type=minecraft:flat
                generator-settings=%s
                level-seed=1
                generate-structures=false
                spawn-monsters=false
                spawn-animals=false
                spawn-npcs=false
                allow-flight=true
                gamemode=creative
                enable-rcon=true
                rcon.port=%d
                rcon.password=%s
                broadcast-rcon-to-ops=false
                """, port, generatorSettings, rconPort, RCON_PASSWORD);

        Files.writeString(serverDir.resolve("server.properties"), properties);
    }

    /**
     * Send a command to the server via RCON.
     *
     * @param command The command to execute (without leading /)
     * @return The command response
     * @throws IOException if RCON communication fails
     */
    public String sendCommand(String command) throws IOException {
        if (!serverReady) {
            throw new IllegalStateException("Server is not ready");
        }
        int rconPort = port + RCON_PORT_OFFSET;
        return RconClient.sendCommand("127.0.0.1", rconPort, RCON_PASSWORD, command);
    }

    private Path downloadPaperServer(String mcVersion) throws Exception {
        LOGGER.info("Fetching Paper server build info for {}...", mcVersion);

        // Get latest build number
        URL buildsUrl = new URL("https://api.papermc.io/v2/projects/paper/versions/" + mcVersion + "/builds");
        HttpURLConnection conn = (HttpURLConnection) buildsUrl.openConnection();
        conn.setRequestMethod("GET");

        String response;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            response = sb.toString();
        }

        // Simple JSON parsing to get latest build
        int buildStart = response.lastIndexOf("\"build\":");
        if (buildStart == -1) {
            throw new RuntimeException("Failed to parse Paper builds response");
        }
        String buildStr = response.substring(buildStart + 8);
        int buildEnd = buildStr.indexOf(',');
        if (buildEnd == -1) buildEnd = buildStr.indexOf('}');
        int latestBuild = Integer.parseInt(buildStr.substring(0, buildEnd).trim());

        LOGGER.info("Latest Paper build: {}", latestBuild);

        // Download the JAR
        String jarName = "paper-" + mcVersion + "-" + latestBuild + ".jar";
        URL downloadUrl = new URL("https://api.papermc.io/v2/projects/paper/versions/" + mcVersion +
                "/builds/" + latestBuild + "/downloads/" + jarName);

        Path jarPath = serverDir.resolve(jarName);
        LOGGER.info("Downloading Paper server...");

        try (InputStream in = downloadUrl.openStream()) {
            Files.copy(in, jarPath, StandardCopyOption.REPLACE_EXISTING);
        }

        LOGGER.info("Downloaded: {} ({} bytes)", jarName, Files.size(jarPath));
        return jarPath;
    }

    private Path findFabricModJar() {
        // Look for the built fabric mod JAR
        Path fabricBuild = Path.of("fabric/build/libs");
        if (Files.exists(fabricBuild)) {
            try (var stream = Files.list(fabricBuild)) {
                return stream
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .filter(p -> !p.getFileName().toString().contains("sources"))
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                LOGGER.warn("Failed to find fabric mod JAR", e);
            }
        }
        return null;
    }

    private Path findBukkitPluginJar() {
        // The game test runs from fabric/build/run/clientGameTest, so go up 4 levels to project root
        Path bukkitBuild = Path.of("../../../../bukkit/build/libs");

        if (!Files.exists(bukkitBuild)) {
            throw new RuntimeException("Bukkit plugin build directory not found: " + bukkitBuild.toAbsolutePath() +
                    ". Run './gradlew :bukkit:build' first.");
        }

        try (var stream = Files.list(bukkitBuild)) {
            // Find the most recently modified JAR (excluding sources)
            Path jar = stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);

            if (jar == null) {
                throw new RuntimeException("No plugin JAR found in " + bukkitBuild.toAbsolutePath() +
                        ". Run './gradlew :bukkit:build' first.");
            }

            LOGGER.info("Using plugin JAR: {}", jar.getFileName());
            return jar;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list bukkit plugin directory", e);
        }
    }

    private void startOutputReader(String readyMarker) {
        outputReader.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    serverLog.append(line).append("\n");
                    LOGGER.debug("[Server] {}", line);

                    if (line.contains(readyMarker)) {
                        serverReady = true;
                    }

                    if (line.contains("FAILED") || line.contains("ERROR") && line.contains("Unable to start")) {
                        serverFailed = true;
                    }
                }
            } catch (IOException e) {
                if (!serverReady) {
                    serverFailed = true;
                }
            }
        });
    }

    /**
     * Wait for a port to be accepting connections.
     *
     * @param portToCheck The port to wait for
     * @param timeoutSeconds Maximum time to wait
     * @return true if port is listening, false if timeout
     */
    private boolean waitForPort(int portToCheck, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", portToCheck)) {
                LOGGER.info("Port {} is now accepting connections", portToCheck);
                return true;
            } catch (IOException e) {
                // Port not ready yet, wait and retry
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Get the server address for connecting.
     */
    public String getAddress() {
        return "127.0.0.1:" + port;
    }

    /**
     * Get the server port.
     */
    public int getPort() {
        return port;
    }

    /**
     * Check if the server is ready.
     */
    public boolean isReady() {
        return serverReady && serverProcess != null && serverProcess.isAlive();
    }

    @Override
    public void close() {
        LOGGER.info("Stopping {} server...", serverType);

        if (serverProcess != null && serverProcess.isAlive()) {
            // Try graceful shutdown first
            serverProcess.destroy();
            try {
                if (!serverProcess.waitFor(10, TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                serverProcess.destroyForcibly();
            }
        }

        outputReader.shutdownNow();

        // Clean up server directory
        try {
            deleteDirectory(serverDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up server directory: {}", serverDir, e);
        }

        LOGGER.info("Server stopped and cleaned up");
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete contents first
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }
}
