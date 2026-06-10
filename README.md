# Brainrot Plugins

Maven multi-module repo that builds 9 separate Paper/Spigot plugins for the "Steal a Brainrot" minigame.

## Modules → output jars

| Module | Plugin | Commands |
|---|---|---|
| `brainrot-admin` | BrainrotAdmin | `/brainrotadmin` |
| `brainrot-bases` | BrainrotBases | `/rebirth`, `/friend`, `/brreload`, `/savemobs`, … |
| `brainrot-codes` | BrainrotCodes | `/code` |
| `brainrot-events` | BrainrotEvents | `/brainrotevent` |
| `brainrot-extras` | BrainrotExtras | `/casino`, `/daily` |
| `brainrot-placeholdermoney` | BrainrotPlaceholderMoney | — |
| `brainrot-shop` | BrainrotShop | `/shop`, `/brainrot` |
| `brainrot-spawner` | BrainrotSpawner | `/brainrotspawn`, `/brainrotspawner` |
| `spawnworld` | SpawnWorld | `/spawnworld` |

## Build locally

```bash
mvn -B clean package
```

Jars land in each module's `target/` folder.

## Build on GitHub (CI)

Push this repo to GitHub. The workflow in `.github/workflows/build.yml` runs on every push,
builds all 9 plugins, and uploads them as a single artifact named **brainrot-plugins**
(download it from the Actions run page).

## Dependency versions

All external dependencies are `provided` (the server / other plugins supply them at runtime).
Versions live in the parent `pom.xml` `<properties>` block — bump them to match your server:

```
paper.version, vault.version, placeholderapi.version,
worldedit.version, worldguard.version, fancyholograms.version, actionbarapi.version
```

If CI fails on a missing/wrong artifact version, adjust the matching property and push again.

## Runtime dependencies on the server

Install alongside these plugins as needed:
- **Vault** + an economy plugin (required by Bases/Shop/Extras/Spawner)
- **PlaceholderAPI** (Bases, PlaceholderMoney)
- **FancyHolograms** (Bases, Spawner)
- **WorldEdit / FastAsyncWorldEdit** + **WorldGuard** (Bases, Events)
- **ActionBarAPI** (Shop)
- **LiteBans** (Admin, optional)

## Notes

These 9 plugins currently communicate at runtime by plugin name + reflection, and some read
each other's YAML files directly. That works but is fragile — see the refactor plan shared
in chat for hardening steps (shared API, async saves, splitting BrainrotBases, timer tuning).
