package de.jexcellence.dependency.type;

import org.jetbrains.annotations.NotNull;

/**
 * Enumeration of supported Maven repository types.
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

    RepositoryType(final String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public @NotNull String getBaseUrl() {
        return this.baseUrl;
    }

    public @NotNull String buildPath(
            final @NotNull String groupId,
            final @NotNull String artifactId,
            final @NotNull String version
    ) {
        final String normalizedGroupId = groupId.replace('.', '/');
        final String jarFileName = artifactId + '-' + version + ".jar";
        return this.baseUrl + normalizedGroupId + '/' + artifactId + '/' + version + '/' + jarFileName;
    }
}