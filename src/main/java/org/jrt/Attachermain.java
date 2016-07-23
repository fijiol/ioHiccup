/**
 * Written by Fedor Burdun of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * For MacOS/Java 1.8.0_92, the JDK tools.jar needs to be configured like this:  -Xbootclasspath/a:tools.jar
 * @author Fedor Burdun
 */
package org.jrt;

import org.jrt.impl.JRT;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;

import java.security.ProtectionDomain;
import java.security.CodeSource;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;

public class Attachermain {
	   private static final String FILE_PROTOCOL_PREFIX = "file:";        //as in  file:/Users/erikostermueller/jRT.jar!/org/jrt/Agentmain.class
	   private static final String CLASS_FILE_SUFFIX = ".class"; //file:/Users/erikostermueller/jRT.jar!/org/jrt/Agentmain.class
    
    public static void main(String[] args) {
        //TODO: Exclude CLI option Xbootclasspath/a=...../tools.jar
//        try {
//        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
//        method.setAccessible(true);
//        method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{new File("/usr/lib/jvm/java-1.7.0-openjdk-amd64/lib/tools.jar").toURI().toURL()});
//        } catch (Exception e) {
//        }
        
        
        boolean needHelp = false;
        String pid = null;
        String agentArguments = "";
	String fileSystemPathToAgentJar = null;
        
        for (String s : args) {
            if (s.startsWith("-pid")) {
                String[] p = s.split("=");
                if (p.length==2) {
                    pid = p[1];
                } else {
                    needHelp = true;
                }
            } else if (s.startsWith("-agentjarpath")) {
                String[] p = s.split("=");
                if (p.length==2) {
                    fileSystemPathToAgentJar = p[1];
                } else {
                    needHelp = true;
                }

            } else if (s.startsWith("-agentargs")) {
                String[] p = s.split("=", 2);
                if (p.length==2) {
                    agentArguments = p[1];
                } else {
                    needHelp = true;
                }
            } else if (s.startsWith("-h") || s.startsWith("--help") || s.startsWith("-help")) {
                needHelp = true;
            } else {
                needHelp = true;
            }
        }
        
        //validate agent arguments
        //print help message and exit if something is wrong
        {
            (new JRT()).parseArguments(agentArguments);
        }
        
        if (needHelp || null == pid) {
            System.err.println("please, to attach jRT to already running application rerun it in next manner:\n\n"
                    + "\tjava -jar jRT.jar -pid=<PID of java VM> -agentargs='<args>' \n\n");
            JRT.printHelpParameters();
            System.exit(1);
        }
        
        try {
            
            VirtualMachine vm = VirtualMachine.attach(pid);
	    if (fileSystemPathToAgentJar ==null) {
		    URL jarFile = getJarFile(Agentmain.class);//fails on macos, part of problem documented in issue #9
		    if (jarFile!=null) {
			fileSystemPathToAgentJar = jarFile.getPath();
		    } else {
			File jarFileFile = getJarFileOldSchool(Agentmain.class);//works on macos
			if (jarFileFile !=null) {
				fileSystemPathToAgentJar = jarFileFile.getCanonicalPath();
			} 
		    }
	    }
	    if (fileSystemPathToAgentJar==null) {
		throw new RuntimeException("Could not find path to jar containing org.jrt.Agentmain.  Try adding command line parameter -agentjarpath=c:/path/to/jRT.jar");
	    } else {	
		    System.out.println("About to load agent from path [" + fileSystemPathToAgentJar + "].");
		    vm.loadAgent(fileSystemPathToAgentJar, agentArguments);
		    vm.detach();
		    System.exit(0);
	    }
        
        } catch (IOException e) {
            System.err.println("Seems like java process with pid="+pid+" doesn't exist or not permit to instrument. \nPlease ensure that pid is correct.");
        } catch (AgentInitializationException e) {
            System.err.println("Failed to initialize agent: " + e);
        } catch (AgentLoadException e) {
            System.err.println("Failed to load agent: " + e);
        } catch (AttachNotSupportedException e) {
            System.err.println("Seems like attach isn't supported: " + e);
        }
    }

	private static 	URL getJarFile(Class clazz) {
		URL rcUrl = null;
		ProtectionDomain pd = clazz.getProtectionDomain();
		if (pd!=null) {
			CodeSource cs = pd.getCodeSource();
			if (cs!=null)
			 	rcUrl = cs.getLocation();
		}
		return rcUrl;
	}

	/**  Given the Agent Main class, return the jar file that contains that class.
	  *  Example:  Given the .class representing this:  file:/Users/erikostermueller/jRT/0.0.1/jRT-master/target/jRT.jar!/org/jrt/Agentmain.class
	  *  return a java.io.File object representing the parent jar file, /Users/erikostermueller/jRT/0.0.1/jRT-master/target/jRT.jar
	  */
	public static File getJarFileOldSchool(Class clazz) {
        String s = null;
	if (clazz!=null) {
        try {
            URL myUrl = clazz.getResource(Agentmain.class.getSimpleName() + ".class");
	    if (myUrl != null) {
            	String path = myUrl.getPath();
	    	if ( path.startsWith(FILE_PROTOCOL_PREFIX) && path.endsWith(CLASS_FILE_SUFFIX) ) {
            	    s = path.substring(0, path.indexOf("!"));
               	    URI uri = new URL(s).toURI();
                    File f = new File(uri);
                    return f;
           	 }
	    }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
	}
        return null;
    }

}
