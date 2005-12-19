/**
 * *******************************************************************
 * This Software is copyright INRIA. 1997.
 * 
 * INRIA holds all the ownership rights on the Software. The scientific
 * community is asked to use the SOFTWARE in order to test and evaluate
 * it.
 * 
 * INRIA freely grants the right to use the Software. Any use or
 * reproduction of this Software to obtain profit or for commercial ends
 * being subject to obtaining the prior express authorization of INRIA.
 * 
 * INRIA authorizes any reproduction of this Software
 * 
 * - in limits defined in clauses 9 and 10 of the Berne agreement for
 * the protection of literary and artistic works respectively specify in
 * their paragraphs 2 and 3 authorizing only the reproduction and quoting
 * of works on the condition that :
 * 
 * - "this reproduction does not adversely affect the normal
 * exploitation of the work or cause any unjustified prejudice to the
 * legitimate interests of the author".
 * 
 * - that the quotations given by way of illustration and/or tuition
 * conform to the proper uses and that it mentions the source and name of
 * the author if this name features in the source",
 * 
 * - under the condition that this file is included with any
 * reproduction.
 * 
 * Any commercial use made without obtaining the prior express agreement
 * of INRIA would therefore constitute a fraudulent imitation.
 * 
 * The Software beeing currently developed, INRIA is assuming no
 * liability, and should not be responsible, in any manner or any case,
 * for any direct or indirect dammages sustained by the user.
 * ******************************************************************
 */

/*
 * Logger.java - Logger class.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 6 June 1996.
 * Updated: no.
 */
package inria.util;

import java.io.*;
import java.util.*;

/**
 * This class provides a flexible log mechanism. Some of the log functions can be
 * redirected to the application. The provided log functions include:
 * <ul>
 * <li> error(), print a message like 'error: null pointer' to the error log if
 * the trace flag is set. The error log is stderr (by default) or a log file
 * set by the application.
 * <li> warning(), like error() but the message is something like 'warning: no buffer'.
 * <li> trace(), print a message if the trace flag is set.
 * <li> fatal(), it is equivalent to two things: error() and System.exit().
 * <li> status(), print a message to the redirected logger if set,
 * otherwise it does nothing.
 * </ul>
 */
public class Logger {
    public static boolean debug = false;
    public static boolean trace = false;
    public static boolean append = true;
    private static PrintWriter errlog = null;
    private static PrintStream err = System.err;
    private static PrintStream out = System.out;
    private static PrintWriter traceOut = null;
    private static LoggerInterface catcher = null;
    protected static int errLogFileMaxSize = 32000;
    protected static String traceFile = null;

    /**
     * redirects trace, error and status messages to the given interface.
     * @param c the console.
     */
    public static void redirect(LoggerInterface c) {
        catcher = c;
    }

    /**
     * turns on/off the debug mode.
     * @param f the debug flag.
     */
    public static void setDebug(boolean f) {
        debug = f;
    }

    /**
     * turns on/off the trace mode.
     * @param f the trace flag.
     */
    public static void setTrace(boolean f) {
        trace = f;
    }

    /**
     * sets the errlog file. If the errlog file is given and logger is redirected,
     * an error message will be printed on both.
     * @param errLogFile the error log file name.
     */
    public static void setErrorLogFile(String errLogFile) {
        if (errLogFile == null || errLogFile.length() == 0) {
            return;
        } 

        errLogFile = Utilities.convertPathname(errLogFile);

        File file = new File(errLogFile);

        if (file.exists()) {
            if (file.length() > errLogFileMaxSize) {
                File file1 = new File(errLogFile + "1");

                file.renameTo(file1);
            }
        }

        try {
            FileOutputStream out = new FileOutputStream(errLogFile, append);

            errlog = new PrintWriter(out, true);
        } catch (IOException e) {
            return;
        }
    }

    /**
     * sets the trace file.
     * @param name the trace file name.
     */
    public static void setTraceFile(String name) {
        if (name == null || name.length() == 0) {
            return;
        } 

        name = Utilities.convertPathname(name);

        File file = new File(name);

        if (file.exists()) {
            File file1 = new File(name + "1");

            file.renameTo(file1);
        }

        try {
            FileOutputStream out = new FileOutputStream(name);

            traceOut = new PrintWriter(out, true);
        } catch (IOException e) {
            return;
        }

        traceFile = name;
    }

    public static String getTraceFile() {
        return traceFile;
    }

    /**
     * prints a trace message. If the error log file is set, this method always prints the
     * message to this file.
     * @param o the object from that the message is issued.
     * @param s the message to print.
     */
    public static void error(Object o, String s) {
        if (catcher != null) {
            catcher.error(o, s);
        } 
        if (errlog != null) {
            errlog.println(o.getClass().getName() + ": " + s);
        } else if (trace) {
            err.println(o.getClass().getName() + ": error: " + s);
        } 
        if (debug) {
            out.println(o.getClass().getName() + ": error: " + s);
        } 
    }

    public static void error(String s) {
        if (catcher != null) {
            catcher.error(s);
        } 
        if (errlog != null) {
            errlog.println(s);
        } else if (trace) {
            err.println("error: " + s);
        } 
    }

    public static void error(Object o, String s, Exception e) {
        error(o, s + " - " + e.getMessage());
    }

    public static void error(String s, Exception e) {
        error("error: " + s + " - " + e.getMessage());
    }

    public static void warning(Object o, String s) {
        if (trace) {
            err.println(o.getClass().getName() + ": warning: " + s);
        } 
    }

    public static void warning(String s) {
        if (trace) {
            err.println("warning: " + s);
        } 
    }

    /**
     * prints a message to stdout if the debug flag is true.
     * @param s the message to print.
     */
    public static void debug(String s) {
        if (debug) {
            out.println(s);
        } 
    }

    /**
     * prints a message to stdout if the debug flag is true.
     * @param o the object from that the message is issued.
     * @param s the message to print.
     */
    public static void debug(Object o, String s) {
        if (debug) {
            out.println(o.getClass().getName() + ": " + s);
        } 
    }

    /**
     * prints a message to stdout or the redirected logger if the trace flag is true.
     * @param s the message to print.
     */
    public static void trace(String s) {
        if (trace) {
            if (catcher != null) {
                catcher.trace(s);
            } 
            if (traceOut != null) {
                traceOut.println(s);
            } else {
                out.println(s);
            }
        }
    }

    /**
     * prints a message to stdout or the redirected logger if the trace flag is true.
     * @param o the object from that the message is issued.
     * @param s the message to print.
     */
    public static void trace(Object o, String s) {
        if (trace) {
            if (catcher != null) {
                catcher.trace(o, s);
            } 
            if (traceOut != null) {
                traceOut.println(shortClassname(o) + ": " + s);
            } else {
                out.println(o.getClass().getName() + ": " + s);
            }
        }
    }

    public static String shortClassname(Object o) {
        String name = o.getClass().getName();
        int i = name.lastIndexOf(".");

        if (i > 0) {
            return name.substring(i + 1);
        } 

        return name;
    }

    public static void fatal(Object o, String s) {
        if (errlog != null) {
            errlog.println(o.getClass().getName() + ": fatal: " + s);
        } 

        err.println(o.getClass().getName() + ": fatal: " + s);
        System.exit(-1);
    }

    public static void fatal(String s) {
        if (errlog != null) {
            errlog.println("fatal: " + s);
        } 

        err.println("fatal: " + s);
        System.exit(-1);
    }

    public static void dump(Object o, byte data[], int offset, int len) {
        if (!debug) {
            return;
        } 

        debug(o, "length=" + len);
        dump(data, offset, len);
    }

    public static void dump(byte data[], int offset, int len) {
        if (!debug) {
            return;
        } 

        StringBuffer sbuf = new StringBuffer();

        for (int i = 0; i < len; i++) {
            if (i % 32 == 0) {
                if (i > 0) {
                    out.println(sbuf.toString());
                    sbuf.setLength(0);
                }

                out.print("0x" + toUnsignedString(i, 4) + ":");
            }
            if (i % 4 == 0) {
                sbuf.append(" ");
            } 

            sbuf.append(Character.forDigit((data[offset + i] & 0xf0) >> 4, 
                                           16));
            sbuf.append(Character.forDigit(data[offset + i] & 0x0f, 16));
        }

        out.println(sbuf.toString());
    }

    public static void stringDump(byte data[], int offset, int len) {
        if (!debug) {
            return;
        } 

        stringDump(out, data, offset, len);
    }

    /**
     * unconditional dump.
     */
    public static void dump(PrintStream out, byte data[], int offset, 
                            int len) {
        StringBuffer sbuf = new StringBuffer();

        for (int i = 0; i < len; i++) {
            if (i % 32 == 0) {
                if (i > 0) {
                    out.println(sbuf.toString());
                    sbuf.setLength(0);
                }

                out.print("0x" + toUnsignedString(i, 4) + ":");
            }
            if (i % 4 == 0) {
                sbuf.append(" ");
            } 

            sbuf.append(Character.forDigit((data[offset + i] & 0xf0) >> 4, 
                                           16));
            sbuf.append(Character.forDigit(data[offset + i] & 0x0f, 16));
        }

        out.println(sbuf.toString());
    }

    public static void stringDump(PrintStream out, byte data[], int offset, 
                                  int len) {
        StringBuffer sbuf = new StringBuffer();

        for (int i = 0; i < len; i++) {
            if (i % 64 == 0) {
                if (i > 0) {
                    out.println(sbuf.toString());
                    sbuf.setLength(0);
                }

                out.print("0x" + toUnsignedString(i, 4) + ": ");
            }
            if (isPrintable(data[offset + i])) {
                char c = (char) (data[offset + i] & 0xff);

                sbuf.append(c);
            } else {
                sbuf.append('.');
            }
        }

        out.println(sbuf.toString());
    }

    private static boolean isPrintable(byte c) {
        int i = (int) c & 0xff;

        if (i < 32) {
            return false;
        } 
        if (i > 126 && i < 160) {
            return false;
        } 

        return true;
    }

    private static String toUnsignedString(int i, int shift) {
        StringBuffer buf = new StringBuffer(shift >= 3 ? 11 : 32);
        int radix = 1 << shift;
        int mask = radix - 1;

        for (int k = 0; k < 4; k++) {
            buf.append(Character.forDigit(i & mask, radix));

            i >>>= shift;
        }

        return buf.reverse().toString();
    }

    public static void busy(int percent) {
        if (catcher != null) {
            catcher.showBusy(percent);
        } 
    }
}
