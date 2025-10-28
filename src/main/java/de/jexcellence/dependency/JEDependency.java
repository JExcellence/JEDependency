package de.jexcellence.dependency;

import de.jexcellence.dependency.dependency.DependencyManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Main entry point for the JEDependency system.
 *
 * <p>Provides initialization and (when the Paper plugin loader is active) performs runtime visibility checks for
 * required external plugin APIs so we fail fast with clear guidance if the custom loader did not expose dependency
 * class loaders.</p>
 */
public final class JEDependency {

    private static final String REMAP_SYSTEM_PROPERTY = "jedependency.remap";
    private static final String PAPER_LOADER_PROPERTY = "paper.plugin.loader.active";

    private JEDependency() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void initialize(
            final @NotNull JavaPlugin plugin,
            final @NotNull Class<?> anchorClass,
            final @Nullable String[] additionalDependencies
    ) {
        coreInitialize(plugin, anchorClass, additionalDependencies, false);
    }

    public static void initialize(
            final @NotNull JavaPlugin plugin,
            final @NotNull Class<?> anchorClass
    ) {
        initialize(plugin, anchorClass, null);
    }

    public static void initializeWithRemapping(
            final @NotNull JavaPlugin plugin,
            final @NotNull Class<?> anchorClass,
            final @Nullable String[] additionalDependencies
    ) {
        coreInitialize(plugin, anchorClass, additionalDependencies, true);
    }

    public static void initializeWithRemapping(
            final @NotNull JavaPlugin plugin,
            final @NotNull Class<?> anchorClass
    ) {
        initializeWithRemapping(plugin, anchorClass, null);
    }

    private static void coreInitialize(
            final @NotNull JavaPlugin plugin,
            final @NotNull Class<?> anchorClass,
            final @Nullable String[] additionalDependencies,
            final boolean forceRemapping
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(anchorClass, "anchorClass");

        final String serverType = getServerType();
        plugin.getLogger().log(Level.INFO, "JEDependency initializing on {0}", serverType);

        if (isPaperPluginLoaderActive()) {
            plugin.getLogger().log(Level.INFO,
                    "Paper plugin loader detected - delegating dependency classpath setup to the loader");

            verifyRuntimeApiVisibilityOrFail(
                    plugin,
                    new String[]{
                            "net.luckperms.api.node.Node",
                            "com.raindropcentral.rcore.api.RCoreAdapter"
                    }
            );

            return;
        }

        plugin.getLogger().log(Level.INFO,
                () -> "Server-specific dependency loading: " + (isPaperServer()
                        ? "Paper dependencies will be prioritized"
                        : "Spigot dependencies will be prioritized"));

        final boolean wantRemap = forceRemapping || shouldUseRemappingProperty();
        final boolean remapperAvailable = isClassPresent(
                "de.jexcellence.dependency.remapper.RemappingDependencyManager"
        );

        if (wantRemap && remapperAvailable) {
            plugin.getLogger().log(Level.INFO,
                    () -> "Using RemappingDependencyManager (remapping requested "
                            + (forceRemapping ? "by API" : "via system property") + ")");
            if (initializeViaRemapper(plugin, anchorClass, additionalDependencies)) {
                plugin.getLogger().info(
                        "JEDependency initialization completed with remapping. Dependencies are isolated to this plugin.");
                return;
            }
            plugin.getLogger().warning(
                    "Remapping initialization failed or incompatible API detected - falling back to standard DependencyManager");
        } else if (wantRemap) {
            plugin.getLogger().warning(
                    "Remapping requested but RemappingDependencyManager not found on classpath - falling back to standard DependencyManager");
        }

        final DependencyManager dependencyManager = new DependencyManager(plugin, anchorClass);
        dependencyManager.initialize(additionalDependencies);
        plugin.getLogger().info("JEDependency initialization completed. Dependencies are isolated to this plugin.");
    }

    private static boolean shouldUseRemappingProperty() {
        final String value = System.getProperty(REMAP_SYSTEM_PROPERTY);
        if (value == null) {
            return false;
        }
        final String trimmed = value.trim();
        return "true".equalsIgnoreCase(trimmed)
                || "1".equals(trimmed)
                || "yes".equalsIgnoreCase(trimmed)
                || "on".equalsIgnoreCase(trimmed);
    }

    private static boolean isClassPresent(final @NotNull String fqcn) {
        Objects.requireNonNull(fqcn, "fqcn");
        try {
            Class.forName(fqcn);
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean initializeViaRemapper(
            final @NotNull JavaPlugin plugin,
            final @NotNull Class<?> anchorClass,
            final @Nullable String[] additionalDependencies
    ) {
        try {
            final Class<?> remapperClass = Class.forName("de.jexcellence.dependency.remapper.RemappingDependencyManager");

            Constructor<?> constructor;
            try {
                constructor = remapperClass.getConstructor(JavaPlugin.class, Class.class);
            } catch (final NoSuchMethodException ignored) {
                constructor = remapperClass.getConstructor();
            }

            final Object manager = constructor.getParameterCount() == 2
                    ? constructor.newInstance(plugin, anchorClass)
                    : constructor.newInstance();

            try {
                final Method initMethod = remapperClass.getMethod("initialize", String[].class);
                initMethod.invoke(manager, (Object) additionalDependencies);
                return true;
            } catch (final NoSuchMethodException ignored) {
                Method addDependencies = null;
                try {
                    addDependencies = remapperClass.getMethod("addDependencies", String[].class);
                } catch (final NoSuchMethodException ignoredOptional) {
                    // optional API
                }

                final Method loadAll;
                try {
                    loadAll = remapperClass.getMethod("loadAll", ClassLoader.class);
                } catch (final NoSuchMethodException missingLoadAll) {
                    plugin.getLogger().log(Level.SEVERE,
                            "RemappingDependencyManager is missing loadAll(ClassLoader) method", missingLoadAll);
                    return false;
                }

                if (addDependencies != null && additionalDependencies != null) {
                    addDependencies.invoke(manager, (Object) additionalDependencies);
                }

                loadAll.invoke(manager, anchorClass.getClassLoader());
                return true;
            }

        } catch (final NoSuchMethodException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Required RemappingDependencyManager constructor or method not found", exception);
            return false;
        } catch (final Throwable throwable) {
            final String message = "Failed to initialize RemappingDependencyManager: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            plugin.getLogger().log(Level.SEVERE, message, throwable);
            return false;
        }
    }

    private static boolean isPaperPluginLoaderActive() {
        final String paperLoaderActive = System.getProperty(PAPER_LOADER_PROPERTY);
        return "true".equalsIgnoreCase(paperLoaderActive);
    }

    public static boolean isPaperServer() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (final ClassNotFoundException ignored) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration");
                return true;
            } catch (final ClassNotFoundException ignoredLegacy) {
                return false;
            }
        }
    }

    public static @NotNull String getServerType() {
        if (isPaperPluginLoaderActive()) {
            return "Paper (with plugin loader)";
        }
        if (isPaperServer()) {
            return "Paper (legacy mode)";
        }
        return "Spigot/CraftBukkit";
    }

    private static void verifyRuntimeApiVisibilityOrFail(
            final @NotNull JavaPlugin plugin,
            final @Nullable String[] requiredClasses
    ) {
        if (requiredClasses == null || requiredClasses.length == 0) {
            return;
        }

        final List<String> missingClasses = Arrays.stream(requiredClasses)
                .filter(Objects::nonNull)
                .filter(fqcn -> !isClassPresent(fqcn))
                .toList();

        if (!missingClasses.isEmpty()) {
            final String missing = String.join(", ", missingClasses);
            plugin.getLogger().severe(
                    "Required external API classes are not visible to this plugin's classloader: " + missing);
            plugin.getLogger().severe(
                    "This is caused by the custom plugin loader using an isolated classloader without exposing dependency plugins.");
            plugin.getLogger().severe(
                    "Fix in RPluginLoader.classloader(PluginClasspathBuilder): grant access to dependency plugins, e.g.:");
            plugin.getLogger().severe("  builder.addPluginDependency(\"LuckPerms\");");
            plugin.getLogger().severe("  builder.addPluginDependency(\"RCore\");");
            plugin.getLogger().severe(
                    "or the equivalent method for your Paper version (addAccessToPlugin/addPlugin).");
            throw new IllegalStateException("Missing runtime API visibility for: " + missing);
        }
    }
}