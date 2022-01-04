package com.safits;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * The Version Verifier checks that files with version identification match the project's version.
 *
 * @author Friedrich Gesell
 *         friedrich.gesell@safits.be
 *
 */
@Mojo( name = "verify-version", defaultPhase = LifecyclePhase.VALIDATE, requiresProject = true, threadSafe = false )
public class VersionVerifier
extends AbstractMojo {

	public static final String TIMESTAMP_PROPERTY = "pictet.timestamp";

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( name = "locations", readonly = true)
    private Location[] locations;

    /**
     * Where shall we look for matching versions?
     */
    public static class Location {

    	/** the name of the file to compare */
    	String file;

    	/**
    	 * The regular expression to look for. The first match will be used.
    	 * If the pattern has one match group, then there must be a full match.
    	 * If the pattern has two match groups, then the version is described by group(1),
    	 * but the match will only be tested with group(2).
    	 * */
    	String pattern;

    	/** If there is a mismatch, then should a warning be issued or should the process be terminated? */
    	Boolean fatal;
    }

    @Override
	public void execute()
	throws MojoExecutionException {
    	
    	if (this.locations == null || this.locations.length == 0) {
    		getLog().info("No locations indicated");
    		return;
    	}

    	String projectVersion = this.project.getVersion();

    	getLog().info(String.format("Verifying project version %s", projectVersion));

    	for (Location location: this.locations) {

    		int lineOfOccurrence = 0;

    		File file = new File(location.file);
    		if (!file.exists()) {
    			String message = "Cannot find file " + location.file;
    			if (location.fatal != null && location.fatal) {
    				throw new MojoExecutionException(message);
    			}
    			else {
    				getLog().warn(message);
    				return;
    			}
    		}

    		Pattern pattern = null;
    		try {
    			pattern = Pattern.compile(location.pattern);
    		}
    		catch (Exception e) {
    			throw new MojoExecutionException("Illegal location pattern " + location.pattern);
    		}

    		BufferedReader bufferedReader = null;
    		try {
    			bufferedReader = new BufferedReader(new FileReader(file));
    			int lineNumber = 0;
    			for (String line = bufferedReader.readLine();
    					line != null;
    					line = bufferedReader.readLine()) {
    				lineNumber++;
    				Matcher matcher = pattern.matcher(line);
    				if (matcher.matches()) {
    					String fileVersion = matcher.group(1);
    					String testVersion = matcher.groupCount() == 1?
    							fileVersion : matcher.group(2);
    					String comparableProjectVersion = projectVersion.substring(0, testVersion.length());
    					if (comparableProjectVersion.equals(testVersion)) {
    						lineOfOccurrence = lineNumber;
    						getLog().info(String.format("Found compliant version %s in %s line %d",
    								fileVersion,
    								location.file,
    								lineOfOccurrence));
    					}
    					else {
    						String message =
    								String.format("Version verification failure, file %s has %s in line %d",
    										location.file,
    										fileVersion,
    										lineNumber);
    						if (location.fatal != null && location.fatal) {
    							throw new MojoExecutionException(message);
    						}
    						else {
        						lineOfOccurrence = lineNumber;
    							getLog().warn(message);
    						}
    					}
    					break;
    				}
    			}
    		}
    		catch (Exception e) {
    			throw new MojoExecutionException(e.getMessage());
    		}
    		finally {
    			if (bufferedReader != null) {
    				try {
    					bufferedReader.close();
    				}
    				catch (Exception e) {
    					//no processing of recursive errors
    				}
    			}
    		}

    		if (lineOfOccurrence == 0)
    			throw new MojoExecutionException(String.format("Could not find the version in %s", location.file));
    	}

    }

}
