#!/usr/bin/env bash
# Checks the tools the project needs (git, curl, Python 3, Node 22+, pnpm 10, JDK 21).
#   scripts/doctor.sh            report only; exit 1 if something is missing
#   scripts/doctor.sh --install  also install what is missing (macOS with Homebrew, Ubuntu/Debian with apt),
#                                after asking; AUTO_INSTALL=1 skips the question
set -u
cd "$(dirname "$0")/.."

ORIG_PATH="$PATH"
install=0
[ "${1:-}" = "--install" ] && install=1
keys="git curl python node pnpm jdk"

case "$(uname -s)" in
  Darwin) os=mac ;;
  Linux) os=linux ;;
  *) os=other ;;
esac
apt=0
if [ "$os" = linux ] && command -v apt-get >/dev/null 2>&1; then apt=1; fi

hint() { # hint <mac> <linux> <other>
  case "$os" in mac) echo "$1" ;; linux) echo "$2" ;; *) echo "$3" ;; esac
}

hint_for() {
  case "$1" in
    git) hint 'xcode-select --install' 'sudo apt install git' 'https://git-scm.com/downloads' ;;
    curl) hint 'curl ships with macOS; check your PATH' 'sudo apt install curl' 'install curl' ;;
    python) hint 'brew install python' 'sudo apt install python3' 'https://www.python.org/downloads/' ;;
    node) hint 'brew install node@22 && brew link --overwrite --force node@22' 'https://nodejs.org/en/download  or  nvm install 22' 'https://nodejs.org/en/download' ;;
    pnpm) echo "sudo corepack enable pnpm   or   npm install -g pnpm@10   (https://pnpm.io/installation)" ;;
    jdk) hint 'brew install --cask temurin@21' 'sudo apt install openjdk-21-jdk   (or: sdk install java 21-tem)' 'https://adoptium.net/temurin/releases/?version=21' ;;
  esac
}

set_st() { printf -v "st_$1" %s "$2"; printf -v "dt_$1" %s "$3"; } # set_st <key> <ok|warn|missing> <detail>

probe() {
  local v major java_major have_21 pnpm_version node_ok=0

  if command -v git >/dev/null 2>&1; then set_st git ok "$(git --version)"; else set_st git missing "not installed"; fi
  if command -v curl >/dev/null 2>&1; then set_st curl ok "curl"; else set_st curl missing "not installed"; fi
  if command -v python3 >/dev/null 2>&1; then set_st python ok "$(python3 --version 2>&1)"; else set_st python missing "not installed"; fi

  if command -v node >/dev/null 2>&1; then
    v="$(node --version)"
    major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
    if [ "$major" -ge 22 ]; then set_st node ok "node $v"; node_ok=1; else set_st node missing "found $v, need 22 or newer"; fi
  else
    set_st node missing "not installed"
  fi

  # No download prompt and no stdin: Corepack otherwise waits for a "continue? [Y/n]" answer nobody can see.
  export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
  if [ "$node_ok" -eq 1 ] && ! command -v pnpm >/dev/null 2>&1 && command -v corepack >/dev/null 2>&1; then
    echo "  pnpm not found; enabling it through Corepack (downloads pnpm 10)..."
    corepack enable pnpm </dev/null >/dev/null 2>&1 || true
    corepack prepare pnpm@10 --activate </dev/null >/dev/null 2>&1 || true
  fi
  pnpm_version=""
  if command -v pnpm >/dev/null 2>&1; then pnpm_version="$(pnpm --version </dev/null 2>/dev/null)"; fi
  if [ -n "$pnpm_version" ]; then
    set_st pnpm ok "pnpm $pnpm_version"
  elif [ "$node_ok" -eq 1 ]; then
    set_st pnpm missing "Corepack could not enable it"
  else
    set_st pnpm missing "needs Node 22 first"
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
  if [ -z "$java_major" ]; then set_st jdk missing "not installed"
  elif [ "$java_major" -lt 17 ]; then set_st jdk missing "found Java $java_major, need 21"
  elif [ "$have_21" -eq 1 ]; then set_st jdk ok "JDK 21 (default java is $java_major)"
  else set_st jdk warn "no JDK 21 found (default java is $java_major); the Gradle build needs one"
  fi
}

report() { # report <title>
  local k s d tag
  echo "$1"
  for k in $keys; do
    eval "s=\$st_$k; d=\$dt_$k"
    case "$s" in ok) tag="ok     " ;; warn) tag="WARNING" ;; *) tag="MISSING" ;; esac
    printf '  %s  %-7s %s\n' "$tag" "$k" "$d"
  done
}

load_brew() {
  local b
  for b in /opt/homebrew/bin/brew /usr/local/bin/brew; do
    if [ -x "$b" ]; then eval "$("$b" shellenv)"; return 0; fi
  done
  return 1
}

not_ok() { local k; for k in $keys; do eval "[ \"\$st_$k\" = ok ]" || printf '%s ' "$k"; done; }
has() { case " $2 " in *" $1 "*) return 0 ;; esac; return 1; } # has <word> <list>

print_hints() { # print_hints <keys>
  local k
  echo "Install them manually:"
  for k in $1; do echo "  $k: $(hint_for "$k")"; done
  echo "Then open a new terminal and run 'make setup' again."
}

probe
report "Checking prerequisites..."
for k in $keys; do eval "before_$k=\$st_$k"; done
need="$(not_ok)"

if [ -z "$need" ]; then
  echo "All required tools are present."
  exit 0
fi

# make doctor: report only. A missing JDK 21 next to a newer default java is only a warning.
if [ "$install" -eq 0 ]; then
  hard=""
  for k in $need; do eval "[ \"\$st_$k\" = missing ]" && hard="$hard$k "; done
  if [ -n "$hard" ]; then echo; print_hints "$hard"; echo "Or run 'make setup', which installs them for you."; exit 1; fi
  exit 0
fi

# ---- install plan ----------------------------------------------------------------------------------------------
plan=()
manual=""
if [ "$os" = mac ]; then
  if ! load_brew && ! command -v brew >/dev/null 2>&1; then
    plan+=('/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"' 'load_brew')
  fi
  has git "$need" && manual="$manual git"
  has curl "$need" && manual="$manual curl"
  has python "$need" && plan+=('brew install python')
  if has node "$need"; then
    plan+=('brew install node@22'
      'for f in $(brew list --formula | grep -E "^node(@.*)?$" | grep -v "^node@22$"); do brew unlink "$f"; done'
      'brew link --overwrite --force node@22')
  fi
  has jdk "$need" && plan+=('brew install --cask temurin@21')
elif [ "$apt" -eq 1 ]; then
  pkgs=""
  has git "$need" && pkgs="$pkgs git"
  has curl "$need" && pkgs="$pkgs curl"
  has python "$need" && pkgs="$pkgs python3"
  has jdk "$need" && pkgs="$pkgs openjdk-21-jdk"
  plan+=('sudo apt-get update')
  [ -n "$pkgs" ] && plan+=("sudo apt-get install -y$pkgs")
  has node "$need" && plan+=('curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -' 'sudo apt-get install -y nodejs')
else
  echo
  print_hints "$need"
  exit 1
fi

echo
echo "Missing: $need"
if [ "${#plan[@]}" -eq 0 ]; then
  print_hints "$need"
  exit 1
fi
echo "These commands will install them (you may be asked for your password):"
i=0
while [ "$i" -lt "${#plan[@]}" ]; do echo "  ${plan[$i]}"; i=$((i + 1)); done

if [ "${AUTO_INSTALL:-}" != 1 ]; then
  if [ -t 0 ]; then
    read -r -p "Install now? [Y/n] " answer
    case "$answer" in n | N | no | NO) echo; print_hints "$need"; exit 1 ;; esac
  else
    echo "No terminal to ask on. Rerun with AUTO_INSTALL=1 to install without asking."
    exit 1
  fi
fi

i=0
while [ "$i" -lt "${#plan[@]}" ]; do
  echo "+ ${plan[$i]}"
  eval "${plan[$i]}" || echo "  step failed: ${plan[$i]}"
  i=$((i + 1))
done

# Later make targets run in new shells; keep Homebrew's directory on their PATH even if the user's profile lacks it.
if command -v brew >/dev/null 2>&1; then
  brew_bin="$(dirname "$(command -v brew)")"
  case ":$ORIG_PATH:" in
    *":$brew_bin:"*) ;;
    *) mkdir -p data && echo "$brew_bin" > data/tools.path ;;
  esac
fi

# ---- result ------------------------------------------------------------------------------------------------------
echo
probe
report "After install:"
installed=""; present=""
for k in $keys; do
  eval "b=\$before_$k; s=\$st_$k"
  if [ "$b" = ok ]; then present="$present $k"; elif [ "$s" = ok ]; then installed="$installed $k"; fi
done
still="$(not_ok)"
echo
echo "Already present:${present:- none}"
echo "Installed now:  ${installed:- none}"
echo "Still missing:  ${still:-none}${manual:+ (also install by hand:$manual)}"

if [ -n "$still$manual" ]; then
  echo
  print_hints "$still$manual"
  exit 1
fi
echo "All required tools are present."
