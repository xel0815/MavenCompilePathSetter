package com.safits;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

@Mojo( name = "set-compile-path", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = true )
public class CompilePathSetter
extends AbstractMojo {

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${settings}", readonly = true )
    private Settings settings;

    @Parameter( name = "compile-dependencies", readonly = true)
    private Dependency[] compileDependencies;

	public static class Dependency {
		String groupId;
		String artifact;
		String version;
	}

	final Map<Dependency,File> dependencyMapping = new HashMap<>();

	private int[] lowerVersion;
	private int[] upperVersion;
	private boolean lowerBoundIncluded;
	private boolean upperBoundIncluded;

	private Pattern jarTestPattern;

    @Override
	public void execute()
	throws MojoExecutionException {
    	if (this.compileDependencies == null || this.compileDependencies.length == 0)
    		throw new MojoExecutionException("The dependencies are missing");
    	if (this.compileDependencies.length == 1)
        	getLog().info("There is 1 dependency:");
    	else
    		getLog().info("There are " + this.compileDependencies.length + " dependencies:");
    	for (Dependency compileDependency: this.compileDependencies) {
    		getLog().info("-- "
		+ compileDependency.groupId
		+ " -- "
    	+ compileDependency.artifact
    	+ " -- "
    	+ compileDependency.version);
    	}

    	for (Dependency dependency: this.compileDependencies) {
    		lookForSuitableJars(dependency);
    	}

    	getLog().info("The compile dependencies are resolved as follows:");
    	StringBuilder compilePathBuilder = null;
    	for (Dependency dependency: this.compileDependencies) {
    		if (!this.dependencyMapping.containsKey(dependency))
    			throw new MojoExecutionException("Could not find a suitable jar for "
    					+ dependency.groupId
    					+ " -- "
    					+ dependency.artifact);
    		File jarFile = this.dependencyMapping.get(dependency);
    		if (compilePathBuilder == null)
    			compilePathBuilder = new StringBuilder();
    		else
    			compilePathBuilder.append(File.pathSeparator);
    		compilePathBuilder.append(jarFile.getAbsolutePath());
    		getLog().info("-- "
    				+ dependency.groupId
					+ " -- "
					+ dependency.artifact
					+ " -- "
					+ dependency.version);
    		getLog().info("   "
    				+ jarFile.getAbsolutePath());
    	}

    	String compileClassPath = compilePathBuilder.toString();

    	getLog().info("Compile class path is:");
    	getLog().info(compileClassPath);

    	this.project.getProperties().setProperty(
    			"java.compile.classpath",
    			compileClassPath);

    }

    /** the pattern for one explicit version of maximum three groups */
    static final Pattern oneVersionPattern = Pattern.compile(
    		"[0-9]+(\\.[0-9]+(\\.[0-9]+)?)?");

	private void lookForSuitableJars(Dependency dependency)
	throws MojoExecutionException {
		Matcher matcher = oneVersionPattern.matcher(dependency.version);
		if (matcher.matches()) {
			//single version
			this.lowerVersion = splitVersion(dependency.version);
			this.upperVersion = this.lowerVersion;
			this.lowerBoundIncluded = this.upperBoundIncluded = true;
		}
		else {
			//not an explicit version. Must be a range.
			boolean lowerOpen = dependency.version.startsWith("(");
			boolean lowerClosed = dependency.version.startsWith("[");
			boolean upperOpen = dependency.version.endsWith(")");
			boolean upperClosed = dependency.version.endsWith("]");
			if (!lowerOpen && !lowerClosed)
				throwIllegalVersion(dependency);
			if (!upperOpen && !upperClosed)
				throwIllegalVersion(dependency);
			this.lowerBoundIncluded = lowerClosed;
			this.upperBoundIncluded = upperClosed;
			String[] lowerGroups;
			String[] upperGroups;
			String versionProper = dependency.version.substring(
					1,
					dependency.version.length()-1);
			if (versionProper.startsWith(",")) {
				//there is no lower bound. Create as many 0's as we have groups.
				upperGroups = versionProper.substring(1).split("\\.");
				versionProper = concatenateDotted("0", upperGroups.length) + versionProper;
			}
			if (versionProper.endsWith(",")) {
				//there is no upper bound. Create as many Integer.MAX_VALUEs as we have groups.
				lowerGroups = versionProper.split("\\.");	//never mind the comma
				versionProper = versionProper + concatenateDotted(
						Integer.toString(Integer.MAX_VALUE), lowerGroups.length);
			}
			String[] bounds = versionProper.split(",");
			if (bounds.length != 2)
				throwIllegalVersion(dependency);
			lowerGroups = bounds[0].split("\\.");
			upperGroups = bounds[1].split("\\.");
			if (lowerGroups.length != upperGroups.length)
				throwIllegalVersion(dependency);
			this.lowerVersion = new int[lowerGroups.length];
			this.upperVersion = new int[upperGroups.length];
			for (int i=0; i<this.lowerVersion.length; i++) {
				this.lowerVersion[i] = Integer.parseInt(lowerGroups[i]);
				this.upperVersion[i] = Integer.parseInt(upperGroups[i]);
			}
		}
		String dependencyRootDirectoryName = this.settings.getLocalRepository()
				+ File.separator
				+ dependency.groupId.replaceAll("\\.", File.separator)
				+ File.separator
				+ dependency.artifact;
		File dependencyRootDirectory = new File(dependencyRootDirectoryName);
		if (!dependencyRootDirectory.isDirectory())
			throw new MojoExecutionException("Cannot find the dependency root directory for "
					+ dependency.groupId
					+ " / "
					+ dependency.artifact);
		StringBuilder stringBuilder = null;
		for (int group = 0; group < this.lowerVersion.length; group++) {
			if (stringBuilder == null)
				stringBuilder = new StringBuilder();
			else
				stringBuilder.append("\\.");
			stringBuilder.append("[0-9]+");
		}
		this.jarTestPattern = Pattern.compile(
				dependency.artifact
				+ "-("
				+ stringBuilder.toString()
				+ ")(.*)\\.jar");
		scanDirectory(dependency, dependencyRootDirectory);
	}

	private int[] splitVersion(String version) {
		String[] parts = version.split("\\.");
		int[] split = new int[parts.length];
		for (int i=0; i<parts.length; i++) {
			split[i] = Integer.parseInt(parts[i]);
		}
		return split;
	}

	private void throwIllegalVersion(Dependency dependency)
	throws MojoExecutionException {
		throw new MojoExecutionException(
				"Version "
				+ dependency.version
				+ " is not conform to rules");
	}

	/**
	 * Create a dotted string with a given number of groups
	 * @param string
	 * @param groups
	 * @return
	 */
	private String concatenateDotted(String string, int groups) {
		StringBuilder groupBuilder = null;
		for (int group = 0; group < groups; group++) {
			if (groupBuilder == null)
				groupBuilder = new StringBuilder();
			else
				groupBuilder.append(".");
			groupBuilder.append(string);
		}
		return groupBuilder.toString();
	}

	private void scanDirectory(Dependency dependency, File directory)
	throws MojoExecutionException {
		for (File file: directory.listFiles()) {
			if (file.isHidden())
				continue;
			if (file.isDirectory()) {
				scanDirectory(dependency, file);
				continue;
			}
			if (file.getName().endsWith(".jar")) {
				considerJar(dependency, file);
				continue;
			}
		}
	}

	private void considerJar(Dependency dependency, File jar)
	throws MojoExecutionException {
		Matcher matcher = this.jarTestPattern.matcher(jar.getName());
		if (!matcher.matches()) {
			return;
		}
		int[] versionParts = splitVersion(matcher.group(1));
		int lowerTest = compareVersion(this.lowerVersion, versionParts);
		if (lowerTest > 0)
			return;
		if (lowerTest == 0 && !this.lowerBoundIncluded)
			return;
		//lower test passes
		int upperTest = compareVersion(versionParts, this.upperVersion);
		if (upperTest > 0)
			return;
		if (upperTest == 0 && !this.upperBoundIncluded)
			return;
		//JAR is fully eligible
		if (this.dependencyMapping.containsKey(dependency)) {
			//there is already a selected JAR.
			//Let's see whether this one has a higher version or a higher time stamp.
			String currentBestName = this.dependencyMapping.get(dependency).getName();
			matcher = this.jarTestPattern.matcher(currentBestName);
			matcher.matches();	//or it would never have gotten into the map
			int[] currentBestVersion = splitVersion(matcher.group(1));
			if (compareVersion(currentBestVersion, versionParts) >= 0)
				//current one is equal or better. Don't overwrite.
				return;
		}
		this.dependencyMapping.put(dependency, jar);
	}

	/**
	 * Compare a version against a given limit
	 * @param testing
	 * @param limit
	 * @return -1 if testing<limit, 0 if testing==limit, +1 if testing>limit
	 */
	private int compareVersion(int[] testing, int[] limit)
	throws MojoExecutionException {
		if (testing.length != limit.length)
			throw new MojoExecutionException("Version length incompatibility");
		for (int i=0; i<testing.length; i++) {
			if (testing[i] < limit[i])
				return -1;
			if (testing[i] > limit[i])
				return +1;
		}
		//absolutely equal
		return 0;
	}

}
