package de.jexcellence.dependency.dependency;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for loading dependency configurations from YAML files.
 * 
 * <p>This class is responsible for parsing dependency configurations from
 * YAML files located in the classpath resources. It expects a specific format:</p>
 * 
 * <pre>
 * dependencies:
 *   - "group.id:artifact-id:version"
 *   - "another.group:another-artifact:1.0.0"
 * </pre>
 * 
 * <p>The loader supports both quoted and unquoted dependency entries,
 * and automatically filters out comments and empty lines.</p>
 * 
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class YamlDependencyLoader {
	
	private static final String DEPENDENCIES_YAML_PATH      = "/dependency/dependencies.yml";
	private static final String DEPENDENCIES_SECTION_MARKER = "dependencies:";
	private static final String LIST_ITEM_PREFIX            = "- ";
	private static final String QUOTED_PREFIX               = "- \"";
	private static final String QUOTED_SUFFIX               = "\"";
	private static final String COMMENT_PREFIX              = "#";
	
	private final Logger logger;
	
	/**
	 * Creates a new YAML dependency loader.
	 */
	public YamlDependencyLoader() {
		
		this.logger = Logger.getLogger(this.getClass().getName());
	}
	
	/**
	 * Loads dependency configurations from the standard YAML file.
	 *
	 * <p>This method attempts to load dependencies from the resource file
	 * {@code /dependency/dependencies.yml} using the provided anchor class
	 * for resource resolution.</p>
	 *
	 * @param anchorClass the class to use for resource loading
	 *
	 * @return array of dependency GAV coordinates, or null if no dependencies found
	 *
	 * @throws IllegalArgumentException if anchorClass is null
	 */
	public String[] loadDependenciesFromYaml(final Class<?> anchorClass) {
		
		if (anchorClass == null) {
			throw new IllegalArgumentException("Anchor class cannot be null");
		}
		
		this.logger.fine("Attempting to load dependencies from: " + DEPENDENCIES_YAML_PATH);
		
		try (final InputStream yamlInputStream = anchorClass.getResourceAsStream(DEPENDENCIES_YAML_PATH)) {
			if (yamlInputStream == null) {
				this.logger.fine("No YAML dependency configuration found at: " + DEPENDENCIES_YAML_PATH);
				return null;
			}
			
			return this.parseDependenciesFromStream(yamlInputStream);
		} catch (final Exception exception) {
			this.logger.log(
				Level.SEVERE,
				"Failed to load dependencies from YAML configuration",
				exception
			);
			return null;
		}
	}
	
	/**
	 * Parses dependency entries from a YAML input stream.
	 *
	 * @param yamlInputStream the input stream containing YAML content
	 *
	 * @return array of parsed dependency GAV coordinates
	 */
	private String[] parseDependenciesFromStream(final InputStream yamlInputStream) {
		
		this.logger.fine("Parsing YAML dependency configuration");
		
		final List<String> parsedDependencies      = new ArrayList<>();
		final Scanner      yamlScanner             = new Scanner(yamlInputStream);
		boolean            isInDependenciesSection = false;
		int                currentLineNumber       = 0;
		
		while (yamlScanner.hasNextLine()) {
			final String currentLine = yamlScanner.nextLine();
			currentLineNumber++;
			final String trimmedLine = currentLine.trim();
			
			this.logger.finest("Processing line " + currentLineNumber + ": " + currentLine);
			
			if (this.isDependenciesSectionStart(trimmedLine)) {
				isInDependenciesSection = true;
				this.logger.fine("Found dependencies section at line " + currentLineNumber);
				continue;
			}
			
			if (isInDependenciesSection) {
				if (this.isDependenciesSectionEnd(trimmedLine)) {
					this.logger.fine("End of dependencies section at line " + currentLineNumber);
					break;
				}
				
				final String dependencyEntry = this.extractDependencyEntry(trimmedLine);
				if (dependencyEntry != null) {
					parsedDependencies.add(dependencyEntry);
					this.logger.fine("Added dependency: " + dependencyEntry);
				}
			}
		}
		
		this.logger.info("Successfully parsed " + parsedDependencies.size() + " dependencies from YAML");
		return parsedDependencies.toArray(new String[0]);
	}
	
	/**
	 * Checks if a line marks the start of the dependencies section.
	 *
	 * @param trimmedLine the line to check
	 *
	 * @return true if this line starts the dependencies section
	 */
	private boolean isDependenciesSectionStart(final String trimmedLine) {
		
		return DEPENDENCIES_SECTION_MARKER.equals(trimmedLine);
	}
	
	/**
	 * Checks if a line marks the end of the dependencies section.
	 *
	 * @param trimmedLine the line to check
	 *
	 * @return true if this line ends the dependencies section
	 */
	private boolean isDependenciesSectionEnd(final String trimmedLine) {
		
		return ! trimmedLine.isEmpty() &&
		       ! trimmedLine.startsWith(COMMENT_PREFIX) &&
		       ! trimmedLine.startsWith(LIST_ITEM_PREFIX);
	}
	
	/**
	 * Extracts a dependency entry from a YAML list item line.
	 *
	 * @param trimmedLine the line to extract from
	 *
	 * @return the dependency GAV coordinate, or null if not a valid entry
	 */
	private String extractDependencyEntry(final String trimmedLine) {
		
		if (this.isQuotedDependencyEntry(trimmedLine)) {
			return this.extractQuotedDependency(trimmedLine);
		} else if (this.isUnquotedDependencyEntry(trimmedLine)) {
			return this.extractUnquotedDependency(trimmedLine);
		}
		return null;
	}
	
	/**
	 * Checks if a line contains a quoted dependency entry.
	 *
	 * @param trimmedLine the line to check
	 *
	 * @return true if this is a quoted dependency entry
	 */
	private boolean isQuotedDependencyEntry(final String trimmedLine) {
		
		return trimmedLine.startsWith(QUOTED_PREFIX) && trimmedLine.endsWith(QUOTED_SUFFIX);
	}
	
	/**
	 * Checks if a line contains an unquoted dependency entry.
	 *
	 * @param trimmedLine the line to check
	 *
	 * @return true if this is an unquoted dependency entry
	 */
	private boolean isUnquotedDependencyEntry(final String trimmedLine) {
		
		return trimmedLine.startsWith(LIST_ITEM_PREFIX) &&
		       ! trimmedLine.startsWith(QUOTED_PREFIX);
	}
	
	/**
	 * Extracts a dependency coordinate from a quoted YAML entry.
	 *
	 * @param trimmedLine the line containing the quoted entry
	 *
	 * @return the extracted dependency coordinate
	 */
	private String extractQuotedDependency(final String trimmedLine) {
		
		return trimmedLine.substring(
			QUOTED_PREFIX.length(),
			trimmedLine.length() - QUOTED_SUFFIX.length()
		);
	}
	
	/**
	 * Extracts a dependency coordinate from an unquoted YAML entry.
	 *
	 * @param trimmedLine the line containing the unquoted entry
	 *
	 * @return the extracted dependency coordinate, or null if invalid
	 */
	private String extractUnquotedDependency(final String trimmedLine) {
		
		final String dependencyCandidate = trimmedLine.substring(LIST_ITEM_PREFIX.length()).trim();
		if (! dependencyCandidate.isEmpty() && ! dependencyCandidate.startsWith(COMMENT_PREFIX)) {
			return dependencyCandidate;
		}
		return null;
	}
	
}