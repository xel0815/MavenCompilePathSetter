package com.safits;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Mojo( name = "set-compile-path", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = true )
public class CompilePathSetter
extends AbstractMojo {

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

	@Parameter( property = "rcp.product", readonly = true)
    private String rcpProductFilename;

	@Parameter( property = "rcp.repository", defaultValue = "export/repository/plugins", readonly = true)
    private String rcpRepositoryFilename;

	private Map<String, String> productPluginMap = new HashMap<>();

    @Override
	public void execute()
	throws MojoExecutionException {
    	getLog().info("Executing... the version is '" + this.project.getVersion() + "'");
    	File baseDir = this.project.getBasedir();
    	String here = baseDir.getAbsolutePath();
    	getLog().info("We are in " + here);
    	getLog().info(this.project.getProperties().toString());
    	checkRepository();
    	if (this.rcpProductFilename == null)
    		findRcpProductFilename(baseDir);
    	getLog().info("Using repository " + this.rcpRepositoryFilename);
    	parseProduct();
    	locatePlugins();
    	checkPluginCompleteness();
    	this.project.getProperties().setProperty("java.compile.classpath", buildClassPath());
    }

	private void checkRepository()
    throws MojoExecutionException {
    	File repository = new File(this.rcpRepositoryFilename);
    	if (!repository.isDirectory())
    		throw new MojoExecutionException(this.rcpRepositoryFilename + " is not an accessible repository directory");
	}

	/**
     * Try to find the one file that ends in ".product".
     * If there is more than one, or if there is none, then throw
     * @param baseDir where the project resides
     */
	private void findRcpProductFilename(File baseDir)
	throws MojoExecutionException {
		for (File file: baseDir.listFiles()) {
			if (file.getName().endsWith(".product")) {
				if (this.rcpProductFilename == null) {
					this.rcpProductFilename = file.getAbsolutePath();
				}
				else
					throw new MojoExecutionException("Don't know which product to choose in " + baseDir.getAbsolutePath()
					+". Set 'rcp.product' property.");
			}
		}
		if (this.rcpProductFilename == null)
			throw new MojoExecutionException("Cannot find an RCP product file in " + baseDir.getAbsolutePath());
	}

	private class ProductHandler
	extends DefaultHandler {

		boolean pluginsTriggered = false;

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
		throws SAXException {
			if ("plugins".equals(qName)) {
				this.pluginsTriggered = true;
				return;
			}
			if ("plugin".equals(qName)) {
				if (!this.pluginsTriggered)
					return;
				String id = attributes.getValue("id");
				CompilePathSetter.this.productPluginMap.put(id, null);	//to be resolved
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
		throws SAXException {
			if ("plugins".equals(qName))
				this.pluginsTriggered = false;
		}

	}

	/**
	 * Parse the product file and locate its plugins in a repository
	 */
	private void parseProduct()
	throws MojoExecutionException {
		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
		try {
			File rcpProductFile = new File(this.rcpProductFilename);
			SAXParser saxParser = saxParserFactory.newSAXParser();
			ProductHandler productHandler = new ProductHandler();
			saxParser.parse(rcpProductFile, productHandler);
		}
		catch (Exception e) {
			throw new MojoExecutionException("Problem parsing product file", e);
		}
		getLog().info("Product file parsed OK");
	}

	static final Pattern versionedJarPattern = Pattern.compile("(.*)_[0-9]+\\.[0-9]+\\.[0-9]+.*\\.jar");

	/**
	 * Check which plugins we can find in the repository
	 */
	private void locatePlugins() {
	    File rcpRepository = new File(this.rcpRepositoryFilename);
	    for (File jarFile: rcpRepository.listFiles()) {
	    	if (!jarFile.isFile())
	    		continue;
	    	String jarFilename = jarFile.getName();
	    	Matcher matcher = versionedJarPattern.matcher(jarFilename);
	    	if (!matcher.matches()) {
	    		getLog().warn(String.format("%s does not look like a plugin jar", jarFilename));
	    		continue;
	    	}
	    	//looks like a versioned JAR file allright. Do we required it?
	    	String id = matcher.group(1);
	    	if (this.productPluginMap.containsKey(id)) {
	    		//this one is required. Do we have different versions of those?
	    		String current = this.productPluginMap.get(id);
	    		if (current == null)
	    			//this one is the only one
	    			this.productPluginMap.put(id, jarFilename);
	    		else if (current.compareTo(jarFilename) < 0) {
	    			//this is a more recent one
	    			getLog().warn("Multiple jars for " + id + ", using " + jarFilename);
	    			this.productPluginMap.put(id, jarFilename);
	    		}
	    	}
	    }
	}

	/**
	 * See that there are no holes in the required plugin map
	 * @throws MojoExecutionException
	 */
	private void checkPluginCompleteness()
	throws MojoExecutionException {
		for (String id: this.productPluginMap.keySet()) {
			if (this.productPluginMap.get(id) == null)
				throw new MojoExecutionException("Could not resolve " + id);
			getLog().info("Using " + this.productPluginMap.get(id) + " for " + id);
		}
	}

	/**
	 * Create a class path, using the repository plugins we had located
	 * @return the full classpath
	 */
    private String buildClassPath() {
    	StringBuilder sb = null;
		for (String jarFilename: this.productPluginMap.values()) {
			if (sb == null)
				sb = new StringBuilder();
			else
				sb.append(File.pathSeparator);
			sb.append(this.rcpRepositoryFilename);
			sb.append(File.separator);
			sb.append(jarFilename);
		}
		return sb.toString();
	}

}
