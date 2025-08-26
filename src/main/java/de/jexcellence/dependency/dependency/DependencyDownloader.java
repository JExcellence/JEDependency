
package de.jexcellence.dependency.dependency;

import de.jexcellence.dependency.type.RepositoryType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles downloading of dependencies from Maven repositories.
 * 
 * <p>This class is responsible for downloading JAR files from various Maven repositories
 * based on GAV (Group:Artifact:Version) coordinates. It supports multiple repository
 * types and implements a fallback mechanism to try different repositories if one fails.</p>
 * 
 * <p>The downloader includes features such as:</p>
 * <ul>
 *   <li>Automatic repository fallback</li>
 *   <li>File existence checking to avoid unnecessary downloads</li>
 *   <li>Proper HTTP connection handling with timeouts</li>
 *   <li>Comprehensive error handling and logging</li>
 * </ul>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class DependencyDownloader {
    
    private static final String USER_AGENT = "JEDependency-Downloader/2.0.0";
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int BUFFER_SIZE = 8192;
    private static final String JAR_EXTENSION = ".jar";
    
    private final Logger logger;
    
    /**
     * Creates a new dependency downloader.
     */
    public DependencyDownloader() {
        this.logger = Logger.getLogger(this.getClass().getName());
    }
    
    /**
     * Downloads a dependency JAR file based on GAV coordinates.
     * 
     * <p>This method attempts to download the specified dependency from various
     * Maven repositories. If the file already exists in the target directory,
     * the download is skipped.</p>
     * 
     * @param gavCoordinates the dependency coordinates in format "group:artifact:version"
     * @param targetDirectory the directory where the JAR should be saved
     * @return the downloaded JAR file, or null if download failed
     * @throws IllegalArgumentException if gavCoordinates or targetDirectory is null
     */
    public File downloadDependency(final String gavCoordinates, final File targetDirectory) {
        if (gavCoordinates == null) {
            throw new IllegalArgumentException("GAV coordinates cannot be null");
        }
        if (targetDirectory == null) {
            throw new IllegalArgumentException("Target directory cannot be null");
        }
        
        final DependencyCoordinate coordinate = this.parseGavCoordinates(gavCoordinates);
        if (coordinate == null) {
            return null;
        }
        
        final File targetJarFile = this.createTargetFile(coordinate, targetDirectory);
        
        if (this.isFileAlreadyExists(targetJarFile)) {
            this.logger.fine("Dependency already exists: " + targetJarFile.getName());
            return targetJarFile;
        }
        
        this.logger.fine("Downloading dependency: " + gavCoordinates);
        
        return this.attemptDownloadFromRepositories(coordinate, targetJarFile);
    }
    
    /**
     * Parses GAV coordinates into a structured format.
     * 
     * @param gavCoordinates the GAV string to parse
     * @return parsed coordinate object, or null if invalid format
     */
    private DependencyCoordinate parseGavCoordinates(final String gavCoordinates) {
        final String[] coordinateParts = gavCoordinates.split(":");
        if (coordinateParts.length != 3) {
            this.logger.severe("Invalid GAV coordinate format: " + gavCoordinates + 
                             " (expected format: group:artifact:version)");
            return null;
        }
        
        return new DependencyCoordinate(coordinateParts[0], coordinateParts[1], coordinateParts[2]);
    }
    
    /**
     * Creates the target file for a dependency.
     * 
     * @param coordinate the dependency coordinate
     * @param targetDirectory the target directory
     * @return the target file
     */
    private File createTargetFile(final DependencyCoordinate coordinate, final File targetDirectory) {
        final String jarFileName = coordinate.getArtifactId() + "-" + coordinate.getVersion() + JAR_EXTENSION;
        return new File(targetDirectory, jarFileName);
    }
    
    /**
     * Checks if a file already exists.
     * 
     * @param targetFile the file to check
     * @return true if the file exists and is a regular file
     */
    private boolean isFileAlreadyExists(final File targetFile) {
        return targetFile.isFile();
    }
    
    /**
     * Attempts to download a dependency from all available repositories.
     * 
     * @param coordinate the dependency coordinate
     * @param targetFile the target file to save to
     * @return the downloaded file, or null if all repositories failed
     */
    private File attemptDownloadFromRepositories(final DependencyCoordinate coordinate, final File targetFile) {
        for (final RepositoryType repository : RepositoryType.values()) {
            final String downloadUrl = repository.buildPath(
                coordinate.getGroupId(), 
                coordinate.getArtifactId(), 
                coordinate.getVersion()
            );
            
            this.logger.finest("Trying repository: " + repository.name() + " at " + downloadUrl);
            
            if (this.downloadFromUrl(downloadUrl, targetFile)) {
                this.logger.fine("Downloaded from repository: " + repository.name());
                return targetFile;
            }
        }
        
        this.logger.warning("Failed to download " + coordinate.getGavString() + " from any repository");
        return null;
    }
    
    /**
     * Downloads a file from a specific URL.
     * 
     * @param downloadUrl the URL to download from
     * @param targetFile the file to save to
     * @return true if download was successful
     */
    private boolean downloadFromUrl(final String downloadUrl, final File targetFile) {
        try {
            final HttpURLConnection connection = this.createHttpConnection(downloadUrl);
            
            try (final InputStream inputStream = connection.getInputStream();
                 final FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                
                this.copyStreamToFile(inputStream, outputStream);
                return true;
                
            }
        } catch (final Exception exception) {
            this.logger.log(Level.FINE, "Download failed from URL: " + downloadUrl, exception);
            this.cleanupFailedDownload(targetFile);
            return false;
        }
    }
    
    /**
     * Creates and configures an HTTP connection.
     * 
     * @param downloadUrl the URL to connect to
     * @return configured HTTP connection
     * @throws IOException if connection creation fails
     */
    private HttpURLConnection createHttpConnection(final String downloadUrl) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }
    
    /**
     * Copies data from an input stream to a file output stream.
     * 
     * @param inputStream the source stream
     * @param outputStream the target stream
     * @throws IOException if copying fails
     */
    private void copyStreamToFile(final InputStream inputStream, final FileOutputStream outputStream) 
            throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }
    
    /**
     * Cleans up a partially downloaded file after a failed download.
     * 
     * @param targetFile the file to clean up
     */
    private void cleanupFailedDownload(final File targetFile) {
        if (targetFile.exists() && !targetFile.delete()) {
            this.logger.warning("Failed to clean up partially downloaded file: " + targetFile.getName());
        }
    }
    
    /**
     * Internal class representing parsed dependency coordinates.
     */
    private static final class DependencyCoordinate {
        private final String groupId;
        private final String artifactId;
        private final String version;
        
        /**
         * Creates a new dependency coordinate.
         * 
         * @param groupId the group ID
         * @param artifactId the artifact ID
         * @param version the version
         */
        public DependencyCoordinate(final String groupId, final String artifactId, final String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
        
        /**
         * Gets the group ID.
         * 
         * @return the group ID
         */
        public String getGroupId() {
            return this.groupId;
        }
        
        /**
         * Gets the artifact ID.
         * 
         * @return the artifact ID
         */
        public String getArtifactId() {
            return this.artifactId;
        }
        
        /**
         * Gets the version.
         * 
         * @return the version
         */
        public String getVersion() {
            return this.version;
        }
        
        /**
         * Gets the full GAV coordinate string.
         * 
         * @return the GAV string
         */
        public String getGavString() {
            return this.groupId + ":" + this.artifactId + ":" + this.version;
        }
    }
}