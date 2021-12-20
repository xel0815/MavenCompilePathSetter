package com.safits;

import java.util.Calendar;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo( name = "set-timestamp", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false )
public class TimeStamp
extends AbstractMojo {

	public static final String TIMESTAMP_PROPERTY = "pictet.timestamp";

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Override
	public void execute()
	throws MojoExecutionException {

    	Calendar calendar = Calendar.getInstance();
    	calendar.setTimeInMillis(System.currentTimeMillis());
    	String timeStamp = String.format(
    			"%04d%02d%02d-%02d%02d",
    			calendar.get(Calendar.YEAR),
    			calendar.get(Calendar.MONTH) + 1,	//as JANUARY is 0
    			calendar.get(Calendar.DAY_OF_MONTH),
    			calendar.get(Calendar.HOUR_OF_DAY),
    			calendar.get(Calendar.MINUTE));

    	this.project.getProperties().setProperty(
    			TIMESTAMP_PROPERTY,
    			timeStamp);

    	getLog().info("Set time stamp to " + timeStamp);

    }

}
