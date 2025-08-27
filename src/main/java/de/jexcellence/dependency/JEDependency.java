package de.jexcellence.dependency;

import de.jexcellence.dependency.dependency.DependencyManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for the JEDependency system.
 * 
 * <p>This class provides a simple, static API for initializing the dependency
 * management system. It serves as a facade over the more complex internal
 * components and provides backward compatibility with existing code.</p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * public class MyPlugin extends JavaPlugin {
 *     {@literal @}Override
 *     public void onLoad() {
 *         JEDependency.initialize(this, MyPlugin.class, new String[]{
 *             "com.example:my-library:1.0.0",
 *             "org.apache.commons:commons-lang3:3.12.0"
 *         });
 *     }
 * }
 * </pre>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class JEDependency {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private JEDependency() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Initializes the dependency management system.
     * 
     * <p>This method sets up the dependency management system for the given plugin
     * and loads all specified dependencies. Dependencies can be loaded from both
     * a YAML configuration file and the provided array of GAV coordinates.</p>
     * 
     * <p>The method will:</p>
     * <ul>
     *   <li>Create a libraries directory in the plugin's data folder</li>
     *   <li>Load dependencies from {@code /dependency/dependencies.yml} if present</li>
     *   <li>Download missing dependencies from Maven repositories</li>
     *   <li>Inject all dependencies into the current classpath</li>
     * </ul>
     * 
     * @param plugin the plugin instance that owns the dependencies
     * @param anchorClass the class to use for resource loading and JAR location detection
     * @param additionalDependencies optional array of additional GAV coordinates to load
     * @throws IllegalArgumentException if plugin or anchorClass is null
     * 
     * @see de.jexcellence.dependency.dependency.DependencyManager#initialize(String[])
     */
    public static void initialize(
		final JavaPlugin plugin,
		final Class<?> anchorClass,
		final String[] additionalDependencies
    ) {
        if (isPaperPluginLoaderActive()) {
            plugin.getLogger().info("Paper plugin loader detected - skipping manual JEDependency initialization");
            return;
        }
		
        final DependencyManager dependencyManager = new DependencyManager(plugin, anchorClass);
        dependencyManager.initialize(additionalDependencies);
    }
    
    /**
     * Initializes the dependency management system without additional dependencies.
     * 
     * <p>This is a convenience method that calls {@link #initialize(JavaPlugin, Class, String[])}
     * with a null array for additional dependencies.</p>
     * 
     * @param plugin the plugin instance that owns the dependencies
     * @param anchorClass the class to use for resource loading and JAR location detection
     * @throws IllegalArgumentException if plugin or anchorClass is null
     */
    public static void initialize(
		final JavaPlugin plugin,
		final Class<?> anchorClass
    ) {
        initialize(plugin, anchorClass, null);
    }
    
    /**
     * Checks if the Paper plugin loader system is active.
     * 
     * @return true if Paper plugin loader is active, false otherwise
     */
    private static boolean isPaperPluginLoaderActive() {
        final String paperLoaderActive = System.getProperty("paper.plugin.loader.active");
        if ("true".equals(paperLoaderActive)) {
            return true;
        }
		
        try {
            Class.forName("io.papermc.paper.plugin.loader.PluginLoader");
            // If we can load the PluginLoader class, we're likely on Paper
            // But only skip if the system property is also set to avoid false positives
            return "true".equals(paperLoaderActive);
        } catch (final ClassNotFoundException exception) {
            return false;
        }
    }
}