package com.safits;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * The compile path setter resolves compile dependencies by creating a path which
 * contains all jars found in the RCP resources, both common and OS specific.
 *
 * @author Friedrich Gesell
 *         friedrich.gesell@safits.be
 *
 */
@Mojo( name = "set-classpath-from-single-directory", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = true )
public class CompilePathSetterSingle
extends AbstractMojo {

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( name = "os-name", required = false)
    private String osName;

    @Parameter( name = "directory", required = true)
    private String directory;

    @Parameter( name = "dependencies", required = true)
    private String[] dependencies;

    final private Map<String,String> mapping = new TreeMap<>();

    @Override
	public void execute()
	throws MojoExecutionException {

    	if (this.osName == null)
    		this.osName = System.getProperty("os.name").toLowerCase();
    	getLog().info("Building for " + this.osName);

    	File directory = new File(this.directory);
    	if (!directory.isDirectory())
    		throw new MojoExecutionException("Cannot identify resolution directory " + directory.getAbsolutePath());
    	getLog().info("Resolving from " + directory.getAbsolutePath());

    	StringBuilder compilePathStringBuilder = null;

    	int dependencyMaxLength = 0;
    	for (String dependency: this.dependencies) {
    		dependencyMaxLength = Math.max(dependencyMaxLength, dependency.length());
    		compilePathStringBuilder = addDependency(compilePathStringBuilder, directory, dependency);
    	}

    	getLog().info("Dependencies[" + this.dependencies.length + "]:");
    	String format = String.format("%%-%ds -> %%s", dependencyMaxLength);
    	for (String dependency: this.dependencies) {
    		String formatted = String.format(format, dependency, this.mapping.get(dependency));
    		getLog().info(formatted);
    	}

    	String compileClassPath = compilePathStringBuilder.toString();

    	getLog().info("Compile class path is:");
    	getLog().info(compileClassPath);

    	this.project.getProperties().setProperty(
    			"java.compile.classpath",
    			compileClassPath);

    }

    private StringBuilder addDependency(
    		StringBuilder stringBuilder,
    		File directory,
    		String dependency)
    throws MojoExecutionException {
    	String jarFilename = dependency.endsWith(".jar")?
    			dependency : dependency + ".jar";
    	File jar = new File(directory, jarFilename);
    	if (!jar.isFile()) {
    		//not a direct hit. Let's go look for jars that start with the dependency name
    		jar = null;
    		for (File file: directory.listFiles()) {
    			if (!file.isFile())
    				continue;
    			if (!file.getName().endsWith(".jar"))
    				continue;
    			if (!file.getName().startsWith(dependency))
    				continue;
    			//match. Is its name higher?
    			if (jar == null)
    				jar = file;
    			else if (jar.getName().compareTo(file.getName()) < 0)
    				jar = file;
    		}
    	}
    	if (jar == null || !jar.isFile())
    		throw new MojoExecutionException("Cannot resolve " + dependency);
    	if (stringBuilder == null)
    		stringBuilder = new StringBuilder();
    	else
    		stringBuilder.append(File.pathSeparator);
    	try {
    		stringBuilder.append(jar.getCanonicalPath());
    		this.mapping.put(dependency, jar.getCanonicalPath());
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Cannot resolve " + dependency, e);
    	}
    	return stringBuilder;
    }

}
