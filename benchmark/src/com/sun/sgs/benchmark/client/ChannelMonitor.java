/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.sun.sgs.benchmark.client.listener.ChannelMessageListener;
import com.sun.sgs.client.SessionId;

public class ChannelMonitor extends BenchmarkClient
    implements ChannelMessageListener
{
    /** Default name of the channel to monitor. */
    public static final String DEFAULT_CHANNEL_NAME =
        "ChannelMessageSend_script";

    /** Default timeout duration (ms). */
    public static final int DEFAULT_SENDER_TIMEOUT = 5000;

    /** Default rate at which messages are expected to be sent (ms). */
    public static final int DEFAULT_MSG_INTERVAL = 500;

    /** Rate at which the timeout-monitoring task runs (ms) */
    public static final int TIMER_INTERVAL = 1000;

    /** Window size (ms) over which to aggregate message stats (ms). */
    public static final int STATS_WINDOW = 10000;

    /** Maps client names to sequence numbers. */
    private Map<String,Long> seqNumMap = new HashMap<String,Long>();

    /** Maps client names to message timestamps. */
    private Map<String,Long> timestampMap = new HashMap<String,Long>();

    /** Global stats (over lifetime of this object). */
    private int globalMsgCount = 0;
    private long globalSumError = 0;
    private long globalMaxError = 0;

    /** Windowed stats (just over the current window of time). */
    private int windowMsgCount = 0;
    private long windowSumError = 0;
    private long windowMaxError = 0;
    
    /** Lock on all above variables. */
    private Object lock = new Object();

    private int msgInterval;

    /**
     * Creates a new {@code ChannelMonitor}.
     */
    public ChannelMonitor() {
        super();
    }

    private static void logln(String s) {
        System.out.println(System.currentTimeMillis() + "\t" + s);
    }

    public static void printUsage() {
        System.out.println("com.sun.sgs.benchmark.client.ChannelMonitor [host" +
            " [msg-interval [channel-name]]]");
    }
    
    // implement ChannelMessageListener
    
    public void receivedMessage(String channelName, SessionId sender,
        byte[] message)
    {
        long now = System.currentTimeMillis();
        String client = formatSession(sender);
        String msg = fromMessageBytes(message);
            
        if (msg.length() >= 20) {
            long seqNum;
                
            try {
                seqNum = Long.valueOf(msg.substring(0, 20));
            } catch (NumberFormatException nfe) {
                logln("Bad message received: " + msg);
                return;
            }

            synchronized (lock) {
                if (seqNumMap.containsKey(client)) {
                    long prevSeqNum = seqNumMap.get(client);

                    if (seqNum == (prevSeqNum + 1)) {
                        /** good message. */
                    } else {
                        logln(String.format("** Missed %d messages from %s",
                                (seqNum - (prevSeqNum + 1)), client));
                    }

                    if (timestampMap.containsKey(client)) {
                        long err = Math.abs(now - (timestampMap.get(client) + msgInterval));

                        globalSumError += err;
                        if (err > globalMaxError) globalMaxError = err;

                        windowSumError += err;
                        if (err > windowMaxError) windowMaxError = err;
                    } else {
                        logln(String.format("Sender %s is active again following" +
                                  " a timeout.", client));
                    }
                } else {
                    logln(String.format("New sender: %s.  Total senders = %d",
                            client, seqNumMap.keySet().size() + 1));
                }

                globalMsgCount++;
                windowMsgCount++;

                seqNumMap.put(client, seqNum);
                timestampMap.put(client, now);
            }
        }
    }

    public void startMonitoring(int senderTimeout, int msgInterval) {
        this.msgInterval = msgInterval;
        
        logln(String.format("Configured with: sender-timout = %dms" +
                ", message-interval = %dms", senderTimeout, msgInterval));
        
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new MonitorTask(senderTimeout),
            TIMER_INTERVAL, TIMER_INTERVAL);
        
        timer.scheduleAtFixedRate(new PrintStatsTask(), STATS_WINDOW,
            STATS_WINDOW);
        
        masterListener.registerChannelMessageListener(this);
    }

    public static void main(String[] args) {
        String hostname = null;
        int msgInterval = DEFAULT_MSG_INTERVAL;
        String channelName = DEFAULT_CHANNEL_NAME;

        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("-h")) {
                printUsage();
                return;
            }
            
            hostname = args[0];
            
            if (args.length >= 2) {
                try {
                    msgInterval = Integer.valueOf(args[1]);
                } catch (NumberFormatException nfe) {
                    printUsage();
                    return;
                }
            }

            if (args.length >= 3) {
                channelName = args[2];
            }
            
            if (args.length >= 4) {
                printUsage();
                return;
            }
        }
        
        logln("Starting MonitorChannel on channel \"" + channelName + "\"");
        
        ChannelMonitor monitor = new ChannelMonitor();

        try {
            if (hostname != null)
                monitor.processInput("config " + hostname + "\n");
            
            monitor.processInput("login chmonitor foo\n");
            monitor.processInput("wait_for logged_in\n");
            monitor.processInput("chjoin " + channelName + "\n");
            
            monitor.startMonitoring(DEFAULT_SENDER_TIMEOUT, msgInterval);
        } catch (ParseException pe) {
            System.err.println(pe);
        }

        /** Loop forever. */
        // todo
        /*
        while (1) {
            try {
                monitor.wait();
            } catch (InterruptedException ignore) { }
        }
        */
    }

    /**
     * Inner class: MonitorTask
     */
    class MonitorTask extends TimerTask {
        private long senderTimeout;

        /**
         * Creates a new {@code MonitorTask}.
         */
        public MonitorTask(long senderTimeout) {
            this.senderTimeout = senderTimeout;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            
            synchronized (lock) {
                Iterator<String> iter = timestampMap.keySet().iterator();
                while (iter.hasNext()) {
                    String client = iter.next();
                    long elapsed = now - timestampMap.get(client);
                    
                    if (elapsed > senderTimeout) {
                        /** Clear from timeouts map to avoid repeating this. */
                        iter.remove();
                        logln(String.format("Timeout for sender %s (%dms elapsed" +
                                ").  Active sender=%d.", client, elapsed,
                                timestampMap.keySet().size()));
                    }
                }
            }
        }
    }

    /**
     * Inner class: PrintStatsTask
     */
    class PrintStatsTask extends TimerTask {
        private DecimalFormat formatter = new DecimalFormat(".2");

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            synchronized (lock) {
                logln(String.format("WINDOW\tmsgCount %d\tavgErr %s\tmaxErr %d",
                        windowMsgCount, formatter.format(windowMsgCount > 0 ?
                            windowSumError/(double)windowMsgCount : 0),
                        windowMaxError));
                
                logln(String.format("TOTAL \tmsgCount %d\tavgErr %s\tmaxErr %d",
                        globalMsgCount, formatter.format(globalMsgCount > 0 ?
                            globalSumError/(double)globalMsgCount : 0),
                        globalMaxError));
                
                /** clear window variables */
                windowMsgCount = 0;
                windowSumError = 0;
                windowMaxError = 0;
            }
        }
    }
}
