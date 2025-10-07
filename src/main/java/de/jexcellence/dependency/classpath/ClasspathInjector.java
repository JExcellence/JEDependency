package de.jexcellence.dependency.classpath;

import de.jexcellence.dependency.module.Deencapsulation;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClasspathInjector {
    
    private static final String ADD_URL_METHOD_NAME = "addURL";
    
    private final Logger logger;
    private final List<URL> injectedUrls = new ArrayList<>();
    private static boolean deencapsulated = false;
    
    public ClasspathInjector() {
        this.logger = Logger.getLogger(this.getClass().getName());
    }
    
    public boolean injectIntoClasspath(final ClassLoader targetClassLoader, final File jarFile) {
        if (targetClassLoader == null) {
            this.logger.severe("Target classloader cannot be null");
            return false;
        }
        if (jarFile == null) {
            this.logger.severe("JAR file cannot be null");
            return false;
        }
        
        this.logger.fine("Injecting JAR into classpath: " + jarFile.getName());
        
        if (!this.validateJarFile(jarFile)) {
            return false;
        }
        
        if (!deencapsulated) {
            try {
                Deencapsulation.deencapsulate(this.getClass());
                deencapsulated = true;
                this.logger.fine("Module deencapsulation completed successfully");
            } catch (final Exception exception) {
                this.logger.log(Level.WARNING, "Module deencapsulation failed - may not work on Java 9+", exception);
            }
        }
        
        try {
            final URL jarUrl = jarFile.toURI().toURL();
            
            if (injectedUrls.contains(jarUrl)) {
                this.logger.fine("Already injected: " + jarFile.getName());
                return true;
            }
            
            final Method addUrlMethod = targetClassLoader.getClass().getDeclaredMethod(ADD_URL_METHOD_NAME, URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(targetClassLoader, jarUrl);
            
            injectedUrls.add(jarUrl);
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
    
    public List<URL> getInjectedUrls() {
        return new ArrayList<>(injectedUrls);
    }
    
    public boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}