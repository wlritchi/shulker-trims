#!/bin/bash
# Paper Plugin Runtime Test Script
# Tests that the Bukkit plugin loads successfully on a real Paper server
#
# Usage: ./paper-runtime-test.sh <plugin-jar-path> [mc-version]
#
# Exit codes:
#   0 - Plugin loaded successfully
#   1 - Plugin failed to load or server error

set -euo pipefail

PLUGIN_JAR="${1:?Plugin JAR path required}"
MC_VERSION="${2:-1.21.10}"
SERVER_DIR="$(mktemp -d)"
STARTUP_TIMEOUT=300  # 5 minutes max for server startup
SHUTDOWN_TIMEOUT=30  # 30 seconds for graceful shutdown

# Cleanup on exit
cleanup() {
    local exit_code=$?
    echo "Cleaning up server directory: $SERVER_DIR"
    # Kill entire process group to ensure tee and java both terminate
    if [[ -n "${SERVER_PID:-}" ]]; then
        # Kill the process group
        kill -- -"$SERVER_PID" 2>/dev/null || kill "$SERVER_PID" 2>/dev/null || true
        sleep 2
        # Force kill if still running
        kill -9 -- -"$SERVER_PID" 2>/dev/null || kill -9 "$SERVER_PID" 2>/dev/null || true
    fi
    rm -rf "$SERVER_DIR"
    exit $exit_code
}
trap cleanup EXIT

# Ensure we're a process group leader for clean shutdown
set -m

echo "=== Paper Plugin Runtime Test ==="
echo "Plugin JAR: $PLUGIN_JAR"
echo "MC Version: $MC_VERSION"
echo "Server directory: $SERVER_DIR"

# Verify plugin JAR exists
if [[ ! -f "$PLUGIN_JAR" ]]; then
    echo "ERROR: Plugin JAR not found: $PLUGIN_JAR"
    exit 1
fi

# Get absolute path to plugin JAR
PLUGIN_JAR="$(realpath "$PLUGIN_JAR")"

# Fetch latest Paper build for the version
echo ""
echo "=== Fetching Paper server build info ==="
BUILD_INFO=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds")
if [[ -z "$BUILD_INFO" ]] || echo "$BUILD_INFO" | grep -q '"error"'; then
    echo "ERROR: Failed to fetch Paper builds for version $MC_VERSION"
    echo "Response: $BUILD_INFO"
    exit 1
fi

LATEST_BUILD=$(echo "$BUILD_INFO" | jq -r '.builds[-1].build')
if [[ -z "$LATEST_BUILD" ]] || [[ "$LATEST_BUILD" == "null" ]]; then
    echo "ERROR: No builds found for Paper $MC_VERSION"
    exit 1
fi

PAPER_JAR="paper-$MC_VERSION-$LATEST_BUILD.jar"
PAPER_URL="https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds/$LATEST_BUILD/downloads/$PAPER_JAR"

echo "Latest build: $LATEST_BUILD"
echo "Download URL: $PAPER_URL"

# Download Paper server
echo ""
echo "=== Downloading Paper server ==="
cd "$SERVER_DIR"
curl -L -o "$PAPER_JAR" "$PAPER_URL"
if [[ ! -f "$PAPER_JAR" ]]; then
    echo "ERROR: Failed to download Paper server"
    exit 1
fi
echo "Downloaded: $PAPER_JAR ($(du -h "$PAPER_JAR" | cut -f1))"

# Setup server
echo ""
echo "=== Setting up server ==="

# Accept EULA
echo "eula=true" > eula.txt

# Create minimal server.properties for fast startup
cat > server.properties << 'EOF'
# Minimal config for CI testing
server-port=25565
online-mode=false
spawn-protection=0
max-players=1
view-distance=2
simulation-distance=2
level-type=minecraft:flat
level-seed=0
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
max-world-size=16
EOF

# Create plugins directory and copy plugin
mkdir -p plugins
cp "$PLUGIN_JAR" plugins/
echo "Installed plugin: $(ls plugins/)"

# Create config to disable some features for faster startup
mkdir -p config
cat > config/paper-global.yml << 'EOF'
_version: 31
chunk-loading-basic:
  autoconfig-send-distance: false
  player-max-concurrent-chunk-generates: 0
  player-max-concurrent-chunk-loads: 0
chunk-loading-advanced:
  auto-config-send-distance: false
  player-max-chunk-generate-rate: -1.0
  player-max-chunk-load-rate: -1.0
  player-max-chunk-send-rate: -1.0
chunk-system:
  gen-parallelism: default
  io-threads: -1
  worker-threads: -1
collisions:
  enable-player-collisions: true
  send-full-pos-for-hard-colliding-entities: true
commands:
  fix-target-selector-tag-completion: true
  suggest-player-names-when-null-tab-completions: true
  time-command-affects-all-worlds: false
console:
  enable-brigadier-completions: true
  enable-brigadier-highlighting: true
  has-all-permissions: false
item-validation:
  book:
    author: 8192
    page: 16384
    title: 8192
  book-size:
    page-max: 2560
    total-multiplier: 0.98
  display-name: 8192
  lore-line: 8192
  resolve-selectors-in-books: false
logging:
  deobfuscate-stacktraces: true
messages:
  kick:
    authentication-servers-down: <lang:multiplayer.disconnect.authservers_down>
    connection-throttle: Connection throttled! Please wait before reconnecting.
    flying-player: <lang:multiplayer.disconnect.flying>
    flying-vehicle: <lang:multiplayer.disconnect.flying>
  no-permission: <red>I'm sorry, but you do not have permission to perform this command.
    Please contact the server administrators if you believe that this is in error.
  use-hierarchical-permissions: false
misc:
  chat-threads:
    chat-executor-core-size: -1
    chat-executor-max-size: -1
  fix-entity-position-desync: true
  lag-compensate-block-breaking: true
  load-permissions-yml-before-plugins: true
  max-joins-per-tick: 5
  region-file-cache-size: 256
  strict-advancement-dimension-check: false
  use-alternative-luck-formula: false
  use-dimension-type-for-custom-spawners: false
packet-limiter:
  all-packets:
    action: KICK
    interval: 7.0
    max-packet-rate: 500.0
  kick-message: <red><lang:disconnect.exceeded_packet_rate>
  overrides:
    ServerboundPlaceRecipePacket:
      action: DROP
      interval: 4.0
      max-packet-rate: 5.0
player-auto-save:
  max-per-tick: -1
  rate: -1
proxies:
  bungee-cord:
    online-mode: true
  proxy-protocol: false
  velocity:
    enabled: false
    online-mode: false
    secret: ''
scoreboards:
  save-empty-scoreboard-teams: true
  track-plugin-scoreboards: false
spam-limiter:
  incoming-packet-threshold: 300
  recipe-spam-increment: 1
  recipe-spam-limit: 20
  tab-spam-increment: 1
  tab-spam-limit: 500
timings:
  enabled: true
  hidden-config-entries:
  - database
  - proxies.velocity.secret
  history-interval: 300
  history-length: 3600
  server-name: Unknown Server
  server-name-privacy: false
  url: https://timings.aikar.co/
  verbose: true
unsupported-settings:
  allow-grindstone-overstacking: false
  allow-headless-pistons: false
  allow-permanent-block-break-exploits: false
  allow-piston-duplication: false
  compression-format: ZLIB
  perform-username-validation: true
watchdog:
  early-warning-delay: 10000
  early-warning-every: 5000
EOF

# Start server and capture output
echo ""
echo "=== Starting Paper server ==="
LOGFILE="$SERVER_DIR/server.log"

# Start server in background, capturing all output
java -Xmx1G -Xms512M -jar "$PAPER_JAR" --nogui 2>&1 | tee "$LOGFILE" &
SERVER_PID=$!

echo "Server started with PID: $SERVER_PID"

# Wait for server to fully start or fail
echo ""
echo "=== Waiting for server startup (timeout: ${STARTUP_TIMEOUT}s) ==="
START_TIME=$(date +%s)
SERVER_READY=false
PLUGIN_LOADED=false
PLUGIN_ERROR=false

while true; do
    ELAPSED=$(($(date +%s) - START_TIME))

    # Check timeout
    if [[ $ELAPSED -ge $STARTUP_TIMEOUT ]]; then
        echo "ERROR: Server startup timeout after ${STARTUP_TIMEOUT}s"
        break
    fi

    # Check if server process died
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "ERROR: Server process died unexpectedly"
        break
    fi

    # Check for server ready message
    if grep -q "Done (" "$LOGFILE" 2>/dev/null; then
        SERVER_READY=true
        echo "Server startup complete! (${ELAPSED}s)"
        break
    fi

    # Check for fatal errors during startup
    if grep -qiE "(FATAL|Could not load|Failed to start|Encountered an unexpected exception)" "$LOGFILE" 2>/dev/null; then
        echo "Potential fatal error detected, waiting for more context..."
    fi

    sleep 1
done

echo ""
echo "=== Analyzing server logs ==="

# Check plugin loading status
if grep -q "\[ShulkerTrims\] Enabling ShulkerTrims" "$LOGFILE" 2>/dev/null; then
    PLUGIN_LOADED=true
    echo "Plugin enabled successfully"
fi

# Check for plugin-related errors
if grep -qiE "\[ShulkerTrims\].*(ERROR|SEVERE|Exception|Error)" "$LOGFILE" 2>/dev/null; then
    PLUGIN_ERROR=true
    echo "WARNING: Plugin-related errors found in logs"
fi

# Check for NMS/reflection errors that might indicate version incompatibility
if grep -qiE "(NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|NoClassDefFoundError).*craft" "$LOGFILE" 2>/dev/null; then
    PLUGIN_ERROR=true
    echo "ERROR: NMS compatibility errors found"
fi

# Gracefully stop the server
echo ""
echo "=== Stopping server ==="
if kill -0 "$SERVER_PID" 2>/dev/null; then
    # Kill the process group (tee + java)
    echo "Sending TERM signal to process group..."
    kill -- -"$SERVER_PID" 2>/dev/null || kill "$SERVER_PID" 2>/dev/null || true

    # Wait for graceful shutdown
    SHUTDOWN_START=$(date +%s)
    while kill -0 "$SERVER_PID" 2>/dev/null; do
        SHUTDOWN_ELAPSED=$(($(date +%s) - SHUTDOWN_START))
        if [[ $SHUTDOWN_ELAPSED -ge $SHUTDOWN_TIMEOUT ]]; then
            echo "Force killing server..."
            kill -9 -- -"$SERVER_PID" 2>/dev/null || kill -9 "$SERVER_PID" 2>/dev/null || true
            break
        fi
        sleep 1
    done
    wait "$SERVER_PID" 2>/dev/null || true
fi
echo "Server stopped"

# Print relevant log sections
echo ""
echo "=== Plugin-related log entries ==="
grep -i "ShulkerTrims" "$LOGFILE" 2>/dev/null || echo "(no plugin log entries found)"

echo ""
echo "=== Error/Warning summary ==="
grep -iE "(ERROR|SEVERE|WARN.*ShulkerTrims|Exception.*ShulkerTrims)" "$LOGFILE" 2>/dev/null | head -50 || echo "(no errors found)"

# Final result
echo ""
echo "=== Test Results ==="
echo "Server ready: $SERVER_READY"
echo "Plugin loaded: $PLUGIN_LOADED"
echo "Plugin errors: $PLUGIN_ERROR"

if [[ "$SERVER_READY" == "true" ]] && [[ "$PLUGIN_LOADED" == "true" ]] && [[ "$PLUGIN_ERROR" == "false" ]]; then
    echo ""
    echo "SUCCESS: Plugin loaded successfully on Paper $MC_VERSION"
    exit 0
else
    echo ""
    echo "FAILURE: Plugin did not load correctly"
    echo ""
    echo "=== Full server log ==="
    cat "$LOGFILE"
    exit 1
fi
