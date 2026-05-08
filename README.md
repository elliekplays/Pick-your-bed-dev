# Pick your bed

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-supported-DBD0B4?style=for-the-badge)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-supported-E04E14?style=for-the-badge)](https://neoforged.net/)
[![Java 21](https://img.shields.io/badge/Java-21-007396?style=for-the-badge)](https://adoptium.net/temurin/releases/?version=21)
[![License](https://img.shields.io/badge/License-Custom_Restricted-lightgrey?style=for-the-badge)](LICENSE.txt)

Pick your bed gives Minecraft's death screen the choice it should have had: pick where you want to come back.

Instead of only using the most recent bed or respawn anchor, the mod keeps a personal list of respawn points you have placed and lets you choose one when you die. It is built for **Minecraft 1.21.1** and supports both **Fabric** and **NeoForge**

## Features

- Replaces the vanilla death screen with a custom respawn picker.
- Saves beds and respawn anchors placed by each player.
- Lets you respawn at any valid saved point instead of only the latest one.
- Shows each point's name, dimension, coordinates, and validity.
- Lets you rename points from the death screen.
- Lets you rename a bed or respawn anchor by sneak-right-clicking it in-world.
- Shows a warning icon on invalid points instead of an edit button.
- Removes broken or destroyed points after the next respawn.
- Supports other mods too like create.

## Compatibility

| Requirement | Version |
| --- | --- |
| Minecraft | `1.21.1` |
| Java | `21` |
| Fabric Loader | `0.15.11` or newer |
| Fabric API | Required for Fabric |
| NeoForge | `21.1.228` or newer |

For multiplayer, install Pick your bed on both the client and the server. The client handles the UI; the server stores and validates respawn points.

## Mod Compatibility

| Mod | Compatibility behavior |
| --- | --- |
| [Comforts](https://www.curseforge.com/minecraft/mc-mods/comforts) | Sleeping bags and hammocks are ignored because they are sleep blocks, not saved respawn points. |
| [Create](https://modrinth.com/mod/create) | Beds and respawn anchors are tagged as non-movable so contraptions do not casually move saved respawn blocks. |
| [Create: Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics) | Ship beds are recorded and listed like normal beds, matching vanilla's working respawn behavior on airships. |
| Other vanilla-compatible respawn mods | If a mod sets the player's normal Minecraft respawn point to a real bed or respawn anchor, Pick your bed can backfill it into the death-screen list. Custom respawn systems that do not use Minecraft's normal respawn position are not automatically supported. |

## Installing Prebuilt Jars

1. Install either [Fabric](https://fabricmc.net/use/installer/) or [NeoForge](https://neoforged.net/).
2. If using Fabric, also install [Fabric API](https://modrinth.com/mod/fabric-api).
3. Download the matching jar from the project's [Releases](../../releases) page.
4. Put the jar into the Minecraft `mods` folder.
5. Start Minecraft 1.21.1.

Use only one jar for your loader. Fabric users should use the Fabric jar; NeoForge users should use the NeoForge jar.

## Building The Mod Yourself

Install [Java 21](https://adoptium.net/temurin/releases/?version=21), then run:

```powershell
.\gradlew.bat clean :fabric:build :neoforge:build --console=plain --max-workers=1
```

On macOS or Linux:

```sh
./gradlew clean :fabric:build :neoforge:build --console=plain --max-workers=1
```

Built jars are created in:

- `fabric/build/libs/`
- `neoforge/build/libs/`

The project only builds playable mod jars. It does not generate `-sources.jar` or `-javadoc.jar` files.

## Project Structure

| Path | Purpose |
| --- | --- |
| [`common`](common) | Shared UI, saved data, validation, networking payloads, and mixins |
| [`fabric`](fabric) | Fabric entrypoints and Fabric networking |
| [`neoforge`](neoforge) | NeoForge entrypoint and NeoForge networking |
| [`buildSrc`](buildSrc) | Shared Gradle setup |
| [`gradle.properties`](gradle.properties) | Version numbers and mod metadata |

## Contributing

Small fixes and improvements are welcome. For bug reports, include the Minecraft version, loader, mod version, crash log if there is one, and a clear way to reproduce the issue. For code changes, build both Fabric and NeoForge before submitting.

## Contact

![Developer](https://img.shields.io/badge/Developer-elliekplays-8A2BE2?style=for-the-badge)
![Discord](https://img.shields.io/badge/Discord-elliekplays-5865F2?style=for-the-badge&logo=discord&logoColor=white)
![Email](https://img.shields.io/badge/Email-soon-EA4335?style=for-the-badge&logo=gmail&logoColor=white)

For bug reports or support, include your Minecraft version, loader, mod version, installed compatibility mods, and a clear way to reproduce the issue.

## License

Pick your bed is source-available under the [Pick your bed Custom Restricted License](LICENSE.txt).

You may download, install, play, study, and privately modify the mod. You may not reupload, redistribute, sell, publish modified builds, or publish modified forks without permission from **Developer**.
