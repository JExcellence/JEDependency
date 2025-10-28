package de.jexcellence.dependency.dependency;

import de.jexcellence.dependency.type.RepositoryType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.jar.JarFile;

public final class DependencyDownloader {

    private static final Logger LOGGER = Logger.getLogger(DependencyDownloader.class.getName());

    private static final String USER_AGENT = "JEDependency-Downloader/2.0.0";
    private static final String ACCEPT = "application/java-archive, application/octet-stream, */*;q=0.1";
    private static final int CONNECTION_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;

    private final Set<String> customRepositories = new LinkedHashSet<>();

    public void addRepository(final @NotNull String repositoryUrl) {
        Objects.requireNonNull(repositoryUrl, "repositoryUrl");
        final String normalized = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        this.customRepositories.add(normalized);
    }

    public @Nullable File downloadDependency(final @NotNull String gavCoordinates, final @NotNull File targetDirectory) {
        Objects.requireNonNull(gavCoordinates, "gavCoordinates");
        Objects.requireNonNull(targetDirectory, "targetDirectory");

        final DependencyCoordinate coordinate = parseGavCoordinates(gavCoordinates);
        if (coordinate == null) {
            return null;
        }

        final File targetJarFile = createTargetFile(coordinate, targetDirectory);
        if (targetJarFile.isFile() && targetJarFile.length() > 0L) {
            LOGGER.fine(() -> "Dependency already present: " + targetJarFile.getName());
            return targetJarFile;
        }

        LOGGER.fine(() -> "Downloading dependency: " + coordinate.getGavString());
        return attemptDownloadFromRepositories(coordinate, targetJarFile) ? targetJarFile : null;
    }

    private @Nullable DependencyCoordinate parseGavCoordinates(final String gavCoordinates) {
        final String[] coordinateParts = gavCoordinates.split(":");
        if (coordinateParts.length < 3) {
            LOGGER.severe("Invalid GAV coordinate format: " + gavCoordinates + " (expected format: group:artifact:version[:classifier])");
            return null;
        }
        final String classifier = coordinateParts.length > 3 ? coordinateParts[3] : null;
        return new DependencyCoordinate(coordinateParts[0], coordinateParts[1], coordinateParts[2], classifier);
    }

    private File createTargetFile(final DependencyCoordinate coordinate, final File targetDirectory) {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(coordinate.artifactId()).append('-').append(coordinate.version());
        if (coordinate.classifier() != null && !coordinate.classifier().isEmpty()) {
            fileName.append('-').append(coordinate.classifier());
        }
        fileName.append(".jar");
        return new File(targetDirectory, fileName.toString());
    }

    private boolean attemptDownloadFromRepositories(final DependencyCoordinate coordinate, final File targetFile) {
        for (final String customRepo : this.customRepositories) {
            final String downloadUrl = buildCustomRepoPath(customRepo, coordinate);
            LOGGER.finest(() -> "Trying custom repository at " + downloadUrl);
            if (downloadFromUrl(downloadUrl, targetFile)) {
                LOGGER.fine("Downloaded from custom repository");
                return true;
            }
        }

        for (final RepositoryType repository : RepositoryType.values()) {
            String downloadUrl = repository.buildPath(coordinate.groupId(), coordinate.artifactId(), coordinate.version());
            if (coordinate.classifier() != null) {
                downloadUrl = downloadUrl.replace(".jar", "-" + coordinate.classifier() + ".jar");
            }
            final String finalDownloadUrl = downloadUrl;
            LOGGER.finest(() -> "Trying repository: " + repository.name() + " at " + finalDownloadUrl);
            if (downloadFromUrl(downloadUrl, targetFile)) {
                LOGGER.fine(() -> "Downloaded from repository: " + repository.name());
                return true;
            }
        }

        LOGGER.warning("Failed to download " + coordinate.getGavString() + " from any repository");
        return false;
    }

    private String buildCustomRepoPath(final String repoUrl, final DependencyCoordinate coordinate) {
        final String groupPath = coordinate.groupId().replace('.', '/');
        final StringBuilder fileName = new StringBuilder();
        fileName.append(coordinate.artifactId()).append('-').append(coordinate.version());
        if (coordinate.classifier() != null && !coordinate.classifier().isEmpty()) {
            fileName.append('-').append(coordinate.classifier());
        }
        fileName.append(".jar");
        return repoUrl + groupPath + "/" + coordinate.artifactId() + "/" + coordinate.version() + "/" + fileName;
    }

    private boolean downloadFromUrl(final String downloadUrl, final File targetFile) {
        try {
            URL url = new URL(downloadUrl);
            int redirects = 0;

            while (redirects <= MAX_REDIRECTS) {
                final HttpURLConnection connection = createHttpConnection(url);
                connection.setInstanceFollowRedirects(false);

                final int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    final long contentLength = parseContentLength(connection.getHeaderField("Content-Length"));
                    final String contentType = safeLower(connection.getHeaderField("Content-Type"));

                    final File tmp = new File(targetFile.getParentFile(), targetFile.getName() + ".part");
                    Files.createDirectories(targetFile.getParentFile().toPath());

                    final long bytesWritten;
                    try (InputStream in = connection.getInputStream();
                         FileOutputStream out = new FileOutputStream(tmp)) {
                        bytesWritten = copyStreamToFile(in, out);
                    }

                    if (contentLength > 0 && bytesWritten != contentLength) {
                        LOGGER.warning(String.format(Locale.ROOT,
                                "Content-Length mismatch for %s: expected %d, got %d",
                                url, contentLength, bytesWritten));
                        safeDelete(tmp);
                        return false;
                    }

                    if (bytesWritten <= 0) {
                        LOGGER.warning("Downloaded 0 bytes from " + url + " — treating as failure");
                        safeDelete(tmp);
                        return false;
                    }

                    if (!isJarFile(tmp)) {
                        LOGGER.warning("Downloaded file is not a valid JAR, deleting: " + tmp.getName()
                                + " (Content-Type=" + contentType + ", bytes=" + bytesWritten + ")");
                        safeDelete(tmp);
                        return false;
                    }

                    try {
                        Files.move(tmp.toPath(), targetFile.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (final AtomicMoveNotSupportedException ignored) {
                        Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    LOGGER.fine(String.format(Locale.ROOT,
                            "Successfully downloaded %s (%d bytes, Content-Type=%s) to %s",
                            url, bytesWritten, contentType, targetFile.getAbsolutePath()));
                    return true;
                }

                if (code >= 300 && code < 400) {
                    final String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        LOGGER.warning("Redirect without Location header from: " + url);
                        cleanupFailedDownload(targetFile);
                        return false;
                    }
                    url = new URL(url, location);
                    LOGGER.finest("Redirect " + code + " to " + url);
                    redirects++;
                    continue;
                }

                LOGGER.warning("HTTP " + code + " when downloading " + url);
                cleanupFailedDownload(targetFile);
                return false;
            }

            LOGGER.warning("Too many redirects (" + MAX_REDIRECTS + ") for " + downloadUrl);
            cleanupFailedDownload(targetFile);
            return false;
        } catch (final Exception exception) {
            LOGGER.log(Level.FINE, "Download failed from URL: " + downloadUrl, exception);
            cleanupFailedDownload(targetFile);
            return false;
        }
    }

    private HttpURLConnection createHttpConnection(final URL url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", ACCEPT);
        return connection;
    }

    private long copyStreamToFile(final InputStream inputStream, final FileOutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];
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

    private boolean isJarFile(final File file) {
        if (!file.isFile() || file.length() < 1024) {
            return false;
        }
        try (JarFile ignored = new JarFile(file, true)) {
            return true;
        } catch (final Exception exception) {
            LOGGER.log(Level.FINE, "Failed to verify JAR file: " + file.getName(), exception);
            return false;
        }
    }

    private void cleanupFailedDownload(final File targetFile) {
        if (targetFile.exists() && !targetFile.delete()) {
            LOGGER.warning("Failed to clean up partially downloaded file: " + targetFile.getName());
        }
    }

    private void safeDelete(final File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (final IOException exception) {
            LOGGER.log(Level.FINE, "Failed to delete file: " + file, exception);
        }
    }

    private long parseContentLength(final String header) {
        if (header == null) {
            return -1L;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (final NumberFormatException ignored) {
            return -1L;
        }
    }

    private String safeLower(final String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private record DependencyCoordinate(String groupId, String artifactId, String version, @Nullable String classifier) {

        String getGavString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(this.groupId).append(':').append(this.artifactId).append(':').append(this.version);
            if (this.classifier != null && !this.classifier.isEmpty()) {
                sb.append(':').append(this.classifier);
            }
            return sb.toString();
        }
    }
}
