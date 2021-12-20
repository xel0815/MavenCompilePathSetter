package com.safits;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo( name = "extract-storeclasses" )
public class StoreClassExtractor
extends AbstractMojo {

    /**
     * The folder where the sources of the server's store classes are found
     */
    @Parameter( property = "server.store.sources", defaultValue = "" )
    private String serverSources;

    /**
     * The folder where the sources of the client's store classes are to be deposited
     */
    @Parameter( property = "client.store.sources", defaultValue = "" )
    private String clientSources;

	public static final String CLIENT_OFF = "//-client";
	public static final String CLIENT_ON = "//+client";

    @Override
	public void execute()
	throws MojoExecutionException
    {
    	if (this.serverSources == null || this.serverSources.isEmpty())
    		throw new MojoExecutionException("server.sources definition is missing");

    	if (this.clientSources == null || this.clientSources.isEmpty())
    		throw new MojoExecutionException("client.sources definition is missing");

        getLog().info( "Server sources at " + this.serverSources );
        getLog().info( "Client sources at " + this.clientSources );
        try {
        	convert(this.serverSources, this.clientSources);
        }
        catch (Exception e) {
        	throw new MojoExecutionException(e.getMessage());
        }
    }

	void convert(String pathFrom, String pathTo)
	throws Exception {
		File fileFrom = new File(pathFrom);
		if (fileFrom.isDirectory()) {
			//recurse
			Map<String,File> sortedFileMap = new TreeMap<>();
			for (File file: fileFrom.listFiles()) {
				sortedFileMap.put(file.getName(), file);
			}
			for (String fileName: sortedFileMap.keySet()) {
				File file = sortedFileMap.get(fileName);
				convert(pathFrom + File.separator +file.getName(),
						pathTo + File.separator + file.getName());
			}
			return;
		}
		//plain file
		if (!pathFrom.endsWith(".java"))
			return;

		boolean noReplication = false;

		List<String> lines = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(pathFrom));
		try {
			boolean clientOn = true;
			for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
				String lineTrim = line.trim();
				if ("@NoReplication".equals(lineTrim)) {
					noReplication = true;
					continue;
				}
				if (CLIENT_OFF.equals(lineTrim)) {
					clientOn = false;
					continue;
				}
				if (CLIENT_ON.equals(lineTrim)) {
					clientOn = true;
					continue;
				}
				if (!clientOn)
					continue;
				if (line.startsWith("import ")) {
					if (lineTrim.endsWith(CLIENT_OFF))
						continue;
					if (!lineTrim.endsWith(CLIENT_ON)) {
						if (!line.startsWith("import com.safits.storekit."))
							continue;
					}
				}

				if (line.endsWith(CLIENT_ON)) {
					line = line.substring(0, line.length() - CLIENT_ON.length()).trim();
				}

				lines.add(line);
			}
		}
		finally {
			bufferedReader.close();
		}

		if (noReplication)
			return;

		File fileTo = new File(pathTo);
		if (fileTo.exists()) {
			if (fileTo.lastModified() >= fileFrom.lastModified()) {
				getLog().info(String.format("File %s is up to date", pathTo));
				return;
			}
		}

		getLog().info(String.format("Now converting %s to %s...", pathFrom, pathTo));

		PrintWriter clientWriter = new PrintWriter(pathTo);

		clientWriter.println("//---------------------------------------------------------------------------------------");
		clientWriter.println("// Generated file. Manual edits are sure to be overwritten by the production process.");
		clientWriter.println("//---------------------------------------------------------------------------------------");

		boolean lineWasBlank = false;
		boolean doingDataStoreAttribute = false;

		for (String line: lines) {
			if (line.isBlank()) {
				doingDataStoreAttribute = false;
				if (lineWasBlank)
					continue;
				lineWasBlank = true;
			}
			else {
				lineWasBlank = false;
			}

			if (line.contains("@StoreAttribute("))
				doingDataStoreAttribute = true;
			else if (doingDataStoreAttribute) {
				//line = line.replace(" final ", " ");
			}

			clientWriter.println(line);
		}

		clientWriter.close();

		getLog().info("..done with " + pathFrom);

	}

}
