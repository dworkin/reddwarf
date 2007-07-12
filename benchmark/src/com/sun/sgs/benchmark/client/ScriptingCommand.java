/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/*
 *
 */
public class ScriptingCommand {
    /** Constants */
    public static final String DELIMITER = "\t";
    public static final String EMPTY_STR = "";
    public static final int HELP_INDENT_LEN = 20;
    public static final String HELP_INDENT_STR;
    public static final String NEWLINE = System.getProperty("line.separator");
    
    static {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < HELP_INDENT_LEN; i++) sb.append(' ');
        HELP_INDENT_STR = sb.toString();
    }
    
    /** Member variablees */
    private ScriptingCommandType type;
    
    private int count = -1;
    private int port = -1;
    private long duration = -1;
    private int size = -1;
    private String channelName = null;
    private String hostname = null;
    private String tagName = null;
    private String topic = null;
    private String msg = null;
    private String printArg = null;
    private ScriptingEvent event = null;
    private String login = null, password = null;
    private List<String> recips = new LinkedList<String>();
    
    /** Constructors */
    
    /** use parse() to create instances */
    private ScriptingCommand(ScriptingCommandType type) {
        this.type = type;
    }
    
    /** Public Methods */
    
    public String getChannelName() { return channelName; }
    
    public int getCount() { return count; }
    
    public long getDuration() { return duration; }
    
    public ScriptingEvent getEvent() { return event; }
    
    public static String getHelpString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" --- Available Commands --- ").append(NEWLINE);
        
        for (ScriptingCommandType type : ScriptingCommandType.values()) {
            sb.append(getUsage(type)).append(NEWLINE);  /** Just usage info */
        }
        
        return sb.toString();
    }
    
    public static String getHelpString(String cmdType) {
        if (cmdType.equalsIgnoreCase("events")) {
            return getEventsHelpString();
        } else {
            ScriptingCommandType type = ScriptingCommandType.parse(cmdType);
            
            if (type == null) {
                return "Unknown help topic.  Try 'help' or 'help events'.";
            } else {
                return getHelpString(type);
            }
        }
    }
    
    public static String getHelpString(ScriptingCommandType type) {
        StringBuilder sb = new StringBuilder();
        String[] aliases = type.getAliases();
        
        sb.append(getUsage(type)).append(NEWLINE);
        sb.append(HELP_INDENT_STR).append("Aliases: ");
        
        for (int i=0; i < aliases.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(aliases[i]);
        }
        
        return sb.toString();
    }
    
    public String getHostname() { return hostname; }
    
    public String getLogin() { return login; }
    
    public String getMessage() { return msg; }
    
    public String getPassword() { return password; }
    
    public int getPort() { return port; }
    
    public String getPrintArg() { return printArg; }
    
    public List<String> getRecipients() {
        return Collections.unmodifiableList(recips);
    }
    
    public int getSize() { return size; }
    
    public String getTagName() { return tagName; }
    
    public String getTopic() { return topic; }
    
    public ScriptingCommandType getType() { return type; }
    
    public static ScriptingCommand parse(String line)
    throws ParseException
    {
        String[] parts = line.trim().split("\\s+", 2);
        
        ScriptingCommandType type = ScriptingCommandType.parse(parts[0]);
        
        if (type == null) {
            throw new ParseException("Unrecognized command: " + parts[0], 0);
        }
        
        ScriptingCommand cmd = new ScriptingCommand(type);
        String[] args = (parts.length == 2) ? parts[1].split("\\s+") :
            new String[] { };
        
        cmd.parseArgs(args);
        return cmd;
    }
    
    /** Private Methods */
    
    private void parseArgs(String[] args) throws ParseException {
        switch (type) {
        case CONFIG:
            if (args.length <= 2) {
                if (args.length >= 1) {  /** Optional argument */
                    hostname = args[0];
                }
                
                if (args.length >= 2) {  /** Optional argument */
                    try {
                        port = Integer.valueOf(args[1]);
                        if (port <= 0) throw new NumberFormatException();
                    }
                    catch (NumberFormatException e) {
                        throw new ParseException("Invalid " + type + " argument," +
                            " must be a valid network port: " + args[1], 0);
                    }
                }
                return;
            }
            break;
            
        case CPU:
            if (args.length == 1) {
                try {
                    duration = Long.valueOf(args[0]);
                    if (duration <= 0) throw new NumberFormatException();
                }
                catch (NumberFormatException e) {
                    throw new ParseException("Invalid " + type + " argument," +
                        " must be a positive integer: " + args[0], 0);
                }
                return;
            }
            break;
            
        case CREATE_CHANNEL:
            if (args.length == 1) {
                channelName = args[0];
                return;
            }
            return;
            
        case DATASTORE:
            if (args.length == 0) return;  /** No arguments */
            break;

        case DISCONNECT:
            if (args.length == 0) return;  /** No arguments */
            break;
            
        case DO_TAG:
            if (args.length >= 1 && args.length <= 2) {
                tagName = args[0];
                count = 1;  /** default */
                
                if (args.length >= 2) {  /** Optional argument */
                    try {
                        count = Integer.valueOf(args[1]);
                        if (count <= 0) throw new NumberFormatException();
                    }
                    catch (NumberFormatException e) {
                        throw new ParseException("Invalid " + type + " argument," +
                            " must be a positive integer: " + args[1], 0);
                    }
                }
                
                return;
            }
            break;
            
        case END_BLOCK:
            if (args.length == 0) return;  /** No arguments */
            break;

        case EXIT:
            if (args.length == 0) return;  /** No arguments */
            break;
            
        case HELP:
            if (args.length <= 1) {
                if (args.length == 1) {  /** Optional argument */
                    topic = args[0];
                }
                return;
            }
            break;
            
        case JOIN_CHANNEL:
            if (args.length == 1) {
                channelName = args[0];
                return;
            }
            return;
            
        case LEAVE_CHANNEL:
            if (args.length == 1) {
                channelName = args[0];
                return;
            }
            return;
            
        case LOGIN:
            if (args.length == 2) {
                login = args[0];
                password = args[1];
                return;
            }
            break;

        case LOGOUT:
            if (args.length == 0) return;  /** No arguments */
            break;

        case MALLOC:
            if (args.length == 1) {
                try {
                    size = Integer.valueOf(args[0]);
                    if (size <= 0) throw new NumberFormatException();
                }
                catch (NumberFormatException e) {
                    throw new ParseException("Invalid " + type + " argument," +
                        " must be a positive integer: " + args[0], 0);
                }
                return;
            }
            break;
            
        case ON_EVENT:
            if (args.length >= 2 && args.length <= 3) {
                event = ScriptingEvent.parse(args[0]);
                
                if (event == null) {
                    throw new ParseException("Invalid ScriptingEvent alias: " +
                        args[0], 0);
                }
                
                tagName = args[1];
                count = 1;  /** default */
                
                if (args.length >= 3) {
                    try {
                        count = Integer.valueOf(args[2]);
                        if (count <= 0) throw new NumberFormatException();
                    }
                    catch (NumberFormatException e) {
                        throw new ParseException("Invalid " + type + " argument," +
                            " must be a positive integer: " + args[1], 0);
                    }
                }
                
                return;
            }
            break;
            
        case PAUSE:
            if (args.length == 1) {
                try {
                    duration = Long.valueOf(args[0]);
                    if (duration <= 0) throw new NumberFormatException();
                }
                catch (NumberFormatException e) {
                    throw new ParseException("Invalid " + type + " argument," +
                        " must be a positive integer: " + args[0], 0);
                }
                return;
            }
            break;
            
        case PRINT:
            /** Accept whole line as argument, including spaces */
            if (args.length == 0) break;
            printArg = stripQuotes(strJoin(args));
            return;
            
        case REQ_RESPONSE:
            if (args.length >= 1 && args.length <= 2) {
                String sizeArg;
                
                if (args.length == 2) {
                    channelName = args[0];
                    sizeArg = args[1];
                }
                else {
                    sizeArg = args[0];
                }
                
                try {
                    size = Integer.valueOf(sizeArg);
                    if (size < 0) throw new NumberFormatException();
                }
                catch (NumberFormatException e) {
                    throw new ParseException("Invalid " + type + " argument, " +
                        " must be a non-negative integer: " + args[0], 0);
                }
                return;
            }
            break;

        case SEND_CHANNEL:
            if (args.length >= 2) {
                channelName = args[0];
                msg = stripQuotes(strJoin(args, 1));
                return;
            }
            break;
            
        case SEND_DIRECT:
            /** Accept whole line as argument, including spaces */
            if (args.length == 0) break;
            printArg = stripQuotes(strJoin(args));
            return;
            
        case START_BLOCK:
            if (args.length == 0) return;  /** No arguments */
            break;
            
        case TAG:
            if (args.length == 1) {
                tagName = args[0];
                return;
            }
            break;
            
        case WAIT_FOR:
            if (args.length == 1) {
                event = ScriptingEvent.parse(args[0]);
                
                if (event == null) {
                    throw new ParseException("Invalid ScriptingEvent alias: " +
                        args[0], 0);
                }
                
                return;
            }
            break;
            
        default:
            throw new IllegalStateException("ScriptingCommand.addProperty()" +
                " switch statement fell through without matching any cases. " +
                " this=" + this);
        }
        
        /**
         * If control reaches here, the argument list was invalid (wrong number
         * of arguments for this particular ScriptingCommand type.
         */
        throw new ParseException("Wrong number of arguments to " + type +
            " command (" + args.length + ").  try 'help " + type + "'.", 0);
    }
    
    private static String stripQuotes(String s) {
        s = s.trim().toLowerCase();
        
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("'") && s.endsWith("'"))) {
            
            s = s.substring(1, s.length() - 1).trim();
        }
        
        return s;
    }
    
    private static String getArgList(ScriptingCommandType type) {
        switch (type) {
        case CONFIG:
            return "[hostname [port]]";
            
        case CPU:
            return "duration_ms";
            
        case CREATE_CHANNEL:
            return "channel  (may not contain spaces)";
            
        case DATASTORE:
            return "";
           
        case DISCONNECT:
            return "";
            
        case DO_TAG:
            return "tagname [repeat-count]";
            
        case END_BLOCK:
            return "";
            
        case EXIT:
            return "";
            
        case HELP:
            return "";
            
        case JOIN_CHANNEL:
            return "channel  (may not contain spaces)";
            
        case LEAVE_CHANNEL:
            return "channel  (may not contain spaces)";
            
        case LOGIN:
            return "username password";
            
        case LOGOUT:
            return "";
            
        case MALLOC:
            return "size_bytes";
            
        case ON_EVENT:
            return "event tagname [repeat-count]";
            
        case PAUSE:
            return "duration_ms";
            
        case PRINT:
            return "message   (may contain spaces)";
            
        case REQ_RESPONSE:
            return "[channel] size_bytes  (channel name may not contain spaces)";
            
        case SEND_CHANNEL:
            return "channel message  (message may contain spaces, but not channel)";
            
        case SEND_DIRECT:
            return "message   (may contain spaces)";
            
        case START_BLOCK:
            return "";
            
        case TAG:
            return "tagname";
            
        case WAIT_FOR:
            return "event";

        default:
            return "Error: unknown command-type: " + type;
        }
    }
    
    private static String getEventsHelpString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(" --- Available Events (Triggers) --- ").append(NEWLINE);
        
        for (ScriptingEvent evt : ScriptingEvent.values()) {
            String[] aliases = evt.getAliases();
            String desc = evt.toString();
            
            sb.append(desc);
            for (int i=desc.length(); i < HELP_INDENT_LEN; i++) sb.append(" ");
            sb.append("Aliases: ");
            
            for (int i=0; i < aliases.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(aliases[i]);
            }
            
            sb.append(")").append(NEWLINE);
        }
        
        return sb.toString();
    }
    
    private static String getUsage(ScriptingCommandType type) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(type);
        while (sb.length() < HELP_INDENT_LEN) sb.append(' ');
        sb.append("Usage: ").append(type.getAlias()).append(" ");
        
        /** Arguments */
        sb.append(getArgList(type));
        
        return sb.toString();
    }
    
    private static String strJoin(String[] strs) {
        return strJoin(strs, 0, strs.length);
    }
    
    private static String strJoin(String[] strs, int offset) {
        return strJoin(strs, offset, strs.length);
    }
    
    private static String strJoin(String[] strs, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        
        for (int i=offset; i < length; i++) {
            sb.append(strs[i]);
            sb.append(" ");
        }
        
        return sb.toString();
    }
}
