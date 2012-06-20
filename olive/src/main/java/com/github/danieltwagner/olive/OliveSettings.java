package com.github.danieltwagner.olive;

import java.util.List;

import com.beust.jcommander.Parameter;

public class OliveSettings {
	@Parameter(names = {"-p", "--port"}, description="The port that Olive should listen on.")
	private Integer port = 8080;
	
	@Parameter(names = {"-t", "--tempdir"}, description="Working directory where uploads are placed.")
	private String tempDir = System.getProperty("java.io.tmpdir") + "olive";
	
	@Parameter(names = {"-l", "--logdir"}, description="Supply a directory to enable access logs.")
	private String logDir = "";
	
	@Parameter(required=true, description="Path to Hadoop conf files.")
	private List<String> confDir;

	public Integer getPort() {
		return port;
	}

	public String getTempDir() {
		return tempDir;
	}
	
	public String getConfDir() {
		return confDir.get(0);
	}

	public String getLogDir() {
		return logDir;
	}
	
	public boolean isLoggingEnabled() {
		return logDir.length() > 0;
	}
}
