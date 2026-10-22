#!/usr/bin/env bash
# Checks the tools the project needs and prints how to install the missing ones.
# Installs nothing system-wide; only enables pnpm through Corepack. Used by `make setup`.
set -u
cd "$(dirname "$0")/.."

case "$(uname -s)" in
  Darwin) os=mac ;;
  Linux) os=linux ;;
  *) os=other ;;
esac

failed=0
fail() { echo "  MISSING  $1"; shift; for line in "$@"; do echo "           $line"; done; failed=1; }
ok() { echo "  ok       $1"; }
warn() { echo "  WARNING  $1"; shift; for line in "$@"; do echo "           $line"; done; }

hint() { # hint <mac> <linux> <other>
  case "$os" in mac) echo "$1" ;; linux) echo "$2" ;; *) echo "$3" ;; esac
}

echo "Checking prerequisites..."

# Make and git come with the Xcode Command Line Tools on macOS.
if command -v git >/dev/null 2>&1; then ok "git"; else
  fail "git" "$(hint 'xcode-select --install' 'sudo apt install git   (or your distro equivalent)' 'https://git-scm.com/downloads')"
fi

if command -v curl >/dev/null 2>&1; then ok "curl"; else
  fail "curl" "$(hint 'curl ships with macOS; check your PATH' 'sudo apt install curl' 'install curl')"
fi

if command -v python3 >/dev/null 2>&1; then ok "python3 ($(python3 --version 2>&1))"; else
  fail "python3 (repository checks)" "$(hint 'brew install python' 'sudo apt install python3' 'https://www.python.org/downloads/')"
fi

# Node 22+
if command -v node >/dev/null 2>&1; then
  node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  if [ "$node_major" -ge 22 ]; then ok "node $(node --version)"; else
    fail "node 22 or newer (found $(node --version))" \
      "$(hint 'brew install node@22   (then follow brew'"'"'s PATH note)' 'https://nodejs.org/en/download  or  nvm install 22' 'https://nodejs.org/en/download')"
  fi
else
  fail "node 22 or newer" \
    "$(hint 'brew install node@22   (then follow brew'"'"'s PATH note)' 'https://nodejs.org/en/download  or  nvm install 22' 'https://nodejs.org/en/download')"
fi

# pnpm 10, enabled through Corepack when Node is there.
if ! command -v pnpm >/dev/null 2>&1 && command -v corepack >/dev/null 2>&1; then
  echo "  pnpm not found; enabling it through Corepack..."
  corepack enable pnpm >/dev/null 2>&1 || true
fi
if command -v pnpm >/dev/null 2>&1; then ok "pnpm $(pnpm --version 2>/dev/null)"; else
  if command -v node >/dev/null 2>&1; then
    fail "pnpm 10" \
      "Corepack could not enable it. Try: sudo corepack enable pnpm" \
      "or: npm install -g pnpm@10   (https://pnpm.io/installation)"
  else
    fail "pnpm 10" "Install Node first; 'make setup' then enables pnpm through Corepack."
  fi
fi

# Gradle runs on any JDK 17+, but the build compiles with a JDK 21 toolchain (no automatic download).
java_major=""
if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print (v[1] == 1 ? v[2] : v[1]); exit}')"
fi
have_21=0
[ "$java_major" = "21" ] && have_21=1
[ -x "${JAVA_HOME:-/nonexistent}/bin/java" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21' && have_21=1
[ "$os" = mac ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1 && have_21=1
ls -d /usr/lib/jvm/*21* "$HOME"/.sdkman/candidates/java/21* "$HOME"/.gradle/jdks/*21* >/dev/null 2>&1 && have_21=1

jdk_hint="$(hint 'brew install --cask temurin@21' 'sudo apt install openjdk-21-jdk   (or: sdk install java 21-tem)' 'https://adoptium.net/temurin/releases/?version=21')"
if [ -z "$java_major" ]; then
  fail "JDK 21" "$jdk_hint"
elif [ "$java_major" -lt 17 ]; then
  fail "JDK 21 (found Java $java_major)" "$jdk_hint"
elif [ "$have_21" -eq 1 ]; then
  ok "JDK 21 (default java is $java_major)"
else
  warn "no JDK 21 found (default java is $java_major); the Gradle build needs one" "$jdk_hint"
fi

if [ "$failed" -ne 0 ]; then
  echo
  echo "Install the missing tools above, open a new terminal, and run 'make setup' again."
  exit 1
fi
echo "All required tools are present."
