package com.safits;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

/**
 * The compile path setter resolves compile dependencies by creating a path which
 * contains all jars found in the RCP resources, both common and OS specific.
 *
 * @author Friedrich Gesell
 *         friedrich.gesell@safits.be
 *
 */
@Mojo( name = "set-compile-path", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = true )
public class CompilePathSetter
extends AbstractMojo {

	public static final String REPOSITORY_RESOLUTIONS = "repository.resolutions";

	public static final String SEPARATOR = "=";

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${settings}", readonly = true )
    private Settings settings;

    @Parameter( name = "os-name", required = false)
    private String osName;

    @Parameter( name = "repository-dependencies", readonly = true)
    private Dependency[] repositoryDependencies;

    public static class Dependency {
    	String groupId;
    	String artifactId;
    	String version;
    }

    @Override
	public void execute()
	throws MojoExecutionException {

    	if (this.osName == null)
    		this.osName = System.getProperty("os.name").toLowerCase();
    	getLog().info("Building for " + this.osName);

    	File commonPluginResourceDirectory = new File("resources/rcp/common/plugins");
    	if (!commonPluginResourceDirectory.isDirectory())
    		throw new MojoExecutionException("Cannot find " + commonPluginResourceDirectory.getAbsolutePath());

    	File osSpecificResourceDirectory = new File("resources/rcp/" + this.osName);
    	if (!osSpecificResourceDirectory.isDirectory())
    		throw new MojoExecutionException("Cannot find " + osSpecificResourceDirectory.getAbsolutePath());

    	if (this.repositoryDependencies == null)
    		getLog().info("No repository dependencies");
    	else {
    		getLog().info("Repository dependencies[" + this.repositoryDependencies.length + "]");
    		for (Dependency dependency: this.repositoryDependencies) {
    			getLog().info("-- groupId    : " + dependency.groupId);
    			getLog().info("-- artifactId : " + dependency.artifactId);
    			getLog().info("-- version    : " + dependency.version);
    		}
    	}

    	Map<String,File> repositoryDependencies = resolveRepositoryDependencies();
    	if (repositoryDependencies != null) {
    		StringBuilder resolverBuilder = null;
    		for (Entry<String,File> entry: repositoryDependencies.entrySet()) {
    			if (resolverBuilder == null)
    				resolverBuilder = new StringBuilder();
    			else
    				resolverBuilder.append(File.pathSeparator);
    			resolverBuilder.append(entry.getKey());
    			resolverBuilder.append(SEPARATOR);
    			resolverBuilder.append(entry.getValue().getAbsolutePath());
    		}

    		if (resolverBuilder != null) {
    			getLog().info("Resolver: " + resolverBuilder.toString());
    			this.project.getProperties().setProperty(
    					REPOSITORY_RESOLUTIONS,
    					resolverBuilder.toString());
    		}
    	}

    	StringBuilder compilePathStringBuilder = null;
    	if (repositoryDependencies != null) {
    		for (Entry<String,File> entry: repositoryDependencies.entrySet()) {
    			if (compilePathStringBuilder == null)
    				compilePathStringBuilder = new StringBuilder();
    			else
    				compilePathStringBuilder.append(File.pathSeparator);
    			compilePathStringBuilder.append(entry.getValue().getAbsolutePath());
    		}
    	}

    	compilePathStringBuilder = addAllJars(compilePathStringBuilder, commonPluginResourceDirectory);
    	compilePathStringBuilder = addAllJars(compilePathStringBuilder, osSpecificResourceDirectory);
    	String compileClassPath = compilePathStringBuilder.toString();

    	getLog().info("Compile class path is:");
    	getLog().info(compileClassPath);

    	this.project.getProperties().setProperty(
    			"java.compile.classpath",
    			compileClassPath);

    }

    /**
     * Resolve the repository dependencies
     * @return a map which maps the artifact name to the file
     */
    private Map<String,File> resolveRepositoryDependencies()
    throws MojoExecutionException {
    	Map<String,File> map = new HashMap<>();
    	if (this.repositoryDependencies == null || this.repositoryDependencies.length == 0)
    		return map;
    	for (Dependency dependency: this.repositoryDependencies) {
    		File groupIdDirectory = new File(
    				this.settings.getLocalRepository()
    				+ File.separator
    				+ dependency.groupId.replaceAll("\\.", File.separator));
    		if (!groupIdDirectory.isDirectory())
    			throw new MojoExecutionException(
    					"Cannot find repository dependency directory "
    							+ groupIdDirectory.getAbsolutePath());
    		File artifactDirectory = new File(groupIdDirectory, dependency.artifactId);
    		if (!artifactDirectory.isDirectory())
    			throw new MojoExecutionException(
    					"Cannot find repository dependency directory "
    							+ artifactDirectory.getAbsolutePath());
    		File versionedDirectory = new File(artifactDirectory, dependency.version);
    		if (!versionedDirectory.isDirectory())
    			throw new MojoExecutionException(
    					"Cannot find repository dependency directory "
    							+ versionedDirectory.getAbsolutePath());
    		File dependencyJar = new File(
    				versionedDirectory,
    				dependency.artifactId
    				+ "-"
    				+ dependency.version
    				+ ".jar");
    		if (!dependencyJar.isFile())
    			throw new MojoExecutionException(
    					"Cannot find repository dependency "
    							+ dependencyJar.getAbsolutePath());
    		getLog().info("-- Resolved with " + dependencyJar.getAbsolutePath());
    		map.put(dependency.artifactId, dependencyJar);
    	}
    	return map;
    }

    /**
     * Recursively add all JARs of a given directory to the string builder.
     * Create such a string builder if necessary.
     * @param stringBuilder to what to add. Can be null -> create it
     * @param directory
     */
    private StringBuilder addAllJars(StringBuilder stringBuilder, File directory) {
    	for (File file: directory.listFiles()) {
    		if (file.isDirectory()) {
    			stringBuilder = addAllJars(stringBuilder, file);
    		}
    		else if (file.getName().endsWith(".jar")) {
    			if (stringBuilder == null)
    				stringBuilder = new StringBuilder();
    			else
    				stringBuilder.append(File.pathSeparator);
    			stringBuilder.append(file.getAbsolutePath());
    		}
    	}
    	return stringBuilder;
    }

}
