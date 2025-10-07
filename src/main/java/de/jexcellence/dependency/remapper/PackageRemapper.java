package de.jexcellence.dependency.remapper;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

public final class PackageRemapper {
    
    private static final Logger LOGGER = Logger.getLogger(PackageRemapper.class.getName());
    
    private final Map<String, String> packageMappings;
    
    public PackageRemapper() {
        this.packageMappings = new HashMap<>();
    }
    
    public void addMapping(String originalPackage, String remappedPackage) {
        String originalPath = originalPackage.replace('.', '/');
        String remappedPath = remappedPackage.replace('.', '/');
        packageMappings.put(originalPath, remappedPath);
    }
    
    public void addMappings(Map<String, String> mappings) {
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            addMapping(entry.getKey(), entry.getValue());
        }
    }
    
    public void remap(Path inputJar, Path outputJar) throws IOException {
        if (packageMappings.isEmpty()) {
            Files.copy(inputJar, outputJar);
            return;
        }
        
        LOGGER.info("Remapping JAR: " + inputJar.getFileName());
        
        CustomRemapper remapper = new CustomRemapper(packageMappings);
        
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(inputJar));
             JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar))) {
            
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                
                if (entryName.endsWith(".class")) {
                    byte[] classBytes = readAllBytes(jis);
                    byte[] remappedBytes = remapClass(classBytes, remapper);
                    String remappedName = remapper.map(entryName.substring(0, entryName.length() - 6)) + ".class";
                    
                    JarEntry newEntry = new JarEntry(remappedName);
                    newEntry.setTime(entry.getTime());
                    jos.putNextEntry(newEntry);
                    jos.write(remappedBytes);
                } else {
                    String remappedName = remapResourcePath(entryName, remapper);
                    JarEntry newEntry = new JarEntry(remappedName);
                    newEntry.setTime(entry.getTime());
                    jos.putNextEntry(newEntry);
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = jis.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                }
                
                jos.closeEntry();
                jis.closeEntry();
            }
        }
        
        LOGGER.info("Successfully remapped JAR to: " + outputJar.getFileName());
    }
    
    private byte[] remapClass(byte[] classBytes, Remapper remapper) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        ClassRemapper classRemapper = new ClassRemapper(writer, remapper);
        reader.accept(classRemapper, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
    
    private String remapResourcePath(String path, CustomRemapper remapper) {
        for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
            if (path.startsWith(entry.getKey() + "/")) {
                return path.replace(entry.getKey(), entry.getValue());
            }
        }
        return path;
    }
    
    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
    
    private static class CustomRemapper extends Remapper {
        private final Map<String, String> packageMappings;
        
        public CustomRemapper(Map<String, String> packageMappings) {
            this.packageMappings = packageMappings;
        }
        
        @Override
        public String map(String internalName) {
            String bestMatch = null;
            String bestReplacement = null;
            
            for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
                String packagePath = entry.getKey();
                if (internalName.startsWith(packagePath + "/") || internalName.equals(packagePath)) {
                    if (bestMatch == null || packagePath.length() > bestMatch.length()) {
                        bestMatch = packagePath;
                        bestReplacement = entry.getValue();
                    }
                }
            }
            
            if (bestMatch != null) {
                return internalName.replace(bestMatch, bestReplacement);
            }
            
            return internalName;
        }
    }
}
