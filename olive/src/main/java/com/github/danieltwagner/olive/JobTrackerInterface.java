package com.github.danieltwagner.olive;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapred.TIPStatus;
import org.apache.hadoop.mapred.TaskReport;
import org.apache.hadoop.util.RunJar;
import org.joda.time.Period;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.github.danieltwagner.olive.ExitHandler.ExitException;

public class JobTrackerInterface {
	
	private OliveSettings settings;
	
	public static class Job {
		public String id, jobname, username, status, duration, trackingUrl;
		public Date start;
		public float mapProgress, reduceProgress;
		public int mapsCompleted, mapsTotal, reducesCompleted, reducesTotal;
		public boolean isComplete;
		
		public boolean isMapComplete() {return mapProgress == 100;}
		public boolean isReduceComplete() {return reduceProgress == 100;}
		
		private static final PeriodFormatter hoursMinutesSeconds = new PeriodFormatterBuilder()
	    	.appendHours().appendSuffix("h ").appendMinutes().appendSuffix("m ").appendSeconds().appendSuffix("s ").toFormatter();

		public Job(String id, String trackingUrl, String jobname, String username, int status, boolean isComplete, float mapProgress, float reduceProgress,
				int mapsCompleted, int mapsTotal, int reducesCompleted, int reducesTotal, long start, long duration) {
			
			this.id = id;
			this.trackingUrl = trackingUrl;
			this.jobname = jobname;
			this.username = username;
			this.status = JobStatus.getJobRunState(status).toLowerCase();
			this.isComplete = isComplete;
			
			this.mapProgress = mapProgress*100;
			this.reduceProgress = reduceProgress*100;
			
			this.mapsCompleted = mapsCompleted;
			this.mapsTotal = mapsTotal;
			this.reducesCompleted = reducesCompleted;
			this.reducesTotal = reducesTotal;
			
			this.start = new Date(start);
			this.duration = new Period(duration).toString(hoursMinutesSeconds);
		}
	}
	
	public JobTrackerInterface(OliveSettings settings) throws IOException {
		this.settings = settings;
	}
	
	public List<Job> getJobs() {
		List<Job> result = new ArrayList<Job>();
		
		try {
			JobClient client = new JobClient(new JobConf());
			JobStatus[] jobStatuses = client.getAllJobs();
			if(jobStatuses != null) {
				for (JobStatus jobStatus : jobStatuses) {
		
					long lastTaskEndTime = 0L;
		
					int mapsCompleted = 0;
					TaskReport[] mapReports = client.getMapTaskReports(jobStatus.getJobID());
					for (TaskReport r : mapReports) {
						if(r.getCurrentStatus() == TIPStatus.COMPLETE) mapsCompleted++;
						if (lastTaskEndTime < r.getFinishTime()) {
							lastTaskEndTime = r.getFinishTime();
						}
					}
		
					int reducesCompleted = 0;
					TaskReport[] reduceReports = client.getReduceTaskReports(jobStatus.getJobID());
					for (TaskReport r : reduceReports) {
						if(r.getCurrentStatus() == TIPStatus.COMPLETE) reducesCompleted++;
						if (lastTaskEndTime < r.getFinishTime()) {
							lastTaskEndTime = r.getFinishTime();
						}
					}
					
					String jobName = client.getJob(jobStatus.getJobID()).getJobName();
					String jobTrackingUrl = client.getJob(jobStatus.getJobID()).getTrackingURL();
					
					result.add(new Job(jobStatus.getJobID().toString(), jobTrackingUrl, jobName, jobStatus.getUsername(), jobStatus.getRunState(), jobStatus.isJobComplete(), 
							jobStatus.mapProgress(), jobStatus.reduceProgress(), mapsCompleted, mapReports.length, reducesCompleted, reduceReports.length,
							jobStatus.getStartTime(), lastTaskEndTime == 0 ? 0: lastTaskEndTime - jobStatus.getStartTime()));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public void addJob(final File jar, String mainClassName, final String[] args) {
		if((jar == null) || (!jar.exists())) return;
		
		try {			
			JarFile jarFile;
			try {
				jarFile = new JarFile(jar);
			} catch(IOException io) {
				throw new IOException("Error opening job jar: " + jar.getPath()).initCause(io);
			}

			if((mainClassName == null) || (mainClassName.length() == 0)) {
				Manifest manifest = jarFile.getManifest();
				if (manifest != null) {
					mainClassName = manifest.getMainAttributes().getValue("Main-Class");
				}
			}
			jarFile.close();

			if((mainClassName == null) || (mainClassName.length() == 0)) {
				System.err.println("No main class name specified and none found in JAR manifest.");
				return;
			}
			mainClassName = mainClassName.replaceAll("/", ".");

			final File workDir = new File(jar.getParentFile(), "unjar");
			workDir.mkdirs();
			if(!workDir.isDirectory()) {
				System.err.println("Mkdirs failed to create " + workDir);
				return;
			}

			RunJar.unJar(jar, workDir);

			// compose class path
			ArrayList<URL> classPath = new ArrayList<URL>();
			classPath.add(new File(workDir + "/").toURI().toURL());
			classPath.add(jar.toURI().toURL());
			classPath.add(new File(workDir, "classes/").toURI().toURL());
			classPath.add(new File(settings.getConfDir()).toURI().toURL());

			File[] libs = new File(workDir, "lib").listFiles();
			if(libs != null) {
				for(int i = 0; i < libs.length; i++) {
					classPath.add(libs[i].toURI().toURL());
				}
			}

			// prepare class loader using above paths
			ClassLoader loader = new URLClassLoader(classPath.toArray(new URL[0]));
			Thread.currentThread().setContextClassLoader(loader);
			Class<?> mainClass = Class.forName(mainClassName, true, loader);
			final Method main = mainClass.getMethod("main", new Class[] {
					Array.newInstance(String.class, 0).getClass()
			});
			
			// energise!
			new Thread() {

				@Override
				public void run() {
					try {
						main.invoke(null, new Object[] { args });
					} catch (Exception e) {
						Throwable cause = e.getCause();
						if((cause != null) && (cause.getClass().equals(ExitException.class))) {
							System.err.println("Job exited with status " + ((ExitException)cause).status);
						} else {
							e.printStackTrace();
						}
					}
					
					try {
						FileUtil.fullyDelete(jar.getParentFile());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
