#!/usr/bin/env bash
# PreToolUse hook: blocks destructive or check-bypassing shell commands. Exit 2 = block.
cmd=$(python3 -c 'import sys,json; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null)
block() { echo "Blocked by project guard: $1. Ask the owner for explicit approval." >&2; exit 2; }
case "$cmd" in
  *"--no-verify"*)                 block "bypassing git hooks";;
  *"git push --force"*|*"git push -f"*) block "force push";;
  *"git reset --hard"*)            block "hard reset";;
  *"rm -rf /"*|*"rm -rf ~"*|*'rm -rf $HOME'*) block "recursive delete of a broad path";;
  *"rm -rf"*)                      block "recursive delete";;
  *".env"*|*"id_rsa"*|*".ssh/"*)   block "access to secret-bearing files";;
  *"| true"*"gradlew"*|*"gradlew"*"|| true"*) block "masking build failures";;
esac
exit 0
