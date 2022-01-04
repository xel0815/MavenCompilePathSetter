package com.safits;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Parameter( name = "resources", required = false)
    private String[] resources;

    private File pictetDirectory;

    private File versionDirectory;

    private File versionResourcesDirectory;

    private File configurationDirectory;

    private File resourceDirectory;

    private File resourceRcpDirectory;

    private File osSpecificResourceRcpDirectory;

	private Element productElement;

	private Element launcherElement;

	private String launcherName;

	private Map<String,File> repositoryResolutions;

	private String osName = System.getProperty("os.name");
	
    @Override
	public void execute()
	throws MojoExecutionException {
    	getRepositoryResolutions();
    	linkResourceDirectories();
    	parseProductFile();
    	createPictetDirectory();
    	createVersionDirectory();
    	createConfigurationDirectory();
    	copyConfigIni();
    	createProductIni();
    	copyLauncher();
    	copyPlugins();
    	copyResources();
    }

    /**
     * Is this MS Windows?
     * @return true if the system property indicates it
     */
    private boolean thisIsMSWindows() {
    	return this.osName.toLowerCase().startsWith("windows");
    }
    
    
    /**
     * The CompilePathSetter may have resolved dependencies and then deposited its resolutions
     * in a project property. Let's look for that property and see what we can resolve through the repository.
     */
	private void getRepositoryResolutions()
	throws MojoExecutionException {
    	String repositoryResolutions = this.project.getProperties().getProperty(
    			CompilePathSetter.REPOSITORY_RESOLUTIONS);
    	if (repositoryResolutions == null)
    		return;
    	this.repositoryResolutions = new HashMap<>();
    	String[] resolutions = repositoryResolutions.split(File.pathSeparator);
    	for (String resolution: resolutions) {
    		String[] artifactAndFilename = resolution.split(CompilePathSetter.SEPARATOR);
    		File artifactFile = new File(artifactAndFilename[1]);
    		if (!artifactFile.isFile())
    			throw new MojoExecutionException("Cannot resolve repository file " + artifactFile.getAbsolutePath());
    		this.repositoryResolutions.put(artifactAndFilename[0], artifactFile);
    	}
	}

	private void linkResourceDirectories()
	throws MojoExecutionException {
    	this.resourceDirectory = new File("resources");
    	if (!this.resourceDirectory.isDirectory())
    		throw new MojoExecutionException("Cannot find resource directory");
    	this.resourceRcpDirectory = new File(this.resourceDirectory, "rcp");
    	if (!this.resourceRcpDirectory.isDirectory())
    		throw new MojoExecutionException("Cannot find resource RCP directory");
    	this.osSpecificResourceRcpDirectory = new File(this.resourceRcpDirectory,
    			this.osName.toLowerCase());
    	if (!this.osSpecificResourceRcpDirectory.isDirectory())
    		throw new MojoExecutionException("Cannot find OS specific resource RCP directory for " + 
    				this.osName);
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
    	this.launcherElement = this.productElement.getChild("launcher");
    	if (this.launcherElement == null)
    		throw new MojoExecutionException("Could not find launcher element in product file");
    	this.launcherName = this.launcherElement.getAttributeValue("name");
    	getLog().info("Launcher name is " + this.launcherName);
    }

	/**
     * Create the pictet export directory in the project's base directory
     * @throws MojoExecutionException
     */
	private void createPictetDirectory()
	throws MojoExecutionException {
		this.pictetDirectory = new File(this.project.getBasedir(), "pictet");
		if (this.pictetDirectory.exists()) {
			if (this.pictetDirectory.isDirectory())
				//all is well
				return;
			throw new MojoExecutionException("File 'pictet' exists, but is not a directory");
		}
		if (!this.pictetDirectory.mkdir())
			throw new MojoExecutionException("Attempt to create pictet directory failed");
		getLog().info("Pictet directory created");
	}

	/**
	 * Create a version directory in the pictet directory
	 */
	private void createVersionDirectory()
	throws MojoExecutionException {
		String version = this.productElement.getAttributeValue("version")
				.replaceAll("\\.qualifier", "");
		this.versionDirectory = new File(this.pictetDirectory, version);
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
     * Copy a config.ini file in the from OS dependent resource directory
     */
    private void copyConfigIni()
    throws MojoExecutionException {
    	File resourceRcpOsDirectory = new File(this.resourceRcpDirectory, 
    			this.osName.toLowerCase());
    	File srcConfigIni = new File(resourceRcpOsDirectory, "config.ini");
    	if (!srcConfigIni.isFile())
    		throw new MojoExecutionException("Cannot find config.ini resource for target OS '" + 
    				this.osName + "'");
    	File dstConfigIni = new File(this.configurationDirectory, "config.ini");
    	copyFile(srcConfigIni, dstConfigIni);
    	getLog().info("config.ini created");
    }

    private void createProductIni()
    throws MojoExecutionException {
    	File launcherIniFile = new File(this.versionDirectory, this.launcherName + ".ini");
    	PrintWriter iniWriter = null;
    	try {
    		iniWriter = new PrintWriter(launcherIniFile);
    		iniWriter.println("-clearPersistedState");
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Could not create " + launcherIniFile.getAbsolutePath(), e);
    	}
    	finally {
    		if (iniWriter != null) {
    			iniWriter.flush();
    			iniWriter.close();
    		}
    	}
    	getLog().info(launcherIniFile.getName() + " created");
	}

    private void copyLauncher()
    throws MojoExecutionException {
    	File launchFile = new File(this.osSpecificResourceRcpDirectory, "launcher-executable");
    	if (!launchFile.isFile())
    		throw new MojoExecutionException("Cannot find " + launchFile.getAbsolutePath());
    	File copiedLauncher = copyFile(launchFile, new File(
    			this.versionDirectory, 
    			thisIsMSWindows()?
    					this.launcherName + ".exe" :
    						this.launcherName));
    	copiedLauncher.setExecutable(true);
    }

    private void copyPlugins()
    throws MojoExecutionException {

    	File exportPlugins = new File(this.versionDirectory, "plugins");
    	exportPlugins.mkdir();

    	Map<String,File> available = new HashMap<>();
    	if (this.repositoryResolutions != null) {
    		for (Entry<String, File> entry: this.repositoryResolutions.entrySet()) {
    			available.put(entry.getKey(), entry.getValue());
    		}
    	}

    	//map the available common plugins and the OS dependent launcher artifacts
    	final Pattern pluginNameAndVersion = Pattern.compile("(.*?)_([0-9]+\\..*\\.jar)");
    	List<File> requiredFiles = new ArrayList<>();
    	File commonResources = new File("resources/rcp/common/plugins");
    	if (!commonResources.isDirectory())
    		throw new MojoExecutionException("Cannot find directory " + commonResources.getAbsolutePath());
    	for (File commonResource: commonResources.listFiles()) {
    		Matcher matcher = pluginNameAndVersion.matcher(commonResource.getName());
    		if (!matcher.matches())
    			throw new MojoExecutionException("Unexpected resource file name " + commonResource.getName());
    		available.put(matcher.group(1), commonResource);
    		getLog().info(matcher.group(1) + " is available as " + commonResource.getName());
    	}
    	for (File osSpecificResource: this.osSpecificResourceRcpDirectory.listFiles()) {
    		Matcher matcher = pluginNameAndVersion.matcher(osSpecificResource.getName());
    		if (!matcher.matches())
    			//that will be config.ini or the launcher executable or something like that
    			continue;
    		available.put(matcher.group(1), osSpecificResource);
    		getLog().info(matcher.group(1) + " is available as " + osSpecificResource.getName());
    	}
    	getLog().info("");
    	List<String> osgiBundles = new ArrayList<>();
    	Element pluginsElement = this.productElement.getChild("plugins");
    	List<Element> pluginElements = pluginsElement.getChildren("plugin");
    	for (Element pluginElement: pluginElements) {
    		String osFilter = pluginElement.getAttributeValue("os");
    		if (osFilter != null && !osFilter.equalsIgnoreCase(this.osName))
    			//that one is not eligible on this platform
    			continue;    		
    		osgiBundles.add(pluginElement.getAttributeValue("id"));
    	}
    	Collections.sort(osgiBundles);
    	String productName = this.productElement.getAttributeValue("name");
    	for (String osgiBundle: osgiBundles) {
    		if (productName.equals(osgiBundle))
    			//we don't need that one, the Main JAR Creator has already built it
    			continue;
    		File bundleFile = available.get(osgiBundle);
    		if (bundleFile == null)
    			throw new MojoExecutionException("Cannot resolve " + osgiBundle);
    		getLog().info(osgiBundle + " is resolved by " + bundleFile.getName());
    		requiredFiles.add(bundleFile);
    	}
    	//copy
    	exportPlugins.mkdir();
    	for (File requiredFile: requiredFiles) {
    		copyFile(requiredFile, new File(exportPlugins, requiredFile.getName()));
    	}

    	//copy the OS specific launch directory
    	File osSpecificLaunchDirectory = new File(this.osSpecificResourceRcpDirectory, "launch");
    	if (!osSpecificLaunchDirectory.isDirectory())
    		throw new MojoExecutionException("Cannot find " + osSpecificLaunchDirectory.getAbsolutePath());
    	for (File launchFile: osSpecificLaunchDirectory.listFiles()) {
    		if (launchFile.isDirectory()) {
    			File launchTargetDirectory = new File(exportPlugins, launchFile.getName());
    			launchTargetDirectory.mkdir();
    			copyDeep(launchFile, launchTargetDirectory);
    		}
    		else if (launchFile.isFile()) {
    			File launchTargetFile = new File(exportPlugins, launchFile.getName());
    			copyFile(launchFile, launchTargetFile);
    		}
    	}

    }

	private void copyDeep(File srcDir, File dstDir)
	throws MojoExecutionException {
		getLog().info("Copying deep...");
		getLog().info("-- srcDir is " + srcDir.getAbsolutePath());
		getLog().info("-- dstDir is " + dstDir.getAbsolutePath());
    	for (File file: srcDir.listFiles()) {
    		getLog().info("Looking at " + file.getAbsolutePath());
    		if (file.isDirectory()) {
    			//directory
    			File deepDir = new File(dstDir, file.getName());
    			deepDir.mkdir();
    			copyDeep(file, deepDir);
    		}
    		else if (file.isFile()) {
    			//just a file
    			copyFile(file, new File(dstDir, file.getName()));
    		}
    		else
    			throw new MojoExecutionException(file.getAbsolutePath() + " is not a file and not a directory");
    	}
    }

	private File copyFile(File src, File dst)
	throws MojoExecutionException {
    	byte[] fileBytes = null;
    	try {
    		getLog().info("Copying " + src.getAbsolutePath());
    		FileInputStream in = new FileInputStream(src);
    		fileBytes = new byte[in.available()];
    		in.read(fileBytes);
    		in.close();
    		getLog().info("     to " + dst.getAbsolutePath());
    		FileOutputStream out = new FileOutputStream(dst);
    		out.write(fileBytes);
    		out.flush();
    		out.close();
    		return dst;
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Could not copy file " + src.getAbsolutePath(), e);
    	}
	}

	private void copyResources()
	throws MojoExecutionException {
		if (this.resources == null || this.resources.length == 0)
			return;
		if (this.versionResourcesDirectory == null) {
			this.versionResourcesDirectory = new File(this.versionDirectory, "resources");
			this.versionResourcesDirectory.mkdir();
		}
		for (String resource: this.resources) {
			File src = new File(this.resourceDirectory, resource);
			if (!src.isFile())
				throw new MojoExecutionException("Cannot find " + src.getAbsolutePath());
			File dst = new File(this.versionResourcesDirectory, src.getName());
			copyFile(src, dst);
		}
	}

}
