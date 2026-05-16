#!/usr/bin/env bash
set -euo pipefail

COMMAND_NAME="${1:-search}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/dist/local-search-engine-1.0.0.jar"
COMMAND_DIR="$HOME/search-bin"
COMMAND_PATH="$COMMAND_DIR/$COMMAND_NAME"

if [ ! -f "$JAR_PATH" ]; then
    echo "Build artifact not found. Building project..."
    (cd "$SCRIPT_DIR" && mvn -q -DskipTests package)
fi

mkdir -p "$COMMAND_DIR"

cat > "$COMMAND_PATH" << EOF
#!/usr/bin/env bash
exec java -jar "$JAR_PATH" search "\$@"
EOF

chmod +x "$COMMAND_PATH"

add_to_path_in_file()
{
    local rc_file="$1"
    if grep -qF "$COMMAND_DIR" "$rc_file" 2>/dev/null; then
        echo "PATH already contains $COMMAND_DIR in $rc_file"
    else
        printf '\n# Local Search Engine\nexport PATH="%s:$PATH"\n' "$COMMAND_DIR" >> "$rc_file"
        echo "Added to PATH in $rc_file"
    fi
}

found_rc=false
for rc_file in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.bash_profile"; do
    if [ -f "$rc_file" ]; then
        add_to_path_in_file "$rc_file"
        found_rc=true
    fi
done

if [ "$found_rc" = false ]; then
    touch "$HOME/.profile"
    add_to_path_in_file "$HOME/.profile"
fi

echo ""
echo "Installed command: $COMMAND_NAME"
echo "Launcher file:     $COMMAND_PATH"
echo ""
echo "Restart your terminal (or run: source ~/.bashrc) and then try:"
echo "  $COMMAND_NAME \"your query\""
echo "Example:"
echo "  $COMMAND_NAME \"resume\" --limit 10 --explain"
