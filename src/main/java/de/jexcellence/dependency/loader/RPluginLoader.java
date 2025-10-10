package de.jexcellence.dependency.loader;

import de.jexcellence.dependency.dependency.DependencyDownloader;
import de.jexcellence.dependency.dependency.YamlDependencyLoader;
import de.jexcellence.dependency.remapper.RemappingDependencyManager;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enhanced Paper plugin loader implementation for dynamically loading external dependencies and libraries.
 *
 * Features:
 * - Ensures existence of a libraries directory in the plugin's data directory
 * - Initializes and downloads dependencies using the JEDependency system
 * - Optionally remaps those dependencies into libraries/remapped to avoid classpath conflicts
 * - Adds all JAR files (remapped when available) to the plugin's classpath
 * - NEW: Grants classpath access to dependency plugins (e.g., LuckPerms, RCore) so typed integrations work
 *
 * This enables plugins to manage and load their own dependencies at runtime with enhanced reliability,
 * supporting modular and extensible plugin development while keeping the main JAR size minimal.
 *
 * @version 2.2.0
 * @since TBD
 * @author J
 */
@SuppressWarnings({"unused", "UnstableApiUsage"})
public class RPluginLoader implements PluginLoader {
    private static final String REMAP_PROP = "jedependency.remap";
    private static final String RELOCATIONS_PROP = "jedependency.relocations";
    private static final String RELOCATIONS_PREFIX_PROP = "jedependency.relocations.prefix";
    private static final String RELOCATIONS_EXCLUDES_PROP = "jedependency.relocations.excludes";
    private static final String PAPER_LOADER_PROP = "paper.plugin.loader.active";
    private static final String REMAPPED_DIR_NAME = "remapped";

    private static final String PLUGIN_GROUP_PROP = "jedependency.plugin.group";
    private static final String ALT_PLUGIN_GROUP_PROP = "jedependency.group";

    private final Logger logger;
    private final DependencyDownloader dependencyDownloader;
    private final YamlDependencyLoader yamlLoader;

    public RPluginLoader() {
        this.logger = Logger.getLogger(RPluginLoader.class.getName());
        this.dependencyDownloader = new DependencyDownloader();
        this.yamlLoader = new YamlDependencyLoader();
    }

    @Override
    public void classloader(final PluginClasspathBuilder classpathBuilder) {
        System.setProperty(PAPER_LOADER_PROP, "true");

        final Path libsDirectory = Path.of(classpathBuilder.getContext().getDataDirectory().toAbsolutePath().toString(), "libraries");
        final Path remappedDirectory = libsDirectory.resolve(REMAPPED_DIR_NAME);

        try {
            logger.log(Level.INFO, "Initializing plugin classpath with JEDependency system (Paper loader)...");
            Files.createDirectories(libsDirectory);
            if (!Files.exists(libsDirectory) || !Files.isDirectory(libsDirectory)) {
                logger.log(Level.INFO, "Libs directory does not exist or is not a directory: " + libsDirectory);
                return;
            }

            try {
                initializeDependencies(libsDirectory.toFile());
                logger.log(Level.INFO, "Dependencies initialized successfully using JEDependency system");
            } catch (final Exception exception) {
                logger.log(Level.WARNING, "Failed to initialize dependencies", exception);
            }

            Path effectiveDirectory = libsDirectory;
            if (shouldRemap()) {
                boolean remapSuccess = tryRemapLibraries(libsDirectory, remappedDirectory);
                if (remapSuccess) {
                    effectiveDirectory = remappedDirectory;
                    logger.log(Level.INFO, "Using remapped libraries from: " + effectiveDirectory);
                } else {
                    logger.log(Level.WARNING, "Remapping requested but failed or unavailable, falling back to unremapped libraries");
                }
            } else {
                logger.log(Level.FINE, "Remapping disabled via system property: " + REMAP_PROP);
            }

            loadJarFiles(classpathBuilder, effectiveDirectory);

            exposePluginDependencies(classpathBuilder, Set.of(
                    "LuckPerms",
                    "RCore"
            ));

        } catch (final IOException exception) {
            throw new RuntimeException("Failed to create libs directory: " + libsDirectory, exception);
        }
    }

    private void initializeDependencies(final File libsDirectory) {
        try {
            final String[] yamlDependencies = yamlLoader.loadDependenciesFromYaml(RPluginLoader.class);
            if (yamlDependencies != null && yamlDependencies.length > 0) {
                logger.log(Level.INFO, "Found " + yamlDependencies.length + " dependencies in YAML configuration");
                for (final String dependency : yamlDependencies) {
                    try {
                        logger.log(Level.FINE, "Processing dependency: " + dependency);
                        final File downloadedFile = dependencyDownloader.downloadDependency(dependency, libsDirectory);
                        if (downloadedFile != null && downloadedFile.exists()) {
                            logger.log(Level.FINE, "Successfully processed dependency: " + dependency);
                        } else {
                            logger.log(Level.WARNING, "Failed to download dependency: " + dependency);
                        }
                    } catch (final Exception exception) {
                        logger.log(Level.WARNING, "Error processing dependency: " + dependency, exception);
                    }
                }
            } else {
                logger.log(Level.INFO, "No dependencies found in YAML configuration");
            }
        } catch (final Exception exception) {
            logger.log(Level.WARNING, "Failed to load dependencies from YAML configuration", exception);
        }
    }

    private void loadJarFiles(final PluginClasspathBuilder classpathBuilder, final Path libsDirectory) {
        try (final Stream<Path> jarFiles = Files.walk(libsDirectory, 1)) {
            final AtomicInteger jarCount = new AtomicInteger(0);
            jarFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .forEach(jarPath -> {
                        try {
                            classpathBuilder.addLibrary(new JarLibrary(jarPath));
                            jarCount.incrementAndGet();
                            logger.log(Level.FINE, "Added JAR to classpath: " + jarPath.getFileName());
                        } catch (final Exception exception) {
                            logger.log(Level.WARNING, "Failed to add JAR to classpath: " + jarPath, exception);
                        }
                    });

            logger.info("Successfully loaded " + jarCount.get() + " JAR files into classpath from " + libsDirectory);
        } catch (final Exception exception) {
            logger.log(Level.SEVERE, "Failed to load libraries from libs directory: " + libsDirectory, exception);
        }
    }

    private boolean shouldRemap() {
        final String value = System.getProperty(REMAP_PROP, "true");
        if (value == null) return false;
        final String v = value.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    private boolean tryRemapLibraries(final Path inputDir, final Path outputDir) {
        Objects.requireNonNull(inputDir, "inputDir");
        Objects.requireNonNull(outputDir, "outputDir");
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create remapped directory: " + outputDir, e);
            return false;
        }

        final List<Path> inputJars = listJarFiles(inputDir);
        if (inputJars.isEmpty()) {
            logger.log(Level.FINE, "No input JARs to remap in: " + inputDir);
            return false;
        }

        final Path dataDirectory = inputDir.getParent() != null ? inputDir.getParent() : inputDir;
        final RemappingDependencyManager manager;
        try {
            manager = new RemappingDependencyManager(dataDirectory);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "RemappingDependencyManager not available or failed to initialize", t);
            return false;
        }

        final int explicitRelocations = applyRelocationsFromProperty(manager);
        if (explicitRelocations == 0) {
            final int autoCount = applyAutomaticRelocations(manager, inputJars);
            if (autoCount > 0) {
                logger.log(Level.INFO, "Applied " + autoCount + " automatic relocation(s) based on detected package roots");
            } else {
                logger.log(Level.WARNING, "No relocations were applied (explicit or automatic). Remapping will not change packages.");
            }
        }

        int processed = 0;
        int remapped = 0;

        for (Path in : inputJars) {
            final Path out = outputDir.resolve(in.getFileName());
            if (isUpToDate(out, in)) {
                logger.log(Level.FINE, "Using cached remapped JAR: " + out.getFileName());
                processed++;
                remapped++;
                continue;
            }

            try {
                if (Files.exists(out)) {
                    Files.delete(out);
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "Failed to delete existing output before remap: " + out, e);
            }

            try {
                manager.remap(in, out);
                processed++;
                if (Files.exists(out) && Files.isRegularFile(out) && fileLooksLikeJar(out)) {
                    remapped++;
                    logger.log(Level.FINE, "Remapped: " + in.getFileName() + " -> " + out.getFileName());
                } else {
                    logger.log(Level.WARNING, "Remapper produced no output for: " + in.getFileName());
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to remap " + in.getFileName() + ": " + t.getMessage(), t);
            }
        }

        if (processed == 0) {
            logger.log(Level.WARNING, "No JARs processed for remapping from: " + inputDir);
            return false;
        }

        logger.log(Level.INFO, "Remapping complete: " + remapped + " of " + processed + " JAR(s) available in " + outputDir);
        return remapped > 0;
    }

    private int applyRelocationsFromProperty(RemappingDependencyManager manager) {
        int applied = 0;
        final String spec = System.getProperty(RELOCATIONS_PROP);
        if (spec == null || spec.trim().isEmpty()) {
            return 0;
        }

        final String[] pairs = spec.split(",");
        for (String p : pairs) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            int idx = s.indexOf("=>");
            if (idx <= 0 || idx >= s.length() - 2) {
                logger.log(Level.WARNING, "Invalid relocation mapping, expected 'from=>to': " + s);
                continue;
            }

            String from = s.substring(0, idx).trim();
            String to = s.substring(idx + 2).trim();
            if (!from.isEmpty() && !to.isEmpty()) {
                manager.relocate(from, to);
                applied++;
            } else {
                logger.log(Level.WARNING, "Ignoring empty relocation mapping: " + s);
            }
        }

        return applied;
    }

    private int applyAutomaticRelocations(RemappingDependencyManager manager, List<Path> jars) {
        final String basePrefix = resolveRelocationBasePrefix();
        final Set<String> excludes = new HashSet<>(defaultExcludedRoots());
        final String excludesProp = System.getProperty(RELOCATIONS_EXCLUDES_PROP);
        if (excludesProp != null && !excludesProp.trim().isEmpty()) {
            Arrays.stream(excludesProp.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(excludes::add);
        }

        final Set<String> roots = new HashSet<>();
        for (Path jar : jars) {
            roots.addAll(detectRootPackages(jar, excludes));
        }

        int applied = 0;
        for (String root : roots) {
            String to = basePrefix + "." + root;
            manager.relocate(root, to);
            applied++;
        }

        if (applied == 0) {
            logger.log(Level.FINE, "No automatic relocation roots detected.");
        } else {
            logger.log(Level.FINE, "Automatic relocation will map " + applied + " root package(s) under '" + basePrefix + "'.");
        }
        return applied;
    }

    private String resolveRelocationBasePrefix() {
        final String explicit = System.getProperty(RELOCATIONS_PREFIX_PROP);
        if (explicit != null && !explicit.trim().isEmpty()) {
            return normalizePackagePrefix(explicit.trim().replaceAll("\\.$", ""));
        }

        String group = System.getProperty(PLUGIN_GROUP_PROP);
        if (group == null || group.isBlank()) {
            group = System.getProperty(ALT_PLUGIN_GROUP_PROP);
        }
        if (group == null || group.isBlank()) {
            group = detectGroupFromPomProperties();
        }
        if (group == null || group.isBlank()) {
            group = deriveGroupFromThisPackage();
        }
        if (group.isBlank()) {
            group = "de.jexcellence";
        }

        return normalizePackagePrefix(group + ".remapped");
    }

    private String deriveGroupFromThisPackage() {
        String pkg = RPluginLoader.class.getPackageName();
        if (pkg.endsWith(".dependency.loader")) {
            pkg = pkg.substring(0, pkg.length() - ".dependency.loader".length());
        }
        return firstTwoSegments(pkg);
    }

    private String detectGroupFromPomProperties() {
        try {
            URL url = RPluginLoader.class.getProtectionDomain().getCodeSource().getLocation();
            Path self = Path.of(url.toURI());
            if (Files.isRegularFile(self) && self.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                try (JarFile jar = new JarFile(self.toFile())) {
                    for (JarEntry e : java.util.Collections.list(jar.entries())) {
                        if (!e.isDirectory() && e.getName().startsWith("META-INF/maven/") && e.getName().endsWith("/pom.properties")) {
                            Properties props = new Properties();
                            try (var in = jar.getInputStream(e)) {
                                props.load(in);
                            }
                            String groupId = props.getProperty("groupId");
                            if (groupId != null && !groupId.isBlank()) {
                                return groupId.trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private String normalizePackagePrefix(String prefix) {
        if (prefix == null) return null;
        String p = prefix.trim();
        p = p.replace(" ", "");
        p = p.replace('-', '_');
        p = p.replaceAll("\\.+", ".");
        p = p.replaceAll("^\\.|\\.$", "");
        return p;
    }

    private Set<String> detectRootPackages(Path jar, Set<String> excludes) {
        final Set<String> roots = new HashSet<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (JarEntry e : java.util.Collections.list(jf.entries())) {
                if (e.isDirectory()) continue;
                final String name = e.getName();
                if (!name.endsWith(".class")) continue;
                if (name.endsWith("module-info.class")) continue;
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash <= 0) continue;
                String pkgPath = name.substring(0, lastSlash);
                String pkg = pkgPath.replace('/', '.');
                String root = firstTwoSegments(pkg);
                if (shouldExclude(root, excludes)) continue;
                roots.add(root);
            }
        } catch (IOException ex) {
            logger.log(Level.FINE, "Failed to scan JAR for package roots: " + jar, ex);
        }
        return roots;
    }

    private String firstTwoSegments(String pkg) {
        int firstDot = pkg.indexOf('.');
        if (firstDot < 0) {
            return pkg;
        }
        int secondDot = pkg.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return pkg.substring(0, Math.max(firstDot, pkg.length()));
        }
        return pkg.substring(0, secondDot);
    }

    private boolean shouldExclude(String root, Set<String> excludes) {
        if (root == null || root.isEmpty()) return true;
        for (String ex : excludes) {
            if (root.equals(ex) || root.startsWith(ex + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<Path> listJarFiles(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed to list JAR files in " + dir, e);
            return List.of();
        }
    }

    private boolean isUpToDate(Path out, Path in) {
        try {
            if (!Files.exists(out) || !Files.isRegularFile(out)) return false;
            FileTime inTime = Files.getLastModifiedTime(in);
            FileTime outTime = Files.getLastModifiedTime(out);
            long inSize = Files.size(in);
            long outSize = Files.size(out);
            return outSize > 0 && outTime.compareTo(inTime) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean fileLooksLikeJar(Path p) {
        try {
            return Files.isRegularFile(p) && Files.size(p) > 1024 && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
        } catch (IOException e) {
            return false;
        }
    }

    private int countJarFiles(final Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return (int) files
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))
                    .filter(n -> n.endsWith(".jar"))
                    .count();
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed to list jar files in " + directory, e);
            return 0;
        }
    }

    /**
     * NEW: Grant classpath access to other plugins declared as dependencies.
     * This is required for typed integrations with plugins like LuckPerms and RCore
     * when using a custom PluginLoader with an isolated classloader.
     *
     * We use reflection to support multiple Paper versions:
     * tries common method names on PluginClasspathBuilder, such as:
     * - addPluginDependency(String)
     * - addAccessToPlugin(String)
     * - addPlugin(String)
     *
     * If none are present, we log a SEVERE with guidance.
     */
    private void exposePluginDependencies(PluginClasspathBuilder builder, Set<String> pluginNames) {
        for (String name : pluginNames) {
            boolean added = tryInvokePluginAccess(builder, "addPluginDependency", name)
                    || tryInvokePluginAccess(builder, "addAccessToPlugin", name)
                    || tryInvokePluginAccess(builder, "addPlugin", name);
            if (added) {
                logger.log(Level.INFO, "Granted classpath access to dependency plugin: " + name);
            } else {
                logger.log(Level.SEVERE,
                        "Could not grant classpath access to dependency plugin '" + name + "' from custom loader. " +
                                "Update to a Paper version exposing PluginClasspathBuilder accessors, or adjust the loader to " +
                                "use the correct method for your server version.");
            }
        }
    }

    private boolean tryInvokePluginAccess(PluginClasspathBuilder builder, String methodName, String pluginName) {
        try {
            Method m = builder.getClass().getMethod(methodName, String.class);
            m.invoke(builder, pluginName);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable t) {
            logger.log(Level.FINE, "Invocation of " + methodName + "(" + pluginName + ") failed: " + t.getMessage(), t);
            return false;
        }
    }

    private Set<String> defaultExcludedRoots() {
        return new HashSet<>(Arrays.asList(
                "java", "javax", "jakarta",
                "sun", "com.sun", "jdk",
                "org.w3c", "org.xml", "org.ietf",
                "org.hibernate",
                "org.slf4j", "org.apache",
                "org.bukkit", "net.md_5"
        ));
    }
}