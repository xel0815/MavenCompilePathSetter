package com.safits;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

@Mojo( name = "export-rcp", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true )
public class RCPExporter
extends AbstractMojo {

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${settings}", readonly = true )
    private Settings settings;

    @Parameter( property = "product", readonly = true )
    private String product;

    private File exportDirectory;

    private File versionDirectory;

    private File configurationDirectory;

	private Element productElement;

    @Override
	public void execute()
	throws MojoExecutionException {
    	parseProductFile();
    	createExportDirectory();
    	createVersionDirectory();
    	createConfigurationDirectory();
    	createConfigIni();
    }

    /**
     * Parse the product file
     */
    private void parseProductFile()
    throws MojoExecutionException {
    	File productFile = null;
    	if (this.product == null) {
    		//locate the one and only product file
    		for (File file: this.project.getBasedir().listFiles()) {
    			if (!file.isFile())
    				continue;
    			if (file.isHidden())
    				continue;
    			if (!file.getName().endsWith(".product"))
    				continue;
    			//this is a product file
    			if (productFile != null)
    				//we already had one
    				throw new MojoExecutionException("Ambiguous product files");
    			productFile = file;
    		}
    	}
    	else {
    		//explicitly given
    		productFile = new File(this.project.getBasedir(), this.product);
    		if (!productFile.exists())
    			throw new MojoExecutionException("Product file " + this.product + " does not exist");
    		if (!productFile.isFile())
    			throw new MojoExecutionException("Invalid product file");
    	}
    	//ready to parse the product file
    	SAXBuilder saxBuilder = new SAXBuilder();
    	Document productDocument;
    	try {
    		productDocument = saxBuilder.build(productFile);
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Failed to parse product file", e);
    	}
    	getLog().info("Parsed product file OK");
    	this.productElement = productDocument.detachRootElement();
    	if (!"product".equals(this.productElement.getName()))
    		throw new MojoExecutionException("Unexpected root element in product file");
    }

	/**
     * Create a directory 'export' in the project's base directory
     * @throws MojoExecutionException
     */
	private void createExportDirectory()
	throws MojoExecutionException {
		this.exportDirectory = new File(this.project.getBasedir(), "export");
		if (this.exportDirectory.exists()) {
			if (this.exportDirectory.isDirectory())
				//all is well
				return;
			throw new MojoExecutionException("File 'export' exists, but is not a directory");
		}
		if (!this.exportDirectory.mkdir())
			throw new MojoExecutionException("Attempt to create directory 'export' failed");
		getLog().info("Directory export created");
	}

	/**
	 * Create a version directory in export
	 */
	private void createVersionDirectory()
	throws MojoExecutionException {
		this.versionDirectory = new File(this.exportDirectory, this.project.getVersion());
		if (this.versionDirectory.exists()) {
			if (this.versionDirectory.isDirectory())
				//all is well
				return;
			throw new MojoExecutionException(
					"File "
				    + this.versionDirectory.getAbsolutePath()
				    + " exists, but is not a directory");
		}
		if (!this.versionDirectory.mkdir())
			throw new MojoExecutionException(
					"Attempt to create directory "
					+ this.versionDirectory.getAbsolutePath()
					+ " failed");
		getLog().info(
				"Directory "
				+ this.versionDirectory.getAbsolutePath()
				+ " created");
	}

	/**
	 * Create a configuration directory
	 */
    private void createConfigurationDirectory()
    throws MojoExecutionException {
		this.configurationDirectory = new File(this.versionDirectory, "configuration");
		if (this.configurationDirectory.exists()) {
			if (this.configurationDirectory.isDirectory())
				//all is well
				return;
			throw new MojoExecutionException(
					"File "
				    + this.configurationDirectory.getAbsolutePath()
				    + " exists, but is not a directory");
		}
		if (!this.configurationDirectory.mkdir())
			throw new MojoExecutionException(
					"Attempt to create directory "
					+ this.configurationDirectory.getAbsolutePath()
					+ " failed");
		getLog().info(
				"Directory "
				+ this.configurationDirectory.getAbsolutePath()
				+ " created");
	}

    /**
     * Create a config.ini file in the configuration directory
     * and fill it with data obtained from the product file.
     */
    private void createConfigIni()
    throws MojoExecutionException {
    	String productId = this.productElement.getAttributeValue("id");
    	if (productId == null)
    		throw new MojoExecutionException("Missing product id in product file");
    	String name = this.productElement.getAttributeValue("name");
    	if (name == null)
    		throw new MojoExecutionException("Missing product name in product file");
    	String application = this.productElement.getAttributeValue("name");
    	if (application == null)
    		throw new MojoExecutionException("Missing application name in product file");
    	File configIni = new File(this.configurationDirectory, "config.ini");
    	if (configIni.exists())
    		configIni.delete();
    	List<String> osgiBundles = new ArrayList<>();
    	Element pluginsElement = this.productElement.getChild("plugins");
    	List<Element> pluginElements = pluginsElement.getChildren("plugin");
    	for (Element pluginElement: pluginElements) {
    		osgiBundles.add(pluginElement.getAttributeValue("id"));
    	}
    	Collections.sort(osgiBundles);
    	PrintWriter printWriter = null;
    	try {
    		String lastBundle = osgiBundles.get(osgiBundles.size()-1);
    		printWriter = new PrintWriter(configIni);
    		printWriter.println("#Product Runtime Configuration File");
    		printWriter.printf("eclipse.product=%s%n", productId);
    		printWriter.printf("osgi.splashPath=platform:/base/plugins/%s%n", name);
    		printWriter.printf("osgi.bundles.defaultStartLevel=4%n");
    		printWriter.printf("eclipse.application=%s%n", application);
    		printWriter.printf("osgi.bundles=");
    		String indent = "";
    		for (String osgiBundle: osgiBundles) {
    			printWriter.printf("%s%s", indent, osgiBundle);
    			if (osgiBundle != lastBundle)
    				printWriter.printf(",\\");
    			printWriter.println();
    			if (indent.isEmpty())
    				indent = "  ";
    		}
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Failed to create config.ini", e);
    	}
    	finally {
    		if (printWriter != null)
    			printWriter.close();
    	}
    	getLog().info("config.ini created");
    }

}
