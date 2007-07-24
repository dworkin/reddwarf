/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.benchmark.shared.CustomTaskType;

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
    private long delay = 0;   /** optional argument; must default to 0 */
    private long period = -1;
    private int size = -1;
    private String channelName = null;
    private String className = null;
    private String hostname = null;
    private String objectName = null;
    private String tagName = null;
    private String topic = null;
    private String msg = null;
    private String printArg = null;
    private String login = null, password = null;
    private ScriptingEvent event = null;
    private CustomTaskType taskType = null;
    private List<String> recips = new LinkedList<String>();
    
    /** Constructors */
    
    /** use parse() to create instances */
    private ScriptingCommand(ScriptingCommandType type) {
        this.type = type;
    }
    
    /** Public Methods */
    
    public String getChannelNameArg() { return channelName; }
    
    public String getClassNameArg() { return className; }
    
    public int getCountArg() { return count; }
    
    public long getDelayArg() { return delay; }
    
    public long getDurationArg() { return duration; }
    
    public ScriptingEvent getEventArg() { return event; }
    
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
    
    public String getHostnameArg() { return hostname; }
    
    public String getLoginArg() { return login; }
    
    public String getMessageArg() { return msg; }
    
    public String getObjectNameArg() { return objectName; }
    
    public String getPasswordArg() { return password; }
    
    public long getPeriodArg() { return period; }
    
    public int getPortArg() { return port; }
    
    public String getPrintArg() { return printArg; }
    
    public List<String> getRecipientArgs() {
        return Collections.unmodifiableList(recips);
    }
    
    public int getSizeArg() { return size; }
    
    public String getTagNameArg() { return tagName; }
    
    public CustomTaskType getTaskTypeArg() { return taskType; }
    
    public String getTopicArg() { return topic; }
    
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
                
                if (args.length == 2) {  /** Optional argument */
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
            break;
            
        case DATASTORE_CREATE:
            if (args.length >= 2 && args.length <= 3) {
                objectName = args[0];
                className = args[1];
                boolean isArray;
                
                try {
                    Class<?> clazz = Class.forName(className);
                    isArray = clazz.isArray();
                } catch (ClassNotFoundException e) {
                    isArray = className.startsWith("[");  /** guess */
                }
                
                if (isArray) {
                    /** Note: only single-dimension arrays are supported. */
                    if (args.length == 3) {
                        try {
                            size = Integer.valueOf(args[2]);
                            if (size < 0) {
                                size = -1;
                                throw new NumberFormatException();
                            }
                            return;
                        }
                        catch (NumberFormatException e) {
                            throw new ParseException("Invalid " + type + " argument, " +
                                " must be a non-negative integer: " + args[2], 0);
                        }
                    }
                } else {  /** Not an array type */
                    if (args.length == 2) return;
                }
            }
            break;
            
        case DATASTORE_READ:
            if (args.length == 1) {
                objectName = args[0];
                return;
            }
            break;
            
        case DATASTORE_WRITE:
            if (args.length == 1) {
                objectName = args[0];
                return;
            }
            break;
            
        case DISCONNECT:
            if (args.length == 0) return;  /** No arguments */
            break;
            
        case DO_TAG:
            if (args.length >= 1 && args.length <= 2) {
                tagName = args[0];
                count = 1;  /** default */
                
                if (args.length == 2) {  /** Optional argument */
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
            
        case NO_OP:
            /** Accept whole line as argument, including spaces */
            msg = stripQuotes(strJoin(args));
            return;
            
        case ON_EVENT:
            if (args.length >= 2 && args.length <= 3) {
                event = ScriptingEvent.parse(args[0]);
                
                if (event == null) {
                    throw new ParseException("Invalid ScriptingEvent alias: " +
                        args[0], 0);
                }
                
                tagName = args[1];
                count = 1;  /** default */
                
                if (args.length == 3) {
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
            msg = stripQuotes(strJoin(args));
            return;
            
        case START_BLOCK:
            if (args.length == 0) return;  /** No arguments */
            break;
            
        case START_TASK:
            if (args.length >= 2 && args.length <= 4) {
                tagName = args[0];
                
                try {
                    taskType = Enum.valueOf(CustomTaskType.class, args[1].toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new ParseException("Invalid " + type + " argument, " +
                        " not recognized: " + args[1] + ".  Should be one of: " +
                        Arrays.toString(CustomTaskType.values()), 0);
                }
                
                if (args.length >= 3) {
                    try {
                        delay = Long.valueOf(args[2]);
                        if (delay < 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        throw new ParseException("Invalid " + type +
                            " argument, must be a non-negative integer: " +
                            args[2], 0);
                    }
                }
                
                if (args.length >= 4) {
                    try {
                        period = Long.valueOf(args[3]);
                        if (period < 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        throw new ParseException("Invalid " + type +
                            " argument, must be a non-negative integer: " +
                            args[3], 0);
                    }
                }
                return;
            }
            
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
            
        case DATASTORE_CREATE:
            return "object-name class-name  - or -  " +
                "object-name array-class-name size";
            
        case DATASTORE_READ:
            return "object-name";
            
        case DATASTORE_WRITE:
            return "object-name";
           
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
            
        case NO_OP:
            return "[message] (ignored by server)";
            
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
            
        case START_TASK:
            return "tag task-type [delay_ms [period_ms]]";
            
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
