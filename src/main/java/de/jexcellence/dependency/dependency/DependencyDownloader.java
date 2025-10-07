package de.jexcellence.dependency.dependency;

import de.jexcellence.dependency.type.RepositoryType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.jar.JarFile;

public final class DependencyDownloader {
    private static final String USER_AGENT = "JEDependency-Downloader/2.0.0";
    private static final String ACCEPT = "application/java-archive, application/octet-stream, */*;q=0.1";
    private static final int CONNECTION_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final List<String> customRepositories = new ArrayList<>();

    public void addRepository(String repositoryUrl) {
        if (!repositoryUrl.endsWith("/")) {
            repositoryUrl += "/";
        }
        this.customRepositories.add(repositoryUrl);
    }
    
    public File downloadDependency(String gavCoordinates, File targetDirectory) {
        if (gavCoordinates == null) {
            throw new IllegalArgumentException("GAV coordinates cannot be null");
        }
        if (targetDirectory == null) {
            throw new IllegalArgumentException("Target directory cannot be null");
        }

        DependencyCoordinate coordinate = this.parseGavCoordinates(gavCoordinates);
        if (coordinate == null) {
            return null;
        }

        File targetJarFile = this.createTargetFile(coordinate, targetDirectory);
        if (this.isFileAlreadyExists(targetJarFile)) {
            this.logger.fine("Dependency already exists: " + targetJarFile.getName());
            return targetJarFile;
        }

        this.logger.fine("Downloading dependency: " + gavCoordinates);
        return this.attemptDownloadFromRepositories(coordinate, targetJarFile);
    }

    private DependencyCoordinate parseGavCoordinates(String gavCoordinates) {
        String[] coordinateParts = gavCoordinates.split(":");
        if (coordinateParts.length < 3) {
            this.logger.severe("Invalid GAV coordinate format: " + gavCoordinates + " (expected format: group:artifact:version[:classifier])");
            return null;
        }
        String classifier = coordinateParts.length > 3 ? coordinateParts[3] : null;
        return new DependencyCoordinate(coordinateParts[0], coordinateParts[1], coordinateParts[2], classifier);
    }

    private File createTargetFile(DependencyCoordinate coordinate, File targetDirectory) {
        StringBuilder fileName = new StringBuilder();
        fileName.append(coordinate.getArtifactId()).append("-").append(coordinate.getVersion());
        if (coordinate.getClassifier() != null && !coordinate.getClassifier().isEmpty()) {
            fileName.append("-").append(coordinate.getClassifier());
        }
        fileName.append(".jar");
        return new File(targetDirectory, fileName.toString());
    }

    private boolean isFileAlreadyExists(File targetFile) {
        return targetFile.isFile() && targetFile.length() > 0L;
    }

    private File attemptDownloadFromRepositories(DependencyCoordinate coordinate, File targetFile) {
        for (String customRepo : customRepositories) {
            String downloadUrl = buildCustomRepoPath(customRepo, coordinate);
            logger.finest("Trying custom repository at " + downloadUrl);
            if (this.downloadFromUrl(downloadUrl, targetFile)) {
                this.logger.fine("Downloaded from custom repository");
                return targetFile;
            }
        }
        
        for (RepositoryType repository : RepositoryType.values()) {
            String downloadUrl = repository.buildPath(coordinate.getGroupId(), coordinate.getArtifactId(), coordinate.getVersion());
            if (coordinate.getClassifier() != null) {
                downloadUrl = downloadUrl.replace(".jar", "-" + coordinate.getClassifier() + ".jar");
            }
            logger.finest("Trying repository: " + repository.name() + " at " + downloadUrl);
            if (this.downloadFromUrl(downloadUrl, targetFile)) {
                this.logger.fine("Downloaded from repository: " + repository.name());
                return targetFile;
            }
        }

        this.logger.warning("Failed to download " + coordinate.getGavString() + " from any repository");
        return null;
    }
    
    private String buildCustomRepoPath(String repoUrl, DependencyCoordinate coordinate) {
        String groupPath = coordinate.getGroupId().replace('.', '/');
        StringBuilder fileName = new StringBuilder();
        fileName.append(coordinate.getArtifactId()).append("-").append(coordinate.getVersion());
        if (coordinate.getClassifier() != null && !coordinate.getClassifier().isEmpty()) {
            fileName.append("-").append(coordinate.getClassifier());
        }
        fileName.append(".jar");
        return repoUrl + groupPath + "/" + coordinate.getArtifactId() + "/" + coordinate.getVersion() + "/" + fileName.toString();
    }

    private boolean downloadFromUrl(String downloadUrl, File targetFile) {
        try {
            URL url = new URL(downloadUrl);
            int redirects = 0;

            while (redirects <= MAX_REDIRECTS) {
                HttpURLConnection connection = createHttpConnection(url);
                connection.setInstanceFollowRedirects(false); // handle manually for full control

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    long contentLength = parseContentLength(connection.getHeaderField("Content-Length"));
                    String contentType = safeLower(connection.getHeaderField("Content-Type"));

                    // Stream to a temp file first
                    File tmp = new File(targetFile.getParentFile(), targetFile.getName() + ".part");
                    Files.createDirectories(targetFile.getParentFile().toPath());

                    long bytesWritten = 0L;
                    try (InputStream in = connection.getInputStream();
                         FileOutputStream out = new FileOutputStream(tmp)) {
                        bytesWritten = copyStreamToFile(in, out);
                    }

                    if (contentLength > 0 && bytesWritten != contentLength) {
                        logger.warning(String.format(Locale.ROOT,
                                "Content-Length mismatch for %s: expected %d, got %d", url, contentLength, bytesWritten));
                        safeDelete(tmp);
                        return false;
                    }

                    if (bytesWritten <= 0) {
                        logger.warning("Downloaded 0 bytes from " + url + " — treating as failure");
                        safeDelete(tmp);
                        return false;
                    }

                    if (!isJarFile(tmp)) {
                        logger.warning("Downloaded file is not a valid JAR, deleting: " + tmp.getName() +
                                " (Content-Type=" + contentType + ", bytes=" + bytesWritten + ")");
                        safeDelete(tmp);
                        return false;
                    }

                    // Move into place atomically when possible
                    try {
                        Files.move(tmp.toPath(), targetFile.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    logger.fine(String.format(Locale.ROOT,
                            "Successfully downloaded %s (%d bytes, Content-Type=%s) to %s",
                            url, bytesWritten, contentType, targetFile.getAbsolutePath()));
                    return true;
                }

                // Handle redirects
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        logger.warning("Redirect without Location header from: " + url);
                        cleanupFailedDownload(targetFile);
                        return false;
                    }
                    URL next = new URL(url, location);
                    logger.finest("Redirect " + code + " to " + next);
                    url = next;
                    redirects++;
                    continue;
                }

                // Other HTTP errors
                String msg = "HTTP " + code + " when downloading " + url;
                logger.warning(msg);
                cleanupFailedDownload(targetFile);
                return false;
            }

            logger.warning("Too many redirects (" + MAX_REDIRECTS + ") for " + downloadUrl);
            cleanupFailedDownload(targetFile);
            return false;

        } catch (Exception exception) {
            this.logger.log(Level.FINE, "Download failed from URL: " + downloadUrl, exception);
            this.cleanupFailedDownload(targetFile);
            return false;
        }
    }

    private HttpURLConnection createHttpConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", ACCEPT);
        return connection;
    }

    private long copyStreamToFile(InputStream inputStream, FileOutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            if (bytesRead > 0) {
                outputStream.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
        }
        return total;
    }

    private boolean isJarFile(File file) {
        if (!file.isFile()) return false;
        if (file.length() < 1024) return false; // heuristic: too small to be a real JAR
        try (JarFile jf = new JarFile(file, true)) {
            return jf.entries().hasMoreElements();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void cleanupFailedDownload(File targetFile) {
        // Clean only the final file; temp .part files are handled where created
        if (targetFile.exists() && !targetFile.delete()) {
            this.logger.warning("Failed to clean up partially downloaded file: " + targetFile.getName());
        }
    }

    private void safeDelete(File f) {
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed to delete file: " + f, e);
        }
    }

    private long parseContentLength(String header) {
        if (header == null) return -1L;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String safeLower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static final class DependencyCoordinate {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String classifier;

        public DependencyCoordinate(String groupId, String artifactId, String version, String classifier) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier;
        }

        public String getGroupId() { return this.groupId; }
        public String getArtifactId() { return this.artifactId; }
        public String getVersion() { return this.version; }
        public String getClassifier() { return this.classifier; }
        public String getGavString() { 
            StringBuilder sb = new StringBuilder();
            sb.append(this.groupId).append(":").append(this.artifactId).append(":").append(this.version);
            if (this.classifier != null && !this.classifier.isEmpty()) {
                sb.append(":").append(this.classifier);
            }
            return sb.toString();
        }
    }
}