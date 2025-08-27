package de.jexcellence.dependency.delegate;

/**
 * Interface for plugin delegates that can be loaded by the Bootstrap system.
 * 
 * Your actual plugin implementation should implement this interface instead of extending JavaPlugin.
 * The Bootstrap will provide the JavaPlugin instance to your delegate.
 */
public interface PluginDelegate<T> {
    
    /**
     * Gets the JavaPlugin instance that this delegate is associated with.
     * This method should return the same instance that was passed to the lifecycle methods.
     * 
     * @return The JavaPlugin instance (Bootstrap) that is delegating to this implementation
     */
    T getImpl();
    
    /**
     * Called when the plugin is loaded.
     */
    void onLoad();
    
    /**
     * Called when the plugin is enabled.
     */
    void onEnable();
    
    /**
     * Called when the plugin is disabled.
     */
    void onDisable();
}