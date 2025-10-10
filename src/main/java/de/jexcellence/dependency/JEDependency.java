package de.jexcellence.dependency;

import de.jexcellence.dependency.dependency.DependencyManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Main entry point for the JEDependency system.
 *
 * Provides initialization and (when the Paper plugin loader is active) performs
 * runtime visibility checks for required external plugin APIs so we fail fast
 * with clear guidance if the custom loader did not expose dependency classloaders.
 *
 * @author JExcellence
 * @version 2.2.0
 * @since 2.0.0
 */
public final class JEDependency {

    private JEDependency() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void initialize(
            final JavaPlugin plugin,
            final Class<?> anchorClass,
            final String[] additionalDependencies
    ) {
        coreInitialize(plugin, anchorClass, additionalDependencies, false);
    }

    public static void initialize(
            final JavaPlugin plugin,
            final Class<?> anchorClass
    ) {
        initialize(plugin, anchorClass, null);
    }

    public static void initializeWithRemapping(
            final JavaPlugin plugin,
            final Class<?> anchorClass,
            final String[] additionalDependencies
    ) {
        coreInitialize(plugin, anchorClass, additionalDependencies, true);
    }

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

        // If the Paper plugin loader is active, our custom PluginLoader (RPluginLoader)
        // is responsible for adding libraries and exposing dependency plugin classloaders.
        // We still verify that required external APIs are visible to our classloader.
        if (isPaperPluginLoaderActive()) {
            plugin.getLogger().info("Paper plugin loader detected - delegating dependency classpath setup to the loader");

            // Fail-fast visibility checks for required dependencies declared in paper-plugin.yml
            // Adjust this list if you add/remove hard depends.
            verifyRuntimeApiVisibilityOrFail(
                    plugin,
                    new String[]{
                            // LuckPerms 5.x API presence (typed Option A integration requires this)
                            "net.luckperms.api.node.Node",
                            // RCore API presence when declared as depend
                            "com.raindropcentral.rcore.api.RCoreAdapter"
                    }
            );

            // With the loader active and APIs visible, nothing to do here.
            return;
        }

        // Log which dependency loading strategy will be used
        if (isPaperServer()) {
            plugin.getLogger().info("Server-specific dependency loading: Paper dependencies will be prioritized");
        } else {
            plugin.getLogger().info("Server-specific dependency loading: Spigot dependencies will be prioritized");
        }

        final boolean wantRemap = forceRemapping || shouldUseRemappingProperty();
        final boolean remapperAvailable = isClassPresent(
                "de.jexcellence.dependency.remapper.RemappingDependencyManager"
        );

        if (wantRemap && remapperAvailable) {
            plugin.getLogger().info("Using RemappingDependencyManager (remapping requested" + (forceRemapping ? " by API" : " via system property") + ")");
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

    private static boolean isClassPresent(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Attempts to initialize using de.jexcellence.dependency.remapper.RemappingDependencyManager via reflection.
     * Expected API:
     * - Constructor: (JavaPlugin, Class<?> anchorClass) or no-arg
     * - Method: initialize(String[]) or the pair addDependencies(String[]) + loadAll(ClassLoader)
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
            Constructor<?> ctor;
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

            final Object manager = (ctor.getParameterCount() == 2) ? ctor.newInstance(plugin, anchorClass) : ctor.newInstance();

            // Try initialize(String[]) first (drop-in replacement semantics)
            try {
                Method initMethod = remapperClass.getMethod("initialize", String[].class);
                initMethod.invoke(manager, (Object) additionalDependencies);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Try an alternative pair: addDependencies(String...) + loadAll(ClassLoader)
                Method addDeps = null;
                Method loadAll;

                try {
                    addDeps = remapperClass.getMethod("addDependencies", String[].class);
                } catch (NoSuchMethodException e) {
                    // ignore: optional
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
     * Returns true only if the system property is explicitly set by our loader.
     */
    private static boolean isPaperPluginLoaderActive() {
        // Our custom loader (RPluginLoader) sets this property.
        final String paperLoaderActive = System.getProperty("paper.plugin.loader.active");
        return "true".equalsIgnoreCase(paperLoaderActive);
    }

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

    public static String getServerType() {
        if (isPaperPluginLoaderActive()) {
            return "Paper (with plugin loader)";
        } else if (isPaperServer()) {
            return "Paper (legacy mode)";
        } else {
            return "Spigot/CraftBukkit";
        }
    }

    /**
     * When the Paper loader is active, verify that critical external APIs are actually
     * visible to this plugin's classloader. If not, fail fast with clear guidance to
     * expose plugin classloaders from the custom loader.
     */
    private static void verifyRuntimeApiVisibilityOrFail(JavaPlugin plugin, String[] requiredClasses) {
        if (requiredClasses == null || requiredClasses.length == 0) return;

        StringBuilder missing = new StringBuilder();
        for (String fqcn : requiredClasses) {
            if (!isClassPresent(fqcn)) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(fqcn);
            }
        }

        if (missing.length() > 0) {
            plugin.getLogger().severe("Required external API classes are not visible to this plugin's classloader: " + missing);
            plugin.getLogger().severe("This is caused by the custom plugin loader using an isolated classloader without exposing dependency plugins.");
            plugin.getLogger().severe("Fix in RPluginLoader.classloader(PluginClasspathBuilder): grant access to dependency plugins, e.g.:");
            plugin.getLogger().severe("  builder.addPluginDependency(\"LuckPerms\");");
            plugin.getLogger().severe("  builder.addPluginDependency(\"RCore\");");
            plugin.getLogger().severe("or the equivalent method for your Paper version (addAccessToPlugin/addPlugin).");
            throw new IllegalStateException("Missing runtime API visibility for: " + missing);
        }
    }
}