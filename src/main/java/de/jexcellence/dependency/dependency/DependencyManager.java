package de.jexcellence.dependency.dependency;

import de.jexcellence.dependency.classpath.ClasspathInjector;
import de.jexcellence.dependency.module.Deencapsulation;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Main dependency management system for JEDependency.
 */
public final class DependencyManager {

    private static final Logger LOGGER = Logger.getLogger(DependencyManager.class.getName());
    private static final String LIBRARIES_DIRECTORY = "libraries";

    private final JavaPlugin plugin;
    private final Class<?> anchorClass;
    private final DependencyDownloader dependencyDownloader;
    private final ClasspathInjector classpathInjector;
    private final YamlDependencyLoader yamlLoader;

    public DependencyManager(final @NotNull JavaPlugin plugin, final @NotNull Class<?> anchorClass) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.anchorClass = Objects.requireNonNull(anchorClass, "anchorClass");
        this.dependencyDownloader = new DependencyDownloader();
        this.classpathInjector = new ClasspathInjector();
        this.yamlLoader = new YamlDependencyLoader();
    }

    public void initialize(final @Nullable String[] additionalDependencies) {
        LOGGER.info(() -> "Initializing dependency management for plugin: " + this.plugin.getName());

        final File pluginJarFile = this.determinePluginJarLocation();
        if (pluginJarFile == null) {
            LOGGER.severe("Failed to determine plugin JAR location - dependency loading aborted");
            return;
        }

        final File librariesDirectory = this.setupLibrariesDirectory();
        final ClassLoader targetClassLoader = this.anchorClass.getClassLoader();

        this.performModuleDeencapsulation();

        final List<String> allDependencies = this.collectAllDependencies(additionalDependencies);

        if (allDependencies.isEmpty()) {
            LOGGER.info("No dependencies to process");
            return;
        }

        LOGGER.info(() -> "Processing " + allDependencies.size() + " dependencies...");
        final DependencyProcessingResult result = this.processDependencies(
                allDependencies,
                librariesDirectory,
                targetClassLoader
        );

        this.logProcessingSummary(result, librariesDirectory.toPath());
        LOGGER.info("Dependency management system initialization completed");
    }

    private @Nullable File determinePluginJarLocation() {
        try {
            final CodeSource codeSource = this.anchorClass.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            final URL jarLocation = codeSource.getLocation();
            return new File(jarLocation.toURI());
        } catch (final URISyntaxException exception) {
            LOGGER.log(Level.WARNING, "Failed to resolve plugin JAR location", exception);
            return null;
        }
    }

    private @NotNull File setupLibrariesDirectory() {
        final File pluginDataFolder = this.plugin.getDataFolder();
        if (!pluginDataFolder.exists() && !pluginDataFolder.mkdirs()) {
            LOGGER.warning("Could not create plugin data folder: " + pluginDataFolder);
        }

        final File librariesDirectory = new File(pluginDataFolder, LIBRARIES_DIRECTORY);
        if (!librariesDirectory.exists() && !librariesDirectory.mkdirs()) {
            LOGGER.warning("Could not create libraries directory: " + librariesDirectory);
        }

        return librariesDirectory;
    }

    private void performModuleDeencapsulation() {
        try {
            Deencapsulation.deencapsulate(this.getClass());
            LOGGER.fine("Module deencapsulation completed successfully");
        } catch (final Exception exception) {
            LOGGER.log(Level.FINE, "Module deencapsulation failed", exception);
        }
    }

    private @NotNull List<String> collectAllDependencies(final @Nullable String[] additionalDependencies) {
        final List<String> dependencies = new ArrayList<>();

        final String[] yamlDependencies = this.yamlLoader.loadDependenciesFromYaml(this.anchorClass);
        if (yamlDependencies != null && yamlDependencies.length > 0) {
            Collections.addAll(dependencies, yamlDependencies);
            LOGGER.info(() -> "Loaded " + yamlDependencies.length + " dependencies from YAML configuration");
        } else {
            LOGGER.fine("No YAML configuration found or it did not define dependencies");
        }

        if (additionalDependencies != null && additionalDependencies.length > 0) {
            Collections.addAll(dependencies, additionalDependencies);
            LOGGER.info(() -> "Added " + additionalDependencies.length + " additional dependencies");
        }

        return dependencies;
    }

    private @NotNull DependencyProcessingResult processDependencies(
            final @NotNull List<String> dependencies,
            final @NotNull File librariesDirectory,
            final @NotNull ClassLoader targetClassLoader
    ) {
        final DependencyProcessingResult result = new DependencyProcessingResult();

        for (final String dependencyCoordinate : dependencies) {
            if (dependencyCoordinate == null || dependencyCoordinate.isBlank()) {
                LOGGER.warning("Skipping empty dependency coordinate");
                result.recordInvalid();
                continue;
            }

            result.recordAttempt();
            final File downloadedJarFile = this.dependencyDownloader.downloadDependency(
                    dependencyCoordinate,
                    librariesDirectory
            );

            if (downloadedJarFile != null && downloadedJarFile.isFile()) {
                result.recordDownloadSuccess();
                final boolean injected = this.classpathInjector.injectIntoClasspath(targetClassLoader, downloadedJarFile);
                if (injected) {
                    result.recordInjectionSuccess();
                } else {
                    result.recordInjectionFailure(dependencyCoordinate);
                }
            } else {
                result.recordDownloadFailure(dependencyCoordinate);
            }
        }

        return result;
    }

    private void logProcessingSummary(final @NotNull DependencyProcessingResult result, final @NotNull Path librariesDirectory) {
        LOGGER.info(() -> String.format(
                "Dependency processing summary: total=%d, downloaded=%d, injected=%d, failed=%d, invalid=%d",
                result.getTotalDependencies(),
                result.getSuccessfulDownloads(),
                result.getSuccessfulInjections(),
                result.getFailedDownloads() + result.getFailedInjections(),
                result.getInvalidCoordinates()
        ));

        if (!result.getFailedDependencies().isEmpty()) {
            LOGGER.warning(() -> "Failed dependencies: " + String.join(", ", result.getFailedDependencies()));
        }

        try (Stream<Path> files = Files.list(librariesDirectory)) {
            final long jarCount = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .count();
            LOGGER.info(() -> "Libraries directory contains " + jarCount + " JAR files");
        } catch (final Exception exception) {
            LOGGER.log(Level.FINE, "Failed to inspect libraries directory", exception);
        }

        LOGGER.info("Dependencies are isolated to this plugin's classloader");
    }

    private static final class DependencyProcessingResult {
        private int totalDependencies;
        private int successfulDownloads;
        private int successfulInjections;
        private int failedDownloads;
        private int failedInjections;
        private int invalidCoordinates;
        private final List<String> failedDependencies = new ArrayList<>();

        void recordAttempt() {
            this.totalDependencies++;
        }

        void recordInvalid() {
            this.invalidCoordinates++;
        }

        void recordDownloadSuccess() {
            this.successfulDownloads++;
        }

        void recordInjectionSuccess() {
            this.successfulInjections++;
        }

        void recordInjectionFailure(final @NotNull String coordinate) {
            this.failedInjections++;
            this.failedDependencies.add(coordinate);
        }

        void recordDownloadFailure(final @NotNull String coordinate) {
            this.failedDownloads++;
            this.failedDependencies.add(coordinate);
        }

        int getTotalDependencies() {
            return this.totalDependencies;
        }

        int getSuccessfulDownloads() {
            return this.successfulDownloads;
        }

        int getSuccessfulInjections() {
            return this.successfulInjections;
        }

        int getFailedDownloads() {
            return this.failedDownloads;
        }

        int getFailedInjections() {
            return this.failedInjections;
        }

        int getInvalidCoordinates() {
            return this.invalidCoordinates;
        }

        @NotNull List<String> getFailedDependencies() {
            return this.failedDependencies;
        }
    }
}
