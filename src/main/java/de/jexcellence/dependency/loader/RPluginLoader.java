package de.jexcellence.dependency.loader;

import de.jexcellence.dependency.dependency.DependencyDownloader;
import de.jexcellence.dependency.dependency.YamlDependencyLoader;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Enhanced Paper plugin loader implementation for dynamically loading external dependencies and libraries.
 * <p>
 * This class is responsible for:
 * <ul>
 * <li>Ensuring the existence of a {@code libs} directory within the plugin's data directory</li>
 * <li>Initializing and downloading dependencies using the JEDependency system</li>
 * <li>Adding all JAR files found in the {@code libs} directory to the plugin's classpath</li>
 * <li>Proper resource cleanup and error handling</li>
 * </ul>
 * <p>
 * This enables plugins to manage and load their own dependencies at runtime with enhanced reliability,
 * supporting modular and extensible plugin development while keeping the main JAR size minimal.
 * </p>
 * 
 * @version 2.0.0
 * @since TBD
 * @author JExcellence
 */
@SuppressWarnings({"unused", "UnstableApiUsage"})
public class RPluginLoader implements PluginLoader {
    
    /**
     * Logger instance for informational and debug messages.
     */
    private final Logger logger;
    
    /**
     * JEDependency downloader instance.
     */
    private final DependencyDownloader dependencyDownloader;
    
    /**
     * YAML dependency loader for loading dependencies from configuration.
     */
    private final YamlDependencyLoader yamlLoader;
    
    /**
     * Constructs a new {@code RPluginLoader} and initializes the logger and dependency components.
     */
    public RPluginLoader() {
        this.logger = Logger.getLogger(RPluginLoader.class.getName());
        this.dependencyDownloader = new DependencyDownloader();
        this.yamlLoader = new YamlDependencyLoader();
    }
    
    /**
     * Configures the plugin classpath by ensuring the {@code libs} directory exists,
     * initializing dependencies using the JEDependency system, and adding all JAR files
     * in the directory to the classpath.
     * <p>
     * The method performs the following steps:
     * <ol>
     * <li>Creates the {@code libs} directory if it does not exist</li>
     * <li>Loads dependencies from YAML configuration if present</li>
     * <li>Downloads missing dependencies using JEDependency downloader</li>
     * <li>Adds each JAR file in the {@code libs} directory to the classpath using {@link JarLibrary}</li>
     * </ol>
     * If any step fails, detailed error information is logged and the process continues where possible.
     * </p>
     * 
     * @param classpathBuilder the classpath builder used to add libraries to the plugin's classpath
     * @throws RuntimeException if the {@code libs} directory cannot be created
     */
    @Override
    public void classloader(final PluginClasspathBuilder classpathBuilder) {
        System.setProperty("paper.plugin.loader.active", "true");
        
        final Path libsDirectory = Path.of(classpathBuilder.getContext().getDataDirectory().toAbsolutePath().toString(), "libs");
        
        try {
            logger.log(Level.INFO, "Initializing plugin classpath with JEDependency system...");
			
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
			
            loadJarFiles(classpathBuilder, libsDirectory);
            
        } catch (final IOException exception) {
            throw new RuntimeException("Failed to create libs directory: " + libsDirectory, exception);
        }
    }
    
    /**
     * Initializes dependencies using the JEDependency system.
     * This method loads dependencies from YAML configuration and downloads them if needed.
     * 
     * @param libsDirectory the directory where dependencies should be stored
     */
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
    
    /**
     * Loads all JAR files from the specified directory into the classpath.
     * 
     * @param classpathBuilder the classpath builder to add libraries to
     * @param libsDirectory the directory containing JAR files
     */
    private void loadJarFiles(final PluginClasspathBuilder classpathBuilder, final Path libsDirectory) {
        try (final Stream<Path> jarFiles = Files.walk(libsDirectory, 1)) {
            final long jarCount = jarFiles
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                .peek(jarPath -> {
                    try {
                        classpathBuilder.addLibrary(new JarLibrary(jarPath));
                        logger.log(Level.FINE, "Added JAR to classpath: " + jarPath.getFileName());
                    } catch (final Exception exception) {
                        logger.log(Level.WARNING, "Failed to add JAR to classpath: " + jarPath, exception);
                    }
                })
                .count();
            
            logger.info("Successfully loaded " + jarCount + " JAR files into classpath");
        } catch (final Exception exception) {
            logger.log(Level.SEVERE, "Failed to load libraries from libs directory: " + libsDirectory, exception);
        }
    }
}