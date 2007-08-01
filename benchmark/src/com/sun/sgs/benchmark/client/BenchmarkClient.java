/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.sgs.benchmark.client.listener.*;
import com.sun.sgs.benchmark.shared.MethodRequest;
import com.sun.sgs.benchmark.shared.impl.CodeMethodRequest;
import com.sun.sgs.benchmark.shared.impl.CodeMethodRequestOp;
import com.sun.sgs.benchmark.shared.impl.StringMethodRequest;
import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.impl.sharedutil.HexDumper;

/**
 * A simple GUI chat client that interacts with an SGS server-side app.
 * It presents an IRC-like interface, with channels, member lists, and
 * private (off-channel) messaging.
 * <p>
 * The {@code BenchmarkClient} understands the following properties:
 * <ul>
 * <li><code>{@value #HOST_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_HOST} <br>
 *     The hostname of the {@code BenchmarkApp} server.<p>
 *
 * <li><code>{@value #PORT_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_PORT} <br>
 *     The port of the {@code BenchmarkApp} server.<p>
 *
 * <li><code>{@value #LOGIN_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_LOGIN} <br>
 *     The login name used when logging into the server.<p>
 *
 * <li><code>{@value #PASSWORD_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_PASSWORD} <br>
 *     The password name used when logging into the server.<p>
 *
 * </ul>
 */
public class BenchmarkClient {
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The name of the host property */
    public static final String HOST_PROPERTY = "BenchmarkClient.host";

    /** The default hostname */
    public static final String DEFAULT_HOST = "localhost";

    /** The name of the port property */
    public static final String PORT_PROPERTY = "BenchmarkClient.port";

    /** The default port */
    public static final String DEFAULT_PORT = "2502";
    
    /** The {@link Charset} encoding for client/server messages */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** Static counter used to generate unique client IDs. */
    private static AtomicInteger instanceCount = new AtomicInteger(0);

    /** Unique id for this client. */
    private final int id;

    /** Server to connect to */
    private String serverHostname, serverPort;        
    
    /** Handles listener multiplexing. */
    private BenchmarkClientListener masterListener = new BenchmarkClientListener();
    
    /** Used for communication with the server */
    private SimpleClient client = new SimpleClient(masterListener);
    
    /** Provides convenient lookup of channels by name. */
    private Map<String, ClientChannel> channels =
        new HashMap<String, ClientChannel>();
    
    /** Holds all registered tags (groupings of commands). */
    private Map<String, List<ScriptingCommand>> tags =
        new HashMap<String, List<ScriptingCommand>>();
    
    /** Used to perform the wait-for operation. */
    private Object waitLock = new Object();
    
    /** Used to perform the pause operation. */
    private Object pauseLock = new Object();
    
    /** Whether to print informational message when any event occurs. */
    private boolean printAllEvents = false;
    
    /** Whether to print informational messages when running commands. */
    private boolean printCommands = true;
    
    /** Whether to print informational messages when certain events occur. */
    private boolean printNotices = true;
    
    /** Variables to keep track of things while processing input: */
    
    /** Whether we are inside a block. */
    private boolean inBlock = false;
    
    /**
     * The name of the active TAG command.  If this reference is non-null, then
     * either we are inside of a block (as indicated by the inblock variable
     * being true) or the immediately previous line was a (this) TAG command.
     */
    private String activeTag = null;
    
    /**
     * The list of commands being stored up following an TAG command.  The
     * contents of this list are only valid if activeTag is non-null.
     */
    private List<ScriptingCommand> tagCmdList;
    
    /**
     * Used when printing out informational messages.
     */
    private DateFormat dateFormat = new SimpleDateFormat("MMM d yyyy HH:mm:ss");

    // Constructor

    /**
     * Creates a new {@code BenchmarkClient}.
     */
    public BenchmarkClient() {
        /** Assign unique ID for this client. */
        id = instanceCount.addAndGet(1);
        
        /** Default values */
        serverHostname = System.getProperty(HOST_PROPERTY, DEFAULT_HOST);
        serverPort = System.getProperty(PORT_PROPERTY, DEFAULT_PORT);
        
        /** Register ourselves for a few events with the master listener. */
        masterListener.registerChannelMessageListener(new ChannelMessageListener() {
                public void receivedMessage(String channelName, SessionId sender,
                    byte[] message)
                {
                    if (printAllEvents)
                        printMsg(String.format("Received message \"%s\" from" +
                                     "\"%s\" on channel \"%s\"",
                                     fromMessageBytes(message),
                                     formatSession(sender), channelName));
                }
            });
        
        masterListener.registerDisconnectedListener(new DisconnectedListener() {
                public void disconnected(boolean graceful, String reason) {
                    if (printNotices || printAllEvents)
                        printMsg(String.format("Disconnected %s: %s",
                                     (graceful ? "gracefully" : "ungracefully"),
                                     reason));
                }
            });
        
        masterListener.registerJoinedChannelListener(new JoinedChannelListener() {
                public void joinedChannel(ClientChannel channel) {
                    channels.put(channel.getName(), channel);
                    
                    if (printAllEvents)
                        printMsg(String.format("Joined channel \"%s\"",
                                     channel.getName()));
                }
            });
        
        masterListener.registerLeftChannelListener(new LeftChannelListener() {
                public void leftChannel(String channelName) {
                    if (printAllEvents)
                        printMsg(String.format("Left channel \"%s\"",
                                     channelName));
                }
            });
        
        masterListener.registerLoggedInListener(new LoggedInListener() {
                public void loggedIn() {
                    if (printNotices || printAllEvents)
                        printMsg(String.format("Logged in with sessionId: %s",
                                     formatSession(client.getSessionId())));
                }
            });
        
        masterListener.registerLoginFailedListener(new LoginFailedListener() {
                public void loginFailed(String reason) {
                    if (printNotices || printAllEvents)
                        printMsg(String.format("Login failed: %s", reason));
                }
            });
        
        masterListener.registerReconnectedListener(new ReconnectedListener() {
                public void reconnected() {
                    if (printAllEvents)
                        printMsg("Reconnected.");
                }
            });
        
        masterListener.registerReconnectingListener(new ReconnectingListener() {
                public void reconnecting() {
                    if (printAllEvents)
                        printMsg("Reconnecting.");
                }
            });
        
        masterListener.registerServerMessageListener(new ServerMessageListener() {
                public void receivedMessage(byte[] message) {
                    if (printNotices || printAllEvents)
                        printMsg(String.format("(from server) %s",
                                     fromMessageBytes(message)));
                }
            });
    }
    
    // Main
    
    /**
     * Runs a new {@code BenchmarkClient} application.
     * 
     * @param args the commandline arguments (not used)
     */
    public static void main(String[] args) {
        BenchmarkClient client = new BenchmarkClient();
        boolean quitOnException = false;
        
        // TODO - cheesy command line parsing used here
        for (int i=0; i < args.length; i++) {
            if (args[i].equals("-c")) {
                client.printCommands(true);
            }
            else if (args[i].equals("-e")) {
                client.printAllEvents(true);
            }
            else if (args[i].equals("-h")) {
                System.out.println("Usage: java com.sun.sgs.benchmark.client." +
                    "BenchmarkClient [-c] [-e] [-h] [-n] [-q] [-s]\n" +
                    "  -c   Enable messages to stdout whenever a command is" +
                    " executed (default:\n" +
                    "       enabled). Think 'commands'.\n" +
                    "  -e   Enable message to stdout whenever ANY event occurs" +
                    " (default:\n" +
                    "       disabled). Think 'events'.\n" +
                    "  -h   Print this help message\n" +
                    "  -n   Enable notification messages to stdout when" +
                    " certain significant\n" +
                    "       events occur (default: enabled). Think 'notify'.\n" +
                    "  -q   Quit JVM on any syntax error when processing input" +
                    ", instead of\n" +
                    "       just printing an error (default: disabled). Think" +
                    " 'quit'.\n" +
                    "  -s   Disable all informational messages. Think" +
                    " 'squelch'.");
                
                System.exit(0);
            }
            else if (args[i].equals("-n")) {
                client.printNotices(true);
            }
            else if (args[i].equals("-q")) {
                quitOnException = true;
            }
            else if (args[i].equals("-s")) {
                client.printAllEvents(false);
                client.printCommands(false);
                client.printNotices(false);
            }
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));    
        String line;
        int lineNum = 0;
        
        try {
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                if (line.length() > 0 && (!line.startsWith("#"))) {
                    try {
                        client.processInput(line);
                    } catch (ParseException e) {
                        System.err.println("Syntax error on line " +
                            lineNum + ": " + e.getMessage());
                        
                        if (quitOnException) System.exit(0);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Reading stdin: " + e);
            System.exit(-1);
            return;
        }
    }
    
    /** Public Methods */
    
    public SessionId getSessionId() { return client.getSessionId(); }
    
    public boolean isConnected() { return client.isConnected(); }
    
    public void printAllEvents(boolean enable) { printAllEvents = enable; }
    
    public void printCommands(boolean enable) { printCommands = enable; }
    
    public void printNotices(boolean enable) { printNotices = enable; }
    
    public void processInput(String line) throws ParseException {
        ScriptingCommand cmd = ScriptingCommand.parse(line);
        
        /** Check for control flow commands */
        switch (cmd.getType()) {
        case END_BLOCK:
            if (!inBlock) {
                throw new ParseException("Close brace with no matching" +
                    " open brace.", 0);
            }
            
            tags.put(activeTag, tagCmdList);
            activeTag = null;
            inBlock = false;
            break;
            
        case TAG:
            if (inBlock) {
                throw new ParseException("Nested blocks are not supported.", 0);
            }
            
            if (activeTag != null) {
                throw new ParseException("Sequential TAG commands; " +
                    "perhaps a command is missing in between?", 0);
            }
            
            activeTag = cmd.getTagNameArg();
            tagCmdList = new LinkedList<ScriptingCommand>();
            break;
            
        case START_BLOCK:
            if (inBlock) {
                throw new ParseException("Nested blocks are not supported.", 0);
            }
            
            if (activeTag == null) {
                throw new ParseException("Blocks can only start immediately" +
                    " following TAG commands.", 0);
            }
            
            inBlock = true;
            break;
            
        default:
            /**
             * If we are not in a block, but activeTag is not null, then this
             * command immediately followed an TAG command.
             */
            if (!inBlock && activeTag != null) {
                tagCmdList.add(cmd);
                tags.put(activeTag, tagCmdList);
                activeTag = null;
            }
            /** Else, if we are in a block, then just store this command. */
            else if (inBlock) {
                tagCmdList.add(cmd);
            }
            /** Else, just a normal situation: execute command now. */
            else {
                executeCommand(cmd);
            }
        }
    }
    
    /*
     * PRIVATE METHODS
     */
    
    private void assignEventHandler(ScriptingEvent event, final String tagName,
        final int repeat)
    {
        /*
         * TODO - some of these commands might want to take the arguments of the
         * event call in as arguments themselves.  Not sure best way to do that.
         */
        switch (event) {
        case DISCONNECTED:
            masterListener.registerDisconnectedListener(new DisconnectedListener() {
                    public void disconnected(boolean graceful, String reason) {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case JOINED_CHANNEL:
            masterListener.registerJoinedChannelListener(new JoinedChannelListener() {
                    public void joinedChannel(ClientChannel channel) {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case LEFT_CHANNEL:
            masterListener.registerLeftChannelListener(new LeftChannelListener() {
                    public void leftChannel(String channelName) {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case LOGGED_IN:
            masterListener.registerLoggedInListener(new LoggedInListener() {
                    public void loggedIn() {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case LOGIN_FAILED:
            masterListener.registerLoginFailedListener(new LoginFailedListener() {
                    public void loginFailed(String reason) {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case RECONNECTED:
            masterListener.registerReconnectedListener(new ReconnectedListener() {
                    public void reconnected() {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case RECONNECTING:
            masterListener.registerReconnectingListener(new ReconnectingListener() {
                    public void reconnecting() {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case RECV_CHANNEL:
            masterListener.registerChannelMessageListener(new ChannelMessageListener() {
                    public void receivedMessage(String channelName,
                        SessionId sender, byte[] message)
                    {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        case RECV_DIRECT:
            masterListener.registerServerMessageListener(new ServerMessageListener() {
                    public void receivedMessage(byte[] message) {
                        executeTag(tagName, repeat);
                    }
                });
            break;
            
        default:
            throw new IllegalArgumentException("Unsupported event: " + event);
        }
    }
    
    /*
     * Creates a {@code MethodRequest} for the given {@code ScriptingCommand}.
     */
    private MethodRequest createMethodRequest(ScriptingCommand cmd)
        throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        
        switch (cmd.getType()) {
        case CPU:
            oos.writeLong(cmd.getDurationArg());
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.CPU,
                baos.toByteArray());
            
        case CREATE_CHANNEL:
            oos.writeObject(cmd.getChannelNameArg());
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.CREATE_CHANNEL,
                baos.toByteArray());
            
        case DATASTORE_CREATE:
            oos.writeObject(cmd.getObjectNameArg());
            oos.writeObject(cmd.getClassNameArg());
            
            if (cmd.getSizeArg() != -1) {
                oos.writeInt(cmd.getSizeArg());
            } else {
                oos.writeObject(new Class<?>[0]);
                oos.writeObject(new Object[0]);
            }
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.DATASTORE_CREATE,
                baos.toByteArray());
            
        case DATASTORE_READ:
            oos.writeObject(cmd.getObjectNameArg());
            oos.writeBoolean(false);  /** do not mark for update */
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.DATASTORE_GET,
                baos.toByteArray());
            
        case DATASTORE_WRITE:
            oos.writeObject(cmd.getObjectNameArg());
            oos.writeBoolean(true);  /** mark for update */
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.DATASTORE_GET,
                baos.toByteArray());
            
        case JOIN_CHANNEL:
            oos.writeObject(cmd.getChannelNameArg());
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.JOIN_CHANNEL,
                baos.toByteArray());
            
        case LEAVE_CHANNEL:
            oos.writeObject(cmd.getChannelNameArg());
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.LEAVE_CHANNEL,
                baos.toByteArray());
            
        case MALLOC:
            oos.writeObject(byte[].class.getName());
            oos.writeInt(cmd.getSizeArg());
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.MALLOC,
                baos.toByteArray());
            
        case NO_OP:
            oos.writeObject(cmd.getMessageArg());
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.NOOP,
                baos.toByteArray());
            
        case REQ_RESPONSE:
            CodeMethodRequestOp op;
            String channelName = cmd.getChannelNameArg();
            if (channelName != null) {
                op = CodeMethodRequestOp.SEND_CHANNEL;
                oos.writeObject(channelName);
            } else {
                op = CodeMethodRequestOp.SEND_DIRECT;
            }
            oos.writeObject("a");
            oos.writeInt(cmd.getSizeArg());
            oos.close();
            return new CodeMethodRequest(op, baos.toByteArray());
            
        case START_TASK:
            List<ScriptingCommand> taskCmds = tags.get(cmd.getTagNameArg());
            if (taskCmds == null)
                throw new IllegalArgumentException("Unknown tag: " +
                    cmd.getTagNameArg());
            
            oos.writeObject(cmd.getTaskTypeArg());
            oos.writeLong(cmd.getDelayArg());
            oos.writeLong(cmd.getPeriodArg());
            oos.writeInt(taskCmds.size());
            
            for (ScriptingCommand taskCmd : taskCmds) {
                MethodRequest request = createMethodRequest(taskCmd);
                if (request == null)
                    throw new IllegalArgumentException("Command " + taskCmd +
                        " is not a legal participant in server tasks.");
                
                oos.writeObject(request);
            }
            oos.close();
            return new CodeMethodRequest(CodeMethodRequestOp.START_TASK,
                baos.toByteArray());
            
        default:
            return null;  /** No method request for this command type */
        }
    }
    
    /*
     * TODO - since this handles calls from both SimpleClientListener and
     * ClientChannelListener, could calls on those 2 interfaces be mixed,
     * meaning we should synchronize to protect member variables?
     */
    private void executeCommand(ScriptingCommand cmd) {
        try {
            if (printCommands)
                printMsg(String.format("Executing: %s", cmd.getType()));
            
            /** If required, send a method request. */
            MethodRequest request = createMethodRequest(cmd);
            if (request != null) sendServerMessage(request);
            
            /**
             * Perform any other actions that need to be done outside of the
             * MethodRequest call.
             */
            switch (cmd.getType()) {
            case CONFIG:
                if (cmd.getHostnameArg() != null) {
                    serverHostname = cmd.getHostnameArg();
                    
                    if (cmd.getPortArg() != -1)
                        serverPort = "" + cmd.getPortArg();
                }
                else {
                    /** Just a query */
                    System.out.printf("Server is currently configured to" +
                        " %s:%s\n", serverHostname, serverPort);
                }
                break;
                
            case DISCONNECT:
                client.logout(true);   /* force */
                break;
                
            case DO_TAG:
                executeTag(cmd.getTagNameArg(), cmd.getCountArg());
                break;
                
            case EXIT:
                System.exit(0);
                break;
                
            case HELP:
                if (cmd.getTopicArg() == null) {
                    System.out.println(ScriptingCommand.getHelpString());
                } else {
                    System.out.println(ScriptingCommand.getHelpString(cmd.getTopicArg()));
                }
                break;
                
            case LOGIN:
                sendLogin(cmd.getLoginArg(), cmd.getPasswordArg());
                break;
                
            case LOGOUT:
                client.logout(false);   /* non-force */
                break;
                
            case ON_EVENT:
                assignEventHandler(cmd.getEventArg(), cmd.getTagNameArg(),
                    cmd.getCountArg());
                break;
                
            case PAUSE:
                long start = System.currentTimeMillis();
                long elapsed = 0;
                long duration = cmd.getDurationArg();
                
                synchronized (pauseLock) {
                    while (elapsed < duration) {
                        try {
                            pauseLock.wait(duration - elapsed);
                        }
                        catch (InterruptedException e) { }
                        
                        elapsed = System.currentTimeMillis() - start;
                    }
                }
                break;
                
            case PRINT:
                printMsg(String.format("PRINT: %s", cmd.getPrintArg()));
                return;
                
            case SEND_PM:
            case SEND_CHANNEL:
                ClientChannel channel = channels.get(cmd.getChannelNameArg());
                
                if (channel == null) {
                    throw new IllegalArgumentException("Error: unknown channel" +
                        " \"" + cmd.getChannelNameArg() + "\"");
                } else {
                    Set<SessionId> recipients = cmd.getRecipients();
                    if (recipients == null)
                        channel.send(toMessageBytes(cmd.getMessageArg()));
                    else
                        channel.send(recipients, toMessageBytes(cmd.getMessageArg()));
                }
                break;
                
            case SEND_DIRECT:
                sendServerMessage(cmd.getMessageArg());
                break;
                
            case WAIT_FOR:
                /** Register listener to call notify() when event happens. */
                modifyNotificationEventHandler(cmd.getEventArg(), true);
                
                while (true) {
                    try {
                        synchronized(waitLock) {
                            waitLock.wait();
                        }
                            
                        /**
                         * Unregister listener so it doesn't screw up
                         * future WAIT_FOR commands.
                         */
                        modifyNotificationEventHandler(cmd.getEventArg(), false);
                            
                        break;  /** Only break after wait() returns normally. */
                    }
                    catch (InterruptedException e) { }
                }
                break;
            }
        /** catch ALL exceptions here */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void executeCommands(List<ScriptingCommand> cmds) {
        for (ScriptingCommand cmd : cmds)
            executeCommand(cmd);
    }
    
    private void executeTag(String tagName, int repeat) {
        if (tags.containsKey(tagName)) {
            for (int i=0; i < repeat; i++) {
                executeCommands(tags.get(tagName));
            }
        }
        else {
            System.err.println("Error executing tag \"" + tagName + "\": tag" +
                " not found.");
        }
    }
    
    /**
     * Nicely format a {@link SessionId} for printed display.
     *
     * @param session the {@code SessionId} to format
     * @return the formatted string
     */
    private String formatSession(SessionId session) {
        if (session == null) {
            return "[null]";
        } else {
            return "[" + HexDumper.toHexString(session.toBytes()) + "]";
        }
    }
    
    private void modifyNotificationEventHandler(ScriptingEvent event,
        boolean register)
    {
        switch (event) {
        case DISCONNECTED:
            masterListener.registerDisconnectedListener(new DisconnectedListener() {
                    public void disconnected(boolean graceful, String reason) {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case JOINED_CHANNEL:
            masterListener.registerJoinedChannelListener(new JoinedChannelListener() {
                    public void joinedChannel(ClientChannel channel) {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case LEFT_CHANNEL:
            masterListener.registerLeftChannelListener(new LeftChannelListener() {
                    public void leftChannel(String channelName) {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case LOGGED_IN:
            masterListener.registerLoggedInListener(new LoggedInListener() {
                    public void loggedIn() {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case LOGIN_FAILED:
            masterListener.registerLoginFailedListener(new LoginFailedListener() {
                    public void loginFailed(String reason) {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case RECONNECTED:
            masterListener.registerReconnectedListener(new ReconnectedListener() {
                    public void reconnected() {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case RECONNECTING:
            masterListener.registerReconnectingListener(new ReconnectingListener() {
                    public void reconnecting() {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case RECV_CHANNEL:
            masterListener.registerChannelMessageListener(new ChannelMessageListener() {
                    public void receivedMessage(String channelName,
                        SessionId sender, byte[] message)
                    {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        case RECV_DIRECT:
            masterListener.registerServerMessageListener(new ServerMessageListener() {
                    public void receivedMessage(byte[] message) {
                        synchronized(waitLock) {
                            waitLock.notifyAll();
                        }
                    }
                });
            break;
            
        default:
            throw new IllegalArgumentException("Unsupported event: " + event);
        }
    }
    
    /**
     * Formatted output to {@code System.out}.
     */
    private void printMsg(String msg) {
        System.out.printf("%s\tBenchmarkClient-%d\t%s\n",
            dateFormat.format(new Date()), id, msg);
    }
    
    private void sendLogin(String login, String password) throws IOException {
        /**
         * The "master listener" needs the login and password for
         * SimpleClientListener.getPasswordAuthentication()
         */
        masterListener.setPasswordAuthentication(login, password);
        
        if (printNotices || printAllEvents)
            printMsg(String.format("Connecting to %s:%s", serverHostname,
                         serverPort));
        
        Properties props = new Properties();
        props.put("host", serverHostname);
        props.put("port", serverPort);
        client.login(props);
    }
    
    private void sendServerMessage(MethodRequest req) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(req);
        oos.close();
        
        byte[] ba = baos.toByteArray();
        client.send(ba);
        
        /*
          System.out.printf("sending MethodRequest: %d bytes\n", ba.length);
        */
    }
    
    private void sendServerMessage(String message) throws IOException {
        client.send(toMessageBytes(message));
    }
    
    // utility methods
    
    /**
     * Decodes the given {@code bytes} into a message string.
     *
     * @param bytes the encoded message
     * @return the decoded message string
     */
    public static String fromMessageBytes(byte[] bytes) {
        try {
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset UTF-8 not found", e);
        }
    }

    /**
     * Encodes the given message string into a byte array.
     *
     * @param s the message string to encode
     * @return the encoded message as a byte array
     */
    public static byte[] toMessageBytes(String s) {
        try {
            return s.getBytes(MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset UTF-8 not found", e);
        }
    }
}
