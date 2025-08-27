package de.jexcellence.dependency.delegate;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for plugin delegates that provides common functionality.
 * 
 * This class handles the JavaPlugin instance management and provides convenient
 * utility methods that most plugin implementations will need.
 * 
 * Extending this class is optional - you can implement PluginDelegate directly
 * if you prefer more control over the implementation.
 */
public abstract class AbstractPluginDelegate<T extends JavaPlugin> implements PluginDelegate<T> {
    
    private final T impl;
    
    /**
     * Constructor that accepts the JavaPlugin instance.
     * This is the recommended constructor for most implementations.
     *
     */
    protected AbstractPluginDelegate(
		final @NotNull T impl
    ) {
        this.impl = impl;
    }
    
    @Override
    public final @NotNull T getImpl() {
	    return this.impl;
    }
}