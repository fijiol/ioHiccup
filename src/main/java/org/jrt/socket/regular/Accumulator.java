/**
 * Written by Fedor Burdun of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Fedor Burdun
 */
package org.jrt.socket.regular;

import org.jrt.impl.Configuration;
import org.jrt.impl.JRT;
import org.jrt.socket.api.JRTHic;
import java.net.InetAddress;

public class Accumulator {

    private static String this_package = "org.jrt.socket.regular";
    private static String this_class = this_package + ".Accumulator";
    
    private static boolean matchPort(String a, String filter) {
        //return a.matches(".*" + filter + ".*");
        if (null == filter) {
            return false;
        }
        return a.equals(filter);
    }
    
    private static boolean matchAddr(String a, String filter) {
        if (null == filter) {
            return false;
        }
        return a.matches(".*" + filter + ".*");
    }
    
    private static boolean match(JRT jRT, InetAddress remoteAddress, int remotePort, int localPort) {
        
        boolean matched = false;
        
        if (jRT.configuration.filterEntries.isEmpty()) {
            matched = true;
        } else {
            for (Configuration.IOFilterEntry entry : jRT.configuration.filterEntries) {
                boolean matched_locally = true;

                if (null != entry.remoteaddr  &&
                        !matchAddr(remoteAddress.getHostAddress(), entry.remoteaddr) &&
                        !matchAddr(remoteAddress.getHostName(), entry.remoteaddr)  ) {
                    matched_locally = false;
                }
                if (null != entry.remoteport && 
                        !matchPort(String.valueOf(remotePort), entry.remoteport)) {
                    matched_locally = false;
                }
                if (null != entry.localport && 
                        !matchPort(String.valueOf(localPort), entry.localport)) {
                    matched_locally = false;
                }
                if (matched_locally) {
                    matched = true;
                    break;
                }
            }
        }
       if (jRT.configuration.debug) 
		System.out.println("remote-addr:remote-port <-> 127.0.0.1:local-port # " + remoteAddress + ":" + remotePort + " <-> " + "127.0.0.1:" + localPort + " === " + matched);
        
        return matched;
    }    
    
    public static JRT getJRT(String uuid) {
        return JRT.jRTWorkers.get(uuid);
    }
    
    public static String _filter(String jRT, String sock, String remoteInetAddress, String remotePort, String localPort) {
        return this_class + ".initializeJRTHic(" + jRT + ", " + sock + ", " + remoteInetAddress + ", " + remotePort + ", " + localPort + ");";
    }
    
    public static JRTHic initializeJRTHic(JRT jRT, Object sock, InetAddress remoteAddress, int remotePort, int localPort) {
        //System.out.println("initializeJRTHic " + sock + "," + remoteAddress + "," + remotePort + "," + localPort);
        
        JRTHic jrtHic = null;
        
        if (jRT == null) {
            System.err.println("jRT non initialized!");
            return null;
        }
        
        if (jRT.sockRTs.containsKey(sock)) {
            return jRT.sockRTs.get(sock);
        } else {
            jrtHic = new JRTHic();
            jRT.sockRTs.put(sock, jrtHic);
        }
        
        //Decide to filter or not?
        if (!match(jRT, remoteAddress, remotePort, localPort)) { 
            //sockRTs.put(sock, null); //??!
            jRT.sockRTs.remove(sock);
            
            return null;
        }
        
        ++jRT.jrtStat.processedSocket;
        
        return jrtHic;
    }

    
    
    

    private static String _timestampStub(String methodName, String jRT, String ioHic) {
        return this_class + "." + methodName + "(" + "(org.jrt.impl.JRT)" + jRT + ", " +  "(org.jrt.socket.api.JRTHic)" +  ioHic + ");";
    }
    
    public static String _readAfter(String jRT, String ioHic) {
        return _timestampStub("putTimestampReadAfter", jRT, ioHic);
    }
    
    public static String _readBefore(String jRT, String ioHic) {
        return _timestampStub("putTimestampReadBefore", jRT, ioHic);
    }
    
    public static String _writeAfter(String jRT, String ioHic) {
        return _timestampStub("putTimestampWriteAfter", jRT, ioHic);
    }
    
    public static String _writeBefore(String jRT, String ioHic) {
        return _timestampStub("putTimestampWriteBefore", jRT, ioHic);
    }
    
    
    
    
    public static void putTimestampReadAfter(JRT jRT, JRTHic hic) {
        if (null == jRT || hic == null) {
            return;
        }
        
        hic.i2oReadTime = System.nanoTime();
        hic.i2oLastRead = true;
    }
    
    public static void putTimestampWriteBefore(JRT jRT, JRTHic hic) {
        if (null == jRT || hic == null) {
            return;
        }
        
        hic.i2oWriteTime = System.nanoTime();
        if (hic.i2oLastRead && (hic.i2oLatency = hic.i2oWriteTime - hic.i2oReadTime) > 0) {
            if (null != jRT.i2oLS) jRT.i2oLS.recordLatency(hic.i2oLatency);
        }
        hic.i2oLastRead = false;
    }
    
    public static void putTimestampWriteAfter(JRT jRT, JRTHic hic) {
        if (null == jRT || hic == null) {
            return;
        }
        
        hic.o2iReadTime = System.nanoTime();
        hic.o2iLastWrite = true;
    }
    
    public static void putTimestampReadBefore(JRT jRT, JRTHic hic) {
        if (null == jRT || hic == null) {
            return;
        }
        
        hic.o2iWriteTime = System.nanoTime();
        if (hic.o2iLastWrite && (hic.o2iLatency = hic.o2iWriteTime - hic.o2iReadTime) > 0) {
            if (null != jRT.o2iLS) jRT.o2iLS.recordLatency(hic.o2iLatency);
        }
        hic.o2iLastWrite = false;
    }
    
}
