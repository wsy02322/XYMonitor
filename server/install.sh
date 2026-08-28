#!/usr/bin/env bash
# XYMonitor VPS 一键安装。在仓库里执行：
#   sudo bash server/install.sh
set -euo pipefail

PORT="${XY_PORT:-8787}"
DEST="${XY_HOME:-/opt/xymonitor}"
SRC="$(cd "$(dirname "$0")" && pwd)"
SERVICE="/etc/systemd/system/xymonitor.service"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "请用 root 或 sudo 执行：sudo bash server/install.sh" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y python3
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y python3
  elif command -v yum >/dev/null 2>&1; then
    yum install -y python3
  else
    echo "请先安装 python3（需要 3.10+）" >&2
    exit 1
  fi
fi

PY_VER="$(python3 -c 'import sys; print("%d.%d" % sys.version_info[:2])')"
PY_OK="$(python3 -c 'import sys; print(int(sys.version_info >= (3, 10)))')"
if [[ "$PY_OK" != "1" ]]; then
  echo "当前 python3 是 ${PY_VER}，需要 3.10+" >&2
  exit 1
fi

mkdir -p "$DEST/data"
for f in app.py httpd.py inspector.py monitor.py mtop.py telegram.py xianyu.py; do
  install -m 0644 "$SRC/$f" "$DEST/$f"
done

if [[ ! -f "$DEST/.token" ]]; then
  python3 -c 'import secrets; print(secrets.token_urlsafe(24))' > "$DEST/.token"
  chmod 600 "$DEST/.token"
fi

cat > "$DEST/env" <<EOF
XY_HOST=0.0.0.0
XY_PORT=$PORT
XY_TOKEN_FILE=$DEST/.token
XY_STATE_FILE=$DEST/data/state.json
# TELEGRAM_BOT_TOKEN=
# TELEGRAM_CHAT_ID=
EOF
chmod 600 "$DEST/env"

cat > "$SERVICE" <<EOF
[Unit]
Description=XYMonitor
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$DEST
EnvironmentFile=-$DEST/env
ExecStart=$(command -v python3) $DEST/app.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow "${PORT}/tcp" || true
fi
if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state 2>/dev/null | grep -q running; then
  firewall-cmd --permanent --add-port="${PORT}/tcp" || true
  firewall-cmd --reload || true
fi

systemctl daemon-reload
systemctl enable xymonitor
systemctl restart xymonitor
sleep 1
systemctl --no-pager --full status xymonitor || true

TOKEN="$(tr -d '[:space:]' < "$DEST/.token")"
IP="$(curl -4 -fsS --max-time 5 ifconfig.me 2>/dev/null || curl -4 -fsS --max-time 5 icanhazip.com 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')"
IP="${IP:-你的VPS公网IP}"

cat <<EOF

已启动。填进 App：
  地址  http://${IP}:${PORT}
  密钥  ${TOKEN}

常用命令：
  systemctl status xymonitor
  journalctl -u xymonitor -f
  cat $DEST/.token

云厂商安全组也要放行 TCP ${PORT}。
EOF
