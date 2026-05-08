# Pick your bed

Pick your bed is a Minecraft 1.21.1 Fabric and NeoForge mod that replaces the death screen with a respawn picker for saved beds and respawn anchors.

## Features

- Records beds and respawn anchors placed by each player.
- Lets players choose a valid saved respawn point from the death screen.
- Shows broken or destroyed respawn points as unavailable and removes them after respawn.
- Supports renaming respawn points from the death screen or by sneak-right-clicking the block.
- Includes search and filters for beds versus other respawn points.
- Adds hardcore-mode death screen support with a frozen survival record.
- Allows hardcore spectators to keep loading and ticking chunks.

## Builds

Use Java 21.

```powershell
.\gradlew.bat :fabric:build :neoforge:build --offline --console=plain --max-workers=1
```

Output jars:

- `fabric/build/libs/pick-your-bed-1.21.1-1.3.8-fabric.jar`
- `neoforge/build/libs/pick-your-bed-1.21.1-1.3.8-neoforge.jar`

## Project Layout

- `common` shared mod code and mixins
- `fabric` Fabric loader entrypoints and networking
- `neoforge` NeoForge loader entrypoints and networking

## Credits

- m.b.r
- elliekplays

## License

This project is currently marked as All Rights Reserved. Choose and apply an open-source license before publishing it as open source.
