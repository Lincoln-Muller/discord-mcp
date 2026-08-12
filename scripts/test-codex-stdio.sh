#!/bin/sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
wrapper="$repo_dir/scripts/codex-stdio.py"
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT

mkdir -p "$test_dir/bin" "$test_dir/state"

cat >"$test_dir/bin/colima" <<'EOF'
#!/bin/sh
case "$1" in
  status) test -f "$TEST_STATE/colima" ;;
  start) : >"$TEST_STATE/colima"; echo colima-start >>"$TEST_STATE/log" ;;
  stop) rm -f "$TEST_STATE/colima"; echo colima-stop >>"$TEST_STATE/log" ;;
esac
EOF

cat >"$test_dir/bin/docker" <<'EOF'
#!/bin/sh
case "$1:$2" in
  image:inspect) exit 0 ;;
  ps:-aq) exit 0 ;;
  ps:-q) test ! -f "$TEST_STATE/container" || echo fake-container ;;
  rm:-f) rm -f "$TEST_STATE/container"; echo docker-rm >>"$TEST_STATE/log" ;;
  run:--rm)
    echo "$*" >"$TEST_STATE/docker-args"
    : >"$TEST_STATE/container"
    (while test -f "$TEST_STATE/container"; do sleep 0.1; done; kill $$) &
    cat
    ;;
  *) echo "unexpected docker command: $*" >&2; exit 1 ;;
esac
EOF

chmod +x "$test_dir/bin/colima" "$test_dir/bin/docker"

run_wrapper() {
  { printf 'mcp-message\n'; sleep 0.1; } | env \
    PATH="$test_dir/bin:$PATH" \
    TEST_STATE="$test_dir/state" \
    CODEX_DISCORD_MCP_STATE_PREFIX="$test_dir/state/codex-discord-mcp" \
    "$wrapper"
}

output=$(run_wrapper)
test "$output" = "mcp-message"
grep -qx colima-start "$test_dir/state/log"
grep -qx colima-stop "$test_dir/state/log"
grep -q -- '--no-healthcheck' "$test_dir/state/docker-args"
grep -q -- 'SPRING_PROFILES_ACTIVE=stdio' "$test_dir/state/docker-args"
grep -q -- 'dst=/outbox,readonly' "$test_dir/state/docker-args"

: >"$test_dir/state/colima"
: >"$test_dir/state/log"
output=$(run_wrapper)
test "$output" = "mcp-message"
! grep -q '^colima-' "$test_dir/state/log"
test -f "$test_dir/state/colima"

echo "codex-stdio lifecycle test passed"
