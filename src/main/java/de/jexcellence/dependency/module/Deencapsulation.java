package de.jexcellence.dependency.module;

import sun.reflect.ReflectionFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for handling Java module system deencapsulation.
 * 
 * <p>This class provides functionality to open module packages for reflection
 * access in Java 9+ environments. It's necessary for accessing internal JVM
 * functionality that may be encapsulated by the module system.</p>
 * 
 * <p>The deencapsulation process involves:</p>
 * <ul>
 *   <li>Identifying all relevant modules in the current runtime</li>
 *   <li>Opening their packages for reflection access</li>
 *   <li>Tracking opened packages for potential cleanup</li>
 * </ul>
 * 
 * <p><strong>Warning:</strong> This class uses internal JVM APIs and reflection
 * to bypass module encapsulation. It should be used with caution and may not
 * work in all environments or future Java versions.</p>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 1.0.0
 */
public final class Deencapsulation {
    
    private static final Map<Module, Set<String>> openedPackages = new HashMap<>();
    
    /**
     * Private constructor to prevent instantiation.
     */
    private Deencapsulation() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a privileged lookup instance for the specified class.
     * 
     * <p>This method uses internal reflection mechanisms to create a
     * {@link MethodHandles.Lookup} instance with full access privileges
     * for the given class.</p>
     * 
     * @param lookupClass the class to create a lookup for
     * @return a privileged lookup instance
     * @throws IllegalStateException if lookup creation fails
     */
    public static MethodHandles.Lookup createPrivilegedLookup(final Class<?> lookupClass) {
        try {
            final Constructor<?> lookupConstructor = ReflectionFactory.getReflectionFactory()
                .newConstructorForSerialization(MethodHandles.Lookup.class, 
                    MethodHandles.Lookup.class.getDeclaredConstructor(Class.class));
            return (MethodHandles.Lookup) lookupConstructor.newInstance(lookupClass);
        } catch (final ReflectiveOperationException exception) {
            final Throwable cause = exception instanceof InvocationTargetException ? 
                ((InvocationTargetException) exception).getTargetException() : exception;
            throw new IllegalStateException("Failed to create privileged lookup", cause);
        }
    }

    /**
     * Performs deencapsulation for the specified caller class.
     * 
     * <p>This method opens all packages in relevant modules to allow
     * reflection access. It processes modules from the caller's module layer,
     * the boot layer, and unnamed modules from the classloader hierarchy.</p>
     * 
     * @param callerClass the class requesting deencapsulation
     * @throws IllegalStateException if deencapsulation fails
     */
    public static void deencapsulate(final Class<?> callerClass) {
        final Set<Module> relevantModules = collectRelevantModules(callerClass);
        
        try {
            final MethodHandle openPackageMethod = createPrivilegedLookup(Module.class)
                .findVirtual(Module.class, "implAddOpens", 
                    MethodType.methodType(void.class, String.class));
            
            for (final Module module : relevantModules) {
                openModulePackages(module, openPackageMethod);
            }
        } catch (final Throwable throwable) {
            throw new IllegalStateException("Failed to perform deencapsulation", throwable);
        }
    }

    /**
     * Closes all previously opened packages.
     * 
     * <p>This method attempts to restore the original module encapsulation
     * by closing all packages that were opened during deencapsulation.</p>
     * 
     * @throws IllegalStateException if package closing fails
     */
    public static void closeOpenedPackages() {
        try {
            final MethodHandle closePackageMethod = createPrivilegedLookup(Module.class)
                .findVirtual(Module.class, "implRemoveOpens",
                    MethodType.methodType(void.class, String.class));
            
            for (final Map.Entry<Module, Set<String>> moduleEntry : openedPackages.entrySet()) {
                final Module module = moduleEntry.getKey();
                for (final String packageName : moduleEntry.getValue()) {
                    closePackageMethod.invokeExact(module, packageName);
                }
            }
            openedPackages.clear();
        } catch (final Throwable throwable) {
            throw new IllegalStateException("Failed to close opened packages", throwable);
        }
    }
    
    /**
     * Collects all modules relevant for deencapsulation.
     * 
     * @param callerClass the caller class
     * @return set of relevant modules
     */
    private static Set<Module> collectRelevantModules(final Class<?> callerClass) {
        final Set<Module> modules = new HashSet<>();
        final Module callerModule = callerClass.getModule();
        final ModuleLayer callerModuleLayer = callerModule.getLayer();
        
        if (callerModuleLayer != null) {
            modules.addAll(callerModuleLayer.modules());
        }
        modules.addAll(ModuleLayer.boot().modules());
        
        for (ClassLoader classLoader = callerClass.getClassLoader(); 
             classLoader != null; 
             classLoader = classLoader.getParent()) {
            modules.add(classLoader.getUnnamedModule());
        }
        
        return modules;
    }
    
    /**
     * Opens all packages in a module.
     * 
     * @param module the module to open packages for
     * @param openPackageMethod the method handle for opening packages
     * @throws Throwable if package opening fails
     */
    private static void openModulePackages(final Module module, final MethodHandle openPackageMethod) 
            throws Throwable {
        final Set<String> moduleOpenedPackages = openedPackages.computeIfAbsent(module, k -> new HashSet<>());
        
        for (final String packageName : module.getPackages()) {
            if (moduleOpenedPackages.add(packageName)) {
                openPackageMethod.invokeExact(module, packageName);
            }
        }
    }
}