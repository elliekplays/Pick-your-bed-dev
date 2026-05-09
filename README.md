# Pick Your Bed

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-supported-DBD0B4?style=for-the-badge)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-supported-E04E14?style=for-the-badge)](https://neoforged.net/)
[![Java 21](https://img.shields.io/badge/Java-21-007396?style=for-the-badge)](https://adoptium.net/temurin/releases/?version=21)
[![Version](https://img.shields.io/badge/Version-1.3.11-80C7D4?style=for-the-badge)](gradle.properties)
[![License](https://img.shields.io/badge/License-Custom_Restricted-lightgrey?style=for-the-badge)](LICENSE.txt)

Pick Your Bed gives Minecraft's death screen the choice it should have had: pick where you want to come back.

Instead of only using the most recent bed or respawn anchor, the mod keeps a personal list of respawn points you have placed and lets you choose one when you die. It is built for **Minecraft 1.21.1** and supports both **Fabric** and **NeoForge**.

## Official Downloads

Please download Pick Your Bed only from the official pages once they are available.

![Modrinth](https://img.shields.io/badge/Modrinth-soon-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)
![CurseForge](https://img.shields.io/badge/CurseForge-soon-F16436?style=for-the-badge&logo=curseforge&logoColor=white)

## Features

- Replaces the vanilla death screen with a custom respawn picker.
- Saves beds and respawn anchors placed by each player.
- Also records the player's current vanilla respawn point when a compatible mod sets one.
- Lets you respawn at any valid saved point instead of only the latest one.
- Shows each point's name, dimension, coordinates, and validity.
- Lets you rename points from the death screen.
- Lets you rename a bed or respawn anchor by sneak-right-clicking it in-world.
- Greys out broken, destroyed, uncharged, obstructed, or dimension-invalid points.
- Shows a warning icon on invalid points instead of an edit button.
- Removes broken or destroyed points after the next respawn.
- Keeps a fallback button for respawning at the last normal respawn point.
- Includes a server config for limiting how many respawn points each player can save.
- Supports Create, Create: Aeronautics, Comforts, and other vanilla-compatible respawn mods.

## Compatibility

| Requirement | Version |
| --- | --- |
| Minecraft | `1.21.1` |
| Java | `21` |
| Fabric Loader | `0.15.11` or newer |
| Fabric API | Required for Fabric |
| NeoForge | `21.1.228` or newer |

For multiplayer, install Pick Your Bed on both the client and the server. The client handles the UI; the server stores and validates respawn points.

## Server Config

On startup, the server creates `config/pick_your_bed-server.properties`.

```properties
respawn_point_limit_enabled=false
max_respawn_points_per_player=5
```

When the limit is enabled, new beds and respawn anchors stop registering once the player reaches the configured cap. Existing saved points can still be edited.

## Mod Compatibility

| Mod | Compatibility behavior |
| --- | --- |
| [Comforts](https://www.curseforge.com/minecraft/mc-mods/comforts) | Sleeping bags and hammocks are ignored because they are sleep blocks, not saved respawn points. |
| [Create](https://modrinth.com/mod/create) | Beds and respawn anchors are tagged as non-movable so contraptions do not casually move saved respawn blocks. |
| [Create: Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics) | Ship beds are recorded and listed like normal beds, matching vanilla's working respawn behavior on airships. |
| Other vanilla-compatible respawn mods | If a mod sets the player's normal Minecraft respawn point to a real bed or respawn anchor, Pick Your Bed can backfill it into the death-screen list. Custom respawn systems that do not use Minecraft's normal respawn position are not automatically supported. |

## Roadmap

These are planned ideas I want to add whenever I have free time. Nothing here has a fixed release date yet.

- [ ] UI redesigns for the death screen and respawn list.
- [ ] UI polish and quality-of-life improvements.
- [ ] A built-in statistics system, with the goal of being clearer and more reliable than vanilla statistics.
- [ ] Ports to newer Minecraft versions.
- [ ] More mod support and compatibility for other respawn-related mods.

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

Pick Your Bed is source-available under the [Pick Your Bed Custom Restricted License](LICENSE.txt).

You may download, install, play, study, and privately modify the mod. You may not reupload, redistribute, sell, publish modified builds, or publish modified forks without permission from **elliekplays**.
