/**
 * Written by Fedor Burdun of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Fedor Burdun
 */
package org.jrt.impl;

import org.jrt.socket.api.JRTHic;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.LatencyUtils.LatencyStats;
import org.jrt.socket.api.CodeWriter;
import org.jrt.socket.api.Transformer;
import org.jrt.socket.nio.NioSocketCodeWrapper;
import org.jrt.socket.regular.JavaNetSocketCodeWrapper;

public class JRT {

    public static volatile boolean initialized = false;
    public static volatile boolean finishByError = false;
    public static long jrtInstances = 0;
    private static final String title = "";
    
    public long startTime;
    public LatencyStats i2oLS;
    public LatencyStats o2iLS;
    public boolean isAlive = true;

    public Configuration configuration = new Configuration();
    public JRTStatistic jrtStat;
    
    public Map<Object, JRTHic> sockRTs = new ConcurrentHashMap(new WeakHashMap<Object, JRTHic>());
    
    public static void main(String[] args) {
        System.out.println("jRT.jar doesn't have now functional main method. Please rerun your application as:\n\t"
                + "java -javaagent:jRT.jar -jar yourapp.jar");
        System.exit(1);
    }

    private static String printKeys(String[] keys) {
        StringBuilder sb = new StringBuilder();
        for (String s : keys) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(s);
        }
        return sb.toString();
    }
    
    private static String printKeys(String[] keys, int align) {
        String st = printKeys(keys);
        return st + String.format("%0" + Math.max(1, align - st.length()) + "d", 0).replace('0', ' ');
    }
    
    public static void printHelpAndExit() {
        System.out.println("Usage:");
        System.out.println("\tjava -jar jRT.jar[=<args>]  -jar yourapp.jar\n");
        printHelpParameters();
                
        System.out.println("\n");
        System.out.println("Please rerun application with proper CLI options.\n");
        
        finishByError = true;
        System.exit(1);
    }

    public static void printHelpParameters() {
        System.out.println("\t\twhere <args> is an comma separated list of arguments like arg1,arg2=val2 e.t.c\n");
        System.out.println("\t\tARGUMENTS:");
        System.out.println("\t\t  " + printKeys(help, 40) + " to print help");
        System.out.println("\t\t  " + printKeys(remoteaddr, 40) + " to add filter by remote address");
        System.out.println("\t\t  " + printKeys(remoteport, 40) + " to add filter by remote port");
        System.out.println("\t\t  " + printKeys(localport, 40) + " to add filter by local port");
        System.out.println("\t\t  " + printKeys(filterentry, 40) + " to add filter by entry: <Local port>:<Remote address>:<Remote port> any part can be empty");
        System.out.println("\t\t  " + printKeys(loginterval, 40) + " to set log sampling interval");
        System.out.println("\t\t  " + printKeys(startdelaying, 40) + " to specify time delay to start jRT");
        System.out.println("\t\t  " + printKeys(workingtime, 40) + " to specify how long jRT will work");
        System.out.println("\t\t  " + printKeys(logprefix, 40) + " to specify jRT log prefix");
        System.out.println("\t\t  " + printKeys(uuid, 40) + " to specify jRT inner ID (take <string>)");
        System.out.println("\t\t  " + printKeys(ioMode, 40) + " to specify jRT mode. Expects one of i2o, o2i, both. Both by default");
//        System.out.println("\t\t  " + printKeys(i2oenabling, 40) + " to calculate latency (take <boolean>)");
//        System.out.println("\t\t  " + printKeys(o2ienabling, 40) + " to calculate latency (take <boolean>)");
    }
    
    public static ConcurrentHashMap<String, JRT> jRTWorkers = new ConcurrentHashMap<String, JRT>();
    
    public void premain(String agentArgument, Instrumentation instrumentation) {
        jrtStat = new JRTStatistic();

        startTime = System.currentTimeMillis();

        parseArguments(agentArgument);
        
        jRTWorkers.put(configuration.uuid, this);
        
        if (configuration.i2oEnabled) i2oLS = new LatencyStats();
        if (configuration.o2iEnabled) o2iLS = new LatencyStats();

        instrument(agentArgument, instrumentation);
        
        //Some temporary place to print collected statistic.
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                synchronized (JRT.title) {
                    if (finishByError) {
                        return;
                    }
                    //TODO move/remove/improve
                    System.out.println("");
                    System.out.println("***************************************************************");
                    System.out.println("jRT configuration: ");
                    System.out.println("jRT uid " + configuration.uuid);
                    System.out.println("log files " + configuration.logPrefix + ".*");
                    System.out.println("---------------------------------------------------------------");
                    System.out.println("jRT Statistic: ");
                    System.out.println(" " + jrtStat.processedSocket + " sockets was processed");
                    System.out.println("***************************************************************");
                    System.out.flush();
                }
            }

        });

        LogWriter jRTLogWriter = new LogWriter(this);
        jRTLogWriter.start();    
    }

    public void parseArguments(String agentArgument) throws NumberFormatException {
        if (null != agentArgument) {
            for (String v : agentArgument.split(",")) {
                String[] vArr = v.split("=");
                if (vArr.length > 2) {
                    System.out.println("Wrong format jRT arguments.\n");
                    printHelpAndExit();
                }
                if (hasKey(help, vArr[0])) {
                    printHelpAndExit();
                }
                if (hasKey(remoteaddr, vArr[0])) {
                    configuration.filterEntries.add(
                            new Configuration.IOFilterEntry(null, vArr[1], null));
                }
                if (hasKey(localport, vArr[0])) {
                    configuration.filterEntries.add(
                            new Configuration.IOFilterEntry(vArr[1], null, null));
                }
                if (hasKey(remoteport, vArr[0])) {
                    configuration.filterEntries.add(
                            new Configuration.IOFilterEntry(null, null, vArr[1]));
                }
                if (hasKey(filterentry, vArr[0]) && vArr.length == 2) {
                    boolean isCorrect = true;
                    
                    String localPort = null;
                    String remoteAddr = null;
                    String remotePort = null;
                    
                    String[] ports = vArr[1].split(":");
                    
                    if (ports.length > 0 && ports[0].length() > 0) {
                        localPort = ports[0];
                    }

                    if (ports.length > 1 && ports[1].length() > 0) {
                        remoteAddr = ports[1];
                    }

                    if (ports.length > 2 && ports[2].length() > 0) {
                        remotePort = ports[2];
                    }

                    if (ports.length < 2 || ports.length > 3) {
                        isCorrect = false;
                    }

                    //System.out.println("'" + localPort + "'" + remoteAddr + "'" + remotePort + "'");
                    
                    if (!isCorrect) {
                        System.err.println("Wrong " + printKeys(filterentry) + " format\n\n");
                        printHelpAndExit();
                    }
                    
                    configuration.filterEntries.add(
                            new Configuration.IOFilterEntry(localPort, remoteAddr, remotePort));
                }
                if (hasKey(loginterval, vArr[0])) {
                    configuration.logWriterInterval = Long.valueOf(vArr[1]);
                }
                if (hasKey(startdelaying, vArr[0])) {
                    configuration.startDelaying = Long.valueOf(vArr[1]);
                }
                if (hasKey(workingtime, vArr[0])) {
                    configuration.workingTime = Long.valueOf(vArr[1]);
                }
                if (hasKey(logprefix, vArr[0])) {
                    configuration.setLogNamePattern(vArr[1]);
                }
                if (hasKey(uuid, vArr[0])) {
                    configuration.uuid = (vArr[1]);
                }
                if (hasKey(i2oenabling, vArr[0])) {
                    configuration.i2oEnabled = Boolean.valueOf(vArr[1]);
                }
                if (hasKey(o2ienabling, vArr[0])) {
                    configuration.o2iEnabled = Boolean.valueOf(vArr[1]);
                }
                if (hasKey(debug, vArr[0])) {
		     if ("true".equals(vArr[1]))
			configuration.debug = true;
		}
                if (hasKey(ioMode, vArr[0])) {
                    if ("i2o".equals(vArr[1])) {
                        configuration.i2oEnabled = true;
                        configuration.o2iEnabled = false;
                    } else if ("o2i".equals(vArr[1])) {
                        configuration.i2oEnabled = false;
                        configuration.o2iEnabled = true;
                    } else if ("both".equals(vArr[1])) {
                        configuration.i2oEnabled = true;
                        configuration.o2iEnabled = true;
                    } else {
                        System.err.println("Parameter " + vArr[0] + 
                                " expects one of i2o, o2i, both argument. But " + vArr[1] + " has been got.");
                        printHelpAndExit();
                    }
                }
            }
        }
    }

    public void instrument(String agentArgument, Instrumentation instrumentation) {
        instrumentation.addTransformer(new Transformer(this, new JavaNetSocketCodeWrapper()), true);
        instrumentation.addTransformer(new Transformer(this, new NioSocketCodeWrapper()), true);
        
    /*
    untested code to support attaching to existing java process
    */
        redeclare(instrumentation, new JavaNetSocketCodeWrapper());
        redeclare(instrumentation, new NioSocketCodeWrapper());
    }
    
    /*
    untested code to support attaching to existing java process
    */
    private void redeclare(Instrumentation instrumentation, CodeWriter cw) {
        ArrayList<Class> ac = new ArrayList<Class>();
        
        for (Class c : instrumentation.getAllLoadedClasses()) {
            final String className = c.getName().replace(".", "/");
            
            if (cw.needInstrument(className)) {
                ac.add(c);
                try {
                    instrumentation.retransformClasses(c);
                } catch (UnmodifiableClassException ex) {
                    ex.printStackTrace();
                }
            }
        }
        
    }
    
    public static JRT premain0(String agentArgument, Instrumentation instrumentation) {
        
        //Check here another instances and exit if then!
        if (initialized) {
            System.out.println("WARNING: multiple instances of jRT was ran. (It's not well tested yet)");
            //System.err.println("\nTrying to run multiple instances of jRT simultaneously.\n"
            //        + "\nPlease run only one at the same time.\n\n");
            //finishByError = true;
            //System.exit(1);
        }
        
        JRT jRT = new JRT();
        jRT.premain(agentArgument, instrumentation);
        
        initialized = true;
        
        return jRT;
    }
    
    private static final String[] remoteaddr = {"-raddr", "remote-addr"};
    private static final String[] loginterval = {"-si", "sample-interval"};
    private static final String[] remoteport = {"-rport", "remote-port"};
    private static final String[] localport = {"-lport", "local-port"};
    private static final String[] filterentry = {"-f", "filter-entry"};
    private static final String[] help = {"-h", "--help", "help", "h"};
    private static final String[] startdelaying = {"-start", "start"};
    private static final String[] workingtime = {"-fin", "finish-after"};
    private static final String[] logprefix = {"-lp", "log-prefix"};
    private static final String[] uuid = {"-id", "uuid"};
    private static final String[] ioMode = {"-mode"};
    private static final String[] i2oenabling = {"-i2o"};
    private static final String[] o2ienabling = {"-o2i"};
    private static final String[] debug = {"-d","debug"};

    private static boolean hasKey(String[] list, String key) {
        for (String s : list) {
            if (s.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
