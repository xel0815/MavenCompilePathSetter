package com.safits;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo( name = "create-main-jar", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = false )
public class MainJarCreator
extends AbstractMojo {

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

	@Parameter( name = "additions", required = false )
	private String[] additions;

	@Parameter( name = "classes", required = true )
	private String classes;

    @Override
	public void execute()
	throws MojoExecutionException {

    	//Check that we run on Linux
    	String osName = System.getProperty("os.name");
    	if (!"linux".equalsIgnoreCase(osName))
    		throw new MojoExecutionException("Only implemented for Linux, not " + osName);

    	//find the jar binary on the path
    	String[] pathComponents = System.getenv("PATH").split(File.pathSeparator);
    	File jarBinary = null;
    	for (String pathComponent: pathComponents) {
    		File file = new File(pathComponent + File.separator + "jar");
    		if (file.exists() && file.canExecute()) {
    			getLog().info("Found jar in " + pathComponent);
    			jarBinary = file;
    			break;
    		}
    	}
    	if (jarBinary == null)
    		throw new MojoExecutionException("Could not find an installed jar binary on the path");

    	getLog().info("Classes directory will be " + this.classes);

    	if (this.additions == null)
    		getLog().info("No additions");
    	else {
    		getLog().info("Additions[" + this.additions.length + "]");
    		for (String addition: this.additions) {
        		getLog().info("-- " + addition);
    		}
    	}

    	/* debug */ System.exit(0);

    	Calendar calendar = Calendar.getInstance();
    	calendar.setTimeInMillis(System.currentTimeMillis());
    	String fileName = String.format(
    			"export/%s/plugins/%s_%s_%04d%02d%02d%02d%02d.jar",
    			this.project.getVersion(),
    			this.project.getArtifactId(),
    			this.project.getVersion(),
    			calendar.get(Calendar.YEAR),
    			calendar.get(Calendar.MONTH) + 1,	//as JANUARY is 0
    			calendar.get(Calendar.DAY_OF_MONTH),
    			calendar.get(Calendar.HOUR_OF_DAY),
    			calendar.get(Calendar.MINUTE));

    	List<String> jarCommands = new ArrayList<>();
    	jarCommands.add(jarBinary.getAbsolutePath());
    	jarCommands.add("--create");
    	jarCommands.add("--file=" + fileName);
    	jarCommands.add("--manifest=META-INF/MANIFEST.MF");
    	if (this.additions != null) {
    		for (String addition: this.additions) {
    			jarCommands.add(addition);
    		}
    	}
    	jarCommands.add("-C");		//switch directory
    	jarCommands.add(this.classes);
    	jarCommands.add(".");

		ProcessBuilder processBuilder = new ProcessBuilder(jarCommands);
    	try {
    		Process process = processBuilder.inheritIO().start();
    		process.waitFor();
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Failed to execute jar creation");
    	}

    	File jarFile = new File(fileName);
    	if (jarFile.isFile())
    		getLog().info("Produced " + fileName);
    	else
    		throw new MojoExecutionException("Production of " + fileName + " failed");

    	/* debug */
    	System.exit(0);

    }

}
