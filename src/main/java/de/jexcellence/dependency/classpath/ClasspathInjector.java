package de.jexcellence.dependency.classpath;

import de.jexcellence.dependency.module.Deencapsulation;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClasspathInjector {

    private static final Logger LOGGER = Logger.getLogger(ClasspathInjector.class.getName());
    private static final String ADD_URL_METHOD_NAME = "addURL";

    private final Set<URL> injectedUrls = new LinkedHashSet<>();
    private static boolean deencapsulated;

    public ClasspathInjector() {
    }

    public boolean injectIntoClasspath(final @NotNull ClassLoader targetClassLoader, final @NotNull File jarFile) {
        Objects.requireNonNull(targetClassLoader, "targetClassLoader");
        Objects.requireNonNull(jarFile, "jarFile");

        if (!validateJarFile(jarFile)) {
            return false;
        }

        LOGGER.fine(() -> "Injecting JAR into classpath: " + jarFile.getAbsolutePath());

        ensureDeencapsulated();

        try {
            final URL jarUrl = jarFile.toURI().toURL();
            if (!this.injectedUrls.add(jarUrl)) {
                LOGGER.fine(() -> "JAR already injected: " + jarFile.getName());
                return true;
            }

            final Method addUrlMethod = targetClassLoader.getClass().getDeclaredMethod(ADD_URL_METHOD_NAME, URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(targetClassLoader, jarUrl);
            LOGGER.fine(() -> "Successfully injected JAR: " + jarFile.getName());
            return true;
        } catch (final Exception exception) {
            LOGGER.log(Level.SEVERE, "Failed to inject JAR: " + jarFile.getName(), exception);
            return false;
        }
    }

    public @NotNull Set<URL> getInjectedUrls() {
        return Set.copyOf(this.injectedUrls);
    }

    public boolean isClassAvailable(final @NotNull String className) {
        Objects.requireNonNull(className, "className");
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    private void ensureDeencapsulated() {
        if (deencapsulated) {
            return;
        }
        try {
            Deencapsulation.deencapsulate(this.getClass());
            deencapsulated = true;
            LOGGER.fine("Module deencapsulation completed successfully");
        } catch (final Exception exception) {
            LOGGER.log(Level.FINE, "Module deencapsulation failed - may not work on Java 9+", exception);
        }
    }

    private boolean validateJarFile(final @NotNull File jarFile) {
        if (!jarFile.exists()) {
            LOGGER.severe("JAR file does not exist: " + jarFile.getAbsolutePath());
            return false;
        }
        if (!jarFile.isFile()) {
            LOGGER.severe("Path is not a file: " + jarFile.getAbsolutePath());
            return false;
        }
        if (!jarFile.canRead()) {
            LOGGER.severe("JAR file is not readable: " + jarFile.getAbsolutePath());
            return false;
        }
        return true;
    }
}
