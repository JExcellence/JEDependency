package de.jexcellence.dependency.delegate;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.logging.Logger;

/**
 * Abstract base class for plugin delegates that provides common functionality.
 * 
 * This class handles the JavaPlugin instance management and provides convenient
 * utility methods that most plugin implementations will need.
 * 
 * Extending this class is optional - you can implement PluginDelegate directly
 * if you prefer more control over the implementation.
 */
public abstract class AbstractPluginDelegate implements PluginDelegate {
    
    private JavaPlugin plugin;
    
    /**
     * Constructor that accepts the JavaPlugin instance.
     * This is the recommended constructor for most implementations.
     * 
     * @param plugin The JavaPlugin instance (Bootstrap) that will delegate to this implementation
     */
    protected AbstractPluginDelegate(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Default constructor for implementations that will set the plugin later.
     * When using this constructor, the plugin will be set automatically during onLoad().
     */
    protected AbstractPluginDelegate() {

    }
    
    @Override
    public final @NotNull JavaPlugin getPlugin() {
        if (plugin == null) {
            throw new IllegalStateException("Plugin instance not yet initialized. Make sure onLoad() has been called.");
        }
        return plugin;
    }
    
    /**
     * Sets the plugin instance. This is called automatically by the lifecycle methods.
     * 
     * @param plugin The JavaPlugin instance
     */
    protected final void setPlugin(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void onLoad(JavaPlugin plugin) {
        this.setPlugin(plugin);
    }
    
    @Override
    public void onEnable(JavaPlugin plugin) {
        if (this.plugin == null) {
            this.setPlugin(plugin);
        }
    }
    
    @Override
    public void onDisable(JavaPlugin plugin) {
    }
    
    /**
     * Gets the plugin's logger.
     * 
     * @return The plugin's logger
     */
    protected final @NotNull Logger getLogger() {
        return getPlugin().getLogger();
    }
    
    /**
     * Gets the plugin's data folder.
     * 
     * @return The plugin's data folder
     */
    protected final @NotNull File getDataFolder() {
        return getPlugin().getDataFolder();
    }
    
    /**
     * Gets the plugin's name.
     * 
     * @return The plugin's name
     */
    protected final @NotNull String getName() {
        return getPlugin().getName();
    }
    
    /**
     * Gets the plugin's description.
     * 
     * @return The plugin's description, or null if not available
     */
    protected final @Nullable String getDescription() {
        return getPlugin().getDescription().getDescription();
    }
    
    /**
     * Gets the plugin's version.
     * 
     * @return The plugin's version
     */
    protected final @NotNull String getVersion() {
        return getPlugin().getDescription().getVersion();
    }
    
    /**
     * Checks if the plugin is enabled.
     * 
     * @return true if the plugin is enabled, false otherwise
     */
    protected final boolean isEnabled() {
        return getPlugin().isEnabled();
    }
}