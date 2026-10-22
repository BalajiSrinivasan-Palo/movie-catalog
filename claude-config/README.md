# Claude Code configuration (agents, skills, hooks)

The desktop bridge cannot write into `.claude/`, so the configuration is staged here. Install it once from the repository root:

```bash
mkdir -p .claude && cp -R claude-config/agents claude-config/skills claude-config/settings.json .claude/
```

Then delete this folder if you like. Contents: 9 agents (`agents/`), 6 skills (`skills/*/SKILL.md`), and `settings.json` with a PreToolUse hook that runs `scripts/hooks/guard-bash.sh` to block destructive or check-bypassing commands.
