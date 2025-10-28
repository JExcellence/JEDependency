package de.jexcellence.dependency.dependency;

import de.jexcellence.dependency.JEDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for loading dependency configurations from YAML resources.
 */
public final class YamlDependencyLoader {

    private static final Logger LOGGER = Logger.getLogger(YamlDependencyLoader.class.getName());

    private static final String DEPENDENCIES_YAML_PATH = "/dependency/dependencies.yml";
    private static final String PAPER_DEPENDENCIES_PATH = "/dependency/paper/dependencies.yml";
    private static final String SPIGOT_DEPENDENCIES_PATH = "/dependency/spigot/dependencies.yml";

    private static final String DEPENDENCIES_SECTION_MARKER = "dependencies:";
    private static final String LIST_ITEM_PREFIX = "- ";
    private static final String QUOTED_PREFIX = "- \"";
    private static final String QUOTED_SUFFIX = "\"";
    private static final String COMMENT_PREFIX = "#";

    public YamlDependencyLoader() {
    }

    public @Nullable String[] loadDependenciesFromYaml(final @NotNull Class<?> anchorClass) {
        if (anchorClass == null) {
            throw new IllegalArgumentException("Anchor class cannot be null");
        }

        final String serverSpecificPath = determineServerSpecificPath();
        if (serverSpecificPath != null) {
            final String[] serverSpecific = loadDependencies(anchorClass, serverSpecificPath);
            if (serverSpecific != null) {
                return serverSpecific;
            }
        }

        return loadDependencies(anchorClass, DEPENDENCIES_YAML_PATH);
    }

    private @Nullable String[] loadDependencies(final @NotNull Class<?> anchorClass, final @NotNull String resourcePath) {
        try (InputStream yamlInputStream = anchorClass.getResourceAsStream(resourcePath)) {
            if (yamlInputStream == null) {
                LOGGER.fine(() -> "No YAML dependency configuration found at: " + resourcePath);
                return null;
            }

            final List<String> dependencies = parseDependenciesFromStream(yamlInputStream);
            if (dependencies.isEmpty()) {
                LOGGER.fine(() -> "YAML configuration " + resourcePath + " did not declare any dependencies");
                return null;
            }

            LOGGER.info(() -> String.format(Locale.ROOT,
                    "Loaded %d dependencies from %s",
                    dependencies.size(),
                    resourcePath
            ));
            return dependencies.toArray(new String[0]);
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING,
                    "Failed to load dependencies from YAML resource: " + resourcePath,
                    exception);
            return null;
        }
    }

    private @Nullable String determineServerSpecificPath() {
        if (JEDependency.isPaperServer()) {
            LOGGER.fine("Detected Paper server - checking Paper dependency overrides");
            return PAPER_DEPENDENCIES_PATH;
        }
        LOGGER.fine("Detected Spigot/CraftBukkit server - checking Spigot dependency overrides");
        return SPIGOT_DEPENDENCIES_PATH;
    }

    private @NotNull List<String> parseDependenciesFromStream(final @NotNull InputStream yamlInputStream) {
        final List<String> parsedDependencies = new ArrayList<>();
        try (Scanner yamlScanner = new Scanner(yamlInputStream, StandardCharsets.UTF_8)) {
            boolean inDependenciesSection = false;

            while (yamlScanner.hasNextLine()) {
                final String currentLine = yamlScanner.nextLine();
                final String trimmedLine = currentLine.trim();

                if (!inDependenciesSection) {
                    inDependenciesSection = DEPENDENCIES_SECTION_MARKER.equals(trimmedLine);
                    continue;
                }

                if (isSectionTerminator(trimmedLine)) {
                    break;
                }

                final String dependencyEntry = extractDependencyEntry(trimmedLine);
                if (dependencyEntry != null) {
                    parsedDependencies.add(dependencyEntry);
                }
            }
        }
        return parsedDependencies;
    }

    private boolean isSectionTerminator(final @NotNull String trimmedLine) {
        return !trimmedLine.isEmpty()
                && !trimmedLine.startsWith(COMMENT_PREFIX)
                && !trimmedLine.startsWith(LIST_ITEM_PREFIX);
    }

    private @Nullable String extractDependencyEntry(final @NotNull String trimmedLine) {
        if (trimmedLine.startsWith(QUOTED_PREFIX) && trimmedLine.endsWith(QUOTED_SUFFIX)) {
            return trimmedLine.substring(QUOTED_PREFIX.length(), trimmedLine.length() - QUOTED_SUFFIX.length());
        }
        if (trimmedLine.startsWith(LIST_ITEM_PREFIX) && !trimmedLine.startsWith(QUOTED_PREFIX)) {
            final String dependencyCandidate = trimmedLine.substring(LIST_ITEM_PREFIX.length()).trim();
            if (!dependencyCandidate.isEmpty() && !dependencyCandidate.startsWith(COMMENT_PREFIX)) {
                return dependencyCandidate;
            }
        }
        return null;
    }
}
