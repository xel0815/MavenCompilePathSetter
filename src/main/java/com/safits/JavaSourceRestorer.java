package com.safits;

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
@Mojo( name = "restore-java-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false )
public class JavaSourceRestorer
extends AbstractMojo {

	public static final String SEPARATOR = "=";

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
	private MavenProject project;

	@Parameter( name = "os-name", required = false)
	private String osName;

	@Parameter( name = "directory", required = false)
	private String directory;

	@Override
	public void execute()
    throws MojoExecutionException {

		if (this.directory == null)
			this.directory = "src/main/java";
		getLog().info("Would process directory " + this.directory);

	}

}
