package de.jexcellence.dependency.model;

import java.util.Objects;

public class Dependency {
    
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String repository;
    
    public Dependency(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null, null);
    }
    
    public Dependency(String groupId, String artifactId, String version, String classifier, String repository) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.repository = repository;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public String getArtifactId() {
        return artifactId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getClassifier() {
        return classifier;
    }
    
    public String getRepository() {
        return repository;
    }
    
    public String getFileName() {
        StringBuilder sb = new StringBuilder();
        sb.append(artifactId).append("-").append(version);
        if (classifier != null && !classifier.isEmpty()) {
            sb.append("-").append(classifier);
        }
        sb.append(".jar");
        return sb.toString();
    }
    
    public String getPath() {
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + getFileName();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dependency that = (Dependency) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(artifactId, that.artifactId) &&
               Objects.equals(version, that.version) &&
               Objects.equals(classifier, that.classifier);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(":").append(artifactId).append(":").append(version);
        if (classifier != null && !classifier.isEmpty()) {
            sb.append(":").append(classifier);
        }
        return sb.toString();
    }
    
    public static Dependency parse(String dependencyString) {
        String[] parts = dependencyString.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid dependency format: " + dependencyString);
        }
        
        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;
        
        return new Dependency(groupId, artifactId, version, classifier, null);
    }
}
