Olive: A simple Hadoop web interface
====================================

Olive is a simple web interface for submitting Hadoop jobs to a remote machine.
It's a simple, small and self-contained Java project that can be up and running in one minute.

Build Olive using Maven:
    mvn clean package

Assuming HADOOP_HOME points to the root of your Hadoop installation, run Olive using:

    java -jar olive.jar /path/to/hadoop/conf
	
Olive will now listen at http://localhost:8080 and allow you to submit new jobs.

You can use the following parameters to configure Olive:

    java -jar olive.jar --port 8080 --tempdir /tmp/olive --logdir /var/logs/olive /path/to/hadoop/conf
	
Logging is disabled by default unless you specify a directory.

Olive uses the excellent Spark framework http://github.com/perwendel/spark to provide a lightweight embedded Jetty server. JCommander (https://github.com/cbeust/jcommander) provides argument parsing. Bootstrap (http://twitter.github.com/bootstrap) is used for laying out HTML. The olive picture is by iStockphoto-Maceofoto.