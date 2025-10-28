# JEDependency

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20+-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Modern dependency management and runtime isolation for Bukkit, Spigot and Paper plugins. JEDependency keeps your plugin
artifacts slim by downloading libraries at runtime, performing optional package remapping and exposing those libraries to your
plugin only.

## Table of Contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Installation](#installation)
4. [Quick Start](#quick-start)
5. [Paper Plugin Loader Integration](#paper-plugin-loader-integration)
6. [Configuration & System Properties](#configuration--system-properties)
7. [Logging](#logging)
8. [Troubleshooting](#troubleshooting)

## Features

- **Automatic runtime downloads** – resolve dependencies from Maven repositories without shading.
- **Paper loader support** – optional plugin loader that grants access to dependency plugins and supports remapping.
- **Classpath isolation** – inject downloaded JARs into your plugin class loader only.
- **YAML driven configuration** – declare dependencies in `dependency/dependencies.yml` or supply them programmatically.
- **Optional remapping** – relocate downloaded classes into a safe namespace to avoid conflicts.
- **Module-aware** – automatically performs the required JVM module deencapsulation on Java 9+.

## Requirements

- Java 17 or newer to build the library (the produced artifacts continue to run on Java 17+, matching modern server
  requirements).
- Bukkit/Spigot/Paper server 1.20 or newer.
- Network access on the target server for runtime downloads (or pre-populated `libraries/` directory).

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("de.jexcellence.dependency:JEDependency:2.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'de.jexcellence.dependency:JEDependency:2.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>de.jexcellence.dependency</groupId>
    <artifactId>JEDependency</artifactId>
    <version>2.0.0</version>
</dependency>
```

After adding the dependency, run a build once (`gradlew publishToMavenLocal` from this project) so your plugin project can
resolve the artifact locally, or publish it to your internal repository.

## Quick Start

### 1. Declare dependencies (optional)

Create `src/main/resources/dependency/dependencies.yml` in your plugin and list the required coordinates:

```yaml
dependencies:
  - "org.apache.commons:commons-lang3:3.14.0"
  - "com.google.code.gson:gson:2.11.0"
```

Server specific overrides can be placed in `dependency/paper/dependencies.yml` or `dependency/spigot/dependencies.yml`.

### 2. Bootstrap in your plugin

```java
public final class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        // Load YAML + programmatic dependencies
        JEDependency.initialize(this, MyPlugin.class, new String[]{
                "com.zaxxer:HikariCP:5.1.0"
        });
    }
}
```

Call `initializeWithRemapping(...)` instead if you want to force package relocation regardless of system properties.

### 3. Additional dependencies at runtime

If you prefer to avoid YAML entirely, simply supply the full list in the initializer:

```java
JEDependency.initialize(
        this,
        MyPlugin.class,
        new String[]{
                "org.apache.commons:commons-lang3:3.14.0",
                "com.fasterxml.jackson.core:jackson-databind:2.17.2"
        }
);
```

## Paper Plugin Loader Integration

When running on Paper you can ship the optional `RPluginLoader` to let Paper download dependencies before the plugin is
constructed. Add the following snippet to your `paper-plugin.yml`:

```yaml
loader: de.jexcellence.dependency.loader.RPluginLoader
```

The loader will:

- Download dependencies declared in your YAML resource during the class loader phase.
- Optionally remap packages (see the system properties below).
- Expose declared plugin dependencies (LuckPerms, RCore, …) to your plugin’s classpath to enable typed integrations.

JEDependency automatically detects when the loader is active and will simply verify that required external APIs are visible,
preventing double work.

## Configuration & System Properties

| Property | Default | Purpose |
| --- | --- | --- |
| `jedependency.remap` | `false` | Enables remapping when running via the standard bootstrap path. Accepts `true`, `1`, `yes`, or `on`. |
| `jedependency.relocations` | – | Comma separated list of explicit relocations (`from=>to`) for the Paper loader. |
| `jedependency.relocations.prefix` | derived | Base package for automatic relocations when using the Paper loader. |
| `jedependency.relocations.excludes` | see source | Additional package roots to exclude from automatic relocation. |
| `jedependency.plugin.group` / `jedependency.group` | – | Hint for the relocation base package detection. |

The Paper loader also honours the Paper-specific property `paper.plugin.loader.active` to signal loader presence. When that
flag is set, `JEDependency.initialize` will validate visibility of API classes such as LuckPerms and RCore and fail fast with
clear guidance if the plugin loader did not grant access.

## Logging

JEDependency uses structured logging:

- `INFO` – High level progress (initialisation, summaries).
- `FINE` / `FINER` – Detailed download and injection steps.
- `WARNING` – Recoverable problems such as missing dependencies or failed downloads.
- `SEVERE` – Misconfiguration that prevents the plugin from loading safely.

Adjust your logger configuration if you need more verbose output during development.

## Troubleshooting

| Symptom | Suggested Fix |
| --- | --- |
| `Missing runtime API visibility` on Paper | Ensure your custom loader exposes dependency plugins via `PluginClasspathBuilder#addPluginDependency` (or the equivalent method on your server version). |
| Dependencies are not downloaded | Verify the coordinates, make sure the server can reach Maven Central or configure a custom repository with `DependencyDownloader#addRepository`. |
| Classes not relocated | Confirm `jedependency.remap=true` or `initializeWithRemapping(...)` is used and that a `RemappingDependencyManager` implementation is available on the classpath. |

For additional examples check the `src/main/resources` directory or the sample plugin structures in this README.
