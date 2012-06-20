package com.github.danieltwagner.olive;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class OliveCli {

	public static void main(String[] args) {
		OliveSettings settings = new OliveSettings();
		JCommander jCom = new JCommander(settings);
		jCom.setProgramName("java -jar olive.jar");
		
		if(args.length == 0) {
			jCom.usage();
			return;
		}
		
		try {
			jCom.parse(args);
			
			try {
				ClassLoader loader = new URLClassLoader(new URL[]{new File(settings.getConfDir()).toURI().toURL()});
				if(loader.getResource("core-site.xml") == null) throw new ParameterException("core-site.xml is not present on conf path.");
				if(loader.getResource("mapred-site.xml") == null) throw new ParameterException("mapred-site.xml is not present on conf path.");
				Thread.currentThread().setContextClassLoader(loader);
			} catch (MalformedURLException e) {
				System.err.println("Could not load specified conf path: " + e.getMessage());
				jCom.usage();
				return;
			}
			
			/*
			Class<?> olive = loader.loadClass(Olive.class.getName());
			Constructor c = olive.getDeclaredConstructor(OliveSettings.class);
			c.newInstance(settings);
			*/
			new Olive(settings);
		} catch (ParameterException e) {
			System.err.println(e.getMessage()+"\n");
			jCom.usage();
		}
	}
}
