#!/bin/sh
# Builds CredCloud Helper for every computer CredCloud serves it to, into a folder
# (helper/dist by default). The Dockerfile runs this; so can you, for a local CredCloud:
#   cd helper && sh build.sh
# -trimpath and no cgo make the builds reproducible: the same code gives the same file, so a
# helper updates itself only when the code changed.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out=${1:-$here/dist}
case "$out" in /*) ;; *) out="$(pwd)/$out" ;; esac
mkdir -p "$out"
cd "$here"
build() {
  echo "building $1/$2"
  CGO_ENABLED=0 GOOS=$1 GOARCH=$2 go build -trimpath -ldflags "-s -w $3" -o "$out/$4" .
}
build darwin arm64 "" credcloud-helper-darwin-arm64
build darwin amd64 "" credcloud-helper-darwin-amd64
# A Windows app, not a console one, so starting it doesn't flash a black window.
build windows amd64 "-H=windowsgui" credcloud-helper-windows-amd64.exe
