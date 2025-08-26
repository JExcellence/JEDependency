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

Fork the project, do a maven/gradle clean install - install it locally on your device. So you can access it through gradle/maven using:

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

## 🏪 Complete Real-World Example

Let's create a comprehensive shop plugin that demonstrates JEDependency's full power with MySQL database integration and inventory framework for GUI management.

### Project Structure
```
MyShopPlugin/
├── src/main/java/com/example/myshop/
│   ├── MyShopPlugin.java          # Bootstrap class
│   ├── MyShopPluginImpl.java      # Implementation
│   ├── database/
│   │   └── DatabaseManager.java   # MySQL integration
│   ├── gui/
│   │   └── ShopGUI.java           # Inventory framework GUI
│   └── commands/
│       └── ShopCommand.java       # Command handling
├── src/main/resources/
│   ├── plugin.yml                 # Plugin configuration
│   ├── dependency/
│   │   └── dependencies.yml       # Dependencies configuration
│   └── config.yml                 # Plugin settings
└── libraries/                     # Auto-generated dependency cache
```

### 1. Dependencies Configuration

Create `/src/main/resources/dependency/dependencies.yml`:

```yaml
dependencies:
  # Database
  - "mysql:mysql-connector-java:8.0.33"
  - "com.zaxxer:HikariCP:5.0.1"
  
  # GUI Framework
  - "com.github.stefvanschie.inventoryframework:IF:0.10.13"
  
  # Utilities
  - "org.apache.commons:commons-lang3:3.12.0"
  - "com.google.code.gson:gson:2.10.1"
```

### 2. Plugin Configuration

Create `/src/main/resources/plugin.yml`:

```yaml
name: MyShopPlugin
version: 1.0.0
main: com.example.myshop.MyShopPlugin
api-version: 1.21
author: YourName
description: A modern shop plugin with MySQL and GUI support
load: STARTUP

commands:
  shop:
    description: Open the shop GUI
    usage: /shop
    permission: myshop.use

permissions:
  myshop.use:
    description: Allows using the shop
    default: true
  myshop.admin:
    description: Shop administration
    default: op
```

### 3. Bootstrap Class

Create `MyShopPlugin.java`:

```java
package com.example.myshop;

import de.jexcellence.dependency.JEDependency;
import de.jexcellence.dependency.PluginDelegate;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bootstrap class for MyShopPlugin.
 * Handles dependency loading and delegates to implementation.
 */
public final class MyShopPlugin extends JavaPlugin {
    
    private PluginDelegate pluginDelegate;
    
    @Override
    public void onLoad() {
        getLogger().info("Loading MyShopPlugin...");
        
        try {
            // Load dependencies first - this is crucial!
            JEDependency.initialize(this, MyShopPlugin.class);
            
            // Create the actual plugin implementation
            this.pluginDelegate = new MyShopPluginImpl();
            this.pluginDelegate.onLoad(this);
            
            getLogger().info("MyShopPlugin loaded successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to load MyShopPlugin: " + e.getMessage());
            e.printStackTrace();
            setEnabled(false);
        }
    }
    
    @Override
    public void onEnable() {
        if (this.pluginDelegate != null) {
            this.pluginDelegate.onEnable(this);
        }
    }
    
    @Override
    public void onDisable() {
        if (this.pluginDelegate != null) {
            this.pluginDelegate.onDisable(this);
        }
    }
}
```

### 4. Implementation Class

Create `MyShopPluginImpl.java`:

```java
package com.example.myshop;

import com.example.myshop.commands.ShopCommand;
import com.example.myshop.database.DatabaseManager;
import com.example.myshop.gui.ShopGUI;
import de.jexcellence.dependency.AbstractPluginDelegate;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main implementation using the delegate pattern.
 * Contains all actual plugin logic.
 */
public final class MyShopPluginImpl extends AbstractPluginDelegate {
    
    private DatabaseManager databaseManager;
    private ShopGUI shopGUI;
    
    @Override
    public void onLoad(JavaPlugin plugin) {
        super.onLoad(plugin);
        getLogger().info("Initializing MyShopPlugin implementation...");
        // Dependencies are now available on the classpath
    }
    
    @Override
    public void onEnable(JavaPlugin plugin) {
        super.onEnable(plugin);
        
        try {
            // Initialize database connection
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();
            
            // Initialize GUI system
            this.shopGUI = new ShopGUI(this, this.databaseManager);
            
            // Register commands
            getPlugin().getCommand("shop").setExecutor(new ShopCommand(this.shopGUI));
            
            getLogger().info("MyShopPlugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to enable MyShopPlugin: " + e.getMessage());
            e.printStackTrace();
            getPlugin().setEnabled(false);
        }
    }
    
    @Override
    public void onDisable(JavaPlugin plugin) {
        getLogger().info("Disabling MyShopPlugin...");
        
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        
        getLogger().info("MyShopPlugin disabled successfully!");
    }
    
    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }
    
    public ShopGUI getShopGUI() {
        return this.shopGUI;
    }
}
```

### 5. Database Integration

Create `database/DatabaseManager.java`:

```java
package com.example.myshop.database;

import com.example.myshop.MyShopPluginImpl;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database operations using HikariCP and MySQL.
 */
public final class DatabaseManager {
    
    private final MyShopPluginImpl plugin;
    private HikariDataSource dataSource;
    
    public DatabaseManager(MyShopPluginImpl plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() throws SQLException {
        this.plugin.getLogger().info("Initializing database connection...");
        
        FileConfiguration config = this.plugin.getPlugin().getConfig();
        
        // Setup HikariCP connection pool
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + 
            config.getString("database.host", "localhost") + ":" +
            config.getInt("database.port", 3306) + "/" +
            config.getString("database.name", "myshop"));
        hikariConfig.setUsername(config.getString("database.username", "root"));
        hikariConfig.setPassword(config.getString("database.password", ""));
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTimeout(30000);
        
        this.dataSource = new HikariDataSource(hikariConfig);
        this.createTables();
        
        this.plugin.getLogger().info("Database initialized successfully!");
    }
    
    private void createTables() throws SQLException {
        String createShopItemsTable = """
            CREATE TABLE IF NOT EXISTS shop_items (
                id INT AUTO_INCREMENT PRIMARY KEY,
                material VARCHAR(50) NOT NULL,
                display_name VARCHAR(100),
                price DECIMAL(10,2) NOT NULL,
                stock INT DEFAULT -1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(createShopItemsTable)) {
            stmt.executeUpdate();
        }
    }
    
    public List<ShopItem> getAllItems() throws SQLException {
        List<ShopItem> items = new ArrayList<>();
        String query = "SELECT * FROM shop_items WHERE stock != 0";
        
        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                items.add(new ShopItem(
                    rs.getInt("id"),
                    rs.getString("material"),
                    rs.getString("display_name"),
                    rs.getDouble("price"),
                    rs.getInt("stock")
                ));
            }
        }
        
        return items;
    }
    
    public void shutdown() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
            this.plugin.getLogger().info("Database connection closed.");
        }
    }
    
    public static class ShopItem {
        private final int id;
        private final String material;
        private final String displayName;
        private final double price;
        private final int stock;
        
        public ShopItem(int id, String material, String displayName, double price, int stock) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.price = price;
            this.stock = stock;
        }
        
        // Getters
        public int getId() { return id; }
        public String getMaterial() { return material; }
        public String getDisplayName() { return displayName; }
        public double getPrice() { return price; }
        public int getStock() { return stock; }
    }
}
```

### 6. GUI with Inventory Framework

Create `gui/ShopGUI.java`:

```java
package com.example.myshop.gui;

import com.example.myshop.MyShopPluginImpl;
import com.example.myshop.database.DatabaseManager;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Shop GUI using InventoryFramework library.
 */
public final class ShopGUI {
    
    private final MyShopPluginImpl plugin;
    private final DatabaseManager databaseManager;
    
    public ShopGUI(MyShopPluginImpl plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    
    public void openShop(Player player) {
        try {
            ChestGui gui = new ChestGui(6, ChatColor.DARK_GREEN + "Shop");
            StaticPane pane = new StaticPane(0, 0, 9, 6);
            
            List<DatabaseManager.ShopItem> items = this.databaseManager.getAllItems();
            
            int slot = 0;
            for (DatabaseManager.ShopItem shopItem : items) {
                if (slot >= 54) break;
                
                Material material = Material.valueOf(shopItem.getMaterial().toUpperCase());
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                
                if (meta != null) {
                    meta.setDisplayName(ChatColor.YELLOW + shopItem.getDisplayName());
                    meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Price: " + ChatColor.GREEN + "$" + shopItem.getPrice(),
                        ChatColor.GRAY + "Stock: " + ChatColor.WHITE + 
                            (shopItem.getStock() == -1 ? "Unlimited" : shopItem.getStock()),
                        "",
                        ChatColor.YELLOW + "Click to purchase!"
                    ));
                    item.setItemMeta(meta);
                }
                
                GuiItem guiItem = new GuiItem(item, event -> {
                    event.setCancelled(true);
                    Player clickedPlayer = (Player) event.getWhoClicked();
                    this.handlePurchase(clickedPlayer, shopItem);
                });
                
                pane.addItem(guiItem, slot % 9, slot / 9);
                slot++;
            }
            
            gui.addPane(pane);
            gui.show(player);
            
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to open shop GUI: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Failed to open shop. Please try again later.");
        }
    }
    
    private void handlePurchase(Player player, DatabaseManager.ShopItem item) {
        player.sendMessage(ChatColor.GREEN + "You purchased " + item.getDisplayName() + 
                          " for $" + item.getPrice() + "!");
        // Implement full purchase logic here
    }
}
```

### 7. Command Handler

Create `commands/ShopCommand.java`:

```java
package com.example.myshop.commands;

import com.example.myshop.gui.ShopGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopCommand implements CommandExecutor {
    
    private final ShopGUI shopGUI;
    
    public ShopCommand(ShopGUI shopGUI) {
        this.shopGUI = shopGUI;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("myshop.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the shop!");
            return true;
        }
        
        this.shopGUI.openShop(player);
        return true;
    }
}
```

### 8. Configuration File

Create `/src/main/resources/config.yml`:

```yaml
# MyShopPlugin Configuration
database:
  host: "localhost"
  port: 3306
  name: "myshop"
  username: "root"
  password: ""

shop:
  title: "Server Shop"
  currency-symbol: "$"
```

### 🎯 Why This Example Works

✅ **Clean Architecture**: Bootstrap handles dependencies, implementation handles logic  
✅ **Real Dependencies**: Uses MySQL, HikariCP, InventoryFramework without shading  
✅ **Proper Lifecycle**: Dependencies loaded in `onLoad()`, used in `onEnable()`  
✅ **Error Handling**: Graceful failure handling throughout  
✅ **Production Ready**: Connection pooling, GUI framework, proper configuration  

### 🚀 Running the Example

1. **Setup Database**: Create MySQL database named `myshop`
2. **Configure**: Update `config.yml` with database credentials  
3. **Build**: Compile with JEDependency dependency
4. **Deploy**: Place in server's plugins folder
5. **Start**: Dependencies automatically downloaded and injected
6. **Use**: Players run `/shop` to open the GUI

This demonstrates JEDependency's full power - no dependency shading needed, clean architecture, and modern Java practices!

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
