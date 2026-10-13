# Repository Guidelines

## Project Structure & Module Organization

Semion TD targets Java 25 / Minecraft 26.3 as a server-side Fabric mod. `C:\steve-td` is the shared source repository; `C:\SemionTD` is the separate operational server installation. Production code lives in `src/main/java/kim/biryeong/semiontd`, grouped by feature packages such as `game`, `tower`, `job`, `config`, `entity`, `ui`, and `persistence`. Resources live in `src/main/resources`; this includes `fabric.mod.json`, mixin configuration, map templates, and models. JUnit tests mirror production packages under `src/test/java`. Fabric GameTests live under `src/gametest/java`. Keep operational documentation in `docs/`.

Treat `build/`, `run/`, and `logs/` as generated local state. Never publish or force-add the licensed files under `src/main/resources/assets/semion-td/`.

## Build, Test, and Development Commands

- `./gradlew runServer`: local development server command for an isolated `run/` environment only; never use the operational world or treat this example as permission to start/restart `C:\SemionTD`.
- `./gradlew test`: run the JUnit 5 unit suite.
- `./gradlew runGameTest`: run server-backed Fabric GameTests. Required compatibility projects and external mod dependencies are supplied by the Gradle runtime graph; do not copy operating-server mods into the isolated test environment.
- `./gradlew remapJar`: create the distributable JAR in `build/libs/`.
- `./gradlew test runGameTest remapJar`: run the complete release validation gate. Windows: `.\gradlew.bat test runGameTest remapJar --console=plain --no-daemon`.

## Coding Style & Naming Conventions

Use UTF-8, four-space indentation, and braces on the declaration line. Follow the target Java 25 code and existing project conventions and avoid unrelated formatting changes. Name classes and records with `UpperCamelCase`, methods and fields with `lowerCamelCase`, constants with `UPPER_SNAKE_CASE`, and project-owned tests with the responsibility name plus `Test`, not a separate `GameTest` suffix. Name production classes by package/category/specific responsibility. Preserve GameTest annotations, interfaces, source sets, registration, tasks and coverage; this naming rule does not replace GameTests with JUnit. Keep persistent job, tower, config, and action IDs lowercase and stable. The build does not configure an automatic formatter, so match neighboring code.

## Testing Guidelines

Add a focused regression test for each behavior change. Use JUnit for pure calculations, configuration, persistence, and catalog logic. Use GameTests for entities, placement, upgrades, combat, lifecycle, dialogs, VFX, and team-lane behavior. The project has no numeric coverage threshold; contributors must cover the changed behavior and run the full release gate before delivery.

## Commit & Pull Request Guidelines

Recent commits use short Conventional Commit prefixes such as `feat:` and `fix:`, followed by a Korean or English summary. Keep each commit to one logical change. Pull requests should describe player-visible behavior, config or migration impact, and exact validation commands. Link the relevant issue when one exists. Include screenshots or logs for UI, VFX, resource-pack, or runtime changes.

## Project Skills

Before starting work, inspect `.agents/skills/` and actively use every project skill whose description matches the task. Read each selected `SKILL.md` completely before acting, follow its referenced instructions, and prefer its scripts, templates, and established workflows over recreating them. If several skills apply, use the smallest set that fully covers the task and state the order in which they will be used.

## Configuration & Agent Notes

Runtime configuration belongs under `config/semion-td/` or local `run/config/semion-td/`; do not commit live databases, credentials, or server-generated state. For builder or production-tower work, follow `.agents/skills/semiontd-builder-tower-dev/SKILL.md` and verify values against the active server configuration.

## Common instructions and current project requirements

Apply the user's common development instructions alongside this repository's role-specific rules. Complete the requested implementation and validation; do not add code comments, start/restart an operational server, or treat a one-time cleanup/deployment approval as standing permission. Commit only when the user requests it; deployment and push are separate instructions. Preserve unrelated work and saved data.

- The current target is Minecraft 26.3 / Java 25. Older README/guide version or restart examples do not override this target or the operational startup prohibition. Check `gradle.properties`, Gradle configuration, `fabric.mod.json`, official platform documentation and actual target APIs. Confirm the role of the required `remapJar` task for this build rather than dropping the gate or copying obsolete mappings/Loom examples.
- Verify Fabric, Polymer, danta, Friends & Foes and related patches through real API, mixin, packet, registration and initialization paths, including item/font/music/shader compatibility. A changed version string or metadata is not migration completion. Inspect the final distributable JAR and all affected protocol/resource references.
- Preserve the original command permission level 2 where it applies; do not blanket-change commands with other original permissions. Test allowed and denied behavior. Never weaken expectations, delete checks or disable tests to hide a regression.
- HUD, sky, translucent OIT, fonts, towers and cosmetics require actual client GPU rendering checks, using GUI scale 2 as the layout baseline and other resolutions/scales for compatibility. Preserve original tooltip formatting and verify all requested input/permission states. Distinguish unit tests, server/mock GameTests, actual client rendering and real multiplayer; report unperformed checks and causes without starting the operational server.
- Verify the user-designated original resourcepack's actual path/name, ZIP integrity, safe entry paths, `pack.mcmeta` and hashes with algorithm names. Preserve the original ZIP and operational world; integrate only a separate copy with current Polymer/danta outputs using verified override order. Do not execute archive scripts, hard-code a one-time filename/hash as policy, or claim identity with a remote pack without evidence.
- Preserve player, cosmetic, build-guide, rating and statistics data, persistent IDs and save compatibility. Current source/configuration and the latest user request govern tower balance; do not turn an old ender-tower experiment into a permanent rule. Do not import HOI's code-blank-line format, AGPL license or X5/X10 simulation criteria.
- Do not add code comments or change project/third-party licenses arbitrarily. Purchased/private assets under `src/main/resources/assets/semion-td/` remain excluded from public source, CI artifacts and releases. Preserve required legal notices and source provenance.

Keep durable rules and verification evidence in the existing [configuration](docs/config-reference.ko.md), [commands](docs/command-reference.ko.md), [builders/towers](docs/builders-and-towers.ko.md), [balance](docs/tower-balance-reference.ko.md), [handoff](docs/next-session-handoff.ko.md) and [service checks](docs/service-readiness-checklist.ko.md), as applicable. Do not add dated completion reports or freeze transient failures/counts into standing instructions. Documentation-only changes need UTF-8, link, command and scope checks rather than the game release gate.
