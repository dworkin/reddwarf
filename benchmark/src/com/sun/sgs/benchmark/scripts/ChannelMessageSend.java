package com.sun.sgs.benchmark.scripts;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Timer;
import java.util.TimerTask;

import com.sun.sgs.benchmark.client.*;
import com.sun.sgs.impl.sharedutil.HexDumper;

public class ChannelMessageSend {
    public static enum SendType {
        BCAST,  /* send to all on channel */
        SINGLE, /* send to one recipient*/
        NONE;   /* don't send anything */
    };
    
    public static final int DEFAULT_NUM_CLIENTS = 60;
    public static final long DEFAULT_INTERVAL = 500;
    public static final int DEFAULT_MSG_LEN = 500;
    
    /**
     * Number of {@link Timer} objects (threads) that run in parallel.
     */
    public static final int NUM_TIMER_THREADS = 32;
    
    /** The host that the server is running on. */
    public static final String HOSTNAME = "dstar3";
    
    /** The name of the channel created on the server. */
    public static final String CHANNEL_NAME = "ChannelMessageSend_script";
    
    /** 
     * Dictates the type of test being run.
     */
    public static final SendType sendType = SendType.BCAST;

    /**
     * String of arbitrary characters that is append to the end of the mutable
     * portion of channel messages (i.e. the sequence number portion) in order
     * to bring the total length of each message up to the required length:
     * {@code msgLen}.
     */
    private final String msgSuffix;

    /**
     * The client sessions.
     */
    private BenchmarkClient[] clients;

    /**
     * Each client's individual sequence number.
     */
    private long[] seqNums;

    /**
     * A set of timers to schedule tasks on.
     */
    private Timer[] timers;
    
    /**
     * Test parameters.
     */
    private final int numClients;
    private final long interval;
    private final int msgLen;
    
    public ChannelMessageSend(int numClients, long interval, int msgLen) {
	this.numClients = numClients;
	this.interval = interval;
	this.msgLen = msgLen;
        
        System.out.printf("Starting up with numClients=%d,  interval=%d ms" +
            ", strlen=%d, sendType=%s\n", numClients, interval, msgLen,
            sendType.toString());
        
        StringBuffer msgBuf = new StringBuffer();
        
        for (int i=0; i < (msgLen - 20); i++)
            msgBuf.append('A');
        
        msgSuffix = msgBuf.toString();
    }

    public ChannelMessageSend() {
	this(DEFAULT_NUM_CLIENTS, DEFAULT_INTERVAL, DEFAULT_MSG_LEN);
    }
    
    public void run() throws ParseException {
        BenchmarkClient channelCreator = new BenchmarkClient();
        channelCreator.processInput("config " + HOSTNAME);
        channelCreator.processInput("login ch_creator password");
        channelCreator.processInput("wait_for login");
        channelCreator.processInput("create_channel " + CHANNEL_NAME);
        
        /** wait a second for the channel to be created. */
        try { Thread.sleep(1000); } catch (InterruptedException ignore) { }
        
        /* channel-creator client no longer needed. */
        channelCreator.processInput("logout");
        channelCreator.processInput("wait_for disconnected");
        channelCreator = null;
        
        /** Create all of the channel clients and their sequence numbers. */
        clients = new BenchmarkClient[numClients];
        seqNums = new long[numClients];
        for (int i=0; i < numClients; i++) clients[i] = new BenchmarkClient();
        for (int i=0; i < numClients; i++) seqNums[i] = 0;
        
        /** Create all of the scheduling timers. */
        timers = new Timer[NUM_TIMER_THREADS];
        for (int i=0; i < NUM_TIMER_THREADS; i++) timers[i] = new Timer();
        
        /** Pause a bit since we just started a bunch of threads... */
        try { Thread.sleep(3000); } catch (InterruptedException ignore) { }
        
        /** Schedule client startup tasks with a random pause after each. */
        for (int i=0; i < numClients; i++) {
            getTimerForClient(i).schedule(new ClientStartupTask(i), 0);
            
            try {
                /** pause = 1 +/- 0.5 seconds */
                Thread.sleep(500 + (int)(1000 * Math.random()));
            }
            catch (InterruptedException ignore) { }
        }
    }
    
    private Timer getTimerForClient(int clientId) {
        return timers[clientId % timers.length];
    }
    
    private static void printUsage() {
	System.out.println("usage: java ChannelMessageSend " +
            "<#clients> <interval (ms)> <message size (msgLen)>");
    }

    public static void main(String[] args) {
        ChannelMessageSend tester;
        
	if (args.length > 0) {
	    if (args.length != 3) {
		printUsage();
                System.exit(1);
            }
            
	    int numClients = 0, interval = 0, msgLen = 0;
	    try {
		numClients = Integer.parseInt(args[0]);
		interval = Integer.parseInt(args[1]);
		msgLen = Integer.parseInt(args[2]);
	    }
	    catch (Throwable t) {
		printUsage();
                System.exit(1);
	    }
            
            tester = new ChannelMessageSend(numClients, interval, msgLen);
	} else {
            tester = new ChannelMessageSend();  /** default parameters */
        }
        
        try {
            tester.run();
        } catch (ParseException pe) {
            pe.printStackTrace();
        }
    }
    
    /**
     * Inner class: ClientStartupTask()
     */
    class ClientStartupTask extends TimerTask {
        /**
         * The index of the {@link BenchmarkClient} for which this task was
         * scheduled.
         */
        private int id;

        /**
         * Creates a new {@code ClientStartupTask}.
         */
        public ClientStartupTask(int id) {
            super();
            this.id = id;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Sends commands to client to: login, join channel.
         * Then reschedules a chaining task to send on the channel.
         */
        @Override
        public void run() {
            BenchmarkClient client = clients[id];
            client.printCommands(false);
            client.printNotices(true);
            
            try {
                client.processInput("config " + HOSTNAME);
                client.processInput("login sender-" + id + " pswd");
                client.processInput("wait_for login");
                client.processInput("join " + CHANNEL_NAME);
                client.processInput("wait_for join_channel");
            } catch (ParseException pe) {
                System.err.println("ParseException in client " + id);
                pe.printStackTrace(); 
            }
            
            String hexSessionId =
                HexDumper.toHexString(client.getSessionId().toBytes());
            
            switch (sendType) {
            case BCAST:
                System.out.printf("Client #%d [%s] will broadcast to" +
                    " channel.\n", id, hexSessionId);
                break;
                
            case SINGLE:
                long longSessionId = Long.parseLong(hexSessionId, 16);
                long longRecipSessionId;
                
                if (longSessionId <= 1)
                    longRecipSessionId = 2;
                else
                    longRecipSessionId = longSessionId - 1;
                
                String hexRecipSessionId =
                    Long.toHexString(longRecipSessionId);
                
                System.out.printf("Client #%d [%s] will send to recipient" +
                    " [%s].\n", id, hexSessionId, hexRecipSessionId);
                break;
                
            case NONE:
                System.out.printf("Client $%d [%s] will not send at all.\n",
                    id, hexSessionId);
                break;

            default:
                throw new UnsupportedOperationException("Unknown sendType" +
                    " value: " + sendType);
            }
            
            /**
             * Schedule recurring task to send on the channel.
             */
            getTimerForClient(id).scheduleAtFixedRate(new SendChannelTask(id),
                interval, interval);
        }
    }

    /**
     * Inner class: SendChannelTask
     */
    class SendChannelTask extends TimerTask {
        /**
         * The index of the {@link BenchmarkClient} for which this task was
         * scheduled.
         */
        private int id;

        /**
         * Command to send to client (to get it to send on the channel), without
         * the message to send itself, which should be concatenated on to this.
         * {@code null} if this client should not send on the channel.
         */
        private String sendCommand;

        /**
         * Formatter for sequence numbers; enforces fixed-width of 20.
         */
        private DecimalFormat formatter =
            new DecimalFormat("00000000000000000000");

        /**
         * Creates a new {@code SendChannelTask}.
         */
        public SendChannelTask(int id) {
            super();
            this.id = id;
            
            BenchmarkClient client = clients[id];
            
            String hexSessionId =
                HexDumper.toHexString(client.getSessionId().toBytes());
            
            switch (sendType) {
            case BCAST:
                sendCommand = String.format("chsend %s ", CHANNEL_NAME);
                break;
                
            case SINGLE:
                long longSessionId = Long.parseLong(hexSessionId, 16);
                long longRecipSessionId;
                
                if (longSessionId <= 1)
                    longRecipSessionId = 2;
                else
                    longRecipSessionId = longSessionId - 1;
                
                String hexRecipSessionId =
                    Long.toHexString(longRecipSessionId);
                
                sendCommand = String.format("pm %s %s ", CHANNEL_NAME,
                    hexRecipSessionId);
                break;
                
            case NONE:
                sendCommand = null;
                break;
            }    
        }

        /**
         * {@inheritDoc} 
         * <p>
         * Sends a command to the client to send a message on the channel.
         */
        @Override
        public void run() {
            BenchmarkClient client = clients[id];
            
            if (sendCommand != null) {
                StringBuffer msgBuf = new StringBuffer();
                
                if (msgLen >= 20) {
                    msgBuf.append(formatter.format(seqNums[id]++));
                    msgBuf.append(msgSuffix);
                } else {
                    while (msgBuf.length() < msgLen)
                        msgBuf.append('A');
                }
                
                try {
                    client.processInput(sendCommand + msgBuf.toString());
                } catch (ParseException pe) {
                    System.err.println("ParseException in client " + id);
                    pe.printStackTrace();
                }
            }
        }
    }
}
