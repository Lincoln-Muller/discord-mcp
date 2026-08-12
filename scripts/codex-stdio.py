#!/usr/bin/env python3
import fcntl
import os
import shutil
import signal
import subprocess
import sys
import threading
from pathlib import Path


REPO = Path(__file__).resolve().parent.parent
ENV_FILE = REPO / ".env"
OUTBOX = REPO / "outbox"
IMAGE = os.environ.get("DISCORD_MCP_IMAGE", "saseq/discord-mcp:latest")
CONTAINER = f"codex-discord-mcp-{os.getpid()}"
STATE_PREFIX = Path(os.environ.get("CODEX_DISCORD_MCP_STATE_PREFIX", f"/tmp/codex-discord-mcp-{os.getuid()}"))
MARKER = STATE_PREFIX.with_suffix(".owned")
LOCK = STATE_PREFIX.with_suffix(".lock")


def executable(name: str) -> str:
    found = shutil.which(name)
    if found:
        return found
    for directory in ("/opt/homebrew/bin", "/usr/local/bin"):
        candidate = Path(directory, name)
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    raise SystemExit(f"{name} is not installed")


COLIMA = executable("colima")
DOCKER = executable("docker")


def run(*args: str, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        args,
        check=check,
        stdout=subprocess.PIPE if capture else sys.stderr,
        stderr=subprocess.DEVNULL if capture else sys.stderr,
        text=capture,
    )


def with_lock(action) -> None:
    LOCK.touch(mode=0o600, exist_ok=True)
    with LOCK.open("r+") as lock_file:
        fcntl.flock(lock_file, fcntl.LOCK_EX)
        action()


def start_colima() -> None:
    def start() -> None:
        if run(COLIMA, "status", check=False, capture=True).returncode != 0:
            run(COLIMA, "start")
            MARKER.touch(mode=0o600, exist_ok=True)

    with_lock(start)


def remove_stale_containers() -> None:
    result = run(
        DOCKER,
        "ps",
        "-aq",
        "--filter",
        "label=codex.discord-mcp.managed=true",
        "--format",
        "{{.ID}} {{.Label \"codex.discord-mcp.parent-pid\"}}",
        capture=True,
    )
    for line in result.stdout.splitlines():
        container_id, _, pid_text = line.partition(" ")
        try:
            os.kill(int(pid_text), 0)
        except (ValueError, ProcessLookupError):
            run(DOCKER, "rm", "-f", container_id, check=False, capture=True)
        except PermissionError:
            pass


def stop_owned_colima_if_idle() -> None:
    def stop() -> None:
        if not MARKER.exists():
            return
        if run(DOCKER, "ps", "-q", capture=True).stdout.strip():
            return
        run(COLIMA, "stop", check=False)
        MARKER.unlink(missing_ok=True)

    with_lock(stop)


def main() -> int:
    if not ENV_FILE.is_file():
        raise SystemExit(f"missing {ENV_FILE}")
    if not OUTBOX.is_dir():
        raise SystemExit(f"missing {OUTBOX}")

    start_colima()
    remove_stale_containers()
    if run(DOCKER, "image", "inspect", IMAGE, check=False, capture=True).returncode != 0:
        run(DOCKER, "build", "-t", IMAGE, str(REPO))

    command = [
        DOCKER,
        "run",
        "--rm",
        "-i",
        "--no-healthcheck",
        "--name",
        CONTAINER,
        "--label",
        "codex.discord-mcp.managed=true",
        "--label",
        f"codex.discord-mcp.parent-pid={os.getpid()}",
        "--env-file",
        str(ENV_FILE),
        "-e",
        "SPRING_PROFILES_ACTIVE=stdio",
        "-e",
        "DISCORD_FILE_ROOT=/outbox",
        "--mount",
        f"type=bind,src={OUTBOX},dst=/outbox,readonly",
        IMAGE,
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=sys.stdout.buffer, stderr=sys.stderr.buffer)
    closing = threading.Event()

    def stop_container() -> None:
        if closing.is_set():
            return
        closing.set()
        run(DOCKER, "rm", "-f", CONTAINER, check=False, capture=True)

    def forward_input() -> None:
        try:
            while data := os.read(sys.stdin.fileno(), 65536):
                process.stdin.write(data)
                process.stdin.flush()
        except (BrokenPipeError, OSError):
            pass
        finally:
            stop_container()

    threading.Thread(target=forward_input, daemon=True).start()

    def handle_signal(_signum, _frame) -> None:
        stop_container()

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)

    try:
        status = process.wait()
        return 0 if closing.is_set() else status
    finally:
        stop_container()
        stop_owned_colima_if_idle()


if __name__ == "__main__":
    raise SystemExit(main())
