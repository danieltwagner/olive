package com.github.danieltwagner.olive;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;

import org.stringtemplate.v4.ST;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.utils.IOUtils;

import com.oreilly.servlet.MultipartRequest;

public class Olive {

	private OliveSettings settings;
	private JobTrackerInterface jobtracker;

	public Olive(OliveSettings settings) {
		this.settings = settings;
		
		File tempDir = new File(settings.getTempDir());
		if(!tempDir.isDirectory() && !tempDir.mkdirs()) {
			System.err.println("Could not create temp directory.");
			return;
		}

		if(settings.isLoggingEnabled()) {
			File logDir = new File(settings.getLogDir());
			if(!logDir.isDirectory() && !logDir.mkdirs()) {
				System.err.println("Could not create log directory.");
				return;
			}
		}

		try {
			jobtracker = new JobTrackerInterface(settings);
		} catch (Exception e) {
			System.err.println("Could not reach job tracker.");
			return;
		}
		
		// we don't want a call to System.exit from inside a JAR tear down the entire web service.
		ExitHandler.enable();
		
		startServer();
	}
	
	/**
	 * starts Spark and configures endpoints
	 */
	private void startServer() {
		Spark.setPort(settings.getPort());
		Spark.get(new Route("/") {
			@Override
			public Object handle(Request request, Response response) {
				String out = "";
				try {
					// load template
					InputStream input = this.getClass().getResourceAsStream("/static/index.html");
					ST template = new ST(IOUtils.toString(input),'$','$');
					
					// fetch job details
					List<JobTrackerInterface.Job> jobs = jobtracker.getJobs();
					
					template.add("jobs", jobs);
					
					out = template.render();
				} catch (IOException e) {
					e.printStackTrace();
				} 
				return out;
			}
		});
		
		Spark.post(new Route("/upload") {
			@Override
			public Object handle(Request request, Response response) {
				try {
					final File workDir = File.createTempFile("upload", "", new File(settings.getTempDir()));
					workDir.delete();
					workDir.mkdirs();
					if(!workDir.isDirectory()) {
						return "Mkdirs failed to create " + workDir;
					}
					System.out.println(request.raw().getContentType());
					MultipartRequest r = new MultipartRequest(request.raw(), workDir.getAbsolutePath());
					
					Enumeration<String> fileNames = r.getFileNames();
					if(!fileNames.hasMoreElements()) {
						return "No file supplied.";
					}
					String elementName = fileNames.nextElement();
					String fileName = r.getFilesystemName(elementName);
					
					if((fileName == null) || !fileName.endsWith(".jar")) {
						return "Illegal file name.";
					}
					
					File file = r.getFile(elementName);
					if(file == null) {
						return "Could not open file " + fileName;
					}
					
					String params = r.getParameter("params");
					String mainClassName = r.getParameter("mainclass");
					jobtracker.addJob(file, mainClassName, (params == null) ? null : params.split(" "));
					return "ok.";
				} catch (IOException e) {
					e.printStackTrace();
					return e.getMessage();
				}
			}
		});
	}
}
