package de.jexcellence.dependency.dependency;

import de.jexcellence.dependency.classpath.ClasspathInjector;
import de.jexcellence.dependency.module.Deencapsulation;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main dependency management system for JEDependency.
 * 
 * <p>This class provides functionality to automatically download and inject
 * dependencies into the classpath at runtime. It supports loading dependencies
 * from both YAML configuration files and programmatically provided GAV coordinates.</p>
 * 
 * <p>The system works by:</p>
 * <ul>
 *   <li>Creating a libraries directory in the plugin's data folder</li>
 *   <li>Downloading missing dependencies from Maven repositories</li>
 *   <li>Injecting the downloaded JARs into the plugin's classloader</li>
 * </ul>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 1.0.0
 */
public final class DependencyManager {
    
    private final Logger logger;
    private final JavaPlugin plugin;
    private final Class<?> anchorClass;
    private final DependencyDownloader dependencyDownloader;
    private final ClasspathInjector classpathInjector;
    private final YamlDependencyLoader yamlLoader;
    
    /**
     * Creates a new dependency manager instance.
     * 
     * @param plugin the plugin instance that owns this dependency manager
     * @param anchorClass the class to use as anchor for resource loading and JAR location detection
     * @throws IllegalArgumentException if plugin or anchorClass is null
     */
    public DependencyManager(final JavaPlugin plugin, final Class<?> anchorClass) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (anchorClass == null) {
            throw new IllegalArgumentException("Anchor class cannot be null");
        }
        
        this.plugin = plugin;
        this.anchorClass = anchorClass;
        this.logger = Logger.getLogger(this.getClass().getName());
        this.dependencyDownloader = new DependencyDownloader();
        this.classpathInjector = new ClasspathInjector();
        this.yamlLoader = new YamlDependencyLoader();
    }
    
    /**
     * Initializes the dependency system and loads all specified dependencies.
     * 
     * <p>This method will:</p>
     * <ul>
     *   <li>Set up the libraries directory</li>
     *   <li>Perform module deencapsulation for Java 9+ compatibility</li>
     *   <li>Load dependencies from YAML configuration if present</li>
     *   <li>Download and inject all dependencies into the plugin's classloader</li>
     * </ul>
     * 
     * @param additionalDependencies optional array of additional GAV coordinates to load
     */
    public void initialize(final String[] additionalDependencies) {
        this.logger.info("Initializing dependency management system for plugin: " + this.plugin.getName());
        
        final File pluginJarFile = this.determinePluginJarLocation();
        if (pluginJarFile == null) {
            this.logger.severe("Failed to determine plugin JAR location - dependency loading aborted");
            return;
        }
        
        final File librariesDirectory = this.setupLibrariesDirectory();
        final ClassLoader targetClassLoader = this.anchorClass.getClassLoader();
        
        this.performModuleDeencapsulation();
        
        final List<String> allDependencies = this.collectAllDependencies(additionalDependencies);
        
        if (allDependencies.isEmpty()) {
            this.logger.info("No dependencies to process");
            return;
        }
        
        this.logger.info("Processing " + allDependencies.size() + " dependencies...");
        final DependencyProcessingResult result = this.processDependencies(allDependencies, librariesDirectory, targetClassLoader);
        
        this.logProcessingSummary(result, librariesDirectory);
        this.logger.info("Dependency management system initialization completed");
    }
    
    /**
     * Determines the location of the plugin JAR file.
     * 
     * @return the plugin JAR file, or null if it cannot be determined
     */
    private File determinePluginJarLocation() {
        try {
            final CodeSource codeSource = this.anchorClass.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            final URL jarLocation = codeSource.getLocation();
            return new File(jarLocation.toURI());
        } catch (final Exception exception) {
            this.logger.log(Level.WARNING, "Failed to determine plugin JAR location", exception);
            return null;
        }
    }
    
    /**
     * Sets up the libraries directory where dependencies will be stored.
     * 
     * @return the libraries directory
     */
    private File setupLibrariesDirectory() {
        final File pluginDataFolder = this.plugin.getDataFolder();
        if (!pluginDataFolder.exists()) {
            pluginDataFolder.mkdirs();
        }
        
        final File librariesDirectory = new File(pluginDataFolder, "libraries");
        if (!librariesDirectory.exists()) {
            librariesDirectory.mkdirs();
        }
        
        return librariesDirectory;
    }
    
    /**
     * Performs module deencapsulation for Java 9+ compatibility.
     */
    private void performModuleDeencapsulation() {
        try {
            Deencapsulation.deencapsulate(this.getClass());
            this.logger.fine("Module deencapsulation completed successfully");
        } catch (final Exception exception) {
            this.logger.log(Level.WARNING, "Module deencapsulation failed", exception);
        }
    }
    
    /**
     * Collects all dependencies from both YAML configuration and additional parameters.
     * 
     * @param additionalDependencies optional additional dependencies
     * @return list of all dependency GAV coordinates
     */
    private List<String> collectAllDependencies(final String[] additionalDependencies) {
        final List<String> allDependencies = new ArrayList<>();
        
        final String[] yamlDependencies = this.yamlLoader.loadDependenciesFromYaml(this.anchorClass);
        if (yamlDependencies != null) {
            this.logger.info("Loaded " + yamlDependencies.length + " dependencies from YAML configuration");
            for (final String dependency : yamlDependencies) {
                allDependencies.add(dependency);
                this.logger.fine("YAML dependency: " + dependency);
            }
        } else {
            this.logger.fine("No YAML configuration found or no dependencies specified");
        }
        
        if (additionalDependencies != null) {
            this.logger.info("Adding " + additionalDependencies.length + " additional dependencies");
            for (final String dependency : additionalDependencies) {
                allDependencies.add(dependency);
                this.logger.fine("Additional dependency: " + dependency);
            }
        }
        
        return allDependencies;
    }
    
    /**
     * Processes all dependencies by downloading and injecting them.
     * 
     * @param dependencies list of dependency GAV coordinates
     * @param librariesDirectory directory to store downloaded dependencies
     * @param targetClassLoader classloader to inject dependencies into
     * @return processing result summary
     */
    private DependencyProcessingResult processDependencies(final List<String> dependencies, 
                                                         final File librariesDirectory, 
                                                         final ClassLoader targetClassLoader) {
        final DependencyProcessingResult result = new DependencyProcessingResult();
        
        for (final String dependencyCoordinate : dependencies) {
            result.totalDependencies++;
            
            final File downloadedJarFile = this.dependencyDownloader.downloadDependency(
                dependencyCoordinate, librariesDirectory);
            
            if (downloadedJarFile != null && downloadedJarFile.isFile()) {
                result.successfulDownloads++;
                
                final boolean injectionSuccessful = this.classpathInjector.injectIntoClasspath(
                    targetClassLoader, downloadedJarFile);
                
                if (injectionSuccessful) {
                    result.successfulInjections++;
                    result.processedDependencies.add(dependencyCoordinate);
                } else {
                    result.failedInjections++;
                    result.failedDependencies.add(dependencyCoordinate);
                }
            } else {
                result.failedDownloads++;
                result.failedDependencies.add(dependencyCoordinate);
            }
        }
        
        return result;
    }
    
    /**
     * Logs a summary of the dependency processing results.
     * 
     * @param result the processing result
     * @param librariesDirectory the libraries directory
     */
    private void logProcessingSummary(final DependencyProcessingResult result, final File librariesDirectory) {
        this.logger.info("Dependency processing summary:");
        this.logger.info("  Total: " + result.totalDependencies + 
                         " | Downloaded: " + result.successfulDownloads + 
                         " | Injected: " + result.successfulInjections + 
                         " | Failed: " + (result.failedDownloads + result.failedInjections));
        
        if (!result.failedDependencies.isEmpty()) {
            this.logger.warning("Failed dependencies: " + String.join(", ", result.failedDependencies));
        }
        
        // Count total libraries in directory
        final File[] libraryFiles = librariesDirectory.listFiles();
        int totalLibraries = 0;
        if (libraryFiles != null) {
            for (final File libraryFile : libraryFiles) {
                if (libraryFile.isFile() && libraryFile.getName().endsWith(".jar")) {
                    totalLibraries++;
                }
            }
        }
        this.logger.info("Libraries directory contains " + totalLibraries + " JAR files");
        this.logger.info("Dependencies are isolated to this plugin's classloader");
    }
    
    /**
     * Internal class to track dependency processing results.
     */
    private static final class DependencyProcessingResult {
        public int totalDependencies = 0;
        public int successfulDownloads = 0;
        public int successfulInjections = 0;
        public int failedDownloads = 0;
        public int failedInjections = 0;
        public final List<String> processedDependencies = new ArrayList<>();
        public final List<String> failedDependencies = new ArrayList<>();
    }
}