
package com.sun.sgs.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class TraceMemUsage {
    public static final int INTERVAL = 10000;  // ms
    
    public static void main(String[] args) {
        if (args.length != 1) {
            printUsage();
            return;
        }
        
        final int pid;
        
        try {
            pid = Integer.valueOf(args[0]);
        } catch (NumberFormatException nfe) {
            printUsage();
            return;
        }
        
        System.out.println("Date\t%CPU  RSS");
        
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        Process proc = Runtime.getRuntime().exec("ps -o pcpu,rss -p " + pid);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                        reader.readLine();  /** discard header row */
                        System.out.println(new Date() + "\t" + reader.readLine());
                    } catch (IOException ioe) {
                        System.out.println(new Date() + "\t" + ioe.toString());
                    }
                }
            }, 0, INTERVAL);
    }
    
    public static void printUsage() {
        System.out.println("Usage: com.sun.sgs.TraceMemUsage PID");
    }
}
