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
 *   <li>Injecting the downloaded JARs into the current classpath</li>
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
    private final ClasspathInjector    classpathInjector;
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
     *   <li>Download and inject all dependencies into the classpath</li>
     * </ul>
     * 
     * @param additionalDependencies optional array of additional GAV coordinates to load
     */
    public void initialize(final String[] additionalDependencies) {
        this.logger.info("Initializing dependency management system");
        
        final File pluginJarFile = this.determinePluginJarLocation();
        if (pluginJarFile == null) {
            this.logger.severe("Failed to determine plugin JAR location - dependency loading aborted");
            return;
        }
        
        this.logger.info("Plugin JAR located at: " + pluginJarFile.getAbsolutePath());
        
        final File librariesDirectory = this.setupLibrariesDirectory();
        final ClassLoader targetClassLoader = this.anchorClass.getClassLoader();
        
        this.performModuleDeencapsulation();
        
        final List<String> allDependencies = this.collectAllDependencies(additionalDependencies);
        this.logger.info("Found " + allDependencies.size() + " dependencies to process");
        
        this.processDependencies(allDependencies, librariesDirectory, targetClassLoader);
        
        this.logLibrariesDirectoryContents(librariesDirectory);
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
            this.logger.info("Created libraries directory: " + librariesDirectory.getAbsolutePath());
        } else {
            this.logger.fine("Using existing libraries directory: " + librariesDirectory.getAbsolutePath());
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
     */
    private void processDependencies(final List<String> dependencies, 
                                   final File librariesDirectory, 
                                   final ClassLoader targetClassLoader) {
        for (final String dependencyCoordinate : dependencies) {
            this.logger.info("Processing dependency: " + dependencyCoordinate);
            
            final File downloadedJarFile = this.dependencyDownloader.downloadDependency(
                dependencyCoordinate, librariesDirectory);
            
            if (downloadedJarFile != null && downloadedJarFile.isFile()) {
                this.logger.info("Successfully downloaded: " + downloadedJarFile.getName());
                
                final boolean injectionSuccessful = this.classpathInjector.injectIntoClasspath(
                    targetClassLoader, downloadedJarFile);
                
                if (injectionSuccessful) {
                    this.logger.info("Successfully injected into classpath: " + downloadedJarFile.getName());
                } else {
                    this.logger.warning("Failed to inject into classpath: " + downloadedJarFile.getName());
                }
            } else {
                this.logger.severe("Failed to download dependency: " + dependencyCoordinate);
            }
        }
    }
    
    /**
     * Logs the contents of the libraries directory for debugging purposes.
     * 
     * @param librariesDirectory the libraries directory to inspect
     */
    private void logLibrariesDirectoryContents(final File librariesDirectory) {
        this.logger.info("=== Libraries Directory Contents ===");
        final File[] libraryFiles = librariesDirectory.listFiles();
        if (libraryFiles != null) {
            for (final File libraryFile : libraryFiles) {
                if (libraryFile.isFile() && libraryFile.getName().endsWith(".jar")) {
                    this.logger.info("Library: " + libraryFile.getName() + 
                                   " (" + libraryFile.length() + " bytes)");
                }
            }
        }
        this.logger.info("=== End Libraries Directory Contents ===");
    }
}