# WorldProtect

WorldProtect gives every loaded Bukkit world an independent protection file.
World configuration is created below `plugins/WorldProtect/worlds/` when the
world loads, including worlds loaded dynamically by WorldManager.

The `world-defaults` section in `config.yml` is the template for new or
incomplete world files. Each world can override its own interaction and
command policies in its generated YAML file. A policy supports `disabled`,
`blacklist`, and `whitelist` modes. In blacklist mode listed values are
blocked. In whitelist mode unlisted values are blocked.

The generated world files use the same shape as the defaults:

```yaml
rules:
  block-placement: true
  block-breaking: true
  explosion-block-damage: true

interact:
  mode: disabled
  block-ids: [54, 58, 61]

commands:
  mode: blacklist
  names: [pl, plugins]
```

Every `rules` value is explicit: `true` blocks the named action. The `interact`
section controls block interactions only. For the list policies, `disabled`
allows everything, `blacklist` blocks listed values, and `whitelist` allows only
listed values. Only the keys shown in the current configuration are read;
unknown keys are ignored.

Commands:

- `/worldprotect reload` (alias: `/wp reload`) reloads the global and loaded-world configuration.
- `/edit` toggles temporary building mode.

Players need the temporary `/edit` mode to place or break blocks in worlds
where those protections are enabled. Edit mode is held only in memory and is
cleared when a player leaves or the plugin is disabled.

Other plugins can obtain the read-only `WorldProtectApi` through Bukkit's
service manager. Its `find(World)` method returns an immutable policy only for
currently loaded worlds; configuration mutation and `/edit` state remain
internal to WorldProtect.
