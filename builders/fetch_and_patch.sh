#!/usr/bin/env bash
set -euo pipefail

REPO_URL="https://github.com/19MisterX98/TextureRotations.git"
ROOT="$(pwd)"
JAVA_DIR="$ROOT/java_src"
PATCH_FILE="$ROOT/builders/StreamlitMain.java"

# Fresh workspace
rm -rf "$JAVA_DIR"
mkdir -p "$JAVA_DIR"

# Clone shallow
rm -rf /tmp/TextureRotations
git clone --depth 1 "$REPO_URL" /tmp/TextureRotations

# Copy upstream src into our local java_src/
cp -r /tmp/TextureRotations/src/* "$JAVA_DIR/"

# Drop our CLI entrypoint next to upstream sources
cp "$PATCH_FILE" "$JAVA_DIR/"

echo "Sources ready in $JAVA_DIR"
