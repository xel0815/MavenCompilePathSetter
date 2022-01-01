package com.safits;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The Version Copier finds a version in a 'master' file and propagates it to other files.
 *
 * @author Friedrich Gesell
 *         friedrich.gesell@safits.be
 *
 */
@Mojo( name = "copy-version", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class VersionCopier
extends AbstractMojo {

	@Parameter(name = "srcfile", readonly = true, required = true)
	/** the name of the file where to take the version from */
	String srcfile;

	@Parameter(name = "srcpattern", readonly = true, required = true)
	/** The 1-group regular expression to look for. */
	String srcpattern;

    @Parameter( name = "destinations", readonly = true)
    private Destination[] destinations;

    /**
     * Where shall we look for matching versions?
     */
    public static class Destination {

    	/** the name of the file where to put the version to */
    	String file;

    	/** The 1-group regular expression to look for. */
    	String pattern;

    }

    @Override
	public void execute()
	throws MojoExecutionException {

    	if (this.destinations == null || this.destinations.length == 0) {
    		getLog().info("No destinations indicated");
    		return;
    	}

		getLog().info("source file = " + this.srcfile);
		getLog().info("source pattern = " + this.srcpattern);

		BufferedReader bufferedReader = null;

		String version = null;

		File srcFile = new File(this.srcfile);
		if (!srcFile.isFile())
			throw new MojoExecutionException("Cannot access source file " + srcFile.getAbsolutePath());

		Pattern pattern = Pattern.compile(this.srcpattern);
		Matcher matcher = pattern.matcher("nothing");
		if (matcher.groupCount() != 1)
			throw new MojoExecutionException("source pattern must have exactly one group");

		try {
			bufferedReader = new BufferedReader(new FileReader(srcFile));
			for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
				matcher = pattern.matcher(line);
				if (matcher.matches()) {
					version = matcher.group(1);
					break;
				}
			}
		}
		catch (Exception e) {
			throw new MojoExecutionException("Could not read version in source", e);
		}
		finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				}
				catch (Exception e) {
					//no recursive exceptions
				}
			}
		}

		if (version == null)
			throw new MojoExecutionException("Could not find version in " + this.srcfile);
		getLog().info("Version is " + version);

    	for (Destination destination: this.destinations) {
    		getLog().info("-- Destination file = " + destination.file);
    		getLog().info("-- Destination pattern = " + destination.pattern);
    		pattern = Pattern.compile(destination.pattern);
    		matcher = pattern.matcher("nothing");
    		if (matcher.groupCount() != 1)
    			throw new MojoExecutionException("destination pattern must have exactly one group");

    		File dstFile = new File(destination.file);
    		if (!dstFile.isFile())
    			throw new MojoExecutionException("Cannot access destination file " + dstFile.getAbsolutePath());

    		bufferedReader = null;
    		boolean found = false;
    		List<String> newLines = new ArrayList<>();

    		try {
    			bufferedReader = new BufferedReader(new FileReader(destination.file));
    			for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
    				matcher = pattern.matcher(line);
    				if (!matcher.matches()) {
    					newLines.add(line);
    					continue;
    				}
    				//this is the line that might have to be patched.
    				if (version.equals(matcher.group(1))) {
    					//that is already ok
    					getLog().info("-- Version is already correct");
    					newLines.clear();
    					found = true;
    					break;
    				}
    				getLog().info("-- Obsolete version " + matcher.group(1) + " must be patched");
    				MatchResult matchResult = matcher.toMatchResult();
    				String before = line.substring(0, matchResult.start(1));
    				String after = line.substring(matchResult.end(1));
    				newLines.add(before + version + after);
    				found = true;
    			}

    			if (!found)
    				throw new MojoExecutionException("Could not find the pattern in the destination");

    		}
    		catch (Exception e) {
    			throw new MojoExecutionException("Could not read this destination", e);
    		}
    		finally {
    			if (bufferedReader != null) {
    				try {
    					bufferedReader.close();
    				}
    				catch (Exception e) {
    					//no recursive exceptions
    				}
    			}
    		}

			if (newLines.isEmpty())
				//this destination does not have to be patched.
				continue;

			File dstBackup = new File(destination.file+".bak");
			if (dstBackup.exists())
				dstBackup.delete();
			dstBackup = new File(destination.file+".bak");
			dstFile.renameTo(dstBackup);

			dstFile = new File(destination.file);
			PrintWriter newDstWriter = null;
			try {
				newDstWriter = new PrintWriter(dstFile);
				for (String line: newLines) {
					newDstWriter.println(line);
				}
			}
			catch (Exception e) {
				throw new MojoExecutionException("Could not update the destination", e);
			}
			finally {
				if (newDstWriter != null)
					newDstWriter.close();
			}
			getLog().info("-- " + dstFile.getAbsolutePath() + " was updated to version " + version);
    	}

    }

}
