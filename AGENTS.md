# Repository Guidelines

## Project Structure & Module Organization

Semion TD is a Java 21, server-side Fabric mod for Minecraft 1.21.8. Production code lives in `src/main/java/kim/biryeong/semiontd`, grouped by feature packages such as `game`, `tower`, `job`, `config`, `entity`, `ui`, and `persistence`. Resources live in `src/main/resources`; this includes `fabric.mod.json`, mixin configuration, map templates, and models. JUnit tests mirror production packages under `src/test/java`. Fabric GameTests live under `src/gametest/java`. Keep operational documentation in `docs/`.

Treat `build/`, `run/`, and `logs/` as generated local state. Never publish or force-add the licensed files under `src/main/resources/assets/semion-td/`.

## Build, Test, and Development Commands

- `./gradlew runServer`: start the local Fabric development server in `run/`.
- `./gradlew test`: run the JUnit 5 unit suite.
- `./gradlew runGameTest`: run server-backed Fabric GameTests. Install the required Friends & Foes and Flowery Mooblooms patch JARs in `run/mods/` first.
- `./gradlew remapJar`: create the distributable JAR in `build/libs/`.
- `./gradlew test runGameTest remapJar --console=plain --no-daemon`: run the release validation gate.

## Coding Style & Naming Conventions

Use UTF-8, four-space indentation, and braces on the declaration line. Follow existing Java 21 patterns and avoid unrelated formatting changes. Name classes and records with `UpperCamelCase`, methods and fields with `lowerCamelCase`, constants with `UPPER_SNAKE_CASE`, and tests with the `*Test` or `*GameTest` suffix. Keep persistent job, tower, config, and action IDs lowercase and stable. The build does not configure an automatic formatter, so match neighboring code.

## Testing Guidelines

Add a focused regression test for each behavior change. Use JUnit for pure calculations, configuration, persistence, and catalog logic. Use GameTests for entities, placement, upgrades, combat, lifecycle, dialogs, VFX, and team-lane behavior. The project has no numeric coverage threshold; contributors must cover the changed behavior and run the full release gate before delivery.

## Commit & Pull Request Guidelines

Recent commits use short Conventional Commit prefixes such as `feat:` and `fix:`, followed by a Korean or English summary. Keep each commit to one logical change. Pull requests should describe player-visible behavior, config or migration impact, and exact validation commands. Link the relevant issue when one exists. Include screenshots or logs for UI, VFX, resource-pack, or runtime changes.

## Project Skills

Before starting work, inspect `.agents/skills/` and actively use every project skill whose description matches the task. Read each selected `SKILL.md` completely before acting, follow its referenced instructions, and prefer its scripts, templates, and established workflows over recreating them. If several skills apply, use the smallest set that fully covers the task and state the order in which they will be used.

## Configuration & Agent Notes

Runtime configuration belongs under `config/semion-td/` or local `run/config/semion-td/`; do not commit live databases, credentials, or server-generated state. For builder or production-tower work, follow `.agents/skills/semiontd-builder-tower-dev/SKILL.md` and verify values against the active server configuration.
