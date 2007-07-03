/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.sgs.benchmark.client.listener.*;
import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;

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
public class BenchmarkClient
{
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
    
    /** Handles listener multiplexing. */
    private BenchmarkClientListener masterListener = new BenchmarkClientListener();
    
    /** Used for communication with the server */
    private SimpleClient client = new SimpleClient(masterListener);
        
    /** The mapping between sessions and names */
    private final Map<SessionId, String> sessionNames =
        new HashMap<SessionId, String>();
    
    /** Provides convenient lookup of channels by name. */
    private Map<String,ClientChannel> channels =
        new HashMap<String,ClientChannel>();
    
    /** The userName (i.e. login) for this client's session. */
    private String login;
    
    /** Variables to keep track of things while processing input: */
    
    /** Whether we are inside a block. */
    private boolean inBlock = false;
    
    /**
     * The active onEvent command.  If this reference is non-null, then either
     * we are inside of a block (as indicated by the inblock variable being
     * true) or the immediately previous line was an on_event command.
     */
    private ScriptingCommand onEvent = null;
    
    /**
     * The list of commands being stored up following an onEvent command.  The
     * contents of this list are only valid if onEvent is non-null.
     */
    private List<ScriptingCommand> onEventCmdList =
        new LinkedList<ScriptingCommand>();
    
    // Constructor

    /**
     * Creates a new {@code BenchmarkClient}.
     */
    public BenchmarkClient() {
        /** Register ourselves for a few events with the master listener. */
        masterListener.registerLoggedInListener(new LoggedInListener() {
                public void loggedIn() {
                    SessionId session = client.getSessionId();
                    sessionNames.put(session, login);
                    System.out.println("Notice: Logged in with session ID " + session);
                }
            });
        
        masterListener.registerLoginFailedListener(new LoginFailedListener() {
                public void loginFailed(String reason) {
                    System.out.println("Notice: Login failed: " + reason);
                }
            });
        
        masterListener.registerDisconnectedListener(new DisconnectedListener() {
                public void disconnected(boolean graceful, String reason) {
                    System.out.println("Notice: Disconnected " +
                        (graceful ? "gracefully" : "ungracefully") +
                        ": " + reason);
                }
            });
        
        masterListener.registerJoinedChannelListener(new JoinedChannelListener() {
                public void joinedChannel(ClientChannel channel) {
                    channels.put(channel.getName(), channel);
                }
            });
        
        masterListener.registerServerMessageListener(new ServerMessageListener() {
                public void receivedMessage(byte[] message) {
                    System.out.println("Notice: (from server) " +
                        fromMessageBytes(message));
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
        PushbackInputStream stdin = new PushbackInputStream(System.in, 100);
        String line;
        int lineNum = 0;
        
        BenchmarkClient client = new BenchmarkClient();
        
        try {
            while (!isEOF(stdin)) {
                line = getInputLine(stdin, 100);
                
                if (line != null) {
                    lineNum++;
                    line = line.trim();
                    
                    if (line.length() > 0) {
                        try {
                            client.processInput(line, lineNum);
                        } catch (ParseException e) {
                            System.err.println(e);
                        }
                    }
                }
                else {
                    try {
                        Thread.currentThread().sleep(100);  // ms
                    } catch (InterruptedException e) { }
                }
            }
        } catch (IOException e) {
            System.err.println("Reading stdin: " + e);
            System.exit(-1);
            return;
        }
        
        System.exit(0);
    }
    
    /** Public Methods */
    
    public void processInput(String line, int lineNum) throws ParseException {
        ScriptingCommand cmd;
        
        try {
            cmd = ScriptingCommand.parse(line);
        } catch (ParseException e) {
            throw new ParseException("Syntax error on line " + lineNum + ": " +
                e.getMessage(), 0);
        }
        
        /** Check for control flow commands */
        switch (cmd.getType()) {
        case END_BLOCK:
            if (!inBlock) {
                throw new ParseException("Close brace with no matching" +
                    "open brace.", 0);
            }
            
            inBlock = false;
            assignEventHandler(onEvent, onEventCmdList);
            break;
            
        case ON_EVENT:
            if (onEvent != null) {
                throw new ParseException("Sequential on_event commands; " +
                    "perhaps a command is missing in between?", 0);
            }
            
            onEvent = cmd;
            break;
            
        case START_BLOCK:
            if (inBlock) {
                throw new ParseException("Nested blocks are not supported.", 0);
            }
            
            if (onEvent == null) {
                throw new ParseException("Blocks can only start immediately" +
                    " after on_event commands.", 0);
            }
            
            inBlock = true;
            onEventCmdList.clear();
            break;
            
        default:
            /**
             * If we are not in a block, but onEvent is not null, then this
             * command immediately followed an onEvent command.
             */
            if (!inBlock && onEvent != null) {
                onEventCmdList.clear();
                onEventCmdList.add(cmd);
                assignEventHandler(onEvent, onEventCmdList);
                onEvent = null;
            }
            /** Else, if we are in a block, then just store this command. */
            else if (inBlock) {
                onEventCmdList.add(cmd);
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
    
    private void assignEventHandler(ScriptingCommand event,
        final List<ScriptingCommand> cmds)
        throws ParseException
    {
        
        /*
         * TODO - some of these commands might want to take the arguments of the
         * event call in as arguments themselves.  Not sure best way to do that.
         */
        
        switch (event.getEvent()) {
        case DISCONNECTED:
            masterListener.registerDisconnectedListener(new DisconnectedListener() {
                    public void disconnected(boolean graceful, String reason) {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case JOINED_CHANNEL:
            masterListener.registerJoinedChannelListener(new JoinedChannelListener() {
                    public void joinedChannel(ClientChannel channel) {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case LEFT_CHANNEL:
            masterListener.registerLeftChannelListener(new LeftChannelListener() {
                    public void leftChannel(String channelName) {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case LOGGED_IN:
            masterListener.registerLoggedInListener(new LoggedInListener() {
                    public void loggedIn() {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case LOGIN_FAILED:
            masterListener.registerLoginFailedListener(new LoginFailedListener() {
                    public void loginFailed(String reason) {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case RECONNECTED:
            masterListener.registerReconnectedListener(new ReconnectedListener() {
                    public void reconnected() {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case RECONNECTING:
            masterListener.registerReconnectingListener(new ReconnectingListener() {
                    public void reconnecting() {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case RECV_CHANNEL:
            masterListener.registerChannelMessageListener(new ChannelMessageListener() {
                    public void receivedMessage(String channelName,
                        SessionId sender,
                        byte[] message) {
                        executeCommands(cmds);
                    }
                });
            break;
            
        case RECV_DIRECT:
            masterListener.registerServerMessageListener(new ServerMessageListener() {
                    public void receivedMessage(byte[] message) {
                        executeCommands(cmds);
                    }
                });
            break;
            
        default:
            throw new ParseException("Unsupported event: " + event, 0);
        }
    }
    
    // todo
    private void debug(String s) {
        System.out.println("[debugging]  " + s);
    }
    
    /*
     * This method can be called into by the various listeners registered with
     * the masterListener, so access to it is synchronized in case any member
     * variables are touched in this method.
     *
     * TODO - is synchronization really needed?  not sure
     */
    private void executeCommand(ScriptingCommand cmd) {
        try {
            System.out.println("# Executing: " + cmd.getType());
            
            switch (cmd.getType()) {
            case CPU:
                cmd.getDuration();
                System.out.println("not yet implemented - TODO");
                break;
                
            case DATASTORE:
                System.out.println("not yet implemented - TODO");
                break;
                
            case DISCONNECT:
                client.logout(true /* force */);
                break;
                
            case EXIT:
                System.exit(0);
                break;
                
            case HELP:
                if (cmd.getTopic() == null) {
                    System.out.println(ScriptingCommand.getHelpString());
                } else {
                    System.out.println(ScriptingCommand.getHelpString(cmd.getTopic()));
                }
                break;
                
            case JOIN_CHANNEL:
                sendServerMessage("/join " + cmd.getChannelName());
                break;
                
            case LEAVE_CHANNEL:
                sendServerMessage("/leave " + cmd.getChannelName());
                break;
                
            case LOGIN:
                sendLogin(cmd.getLogin(), cmd.getPassword());
                break;
                
            case LOGOUT:
                client.logout(false /* non-force */);
                break;
                
            case MALLOC:
                cmd.getSize();
                System.out.println("not yet implemented - TODO");
                break;
                
            case PRINT:
                boolean first = true;
                StringBuilder sb = new StringBuilder("PRINT: ");
                
                for (String arg : cmd.getPrintArgs()) {
                    if (first) first = false;
                    else sb.append(", ");
                    sb.append(arg);
                }
                
                System.out.println(sb);
                return;
                
            case SEND_CHANNEL:
                // need to somehow get a handle to the channel -- probably will need
                // to save a map of channel-names to ClientChannel objects (which
                // get passed to you in joinedChannel() event call)
                cmd.getChannelName();
                cmd.getMessage();
                cmd.getRecipients();
                //sendServerMessage("/chsend ");
                System.out.println("not yet implemented - TODO");
                break;
                
            case SEND_DIRECT:
                sendServerMessage(cmd.getMessage());
                break;
                
            default:
                throw new IllegalStateException("ERROR!  executeCommand() does" +
                    " not support command type " + cmd.getType());
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }
    
    private void executeCommands(List<ScriptingCommand> cmds) {
        for (ScriptingCommand cmd : cmds)
            executeCommand(cmd);
    }
    
    private void sendLogin(String login, String password) throws IOException {
        String host = System.getProperty(HOST_PROPERTY, DEFAULT_HOST);
        String port = System.getProperty(PORT_PROPERTY, DEFAULT_PORT);
        
        /**
         * The "master listener" needs the login and password for
         * SimpleClientListener.getPasswordAuthentication()
         */
        masterListener.setPasswordAuthentication(login, password);
        
        this.login = login;
        
        Properties props = new Properties();
        props.put("host", host);
        props.put("port", port);
        client.login(props);
    }
    
    private void sendServerMessage(String message) throws IOException {
        client.send(toMessageBytes(message));
    }
    
    /**
     * Nicely format a {@link SessionId} for printed display.
     *
     * @param session the {@code SessionId} to format
     * @return the formatted string
     */
    private String formatSession(SessionId session) {
        return sessionNames.get(session) + " [" +
            toHexString(session.toBytes()) + "]";
    }

    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.
     *
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder buf = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            buf.append(String.format("%02X", b));
        }
        return buf.toString();
    }

    /**
     * Returns a byte array constructed with the contents of the given
     * string, which contains a series of byte values in hex format.
     *
     * @param hexString a string to convert
     * @return the byte array corresponding to the hex-formatted string
     */
    public static byte[] fromHexString(String hexString) {
        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < bytes.length; ++i) {
            String hexByte = hexString.substring(2*i, 2*i+2);
            bytes[i] = Integer.valueOf(hexByte, 16).byteValue();
        }
        return bytes;
    }
    
    private static String getInputLine(PushbackInputStream is, int readAheadLimit)
        throws IOException
    {
        int readTotal = 0;
        byte ba[] = new byte[readAheadLimit];
        
        while (readTotal < readAheadLimit) {
            if (is.available() == 0) break;
            
            int readlen = is.read(ba, readTotal, readAheadLimit - readTotal);
            
            for (int i=0; i < readlen; i++) {
                if (ba[readTotal + i] == '\n') {
                    is.unread(ba, readTotal + i + 1, readlen - (i + 1));
                    return new String(ba, 0, readTotal + i);
                }
            }
            
            readTotal += readlen;
        }
        
        is.unread(ba, 0, readTotal);
        return null;
    }
    
    private static boolean isEOF(PushbackInputStream is)
        throws IOException
    {
        if (is.available() > 0) {
            int c = is.read();
            if (c == -1) return true;
            is.unread(c);
        }
        
        return false;
    }
    
    private void userLogin(String memberString) {
        System.err.println("userLogin: " + memberString);
    }

    private void userLogout(String idString) {
        System.err.println("userLogout: " + idString);
        SessionId session = SessionId.fromBytes(fromHexString(idString));
        sessionNames.remove(session);
    }

    // Implement ClientChannelListener

    /**
     * {@inheritDoc}
     */
    /*
      public void receivedMessage(ClientChannel channel, SessionId sender,
      byte[] message)
      {
      try {
      String messageString = fromMessageBytes(message);
      System.err.format("Recv on %s from %s: %s\n",
      channel.getName(), sender, messageString);
      String[] args = messageString.split(" ", 2);
      String command = args[0];

      if (command.equals("/joined")) {
      userLogin(args[1]);
      } else if (command.equals("/left")) {
      userLogout(args[1]);
      } else if (command.equals("/members")) {
      List<String> members = Arrays.asList(args[1].split(" "));
      if (! members.isEmpty()) {
      //
      }
      } else if (command.equals("/pm")) {
      pmReceived(sender, args[1]);
      } else if (command.startsWith("/")) {
      System.err.format("Unknown command: %s\n", command);
      } else {
      System.err.format("Not a command: %s\n", messageString);
      }
      } catch (Exception e) {
      e.printStackTrace();
      }
      }
    */

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
