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
    public static final int HELP_INDENT_LEN = 16;
    public static final String HELP_INDENT_STR;
    public static final String NEWLINE = System.getProperty("line.separator");
    
    static {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < HELP_INDENT_LEN; i++) sb.append(' ');
        HELP_INDENT_STR = sb.toString();
    }
    
    /** Member variablees */
    private ScriptingCommandType type;
    
    private long duration = -1;           /** CPU */
    private long size = -1;               /** MALLOC */
    private String channelName = null;    /** JOIN_, LEAVE_, SEND_CHANNEL */
    private String topic = null;          /** HELP */
    private String msg = null;            /** SEND_CHANNEL, SEND_DIRECT */
    private ScriptingEvent event = null;  /** ON_EVENT */
    private String login = null, password = null;  /** LOGIN */
    private List<String> recips = new LinkedList<String>();  /** SEND_CHANNEL */
    private List<String> prints = new LinkedList<String>();  /** PRINT */
    
    /** Constructors */
    
    /** use parse() to create instances */
    private ScriptingCommand(ScriptingCommandType type) {
        this.type = type;
    }
    
    /** Public Methods */
    
    public String getChannelName() { return channelName; }
    
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
    
    public String getLogin() { return login; }
    
    public String getMessage() { return msg; }
    
    public String getPassword() { return password; }
    
    public String getTopic() { return topic; }
    
    public List<String> getPrintArgs() {
        return Collections.unmodifiableList(prints);
    }
    
    public List<String> getRecipients() {
        return Collections.unmodifiableList(recips);
    }
    
    public long getSize() { return size; }
    
    public ScriptingCommandType getType() { return type; }
    
    public static ScriptingCommand parse(String line)
    throws ParseException
    {
        String[] parts = line.trim().split(DELIMITER);
        
        ScriptingCommandType type = ScriptingCommandType.parse(parts[0]);
        
        if (type == null) {
            throw new ParseException("Unrecognized command: " + parts[0], 0);
        }
        
        ScriptingCommand cmd = new ScriptingCommand(type);
        
        for (int i=1; i < parts.length; i++) {
            if (parts[i].startsWith("@")) {  /** Property */
                String[] fields = parts[i].split("=", 2);
                
                if (fields.length == 1) {
                    if (fields[0].trim().length() > 0)  /* Ignore empty strings */
                        throw new ParseException("Invalid property format: " +
                            parts[i], 0);
                }
                else {
                    cmd.addProperty(cleanupString(fields[0]),
                        cleanupString(fields[1]));
                }
            }
            else {  /** Argument */
                cmd.addArgument(i - 1, cleanupString(parts[i]));
            }
        }
        
        /** confirm that all required arguments were present */
        if (!cmd.checkComplete()) {
            throw new ParseException("Missing argument.  " + type + " syntax: " +
                type.getAlias() + " " + getArgList(type), 0);
        }
        
        return cmd;
    }
    
    /** Private Methods */
    
    private void addArgument(int index, String arg) throws ParseException
    {
        switch (type) {
        case CPU:
            if (index == 0) {  /** 0 = duration (ms) */
                try {
                    duration = Long.valueOf(arg);
                    if (duration <= 0) throw new NumberFormatException();
                    return;
                }
                catch (NumberFormatException e) {
                    throw new ParseException("Invalid CPU argument, must be a" +
                        " positive integer: " + arg, 0);
                }
            }
            break;
            
        case DATASTORE:
            break;  /** No arguments */

        case DISCONNECT:
            break;  /** No arguments */

        case END_BLOCK:
            break;  /** No arguments */

        case EXIT:
            break;  /** No arguments */
            
        case HELP:
            if (index == 0) {  /** 0 = topic (optional) */
                topic = arg;
                return;
            }
            break;  /** No arguments */
            
        case JOIN_CHANNEL:
            if (index == 0) {  /** 0 = channel name */
                channelName = arg;
                return;
            }
            break;

        case LEAVE_CHANNEL:
            if (index == 0) {  /** 0 = channel name */
                channelName = arg;
                return;
            }
            break;
            
        case LOGIN:
            if (index == 0) {  /** 0 = login name */
                login = arg;
                return;
            }
            else if (index == 1) {  /** 1 = password */
                password = arg;
                return;
            }
            break;

        case LOGOUT:
            break;  /** No arguments */

        case MALLOC:
            if (index == 0) {  /** 0 = size (bytes) */
                try {
                    size = Long.valueOf(arg);
                    if (size <= 0) throw new NumberFormatException();
                    return;
                }
                catch (NumberFormatException e) {
                    throw new ParseException("Invalid MALLOC argument, must be a" +
                        " positive integer: " + arg, 0);
                }
            }
            break;

        case ON_EVENT:
            if (index == 0) {  /** 0 = event trigger */
                event = ScriptingEvent.parse(arg);
                
                if (event == null) {
                    throw new ParseException("Invalid ScriptingEvent alias: " +
                        arg, 0);
                }
                
                return;
            }
            break;
            
        case PRINT:
            /** No checks on arguments; everything accepted. */
            prints.add(arg);
            return;

        case SEND_CHANNEL:
            if (index == 0) {  /** 0 = channel name */
                channelName = arg;
                return;
            }
            else if (index == 1) { /** 1 = message */
                msg = arg;
                return;
            }
            else {  /** 2+ = recipient name (TODO - name or id?) */
                recips.add(arg);
                return;
            }
            
        case SEND_DIRECT:
            if (index == 0) {  /** message */
                msg = arg;
                return;
            }
            break;

        case START_BLOCK:
            break;  /** No arguments */
            
        default:
            throw new IllegalStateException("ScriptingCommand.addProperty()" +
                " switch statement fell through without matching any cases. " +
                " this=" + this);
        }
        
        /**
         * If control reaches here, the argument is not supported by this
         * particular ScriptingCommand type.
         */
        throw new ParseException("Excessive arguments to " + this + " command: " +
            arg, 0);
    }
    
    private void addProperty(String key, String value) throws ParseException
    {
        switch (type) {
        case CPU:
            break;
        
        case DATASTORE:
            break;

        case DISCONNECT:
            break;

        case END_BLOCK:
            break;
            
        case EXIT:
            break;

        case HELP:
            break;
            
        case JOIN_CHANNEL:
            break;

        case LEAVE_CHANNEL:
            break;

        case LOGIN:
            break;

        case LOGOUT:
            break;

        case MALLOC:
            break;

        case ON_EVENT:
            break;

        case PRINT:
            break;

        case SEND_CHANNEL:
            break;

        case SEND_DIRECT:
            break;

        case START_BLOCK:
            break;
            
        default:
            throw new IllegalStateException("ScriptingCommand.addProperty()" +
                " switch statement fell through without matching any cases. " +
                " this=" + this);
        }
        
        /**
         * If control reaches here, the property is not recognized/supported by
         * this particular ScriptingCommand type.
         */
        throw new ParseException("\"" + key + "\" property not supported by " +
            this + " command.", 0);
    }
    
    /** 
     * Throws a ParseException if not all of the commands's required arguments
     * have been set.
     */
    private boolean checkComplete() {
        switch (type) {
        case CPU:
            return (duration != -1);
            
        case DATASTORE:
            return true;  /** no arguments (yet) */

        case DISCONNECT:
            return true;  /** no arguments (yet) */

        case END_BLOCK:
            return true;  /** no arguments (yet) */

        case EXIT:
            return true;  /** no arguments (yet) */
            
        case HELP:
            return true;  /** no arguments (yet) */
            
        case JOIN_CHANNEL:
            return (channelName != null);

        case LEAVE_CHANNEL:
            return (channelName != null);

        case LOGIN:
            return (login != null && password != null);

        case LOGOUT:
            return true;  /** no arguments (yet) */

        case MALLOC:
            return (size != -1);

        case ON_EVENT:
            return (event != null);

        case PRINT:
            return (prints.size() > 0);

        case SEND_CHANNEL:
            return (channelName != null && msg != null);

        case SEND_DIRECT:
            return (msg != null);

        case START_BLOCK:
            return true;  /** no arguments (yet) */
            
        default:
            throw new IllegalStateException("ScriptingCommand.checkComplete()" +
                " switch statement fell through without matching any cases. " +
                " this=" + this);
        }
    }
    
    private static String cleanupString(String s) {
        s = s.trim().toLowerCase();
        
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("'") && s.endsWith("'"))) {
            
            s = s.substring(1, s.length() - 1).trim();
        }
        
        return s;
    }
    
    private static String getArgList(ScriptingCommandType type) {
        /** Note that only commands with arguments are listed */
        switch (type) {
        case CPU:
            return "duration_ms";
            
        case JOIN_CHANNEL:
            return "channel";
            
        case LEAVE_CHANNEL:
            return "channel";
            
        case LOGIN:
            return "username password";
            
        case MALLOC:
            return "size_bytes";
            
        case ON_EVENT:
            return "event";
            
        case PRINT:
            return "str1 [str2] [...]";
            
        case SEND_CHANNEL:
            return "channel msg [recipient1] [recipient2] [...]";

        default:
            return "";  /** no arguments */
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
        
        /** Properties (TODO) */
        // sb.append("??");
        
        return sb.toString();
    }
}
