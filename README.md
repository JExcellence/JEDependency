# JEDependency

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](https://github.com/jexcellence/JEDependency)

> **Modern dependency management and plugin architecture for Minecraft servers**

JEDependency is a powerful, lightweight library that provides automatic dependency resolution and a clean plugin architecture pattern for Minecraft server plugins. It eliminates the need to shade dependencies into your plugin JAR and provides a modern alternative to traditional plugin development patterns.

## ✨ Features

### 🚀 **Dependency Management**
- **Automatic Download**: Downloads dependencies from Maven repositories at runtime
- **Multiple Repositories**: Supports Maven Central, Sonatype, JitPack, and more
- **YAML Configuration**: Define dependencies in a simple YAML file
- **Classpath Injection**: Seamlessly injects dependencies into the runtime classpath
- **Caching**: Downloaded dependencies are cached to avoid repeated downloads

### 🏗️ **Plugin Architecture**
- **Delegate Pattern**: Clean separation between plugin bootstrap and implementation
- **Lifecycle Management**: Proper handling of plugin load, enable, and disable phases
- **Utility Methods**: Common plugin functionality built-in
- **Type Safety**: Full support for modern Java features and annotations

### 🔧 **Modern Java**
- **Java 21+ Support**: Built with modern Java features
- **Module System**: Compatible with Java 9+ module system
- **Comprehensive Documentation**: Full JavaDoc coverage
- **Exception Safety**: Robust error handling and recovery

## 📦 Installation

### Maven
```xml
<dependency>
    <groupId>de.jexcellence.dependency</groupId>
    <artifactId>JEDependency</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle
```gradle
dependencies {
    implementation 'de.jexcellence.dependency:JEDependency:2.0.0'
}
```

## 🚀 Quick Start

### Basic Dependency Loading

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        // Load dependencies programmatically
        JEDependency.initialize(this, MyPlugin.class, new String[]{
            "org.apache.commons:commons-lang3:3.12.0",
            "com.google.guava:guava:32.1.3-jre",
            "com.fasterxml.jackson.core:jackson-core:2.15.2"
        });
    }
    
    @Override
    public void onEnable() {
        // Your plugin logic here
        // Dependencies are now available on the classpath
    }
}
```

### YAML Configuration

Create `/src/main/resources/dependency/dependencies.yml`:

```yaml
dependencies:
  - "org.apache.commons:commons-lang3:3.12.0"
  - "com.google.guava:guava:32.1.3-jre"
  - "com.fasterxml.jackson.core:jackson-core:2.15.2"
  - "org.slf4j:slf4j-api:2.0.7"
```

Then in your plugin:

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        // Automatically loads from dependencies.yml
        JEDependency.initialize(this, MyPlugin.class);
    }
}
```

## 🏗️ Plugin Delegate Pattern

JEDependency provides a modern plugin architecture that separates your plugin's bootstrap logic from its implementation.

### Creating a Plugin Delegate

```java
public class MyPluginImpl extends AbstractPluginDelegate {
    
    public MyPluginImpl() {
        super(); // Plugin instance will be set automatically
    }
    
    @Override
    public void onLoad(JavaPlugin plugin) {
        super.onLoad(plugin);
        getLogger().info("Plugin loading: " + getName());
        
        // Load your dependencies here
        JEDependency.initialize(plugin, MyPluginImpl.class, new String[]{
            "com.example:my-library:1.0.0"
        });
    }
    
    @Override
    public void onEnable(JavaPlugin plugin) {
        super.onEnable(plugin);
        getLogger().info("Plugin enabled: " + getVersion());
        
        // Initialize your plugin logic
        setupCommands();
        registerListeners();
    }
    
    @Override
    public void onDisable(JavaPlugin plugin) {
        getLogger().info("Plugin disabled");
        // Cleanup logic
    }
    
    private void setupCommands() {
        // Use built-in utility methods
        // getDataFolder(), getLogger(), etc. are available
    }
}
```

### Bootstrap Class

```java
public class MyPlugin extends JavaPlugin {
    private PluginDelegate delegate;
    
    @Override
    public void onLoad() {
        this.delegate = new MyPluginImpl();
        this.delegate.onLoad(this);
    }
    
    @Override
    public void onEnable() {
        this.delegate.onEnable(this);
    }
    
    @Override
    public void onDisable() {
        this.delegate.onDisable(this);
    }
}
```

## 📚 Advanced Usage

### Custom Repository Configuration

```java
// The system automatically tries multiple repositories:
// - Maven Central
// - Sonatype OSS
// - JitPack
// - PaperMC
// - And more...

JEDependency.initialize(this, MyPlugin.class, new String[]{
    "com.github.username:repository:version", // JitPack
    "io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT" // PaperMC
});
```

### Error Handling

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        try {
            JEDependency.initialize(this, MyPlugin.class, new String[]{
                "com.example:my-dependency:1.0.0"
            });
        } catch (Exception e) {
            getLogger().severe("Failed to load dependencies: " + e.getMessage());
            // Handle gracefully or disable plugin
        }
    }
}
```

### Mixed Configuration

```java
// Combine YAML and programmatic dependencies
JEDependency.initialize(this, MyPlugin.class, new String[]{
    "com.example:additional-lib:2.0.0" // Added to YAML dependencies
});
```

## 🔧 Configuration

### Supported Repositories

JEDependency automatically searches these repositories in order:

1. **Maven Central** - `https://central.maven.org/maven2`
2. **Maven Central (Repo1)** - `https://repo1.maven.org/maven2`
3. **Apache Maven** - `https://repo.maven.apache.org/maven2`
4. **Sonatype OSS** - `https://oss.sonatype.org/content/groups/public/`
5. **Sonatype Snapshots** - `https://oss.sonatype.org/content/repositories/snapshots`
6. **JitPack** - `https://jitpack.io`
7. **PaperMC** - `https://repo.papermc.io/repository/maven-public/`
8. **And more...**

### File Structure

```
your-plugin/
├── src/main/java/
│   └── com/yourname/yourplugin/
│       ├── YourPlugin.java (Bootstrap)
│       └── YourPluginImpl.java (Implementation)
├── src/main/resources/
│   ├── plugin.yml
│   └── dependency/
│       └── dependencies.yml
└── libraries/ (created automatically)
    ├── commons-lang3-3.12.0.jar
    ├── guava-32.1.3-jre.jar
    └── ...
```

## 🎯 Best Practices

### 1. **Load Dependencies Early**
```java
@Override
public void onLoad() {
    // Load dependencies in onLoad(), not onEnable()
    JEDependency.initialize(this, MyPlugin.class);
}
```

### 2. **Use Specific Versions**
```yaml
dependencies:
  - "com.google.guava:guava:32.1.3-jre" # ✅ Specific version
  # - "com.google.guava:guava:LATEST"   # ❌ Avoid LATEST
```

### 3. **Handle Failures Gracefully**
```java
@Override
public void onLoad() {
    try {
        JEDependency.initialize(this, MyPlugin.class);
    } catch (Exception e) {
        getLogger().severe("Dependency loading failed - plugin will be disabled");
        setEnabled(false);
        return;
    }
}
```

### 4. **Use the Delegate Pattern**
```java
// Separate bootstrap from implementation for cleaner code
public class MyPlugin extends JavaPlugin {
    private final PluginDelegate delegate = new MyPluginImpl();
    
    @Override public void onLoad() { delegate.onLoad(this); }
    @Override public void onEnable() { delegate.onEnable(this); }
    @Override public void onDisable() { delegate.onDisable(this); }
}
```

## 🔍 Troubleshooting

### Common Issues

**Dependencies not found:**
```
[SEVERE] Failed to download com.example:library:1.0.0 from any repository
```
- Verify the GAV coordinates are correct
- Check if the dependency exists in supported repositories
- Ensure network connectivity

**ClassNotFoundException at runtime:**
```
java.lang.ClassNotFoundException: com.example.MyClass
```
- Make sure dependencies are loaded in `onLoad()`, not `onEnable()`
- Verify the dependency contains the expected classes

**Module system issues:**
```
java.lang.IllegalAccessError: class X cannot access class Y
```
- JEDependency automatically handles module deencapsulation
- Ensure you're using Java 21+ as recommended

### Debug Logging

Enable debug logging in your server configuration:
```yaml
# In your server's logging configuration
loggers:
  de.jexcellence.dependency: FINE
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/jexcellence/JEDependency.git
   cd JEDependency
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run tests**
   ```bash
   ./gradlew test
   ```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built for the Minecraft server development community
- Inspired by modern dependency management practices
- Thanks to all contributors and users

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/jexcellence/JEDependency/issues)
- **Discussions**: [GitHub Discussions](https://github.com/jexcellence/JEDependency/discussions)
- **Documentation**: [Wiki](https://github.com/jexcellence/JEDependency/wiki)

---

<div align="center">

**Made with ❤️ for the Minecraft community**

[⭐ Star this project](https://github.com/jexcellence/JEDependency) if you find it useful!

</div>