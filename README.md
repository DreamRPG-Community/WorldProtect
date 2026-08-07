# WorldProtect

Paper 1.12.2 plugin for per-world block and environment protection with a
temporary edit mode. Requires `Lib`; integrates with `WorldManager` when it is
installed.

## Configuration

World files are stored at `plugins/WorldProtect/worlds/<world>.yml`.
`world-defaults` in `config.yml` seeds new or incomplete files; existing values
remain unchanged when defaults are edited.

- `rules`: protection switches; `true` blocks the named action
- `rules.natural-mob-spawning`: blocks creature spawning from natural spawning and chunk generation
- `interact`: block ID policy
- `commands`: command-name policy
- List policies support `disabled`, `blacklist`, and `whitelist`

## Commands

```text
/worldprotect reload
/wp reload
/edit
```

`/worldprotect reload` requires `worldprotect.admin`. `/edit` requires
`worldprotect.edit` and toggles temporary building mode for the player.

Other plugins can access the read-only `WorldProtectApi` through Bukkit's
service manager.

## Build

Requires JDK 21.

```powershell
.\gradlew.bat clean build check --warning-mode all
```
