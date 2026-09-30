#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./start-all.sh [--clean] [--force]

  --clean   Delete mutable blockchain state before starting.
  --force   Kill processes using UDP ports 2999-3004 before starting.
EOF
}

require_command() {
  local cmd=$1
  local hint=$2

  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    echo "$hint" >&2
    exit 1
  fi
}

collect_busy_udp_ports() {
  BUSY_PORTS=()
  PIDS_TO_KILL=()

  local port
  local pid
  local known_pid
  local seen

  for port in "${ALL_PORTS[@]}"; do
    if lsof -nP -iUDP:"$port" >/dev/null 2>&1; then
      BUSY_PORTS+=("$port")

      while IFS= read -r pid; do
        [ -z "$pid" ] && continue

        seen=false
        for known_pid in "${PIDS_TO_KILL[@]}"; do
          if [ "$known_pid" = "$pid" ]; then
            seen=true
            break
          fi
        done

        if [ "$seen" = false ]; then
          PIDS_TO_KILL+=("$pid")
        fi
      done < <(lsof -t -nP -iUDP:"$port" 2>/dev/null || true)
    fi
  done
}

kill_busy_udp_ports() {
  if [ "${#PIDS_TO_KILL[@]}" -eq 0 ]; then
    return
  fi

  kill "${PIDS_TO_KILL[@]}" 2>/dev/null || true
  sleep 1
  collect_busy_udp_ports

  if [ "${#BUSY_PORTS[@]}" -eq 0 ]; then
    return
  fi

  if [ "${#PIDS_TO_KILL[@]}" -gt 0 ]; then
    kill -9 "${PIDS_TO_KILL[@]}" 2>/dev/null || true
    sleep 1
  fi

  collect_busy_udp_ports

  if [ "${#BUSY_PORTS[@]}" -gt 0 ]; then
    echo "Could not free UDP ports: ${BUSY_PORTS[*]}" >&2
    exit 1
  fi
}

launch_in_pane() {
  local target=$1
  local dir=$2
  local args=$3

  tmux send-keys -t "$target" \
    "cd '$dir' && mvn -q -DskipTests -f pom.xml exec:java -Dexec.args='$args'; exec bash" C-m
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SESSION="sec-all"
DATABASE_DIR="$SCRIPT_DIR/database"
NODE_DIR="$SCRIPT_DIR/node"
CLIENT_DIR="$SCRIPT_DIR/client"
NODE_COUNT=4
START_NODE_PORT=3001
CLIENT_PORTS=(2999 3000)
NODE_PORTS=(3001 3002 3003 3004)
ALL_PORTS=(2999 3000 3001 3002 3003 3004)
BUSY_PORTS=()
PIDS_TO_KILL=()
FORCE=false
CLEAN=false

while [ $# -gt 0 ]; do
  case "$1" in
    --clean)
      CLEAN=true
      ;;
    --force)
      FORCE=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

require_command tmux "Install tmux and rerun."
require_command mvn "Install Maven 3.x and ensure Java 17 is available."
require_command lsof "Install lsof and rerun."

echo "Building project..."
(cd "$SCRIPT_DIR" && mvn -q -DskipTests install)

if tmux has-session -t "$SESSION" 2>/dev/null; then
  tmux kill-session -t "$SESSION"
  sleep 1
fi

if [ "$CLEAN" = true ]; then
  rm -f "$DATABASE_DIR/accounts.json" "$DATABASE_DIR/smart_contracts.json"
fi

collect_busy_udp_ports
if [ "${#BUSY_PORTS[@]}" -gt 0 ]; then
  if [ "$FORCE" = true ]; then
    echo "Clearing UDP ports: ${BUSY_PORTS[*]}"
    kill_busy_udp_ports
  else
    echo "UDP ports already in use: ${BUSY_PORTS[*]}" >&2
    echo "Re-run with --force to kill processes using ports 2999-3004." >&2
    exit 1
  fi
fi

FIRST_PANE="$(tmux new-session -d -P -F '#{pane_id}' -s "$SESSION" -n nodes)"
launch_in_pane "$FIRST_PANE" "$NODE_DIR" "$START_NODE_PORT $NODE_COUNT $START_NODE_PORT"

for port in "${NODE_PORTS[@]:1}"; do
  PANE_ID="$(tmux split-window -P -F '#{pane_id}' -t "$SESSION:nodes" -v)"
  launch_in_pane "$PANE_ID" "$NODE_DIR" "$port $NODE_COUNT $START_NODE_PORT"
  tmux select-layout -t "$SESSION:nodes" tiled
done

for port in "${CLIENT_PORTS[@]}"; do
  tmux new-window -t "$SESSION" -n "$port"
  launch_in_pane "$SESSION:$port.0" "$CLIENT_DIR" "$port $NODE_COUNT $START_NODE_PORT"
done

tmux set-option -t "$SESSION" status on
tmux set-option -t "$SESSION" status-position bottom
tmux set-option -t "$SESSION" mouse on
tmux select-window -t "$SESSION:nodes"

if [ -n "${TMUX:-}" ]; then
  exec tmux switch-client -t "$SESSION"
else
  exec tmux attach-session -t "$SESSION"
fi
