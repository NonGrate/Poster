#!/usr/bin/env bash
# Reaches the local backend from a phone.
#
#   scripts/dev-tunnel.sh android      # USB-connected Android device: adb reverse
#   scripts/dev-tunnel.sh lan          # print the LAN address to bake into a build
#   scripts/dev-tunnel.sh public       # a public https tunnel (cloudflared or ngrok)
#
# The Android *emulator* needs none of this — 10.0.2.2:8080 is the host machine,
# and the e2e flavour is wired to it already. The iOS *simulator* needs none of
# it either — 127.0.0.1:8080 is the Mac. This script is for real hardware.
set -euo pipefail
PORT=${PORT:-8080}
MODE=${1:-help}

case $MODE in
  android)
    # The phone's localhost:8080 becomes the Mac's localhost:8080 over USB. Build
    # the app pointed at 127.0.0.1 — e.g.
    #   ./gradlew :composeApp:installRemoteDebug -PposterRemoteScheme=http \
    #       -PposterRemoteHost=127.0.0.1 -PposterRemotePort=8080
    adb reverse "tcp:$PORT" "tcp:$PORT"
    echo "adb reverse set: the device's 127.0.0.1:$PORT now reaches this machine's :$PORT"
    echo "Build with: -PposterRemoteScheme=http -PposterRemoteHost=127.0.0.1 -PposterRemotePort=$PORT"
    ;;
  lan)
    IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')
    [ -n "${IP:-}" ] || { echo "could not determine a LAN address" >&2; exit 1; }
    echo "This machine on the LAN: http://$IP:$PORT  (the phone must be on the same Wi-Fi)"
    echo "Android: -PposterRemoteScheme=http -PposterRemoteHost=$IP -PposterRemotePort=$PORT"
    echo "iOS:     POSTER_SERVER_SCHEME=http POSTER_SERVER_HOST=$IP POSTER_SERVER_PORT=$PORT (Config.xcconfig or xcodebuild args)"
    echo "Note: iOS App Transport Security allows plain http only to local networks; Info.plist already sets NSAllowsLocalNetworking."
    ;;
  public)
    # A real https URL for a device anywhere, and the only way to test Apple
    # sign-in on Android (Apple needs a public https return URL) or email links.
    if command -v cloudflared >/dev/null; then
      echo "Starting cloudflared quick tunnel to http://localhost:$PORT — read the https URL it prints."
      echo "Then build with -PposterRemoteScheme=https -PposterRemoteHost=<that host> -PposterRemotePort=443"
      exec cloudflared tunnel --url "http://localhost:$PORT"
    elif command -v ngrok >/dev/null; then
      echo "Starting ngrok to http://localhost:$PORT — read the https URL it prints."
      exec ngrok http "$PORT"
    else
      echo "Install one of: brew install cloudflared   |   brew install ngrok" >&2
      exit 1
    fi
    ;;
  *)
    sed -n '2,10p' "$0"
    ;;
esac
