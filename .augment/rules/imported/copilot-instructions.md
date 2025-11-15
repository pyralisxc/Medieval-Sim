---
type: "always_apply"
---

This repository is a Necesse mod (Java, Gradle) named "Medieval Sim". These instructions help an AI coding agent be productive quickly in this codebase.

Please keep responses actionable and specific to this repository — reference files/paths that contain the relevant code.

Key facts (big picture)
- **Mod ID**: `medieval.sim` (defined in build.gradle)
- **Package Root**: `medievalsim` (lowercase - IMPORTANT: not MedievalSim)
- **Entry Point**: `src/main/java/medievalsim/MedievalSim.java` (annotated `@ModEntry`)
- **Status**: Production-ready mod with stable architecture and comprehensive features
- Code is organized by feature packages under `src/main/java/medievalsim/` (e.g., `buildmode`, `commandcenter`, `zones`, `registries`, `ui`, `util`)
- Initialization pattern: `MedievalSim.init()` calls registry classes (e.g., `MedievalSimPackets.registerCore()`), which use static registration helpers
- Network flow: custom `Packet` subclasses live in `medievalsim.packets`. Packets are registered in `medievalsim.registries.MedievalSimPackets` and follow the pattern of a receiving ctor(byte[]), a sending ctor(...), and `processServer` / `processClient` overrides

Build / run / debug (project-specific)
- Build jar: Gradle `buildModJar` task. It outputs to `build/jar/` and names the jar `${modName}-${gameVersion}-${modVersion}.jar` using values in `build.gradle`.
- Run client for development: `gradlew runDevClient` or `./gradlew.bat runDevClient` on Windows; this requires the `gameDirectory` set in `build.gradle` to point to a local Necesse install (default points to a Steam path). If Necesse.jar is not found the Gradle build throws an exception.
- Run server: `gradlew runServer` / `./gradlew.bat runServer`.
- Quick checks: `gradlew classes` compiles and will also generate `mod.info` via the `createModInfoFile` task.

Necesse engine specifics (from supplied resources in `NECESSE_MODDING_RESOURCES/readable_source_complete`)
- Start classes: the Gradle tasks call classes inside the game jar to launch the client/server. Examples shipped with the resource folder:
	- `StartSteamClient` — calls `StartPlatformClient.start(args, new SteamPlatform())` and is used by `runClient`/`runDevClient` Gradle tasks.
	- `StartPlatformClient` / `StartPlatformServer` — engine entry points used by the platform-specific start classes.
- `CreateModInfoFile` is the utility used by the `createModInfoFile` Gradle task to write `mod.info` (it parses launch options and serializes mod metadata).
- `PreAntialiasTextures` is a supporting utility to preprocess PNGs for correct alpha handling; run it when adding textures that require pre-antialiasing (refer to `PreAntialiasTextures.java`).

Resources & packaging
- The Gradle build uses `build/mod/` and `build/mod/resources/` as intermediate outputs that `buildModJar` packages into the final jar (see `build.gradle` sourceSets settings).
- When adding third-party jars, use the `libDepends` configuration so `buildModJar` will unpack them into the mod jar (the `build.gradle` file demonstrates this pattern).
- The build expects `Necesse.jar` (client) or `Server.jar` (server) inside the configured `gameDirectory`. If missing, Gradle will throw an exception — update `gameDirectory` in `build.gradle` for your local install.

Project conventions and patterns to follow
- **Mod ID**: `medieval.sim` (set in `build.gradle`). Use this id when looking up `LoadedMod` or config data
- **Package naming**: Always use lowercase `medievalsim.*` (not MedievalSim)
- **Registries**: The mod centralizes registration through `registries` package classes with `registerCore()` static methods. Add new registrations there (containers, packets, controls, level data)
- **Constants vs Configuration**:
  - `medievalsim.util.Constants` - Immutable compile-time constants (UI dimensions, game data keys, hard limits, localization keys)
  - `medievalsim.config.ModConfig` - Configurable runtime values (gameplay settings, user preferences) - USE THIS for all configurable values
  - NEVER create new RuntimeConstants or ZoneConstants classes - use ModConfig instead
- **Validation**: Use `ValidationUtil` for consistent validation patterns across the codebase
- **Error handling**: Use `ModLogger` with appropriate levels (info, warn, error) for consistent logging
- **Settings architecture**:
  - `ModConfig` - Configurable values (gameplay settings, limits, defaults) - access via public static fields (e.g., `ModConfig.Zones.pvpReentryCooldownMs`)
  - `SettingsManager.getInstance()` - Runtime user data (favorites, history, UI state)
  - `UnifiedMedievalSimSettings` - Thin adapter for Necesse's ModSettings system
  - Save via `Settings.saveClientSettings()` after changes

Examples & concrete patterns
- **Packet classes**: See `src/main/java/medievalsim/packets/PacketCreateZone.java` for the canonical pattern:
	- Receiving constructor: `public PacketX(byte[] data)` uses `PacketReader` to parse fields
	- Sending constructor: `public PacketX(...)` uses `PacketWriter` to write fields
	- Server-side handling: override `processServer(NetworkPacket, Server, ServerClient)` with permission checks and world/level operations, then broadcast `PacketZoneSync` where appropriate

- **Registries**: Add new packet classes to `medievalsim.registries.MedievalSimPackets.registerCore()` so they are registered during `MedievalSim.init()`

- **Settings**: Persistent values are stored in `MedievalSimSettings` and applied via `RuntimeConstants` during initialization

- **Zone Management**: Use `ZoneManager` static methods for zone operations. All zone data is stored in `AdminZonesLevelData`

- **Validation**: Use `ValidationUtil.validateServerLevel()`, `ValidationUtil.validateClient()`, etc. for consistent validation with logging

Code style & error handling conventions
- **Logging**: Use `ModLogger.info()`, `ModLogger.warn()`, `ModLogger.error()` for consistent logging
- **Validation**: Use `ValidationUtil` methods for input validation with automatic error logging  
- **Constants**: Reference values from `Constants` and `RuntimeConstants` classes
- **Error handling**: Follow existing patterns with try-catch blocks and appropriate ModLogger calls

Current mod features and status
- **Admin Tools System**: Central hub providing access to all administrative functions (fully implemented)
- **Command Center**: GUI wrapper for Necesse console commands with strategic filtering (production-ready)
- **Zone Management**: 
  - Protected Zones: Fully implemented with granular permissions (build, break, doors, containers, stations, signs, switches, furniture)
  - PvP Zones: Fully implemented with damage multipliers, combat tracking, DoT control, barrier management
- **Build Mode**: Advanced construction tools with shape-based placement (established system)

Architecture highlights
- **Command Center**: Uses strategic filtering to focus on server administration commands, avoiding overlap with F10 debug menu
- **Protected Zones**: Enhanced with convenience methods (enableVisitorPermissions, enableTrustedMemberPermissions, enableFullPermissions) and configuration validation
- **Zone System**: Optimized with helper methods in ZoneManager to reduce code duplication. Zone events (creation, deletion) are fired via ZoneEventBus
- **Error Handling**: Consistent throughout with ModLogger and ValidationUtil patterns
- **Settings System**: Clean separation between static config (ModConfig), runtime user data (SettingsManager), and Necesse integration (UnifiedMedievalSimSettings)

Where to start for common tasks
- Add a new packet: create packet in `medievalsim.packets`, implement both ctors and `processServer`/`processClient` as needed, register in `MedievalSimPackets`.
- Add a UI screen or element: follow `ui` package patterns and use constants from `Constants` class.
- Persist a new mod setting:
  - For static config: add to `ModConfig` class
  - For user data: add to `SettingsManager` class
  - Call `Settings.saveClientSettings()` to persist changes

Quality gates & quick checks
- After edits run `./gradlew.bat classes` (Windows) to compile and generate `mod.info`.
- Build full jar with `./gradlew.bat buildModJar` (outputs to `build/jar/`).
- If running the dev client, ensure `gameDirectory` in `build.gradle` points to a valid Necesse install (checks for `Necesse.jar`) and call `./gradlew.bat runDevClient`.

If anything in these instructions is unclear or you want me to expand examples (packets, UI, settings), tell me which area to expand and I'll iterate.

## Engine API appendix (quick references)
Below are the most useful engine packages and classes you will consult frequently. Example engine sources live under `NECESSE_MODDING_RESOURCES/readable_source_complete/` in this repo.

- necesse.engine.GameLaunch / GameUtils
	- Parses CLI launch options. Example: `CreateModInfoFile.java` uses `GameLaunch.parseLaunchOptions(args)` to gather mod metadata.
- necesse.engine.modLoader
	- `ModLoader`, `LoadedMod`, `ModSettings`, `ModInfoFile` — used for mod discovery, reading/writing `mod.info`, and persistent mod settings. See `CreateModInfoFile.java` and code that calls `ModLoader.getEnabledMods()`.
- necesse.engine.network
	- `Packet`, `NetworkPacket`, `PacketReader`, `PacketWriter`, `PacketRegistry` — canonical packet flow used by this mod (see `MedievalSim.packets` and `MedievalSim.registries.MedievalSimPackets`).
- necesse.engine.Settings
	- `Settings.saveClientSettings()` persists `ModSettings` (used by `BuildModeManager.saveSettings()`).
- necesse.level.maps
	- `Level`, `SurfaceLevel`, region system and managers (regionSystem/*) — used when modifying level data, syncing zones, and placing tiles/objects (see `PacketCreateZone.processServer`).
- necesse.entity.mobs
	- `PlayerMob` and permission systems; server-side permission checks use `ServerClient.getPermissionLevel()`.
- necesse.gfx.gameTexture
	- `GameTexture` — used by `PreAntialiasTextures` to preprocess PNGs for correct alpha handling.

Where to read (examples in this repo)
- `NECESSE_MODDING_RESOURCES/readable_source_complete/StartSteamClient.java`
- `NECESSE_MODDING_RESOURCES/readable_source_complete/CreateModInfoFile.java`
- `NECESSE_MODDING_RESOURCES/readable_source_complete/PreAntialiasTextures.java`
- `NECESSE_MODDING_RESOURCES/readable_source_complete/necesse/*` (deep engine sources)

## Recommended developer tooling (add to your toolbelt)
Small, concrete tools and Gradle tasks that make working with Necesse mods easier. These are actionable additions you can implement in this repo.

- Gradle task `preAntialias` (JavaExec)
	- Wraps `PreAntialiasTextures` so you can run `./gradlew preAntialias -Pfolders=src/main/resources/...` to preprocess textures automatically before packaging.

- Gradle task `modValidate` (simple checks)
	- Verifies `gameDirectory` is configured and `Necesse.jar` / `Server.jar` exist, warns about missing files, and verifies `modID` isn't empty. Run before building or running the dev client.

- `scripts/generate-modinfo.ps1` (PowerShell helper)
	- Convenience wrapper that calls the existing Gradle task `createModInfoFile` with common args (id, name, version) or directly invokes the `CreateModInfoFile` Java class with consistent arguments.

- `tools/list-packets` (small reflection tool)
	- Java utility that scans the compiled classes under `build/mod/` and prints classes that extend `Packet` (helps ensure new packets are correctly named/placed). Place under `tools/` and run as a JavaExec Gradle task.

- VS Code tasks / launch configs
	- Add `tasks.json` entries for `./gradlew.bat classes`, `./gradlew.bat buildModJar`, and `./gradlew.bat runDevClient` so devs can build and launch from the IDE quickly.

- libDepends workflow helper
	- Add a README snippet showing how to add third-party jars via the `libDepends` configuration in `build.gradle` (so they are embedded into the mod jar on build).

If you'd like, I can implement any of the above tasks/scripts in this repo (small patches). Tell me which ones you want first and I'll add them and validate with a fast `./gradlew.bat classes` run (note: that run requires a valid `gameDirectory` with Necesse jars).

## Zone System HOWTO (safe, minimal, copy-first)
This small HOWTO gives a low-risk, documentation-first path toward implementing Phase 4 (zones). Do this before writing runtime code: scaffold + compile + review.

1) Purpose and flow (big picture)
	- Zones are stored per-level using a LevelData class (example: `AdminZonesLevelData`).
	- Server-side ZoneManager owns creation/edit operations and persists to level data.
	- Clients request actions via packets (e.g., `PacketCreateZone`) and receive updates via a sync packet (`PacketZoneSync`).

2) Files & classes to create or inspect
	- Zone data container: `src/main/java/MedievalSim/zones/YourZoneData.java` (extend the engine level data pattern used by other mods).
	- Manager: `src/main/java/MedievalSim/zones/ZoneManager.java` (singleton, server-authoritative API: createProtectedZone, createPvPZone, expand, shrink, delete).
	- Packets: register packets in `src/main/java/MedievalSim/registries/MedievalSimPackets.java`. Use `PacketCreateZone.java` as canonical example for constructors and `processServer`.
	- Sync packet: `PacketZoneSync` should serialize the minimal zone list needed by clients; send via `server.network.sendToAllClients(new PacketZoneSync(zoneData))` after changes.

3) Minimal implementation checklist (docs-first)
	- Create a LevelData class that holds a serializable list of zones and implements save/load hooks.
	- Implement a `ZoneManager.getInstance()` that locates `AdminZonesLevelData` for the level and manipulates it.
	- Add a server-side permission check in packet handlers (require `PermissionLevel.ADMIN` for modification packets).
	- After change, broadcast `PacketZoneSync` so clients refresh their local view.
	- Keep visualization decoupled: implement a client-only preview/hud element under `ui` that reads the synced zone list.

4) Packet template (docs-only example)
	- Follow `src/main/java/MedievalSim/packets/PacketCreateZone.java` pattern. Minimal skeleton:

		- Receiving ctor: `public PacketX(byte[] data)` → use `PacketReader` to parse.
		- Sending ctor: `public PacketX(...)` → use `PacketWriter` to write.
		- Server handler: `processServer(NetworkPacket packet, Server server, ServerClient client)`
			- Check `client.getPermissionLevel()` >= `PermissionLevel.ADMIN` before mutating level data.
			- Modify `ZoneManager` and then `server.network.sendToAllClients(new PacketZoneSync(...))`.

5) Safety notes (why this is low-risk)
	- Implement and compile the LevelData + packets first without wiring UI. That ensures signatures match the engine and the mod compiles.
	- Use small, focused commits (one new LevelData class, then one manager, then packets). Run `./gradlew.bat classes` after each step.
	- Avoid running the dev client until compile and sync packet serialization are confirmed.

If you want, I'll add this HOWTO as a separate `docs/ZONE_HOWTO.md` file and a small scaffold generator that creates the LevelData and ZoneManager skeletons (no runtime logic) so you can review and compile. Tell me if you want the scaffold (it will be docs + skeleton files only).