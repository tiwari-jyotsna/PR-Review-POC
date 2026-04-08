#!/usr/bin/env bash
set -euo pipefail

# ─── Config ────────────────────────────────────────────────────────────────────
APP_NAME="loyalty-wallet"
JAR_PATTERN="target/wallet-*.jar"
LOG_DIR="logs"
LOG_FILE="$LOG_DIR/startup.log"
PORT="${SERVER_PORT:-8080}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

# ─── Colours ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
section() { echo -e "\n${CYAN}══════════════════════════════════════════${NC}"; echo -e "${CYAN}  $*${NC}"; echo -e "${CYAN}══════════════════════════════════════════${NC}"; }

# ─── Prerequisite checks ────────────────────────────────────────────────────────
section "Checking prerequisites"

if ! command -v java &>/dev/null; then
  error "Java is not installed or not on PATH. Install Java 17+."
  exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 17 ]]; then
  error "Java 17+ required. Found version: $JAVA_VERSION"
  exit 1
fi
info "Java version: $(java -version 2>&1 | head -1)"

if ! command -v mvn &>/dev/null; then
  error "Maven is not installed or not on PATH."
  exit 1
fi
info "Maven version: $(mvn -version 2>&1 | head -1)"

# ─── Port availability ──────────────────────────────────────────────────────────
section "Checking port $PORT"

if command -v lsof &>/dev/null && lsof -Pi ":$PORT" -sTCP:LISTEN -t &>/dev/null; then
  warn "Port $PORT is already in use."
  read -r -p "  Kill the existing process and continue? [y/N]: " answer
  if [[ "$answer" =~ ^[Yy]$ ]]; then
    lsof -ti ":$PORT" | xargs kill -9
    info "Process on port $PORT terminated."
  else
    error "Startup aborted. Free port $PORT and retry."
    exit 1
  fi
else
  info "Port $PORT is available."
fi

# ─── Build ──────────────────────────────────────────────────────────────────────
section "Building $APP_NAME"

mkdir -p "$LOG_DIR"

info "Running: mvn clean package -DskipTests"
if ! mvn clean package -DskipTests 2>&1 | tee -a "$LOG_FILE"; then
  error "Maven build failed. Check $LOG_FILE for details."
  exit 1
fi
info "Build successful."

# ─── Locate JAR ─────────────────────────────────────────────────────────────────
JAR_FILE=$(ls $JAR_PATTERN 2>/dev/null | head -1)
if [[ -z "$JAR_FILE" ]]; then
  error "No JAR found matching '$JAR_PATTERN'. Build may have failed."
  exit 1
fi
info "JAR: $JAR_FILE"

# ─── Launch ─────────────────────────────────────────────────────────────────────
section "Starting $APP_NAME"

info "JVM opts : $JAVA_OPTS"
info "Port     : $PORT"
info "Log file : $LOG_DIR/$APP_NAME.log"

java $JAVA_OPTS \
  -jar "$JAR_FILE" \
  --server.port="$PORT" \
  2>&1 | tee -a "$LOG_DIR/$APP_NAME.log" &

APP_PID=$!
echo $APP_PID > "$LOG_DIR/$APP_NAME.pid"
info "PID $APP_PID written to $LOG_DIR/$APP_NAME.pid"

# ─── Health check ───────────────────────────────────────────────────────────────
section "Waiting for application to be ready"

HEALTH_URL="http://localhost:$PORT/v3/api-docs"
MAX_WAIT=60   # seconds
INTERVAL=3
ELAPSED=0

while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf "$HEALTH_URL" -o /dev/null 2>/dev/null; then
    break
  fi

  # Check that the process is still alive
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    error "Application process died during startup. Check $LOG_DIR/$APP_NAME.log"
    exit 1
  fi

  echo -n "."
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done
echo ""

if ! curl -sf "$HEALTH_URL" -o /dev/null 2>/dev/null; then
  error "Application did not become ready within ${MAX_WAIT}s. Check $LOG_DIR/$APP_NAME.log"
  exit 1
fi

# ─── Ready banner ───────────────────────────────────────────────────────────────
section "Application is UP"
echo ""
echo -e "  ${GREEN}Swagger UI${NC}        →  http://localhost:$PORT/swagger-ui.html"
echo -e "  ${GREEN}OpenAPI JSON${NC}      →  http://localhost:$PORT/v3/api-docs"
echo -e "  ${GREEN}OpenAPI YAML${NC}      →  http://localhost:$PORT/v3/api-docs.yaml"
echo ""
echo -e "  ${CYAN}API Endpoints${NC}"
echo -e "  POST  http://localhost:$PORT/api/wallet/earn"
echo -e "  POST  http://localhost:$PORT/api/wallet/redeem"
echo -e "  GET   http://localhost:$PORT/api/wallet/{userId}"
echo -e "  GET   http://localhost:$PORT/api/wallet/{userId}/transactions"
echo ""
echo -e "  ${YELLOW}Logs${NC}  →  $LOG_DIR/$APP_NAME.log"
echo -e "  ${YELLOW}PID${NC}   →  $APP_PID  (saved in $LOG_DIR/$APP_NAME.pid)"
echo ""
info "To stop: kill \$(cat $LOG_DIR/$APP_NAME.pid)  or  ./startup.sh stop"

# ─── Optional stop command ──────────────────────────────────────────────────────
if [[ "${1:-}" == "stop" ]]; then
  PID_FILE="$LOG_DIR/$APP_NAME.pid"
  if [[ -f "$PID_FILE" ]]; then
    STOP_PID=$(cat "$PID_FILE")
    kill "$STOP_PID" 2>/dev/null && info "Stopped PID $STOP_PID." || warn "Process $STOP_PID not found."
    rm -f "$PID_FILE"
  else
    warn "No PID file found at $PID_FILE."
  fi
  exit 0
fi

# Keep the script alive so Ctrl-C cleanly stops the app
wait "$APP_PID"
