package com.safits;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

@Mojo( name = "pdf-to-pngs", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true )
public class PdfToPngs
extends AbstractMojo {

	private static final int FORMATVERSION = 1;

    @Parameter( property = "pdf", readonly = true, required = true )
    private String pdf;

    @Parameter( property = "pngs", readonly = true, required = true )
    private String pngs;

	@Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

	List<String> matchingFilenames = new ArrayList<>();

	File outputFile;

    @Override
	public void execute()
	throws MojoExecutionException {
    	String osName = System.getProperty("os.name");
    	if (!"linux".equalsIgnoreCase(osName))
    		throw new MojoExecutionException("Only implemented for Linux, not " + osName);

    	//check the presence of the indicated PDF
    	File pdfFile = new File(this.project.getBasedir(), this.pdf);
    	if (!pdfFile.exists() || !pdfFile.isFile() || !pdfFile.canRead())
    		throw new MojoExecutionException(this.pdf + " is not a readable PDF file");

    	//check the presence of the indicated output file
		this.outputFile = new File(this.pngs);
		if (this.outputFile.exists()) {
			//that exists. Maybe we don't have to build it.
			if (this.outputFile.lastModified() > pdfFile.lastModified()) {
				//correct. We don't have to do this.
				getLog().info("Output file " + this.outputFile.getAbsolutePath() + " is up to date.");
				return;
			}
		}

		getLog().info(
				"Creating "
				+ this.outputFile.getAbsolutePath()
				+ " from "
				+ pdfFile.getAbsolutePath());

    	String pdfBasename = pdfFile.getName();
    	if (pdfBasename.endsWith(".pdf"))
    		pdfBasename = pdfBasename.substring(0, pdfBasename.length()-4);

    	String[] pathComponents = System.getenv("PATH").split(File.pathSeparator);
    	String pdfToPpmBinary = null;
    	for (String pathComponent: pathComponents) {
    		File file = new File(pathComponent + File.separator + "pdftoppm");
    		if (file.exists() && file.canExecute()) {
    			getLog().info("Found pdftoppm in " + pathComponent);
    			pdfToPpmBinary = file.getAbsolutePath();
    			break;
    		}
    	}
    	if (pdfToPpmBinary == null)
    		throw new MojoExecutionException("Could not find an installed pdftoppm binary on the path");
    	File tempDir = null;
    	try {
    		Path tempDirPath = Files.createTempDirectory("pictet-");
    		tempDir = tempDirPath.toFile();
    		tempDir.deleteOnExit();
    		getLog().info("Using temporary storage in " + tempDir);
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Failed to create a temporary directory");
    	}

		ProcessBuilder processBuilder = new ProcessBuilder(
				pdfToPpmBinary, "-png", pdfFile.getAbsolutePath(), pdfBasename);
		processBuilder.directory(tempDir);
    	try {
    		Process process = processBuilder.inheritIO().start();
    		process.waitFor();
    	}
    	catch (Exception e) {
    		throw new MojoExecutionException("Failed to execute pdftoppm");
    	}

		getLog().info("PDF base name is " + pdfBasename);
    	Pattern filePattern = Pattern.compile(pdfBasename+"-[0-9]+\\.png");
		for (File file: tempDir.listFiles()) {
			Matcher matcher = filePattern.matcher(file.getName());
			if (matcher.matches()) {
				this.matchingFilenames.add(file.getName());
			}
		}
		if (this.matchingFilenames.isEmpty())
			throw new MojoExecutionException("Didn't find any matching files");
		Collections.sort(this.matchingFilenames);

		Display display = Display.getDefault();

		Rectangle equalBounds = null;

		List<Integer> fileLengthes = new ArrayList<>();

		//make sure that all files have the same extent
		for (String filename: this.matchingFilenames) {
			Image image = null;
			File file = null;
			try {
				file = new File(tempDir, filename);
				getLog().info("Including " + file.getAbsolutePath());
				image = new Image(display, file.getAbsolutePath());
				if (equalBounds == null)
					equalBounds = image.getBounds();
				else {
					Rectangle bounds = image.getBounds();
					if (equalBounds.width != bounds.width)
						throw new MojoExecutionException("Width of " + filename + " does not match others");
					if (equalBounds.height != bounds.height)
						throw new MojoExecutionException("Height of " + filename + " does not match others");
				}
				fileLengthes.add((int) file.length());
			}
			finally {
				if (image != null)
					image.dispose();
			}
		}

		//ready to start producing
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(this.outputFile);
		}
		catch (Exception e) {
			throw new MojoExecutionException("Could not create output file", e);
		}
		ByteBuffer intBuffer = ByteBuffer.allocate(
				4			//format version
				+ 4 		//width
				+ 4 		//height
				+ 4			//number of images
				+ 4 * this.matchingFilenames.size());
		try {
			//write the PNGS marker
			fileOutputStream.write("PNGS".getBytes());
			//write the format version
			intBuffer.putInt(FORMATVERSION);
			//write the width of each image
			intBuffer.putInt(equalBounds.width);
			//write the height of each image
			intBuffer.putInt(equalBounds.height);
			//write the number of files
			intBuffer.putInt(this.matchingFilenames.size());
			//write the length information of all files
			for (Integer length: fileLengthes)
				intBuffer.putInt(length);
			//write the combined integers to the file
			fileOutputStream.write(intBuffer.array());
			fileOutputStream.flush();
			//copy the data of these files
			for (String filename: this.matchingFilenames) {
				File file = new File(tempDir, filename);
				FileInputStream fileInputStream = new FileInputStream(file);
				byte[] bytes = fileInputStream.readAllBytes();
				fileOutputStream.write(bytes);
				fileOutputStream.flush();
				fileInputStream.close();
				file.delete();
			}
		}
		catch (Exception e) {
			throw new MojoExecutionException("Failure when writing output file", e);
		}
		finally {
			try {
				fileOutputStream.flush();
				fileOutputStream.close();
			}
			catch (Exception e) {
				//nested exception...
			}
		}

	}

}
