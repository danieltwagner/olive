package com.github.danieltwagner.olive;

import java.security.Permission;

/**
 * Puts a SecurityManager in place that will throw an ExitException whenever an application attempts to call System.exit().
 * See http://stackoverflow.com/questions/309396/java-how-to-test-methods-that-call-system-exit
 *
 */
public class ExitHandler {
	
	protected static class ExitException extends SecurityException {
        public final int status;
        public ExitException(int status) 
        {
                super("There is no escape!");
                this.status = status;
        }
    }
	
	private static class NoExitSecurityManager extends SecurityManager 
    {
        @Override
        public void checkPermission(Permission perm) 
        {
                // allow anything.
        }
        @Override
        public void checkPermission(Permission perm, Object context) 
        {
                // allow anything.
        }
        @Override
        public void checkExit(int status) 
        {
                super.checkExit(status);
                throw new ExitException(status);
        }
    }
	
	public static void enable() {
		System.setSecurityManager(new NoExitSecurityManager());
	}
	
	public static void disable() {
		System.setSecurityManager(null);
	}
}
