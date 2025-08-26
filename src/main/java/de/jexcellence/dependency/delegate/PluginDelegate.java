package de.jexcellence.dependency.delegate;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Interface for plugin delegates that can be loaded by the Bootstrap system.
 * 
 * Your actual plugin implementation should implement this interface instead of extending JavaPlugin.
 * The Bootstrap will provide the JavaPlugin instance to your delegate.
 */
public interface PluginDelegate {
    
    /**
     * Gets the JavaPlugin instance that this delegate is associated with.
     * This method should return the same instance that was passed to the lifecycle methods.
     * 
     * @return The JavaPlugin instance (Bootstrap) that is delegating to this implementation
     */
    JavaPlugin getPlugin();
    
    /**
     * Called when the plugin is loaded.
     * @param plugin The JavaPlugin instance (Bootstrap) that is delegating to this implementation
     */
    void onLoad(JavaPlugin plugin);
    
    /**
     * Called when the plugin is enabled.
     * @param plugin The JavaPlugin instance (Bootstrap) that is delegating to this implementation
     */
    void onEnable(JavaPlugin plugin);
    
    /**
     * Called when the plugin is disabled.
     * @param plugin The JavaPlugin instance (Bootstrap) that is delegating to this implementation
     */
    void onDisable(JavaPlugin plugin);
}