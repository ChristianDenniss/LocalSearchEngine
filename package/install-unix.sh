#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Create a 'search' command that delegates to the platform binary
if [ -d "$HERE/local-search.app" ]; then
    # macOS app-image
    BINARY="$HERE/local-search.app/Contents/MacOS/local-search"
else
    # Linux app-image
    BINARY="$HERE/local-search/bin/local-search"
fi

WRAPPER="$HERE/search"
printf '#!/usr/bin/env bash\nexec "%s" "$@"\n' "$BINARY" > "$WRAPPER"
chmod +x "$WRAPPER"

added=false
for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.bash_profile"; do
    if [ -f "$rc" ] && ! grep -qF "$HERE" "$rc" 2>/dev/null; then
        printf '\nexport PATH="%s:$PATH"\n' "$HERE" >> "$rc"
        echo "Added to PATH in $rc"
        added=true
    fi
done

if [ "$added" = false ] && ! grep -qF "$HERE" "$HOME/.profile" 2>/dev/null; then
    printf '\nexport PATH="%s:$PATH"\n' "$HERE" >> "$HOME/.profile"
    echo "Added to PATH in ~/.profile"
fi

echo ""
echo "Done! Restart your terminal (or run: source ~/.bashrc) then try:"
echo "  search \"your query\""
echo "  search \"resume\" --limit 10 --explain"
