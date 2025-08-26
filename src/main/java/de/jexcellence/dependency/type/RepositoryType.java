package de.jexcellence.dependency.type;

/**
 * Enumeration of supported Maven repository types.
 * 
 * <p>This enum defines various Maven repositories that can be used for
 * dependency resolution. Each repository type includes its base URL and
 * provides functionality to build complete download paths for artifacts.</p>
 * 
 * <p>The repositories are ordered by reliability and popularity, with
 * Maven Central being the primary repository followed by other well-known
 * public repositories.</p>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 1.0.0
 */
public enum RepositoryType {
    
    /**
     * Maven Central Repository - the primary repository for Maven artifacts.
     */
    MAVEN_CENTRAL("https://central.maven.org/maven2"),
    
    /**
     * Maven Central Repository (alternative URL).
     */
    MAVEN_CENTRAL_REPO1("https://repo1.maven.org/maven2"),
    
    /**
     * Apache Maven Repository.
     */
    APACHE_MAVEN("https://repo.maven.apache.org/maven2"),
    
    /**
     * Sonatype OSS Repository for open source projects.
     */
    SONATYPE_OSS("https://oss.sonatype.org/content/groups/public/"),
    
    /**
     * Sonatype OSS Snapshots Repository.
     */
    SONATYPE_OSS_SNAPSHOTS("https://oss.sonatype.org/content/repositories/snapshots"),
    
    /**
     * NeetGames Nexus Repository.
     */
    NEETGAMES_NEXUS("https://nexus.neetgames.com/repository/maven-releases/"),
    
    /**
     * JitPack Repository for GitHub projects.
     */
    JITPACK("https://jitpack.io"),
    
    /**
     * Auxilor Repository.
     */
    AUXILOR("https://repo.auxilor.io/repository/maven-public/"),
    
    /**
     * TCoded Repository.
     */
    TCODED("https://repo.tcoded.com/releases"),
    
    /**
     * PaperMC Repository for Minecraft server software.
     */
    PAPERMC("https://repo.papermc.io/repository/maven-public/");
    
    private final String baseUrl;
    
    /**
     * Creates a new repository type with the specified base URL.
     * 
     * @param baseUrl the base URL of the repository
     */
    RepositoryType(final String baseUrl) {
        this.baseUrl = this.normalizeUrl(baseUrl);
    }
    
    /**
     * Gets the base URL of this repository.
     * 
     * @return the base URL
     */
    public String getBaseUrl() {
        return this.baseUrl;
    }
    
    /**
     * Builds the complete download path for an artifact.
     * 
     * <p>This method constructs the full URL path to download a specific
     * artifact from this repository using Maven's standard directory structure:</p>
     * 
     * <pre>
     * {baseUrl}/{groupId}/{artifactId}/{version}/{artifactId}-{version}.jar
     * </pre>
     * 
     * @param groupId the group ID of the artifact (dots will be converted to slashes)
     * @param artifactId the artifact ID
     * @param version the version of the artifact
     * @return the complete download URL for the artifact
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public String buildPath(final String groupId, final String artifactId, final String version) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        if (artifactId == null || artifactId.trim().isEmpty()) {
            throw new IllegalArgumentException("Artifact ID cannot be null or empty");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }
        
        final String normalizedGroupId = groupId.replace('.', '/');
        final String jarFileName = artifactId + "-" + version + ".jar";
        
        return this.baseUrl + normalizedGroupId + "/" + artifactId + "/" + version + "/" + jarFileName;
    }
    
    /**
     * Normalizes a URL by ensuring it ends with a forward slash.
     * 
     * @param url the URL to normalize
     * @return the normalized URL
     */
    private String normalizeUrl(final String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}