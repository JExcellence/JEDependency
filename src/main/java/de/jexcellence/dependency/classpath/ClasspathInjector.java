package de.jexcellence.dependency.classpath;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles injection of JAR files into the runtime classpath.
 * 
 * <p>This class provides functionality to dynamically add JAR files to the
 * classpath of a running Java application. It uses reflection to access the
 * internal {@code addURL} method of {@code URLClassLoader}.</p>
 * 
 * <p>The injection process involves:</p>
 * <ul>
 *   <li>Converting the JAR file to a URL</li>
 *   <li>Accessing the URLClassLoader's addURL method via reflection</li>
 *   <li>Invoking the method to add the JAR to the classpath</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This approach relies on internal JVM mechanisms
 * and may not work in all environments, particularly with newer Java versions
 * or custom classloader implementations.</p>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class ClasspathInjector {
    
    private static final String ADD_URL_METHOD_NAME = "addURL";
    
    private final Logger logger;
    
    /**
     * Creates a new classpath injector.
     */
    public ClasspathInjector() {
        this.logger = Logger.getLogger(this.getClass().getName());
    }
    
    /**
     * Injects a JAR file into the specified classloader's classpath.
     * 
     * <p>This method attempts to add the given JAR file to the classpath
     * of the specified classloader. The operation is performed using
     * reflection to access internal classloader methods.</p>
     * 
     * @param targetClassLoader the classloader to inject the JAR into
     * @param jarFile the JAR file to inject
     * @return true if injection was successful, false otherwise
     * @throws IllegalArgumentException if targetClassLoader or jarFile is null
     */
    public boolean injectIntoClasspath(final ClassLoader targetClassLoader, final File jarFile) {
        if (targetClassLoader == null) {
            throw new IllegalArgumentException("Target classloader cannot be null");
        }
        if (jarFile == null) {
            throw new IllegalArgumentException("JAR file cannot be null");
        }
        
        this.logger.fine("Injecting JAR into classpath: " + jarFile.getName());
        
        if (!this.validateJarFile(jarFile)) {
            return false;
        }
        
        if (!this.isCompatibleClassLoader(targetClassLoader)) {
            this.logger.severe("Incompatible classloader type: " + targetClassLoader.getClass().getName());
            return false;
        }
        
        try {
            final URL jarUrl = this.convertFileToUrl(jarFile);
            final URLClassLoader urlClassLoader = (URLClassLoader) targetClassLoader;
            
            this.performInjection(urlClassLoader, jarUrl);
            
            this.logger.fine("Successfully injected JAR: " + jarFile.getName());
            return true;
            
        } catch (final Exception exception) {
            this.logger.log(Level.SEVERE, "Failed to inject JAR: " + jarFile.getName(), exception);
            return false;
        }
    }
    
    /**
     * Validates that the JAR file exists and is readable.
     * 
     * @param jarFile the JAR file to validate
     * @return true if the file is valid
     */
    private boolean validateJarFile(final File jarFile) {
        if (!jarFile.exists()) {
            this.logger.severe("JAR file does not exist: " + jarFile.getAbsolutePath());
            return false;
        }
        
        if (!jarFile.isFile()) {
            this.logger.severe("Path is not a file: " + jarFile.getAbsolutePath());
            return false;
        }
        
        if (!jarFile.canRead()) {
            this.logger.severe("JAR file is not readable: " + jarFile.getAbsolutePath());
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if the classloader is compatible with URL injection.
     * 
     * @param classLoader the classloader to check
     * @return true if the classloader is compatible
     */
    private boolean isCompatibleClassLoader(final ClassLoader classLoader) {
        return classLoader instanceof URLClassLoader;
    }
    
    /**
     * Converts a file to a URL.
     * 
     * @param jarFile the file to convert
     * @return the URL representation of the file
     * @throws Exception if conversion fails
     */
    private URL convertFileToUrl(final File jarFile) throws Exception {
        return jarFile.toURI().toURL();
    }
    
    /**
     * Performs the actual injection using reflection.
     * 
     * @param urlClassLoader the target classloader
     * @param jarUrl the URL of the JAR to inject
     * @throws Exception if injection fails
     */
    private void performInjection(final URLClassLoader urlClassLoader, final URL jarUrl) throws Exception {
        final Method addUrlMethod = this.getAddUrlMethod();
        addUrlMethod.setAccessible(true);
        addUrlMethod.invoke(urlClassLoader, jarUrl);
    }
    
    /**
     * Gets the addURL method from URLClassLoader using reflection.
     * 
     * @return the addURL method
     * @throws Exception if method cannot be found
     */
    private Method getAddUrlMethod() throws Exception {
        return URLClassLoader.class.getDeclaredMethod(ADD_URL_METHOD_NAME, URL.class);
    }
}