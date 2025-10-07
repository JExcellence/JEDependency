package de.jexcellence.dependency;

import de.jexcellence.dependency.dependency.DependencyManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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
 * <p>Remapping support:
 * <ul>
 *   <li>Set system property {@code -Djedependency.remap=true} to prefer the RemappingDependencyManager if present.</li>
 *   <li>Use {@link #initializeWithRemapping(JavaPlugin, Class, String[])} to force remapping regardless of the property.</li>
 *   <li>The remapper class is discovered reflectively as
 *       {@code de.jexcellence.dependency.remapper.RemappingDependencyManager} and should provide:
 *       <ul>
 *         <li>a constructor {@code (JavaPlugin, Class<?> anchorClass)}</li>
 *         <li>and a method {@code initialize(String[] additionalDependencies)}</li>
 *       </ul>
 *   </li>
 * </ul>
 * </p>
 *
 * @author JExcellence
 * @version 2.1.0
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
     *   <li>Inject all dependencies into the plugin's classloader</li>
     * </ul>
     *
     * <p>If the system property {@code jedependency.remap=true} is set and a suitable
     * remapping manager is on the classpath, remapping will be used automatically.</p>
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
        coreInitialize(plugin, anchorClass, additionalDependencies, false);
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
     * Initializes the dependency management system forcing remapping usage,
     * regardless of the system property.
     *
     * <p>This method mirrors {@link #initialize(JavaPlugin, Class, String[])} but
     * will always attempt to use the remapping manager if it is present on the classpath.
     * Falls back to the default manager if the remapper class is not available.</p>
     *
     * @param plugin the plugin instance
     * @param anchorClass the class used as anchor
     * @param additionalDependencies optional dependencies
     */
    public static void initializeWithRemapping(
            final JavaPlugin plugin,
            final Class<?> anchorClass,
            final String[] additionalDependencies
    ) {
        coreInitialize(plugin, anchorClass, additionalDependencies, true);
    }

    /**
     * Initializes the dependency management system forcing remapping usage
     * without additional dependencies.
     *
     * @param plugin the plugin instance
     * @param anchorClass the class used as anchor
     */
    public static void initializeWithRemapping(
            final JavaPlugin plugin,
            final Class<?> anchorClass
    ) {
        initializeWithRemapping(plugin, anchorClass, null);
    }

    private static void coreInitialize(
            final JavaPlugin plugin,
            final Class<?> anchorClass,
            final String[] additionalDependencies,
            final boolean forceRemapping
    ) {
        final String serverType = getServerType();

        plugin.getLogger().info("JEDependency initializing on " + serverType);

        if (isPaperPluginLoaderActive()) {
            plugin.getLogger().info("Paper plugin loader detected - skipping manual JEDependency initialization");
            return;
        }

        // Log which dependency loading strategy will be used
        if (isPaperServer()) {
            plugin.getLogger().info("Server-specific dependency loading: Paper dependencies will be prioritized");
        } else {
            plugin.getLogger().info("Server-specific dependency loading: Spigot dependencies will be prioritized");
        }

        final boolean wantRemap = forceRemapping || shouldUseRemappingProperty();
        final boolean remapperAvailable = isClassPresent();

        if (wantRemap && remapperAvailable) {
            plugin.getLogger().info("Using RemappingDependencyManager (remapping requested"
                    + (forceRemapping ? " by API" : " via system property") + ")");
            if (initializeViaRemapper(plugin, anchorClass, additionalDependencies)) {
                plugin.getLogger().info("JEDependency initialization completed with remapping. Dependencies are isolated to this plugin.");
                return;
            } else {
                plugin.getLogger().warning("Remapping initialization failed or incompatible API detected - falling back to standard DependencyManager");
            }
        } else if (wantRemap) {
            plugin.getLogger().warning("Remapping requested but RemappingDependencyManager not found on classpath - falling back to standard DependencyManager");
        }

        // Fallback: standard dependency manager
        final DependencyManager dependencyManager = new DependencyManager(plugin, anchorClass);
        dependencyManager.initialize(additionalDependencies);

        plugin.getLogger().info("JEDependency initialization completed. Dependencies are isolated to this plugin.");
    }

    private static boolean shouldUseRemappingProperty() {
        final String value = System.getProperty("jedependency.remap");
        if (value == null) return false;
        final String v = value.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    private static boolean isClassPresent() {
        try {
            Class.forName("de.jexcellence.dependency.remapper.RemappingDependencyManager");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Attempts to initialize using de.jexcellence.dependency.remapper.RemappingDependencyManager via reflection.
     * Expected API:
     *   - Constructor: (JavaPlugin, Class<?> anchorClass)
     *   - Method: initialize(String[] additionalDependencies)
     *
     * @return true if initialization ran without reflective errors, false otherwise
     */
    private static boolean initializeViaRemapper(
            final JavaPlugin plugin,
            final Class<?> anchorClass,
            final String[] additionalDependencies
    ) {
        try {
            final Class<?> remapperClass = Class.forName("de.jexcellence.dependency.remapper.RemappingDependencyManager");

            // Prefer constructor (JavaPlugin, Class<?>)
            Constructor<?> ctor = null;
            try {
                ctor = remapperClass.getConstructor(JavaPlugin.class, Class.class);
            } catch (NoSuchMethodException ignored) {
                // Try a no-arg constructor as a fallback
                try {
                    ctor = remapperClass.getConstructor();
                } catch (NoSuchMethodException e) {
                    plugin.getLogger().severe("No compatible constructor found in RemappingDependencyManager");
                    return false;
                }
            }

            final Object manager = (ctor.getParameterCount() == 2)
                    ? ctor.newInstance(plugin, anchorClass)
                    : ctor.newInstance();

            // Try initialize(String[]) first (drop-in replacement semantics)
            Method initMethod = null;
            try {
                initMethod = remapperClass.getMethod("initialize", String[].class);
                initMethod.invoke(manager, (Object) additionalDependencies);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Try an alternative pair: addDependencies(String...) + loadAll(ClassLoader)
                Method addDeps = null;
                Method loadAll = null;

                try {
                    addDeps = remapperClass.getMethod("addDependencies", String[].class);
                } catch (NoSuchMethodException e) {
                    // If addDependencies is not present but loadAll is, proceed without additional deps
                }

                try {
                    loadAll = remapperClass.getMethod("loadAll", ClassLoader.class);
                } catch (NoSuchMethodException e) {
                    plugin.getLogger().severe("RemappingDependencyManager is missing both initialize(String[]) and loadAll(ClassLoader) methods");
                    return false;
                }

                if (addDeps != null && additionalDependencies != null) {
                    addDeps.invoke(manager, (Object) additionalDependencies);
                }

                final ClassLoader cl = anchorClass.getClassLoader();
                loadAll.invoke(manager, cl);
                return true;
            }
        } catch (final Throwable t) {
            plugin.getLogger().severe("Failed to initialize RemappingDependencyManager: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Checks if the Paper plugin loader system is active.
     *
     * @return true if Paper plugin loader is active, false otherwise
     */
    private static boolean isPaperPluginLoaderActive() {
        // Check system property first
        final String paperLoaderActive = System.getProperty("paper.plugin.loader.active");
        if ("true".equals(paperLoaderActive)) {
            return true;
        }

        // Check for Paper-specific classes that indicate modern Paper plugin loading
        try {
            Class.forName("io.papermc.paper.plugin.loader.PluginLoader");
            Class.forName("io.papermc.paper.plugin.bootstrap.PluginBootstrap");

            // Only skip if we have clear evidence of Paper plugin loader being active
            // This is more restrictive to avoid false positives on regular Paper servers
            return "true".equals(paperLoaderActive);
        } catch (final ClassNotFoundException exception) {
            // Paper plugin loader classes not found, proceed with manual loading
            return false;
        }
    }

    /**
     * Checks if we're running on Paper (but not necessarily using Paper plugin loader).
     *
     * @return true if running on Paper, false if on Spigot/CraftBukkit
     */
    public static boolean isPaperServer() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (final ClassNotFoundException exception) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration");
                return true;
            } catch (final ClassNotFoundException exception2) {
                return false;
            }
        }
    }

    /**
     * Gets server type information for debugging.
     *
     * @return server type string
     */
    public static String getServerType() {
        if (isPaperPluginLoaderActive()) {
            return "Paper (with plugin loader)";
        } else if (isPaperServer()) {
            return "Paper (legacy mode)";
        } else {
            return "Spigot/CraftBukkit";
        }
    }
}