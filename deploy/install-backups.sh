#!/bin/bash
# Sets up nightly production backups on this Mac. Safe to run again after changing the
# scripts: it copies them into place and reloads the schedule.
#
#   bash deploy/install-backups.sh
#
#   1. makes the backup key (once): ~/credcloud/backup-key.txt, and .pub beside it
#   2. copies backup.sh and restore-drill.sh to ~/credcloud/bin, so the schedule doesn't
#      depend on which branch this checkout is on
#   3. schedules backup.sh for 2:30 every night with launchd (a Mac that's asleep then
#      catches up when it wakes)
#   4. runs one backup with a restore drill right away, the same way launchd will
set -euo pipefail
cd "$(dirname "$0")"

root="$HOME/credcloud"
label="app.credcloud.backup"
plist="$HOME/Library/LaunchAgents/$label.plist"

command -v age >/dev/null || { echo "Install age first: brew install age"; exit 1; }
command -v docker >/dev/null || { echo "Docker isn't installed or isn't on your PATH."; exit 1; }

mkdir -p "$root/bin" "$root/logs"
if [ ! -f "$root/backup-key.txt" ]; then
  (umask 077 && age-keygen -o "$root/backup-key.txt" 2>/dev/null)
  echo "Made a new backup key."
  new_key=true
else
  new_key=false
fi
age-keygen -y "$root/backup-key.txt" > "$root/backup-key.pub"
chmod 600 "$root/backup-key.txt"

install -m 700 backup.sh restore-drill.sh "$root/bin/"

cat > "$plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$label</string>
    <key>ProgramArguments</key>
    <array>
        <string>/bin/bash</string>
        <string>$root/bin/backup.sh</string>
    </array>
    <key>StartCalendarInterval</key>
    <dict>
        <key>Hour</key>
        <integer>2</integer>
        <key>Minute</key>
        <integer>30</integer>
    </dict>
    <key>StandardOutPath</key>
    <string>$root/logs/backup.log</string>
    <key>StandardErrorPath</key>
    <string>$root/logs/backup.log</string>
</dict>
</plist>
EOF

domain="gui/$(id -u)"
launchctl bootout "$domain/$label" 2>/dev/null || true
launchctl bootstrap "$domain" "$plist"
echo "Scheduled nightly at 2:30. Log: $root/logs/backup.log"

# A first run through launchd itself, so a permissions problem shows up now, not at 2:30.
log="$root/logs/backup.log"
touch "$log"
start=$(wc -l < "$log")
echo "Running a first backup with a restore drill..."
launchctl kickstart -k "$domain/$label"
# kickstart can't pass --drill, so run the drill here once the backup finishes.
for _ in $(seq 1 120); do
  if tail -n +"$((start + 1))" "$log" | grep -qE "Done$|FAILED"; then
    break
  fi
  sleep 1
done
tail -n +"$((start + 1))" "$log"
if tail -n +"$((start + 1))" "$log" | grep -q "FAILED"; then
  echo
  echo "The first backup failed. If the log says 'Operation not permitted', macOS is keeping"
  echo "the job out of iCloud Drive: System Settings > Privacy & Security > Full Disk Access,"
  echo "press +, press Cmd-Shift-G, enter /bin/bash, add it, then run this script again."
  exit 1
fi
"$root/bin/restore-drill.sh"

if $new_key; then
  echo
  echo "IMPORTANT: save the backup key somewhere other than this Mac. Without it the backups"
  echo "can't be opened. To copy it for your password manager:"
  echo "  pbcopy < $root/backup-key.txt"
fi
