package de.jexcellence.dependency.remapper;

import de.jexcellence.dependency.classpath.ClasspathInjector;
import de.jexcellence.dependency.dependency.DependencyDownloader;
import de.jexcellence.dependency.model.Dependency;
import de.jexcellence.dependency.module.Deencapsulation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class RemappingDependencyManager {
    
    private static final Logger LOGGER = Logger.getLogger(RemappingDependencyManager.class.getName());
    
    private final DependencyDownloader downloader;
    private final ClasspathInjector injector;
    private final PackageRemapper remapper;
    private final Path librariesDirectory;
    private final Path remappedDirectory;
    private final List<Dependency> dependencies;
    private final Map<String, String> packageRelocations;
    
    public RemappingDependencyManager(Path dataDirectory) {
        this.librariesDirectory = dataDirectory.resolve("libraries");
        this.remappedDirectory = dataDirectory.resolve("libraries/remapped");
        this.downloader = new DependencyDownloader();
        this.injector = new ClasspathInjector();
        this.remapper = new PackageRemapper();
        this.dependencies = new ArrayList<>();
        this.packageRelocations = new HashMap<>();
        
        try {
            Files.createDirectories(librariesDirectory);
            Files.createDirectories(remappedDirectory);
        } catch (Exception e) {
            LOGGER.severe("Failed to create directories: " + e.getMessage());
        }
    }
    
    public RemappingDependencyManager addRepository(String repositoryUrl) {
        downloader.addRepository(repositoryUrl);
        return this;
    }
    
    public RemappingDependencyManager addDependency(String groupId, String artifactId, String version) {
        dependencies.add(new Dependency(groupId, artifactId, version));
        return this;
    }
    
    public RemappingDependencyManager addDependency(String groupId, String artifactId, String version, String classifier) {
        dependencies.add(new Dependency(groupId, artifactId, version, classifier, null));
        return this;
    }
    
    public RemappingDependencyManager addDependency(String dependencyString) {
        dependencies.add(Dependency.parse(dependencyString));
        return this;
    }
    
    public RemappingDependencyManager relocate(String originalPackage, String relocatedPackage) {
        packageRelocations.put(originalPackage, relocatedPackage);
        return this;
    }
    
    public void loadAll(ClassLoader targetClassLoader) throws Exception {
        LOGGER.info("Loading " + dependencies.size() + " dependencies...");
        
        Deencapsulation.deencapsulate(this.getClass());
        
        List<Path> downloadedJars = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            File jarFile = downloader.downloadDependency(dependency.toString(), librariesDirectory.toFile());
            if (jarFile != null && jarFile.exists()) {
                downloadedJars.add(jarFile.toPath());
            } else {
                LOGGER.warning("Failed to download: " + dependency);
            }
        }
        
        List<Path> jarsToInject = new ArrayList<>();
        if (!packageRelocations.isEmpty()) {
            remapper.addMappings(packageRelocations);
            for (int i = 0; i < downloadedJars.size(); i++) {
                Path originalJar = downloadedJars.get(i);
                Path remappedJar = remappedDirectory.resolve(dependencies.get(i).getFileName());
                
                if (!Files.exists(remappedJar)) {
                    LOGGER.info("Remapping: " + originalJar.getFileName());
                    remapper.remap(originalJar, remappedJar);
                } else {
                    LOGGER.fine("Using cached remapped JAR: " + remappedJar.getFileName());
                }
                jarsToInject.add(remappedJar);
            }
        } else {
            jarsToInject = downloadedJars;
        }
        
        for (Path jarPath : jarsToInject) {
            injector.injectIntoClasspath(targetClassLoader, jarPath.toFile());
        }
        
        LOGGER.info("Successfully loaded all dependencies!");
    }
    
    public Path download(Dependency dependency) throws Exception {
        File jarFile = downloader.downloadDependency(dependency.toString(), librariesDirectory.toFile());
        return jarFile != null ? jarFile.toPath() : null;
    }
    
    public void inject(Path jarPath, ClassLoader targetClassLoader) throws Exception {
        injector.injectIntoClasspath(targetClassLoader, jarPath.toFile());
    }
    
    public void remap(Path inputJar, Path outputJar) throws Exception {
        if (!packageRelocations.isEmpty()) {
            remapper.addMappings(packageRelocations);
        }
        remapper.remap(inputJar, outputJar);
    }
    
    public boolean isClassAvailable(String className) {
        return injector.isClassAvailable(className);
    }
    
    public Path getLibrariesDirectory() {
        return librariesDirectory;
    }
    
    public Path getRemappedDirectory() {
        return remappedDirectory;
    }
    
    public static class Builder {
        private final Path dataDirectory;
        private final List<String> repositories = new ArrayList<>();
        private final List<Dependency> dependencies = new ArrayList<>();
        private final Map<String, String> relocations = new HashMap<>();
        
        public Builder(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
        }
        
        public Builder repository(String url) {
            repositories.add(url);
            return this;
        }
        
        public Builder dependency(String groupId, String artifactId, String version) {
            dependencies.add(new Dependency(groupId, artifactId, version));
            return this;
        }
        
        public Builder dependency(String dependencyString) {
            dependencies.add(Dependency.parse(dependencyString));
            return this;
        }
        
        public Builder relocate(String originalPackage, String relocatedPackage) {
            relocations.put(originalPackage, relocatedPackage);
            return this;
        }
        
        public RemappingDependencyManager build() {
            RemappingDependencyManager manager = new RemappingDependencyManager(dataDirectory);
            for (String repo : repositories) {
                manager.addRepository(repo);
            }
            for (Dependency dep : dependencies) {
                manager.dependencies.add(dep);
            }
            manager.packageRelocations.putAll(relocations);
            return manager;
        }
    }
}
