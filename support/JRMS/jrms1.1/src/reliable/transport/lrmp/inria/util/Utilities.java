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
 * Utilities.java - Common utilities.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 3 July 1996.
 * Updated: no.
 */
package inria.util;

import java.io.*;
import java.util.*;
import java.net.*;
import java.text.*;

/**
 * Static methods providing common utilities.
 */
public class Utilities {
    public static final long Modulo32 = (1L << 32);
    protected static InetAddress localhost = null;
    protected static String localHostName = "localhost";

    /**
     * gets the local host address.
     */
    public static String getLocalHostName() {
        if (localhost == null) {
            getLocalHost();
        } 
        if (localhost != null) {
            localHostName = localhost.getHostName().toLowerCase();
        } 

        return localHostName;
    }

    /**
     * gets the local host address.
     */
    public static InetAddress getLocalHost() {
        if (localhost == null) {
            try {
                localhost = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                try {
                    localhost = InetAddress.getByName("127.0.0.1");
                } catch (UnknownHostException e1) {
                    return null;
                }
            }

            /* reverse lookup to get fully qualified host name */

            if (localhost.getHostName().indexOf('.') < 0) {
                try {
                    localhost = 
                        InetAddress.getByName(localhost.getHostAddress());
                } catch (UnknownHostException e) {}
            }
        }

        return localhost;
    }

    /**
     * gets extension string of a file name.
     * @param filename the file pathname.
     */
    public static String fileExtension(String filename) {
        if (filename == null) {
            return null;
        } 

        int dot = filename.lastIndexOf(".");
        char sep;

        if (filename.indexOf('/') >= 0) {
            sep = '/';
        } else if (filename.indexOf('\\') >= 0) {
            sep = '\\';
        } else if (dot >= 0) {
            return filename.substring(dot + 1);
        } else {
            return null;
        }

        int slash = filename.lastIndexOf(sep);

        if (dot >= 0 && dot > slash) {
            return filename.substring(dot + 1);
        } 

        return null;
    }

    /**
     * converts a file path name appropriate to the platform.
     * @param filename the file pathname.
     * @return a converted path name.
     */
    public static String convertPathname(String filename) {
        if (filename == null) {
            return null;
        } 

        String sep = File.separator;

        if (sep.equals("/")) {
            return filename.replace('\\', '/');
        } 
        ;

        return filename.replace('/', '\\');
    }

    /**
     * Remove ./ and ../ from pathname.
     * @param s the file pathname or http URL string.
     */
    public static String shortPathname(String s) {

        /* guess UNIX or DOS pathname */

        if (s.indexOf('/') >= 0) {
            int i, j;

            while (true) {
                i = s.indexOf("/./");

                if (i < 0) {
                    break;
                } 

                s = s.substring(0, i) + s.substring(i + 2);
            }

            /* relative? */

            if (s.startsWith("../")) {
                return s;
            } 

            while (true) {
                i = s.indexOf("/../");

                if (i < 0) {
                    break;
                } 

                j = s.lastIndexOf('/', i - 1);

                if (j <= 0 || (j > 0 && s.charAt(j - 1) == '/')) {
                    s = s.substring(0, i) + s.substring(i + 3);
                } else {
                    s = s.substring(0, j) + s.substring(i + 3);
                }
            }
        } else if (s.indexOf('\\') >= 0) {
            int i, j;

            while (true) {
                i = s.indexOf("\\.\\");

                if (i < 0) {
                    break;
                } 

                s = s.substring(0, i) + s.substring(i + 2);
            }

            /* relative? */

            if (s.startsWith("..\\")) {
                return s;
            } 

            while (true) {
                i = s.indexOf("\\..\\");

                if (i < 0) {
                    break;
                } 

                j = s.lastIndexOf('\\', i - 1);

                if (j <= 0 || (j > 0 && s.charAt(j - 1) == '\\')) {
                    s = s.substring(0, i) + s.substring(i + 3);
                } else {
                    s = s.substring(0, j) + s.substring(i + 3);
                }
            }
        }

        return s;
    }

    /**
     * Returns the dir name from a file name.
     * @param filename the file pathname.
     */
    public static String dirname(String filename) {
        String sep = File.separator;
        int slash = filename.lastIndexOf(sep);

        if (slash == 0) {
            return filename.substring(0, 1);
        } 
        if (slash > 0) {
            return filename.substring(0, slash + 1);
        } 

        return "";
    }

    /**
     * Returns the base name from a file name.
     * @param filename the file pathname.
     */
    public static String basename(String filename) {
        String sep = File.separator;
        int slash = filename.lastIndexOf(sep);

        if (slash >= 0 && (slash + 1) < filename.length()) {
            return filename.substring(slash + 1);
        } 

        return "";
    }

    /**
     * gets the root of url, something like proto://a.b.c
     * @param url the absolute url.
     */
    public static String getURLRoot(String url) {
        if (url == null || url.length() == 0) {
            return null;
        } 

        /*
         * exception is the url staring with "file:".
         * file://host/pathname
         * file:/pathname
         */
        int colon = url.indexOf(":");

        if (colon <= 0) {
            return null;
        } 
        if (url.startsWith("file:")) {
            int slash = url.indexOf("//", colon + 1);

            if (slash > 0) {
                slash = url.indexOf("/", slash + 2);

                if (slash > 0) {
                    return url.substring(0, slash);
                } 
            } else {
                return url.substring(0, 5);
            }
        } else {
            int slash = url.indexOf("/", colon + 1);

            if (slash > 0) {
                slash = url.indexOf("/", slash + 1);

                if (slash > 0) {
                    slash = url.indexOf("/", slash + 1);

                    if (slash > 0) {
                        return url.substring(0, slash);
                    } 
                }
            }
        }

        return null;
    }

    /**
     * gets root http url, something like http://a.b.c
     * @param url absolute url.
     */
    public static String getHttpRoot(String url) {
        return getURLRoot(url);
    }

    /**
     * Put an integer value into a byte array in the MSBF order.
     * @param i the integer value.
     * @param buff the byte array.
     * @param offset the offset in the array to put the integer.
     */
    public static void intToByte(int i, byte[] buff, int offset) {
        buff[offset] = (byte) (i >> 24);
        buff[offset + 1] = (byte) (i >> 16);
        buff[offset + 2] = (byte) (i >> 8);
        buff[offset + 3] = (byte) i;
    }

    /**
     * Get integer value from a byte array.
     * @param buff the byte array.
     * @param offset the offset in the array.
     */
    public static int byteToInt(byte[] buff, int offset) {
        int i;

        i = ((buff[offset] << 24) & 0xff000000);
        i |= ((buff[offset + 1] << 16) & 0xff0000);
        i |= ((buff[offset + 2] << 8) & 0xff00);
        i |= (buff[offset + 3] & 0xff);

        return i;
    }

    /**
     * Get long value from a byte array.
     * @param buff the byte array.
     * @param offset the offset in the array.
     */
    public static long byteToLong(byte[] buff, int offset) {
        long l;

        l = (buff[offset] << 56) & 0xff00000000000000L;
        l |= ((buff[offset + 1] << 48) & 0xff000000000000L);
        l |= ((buff[offset + 2] << 40) & 0xff0000000000L);
        l |= ((buff[offset + 3] << 32) & 0xff00000000L);
        l |= ((buff[offset + 4] << 24) & 0xff000000L);
        l |= ((buff[offset + 5] << 16) & 0xff0000L);
        l |= ((buff[offset + 6] << 8) & 0xff00L);
        l |= (buff[offset + 7] & 0xffL);

        return l;
    }

    /**
     * Get short value from a byte array.
     * @param buff the byte array.
     * @param offset the offset in the array.
     */
    public static int getShort(byte buf[], int offset) {
        return ((buf[offset] & 0xff) << 8) | (buf[offset + 1] & 0xff);
    }

    /**
     * Check if the url is valid.
     * @param url the URL string.
     */
    public static boolean isValidURL(String url) {
        try {
            URL tmpUrl = new URL(url);
        } catch (MalformedURLException e) {
            return false;
        }

        return true;
    }

    /**
     * converts int to INET address string.
     * @param addr 32 bit address values.
     */
    public static String toAddressString(int addr) {
        return ((addr >>> 24) & 0xFF) + "." + ((addr >>> 16) & 0xFF) + "." 
               + ((addr >>> 8) & 0xFF) + "." + ((addr >>> 0) & 0xFF);
    }

    /**
     * pad a byte buffer so that it contains an integer number of 32 bit word.
     * returns number of bytes pad.
     */
    public static int pad32(byte[] buff, int offset) {
        int mod = offset % 4;

        if (mod > 0) {
            int padlen = 4 - mod;

            for (int i = mod; i < 3; i++) {
                buff[offset++] = 0;
            }

            buff[offset++] = (byte) padlen;

            return padlen;
        }

        return 0;
    }

    /**
     * does 32-bit diff of seqno (seq1 - seq2). Handle overflow and underflow.
     * The result will be correct provided that the absolute diff is less than
     * Modulo32/2.
     * @param seq1 first seqno.
     * @param seq2 second seqno.
     */
    public static int diff32(int seq1, int seq2) {
        int diff = seq1 - seq2;

        if (diff > (Modulo32 >> 1)) {
            diff -= Modulo32;
        } else if (diff < -(Modulo32 >> 1)) {
            diff += Modulo32;
        } 

        return diff;
    }

    /**
     * does a byte compare.
     * @param v1 the first byte array.
     * @param v1 the second byte array.
     * @return true if they match.
     */
    public static boolean bcomp(byte v1[], byte v2[]) {
        if (v1.length != v2.length) {
            return false;
        } 

        for (int i = 0; i < v1.length; i++) {
            if (v1[i] != v2[i]) {
                return false;
            } 
        }

        return true;
    }

    /**
     * sorts the given table by characters. This method uses the string representation
     * of the objects contained in the table which is obtained using the toString() method.
     * @param tab the table to sort.
     * @param ignoreCase if true, ingore case while comparing characters.
     * @return the sorted table.
     */
    public static Vector sortByString(Vector tab, boolean ignoreCase) {
        Vector sorted = new Vector();

        for (int i = 0; i < tab.size(); i++) {
            Object o = tab.elementAt(i);
            String s1 = o.toString();

            if (ignoreCase) {
                s1 = s1.toLowerCase();
            } 

            int l = 0;                  /* low bound */
            int h = sorted.size();      /* high bound */

            while ((h - l) > 0) {
                int m = (h + l) >> 1;
                String s2 = sorted.elementAt(m).toString();

                if (ignoreCase) {
                    s2 = s2.toLowerCase();
                } 

                int result = s1.compareTo(s2);

                if (result > 0) {
                    l = m + 1;
                } else if (result < 0) {
                    h = m;
                } else {
                    l = m;

                    break;
                }
            }

            sorted.insertElementAt(o, l);
        }

        return sorted;
    }

    static Random rand = null;

    /**
     * returns a random integer using the default seed.
     */
    public static int getRandomInteger() {
        if (rand == null) {
            rand = new Random();
        } 

        return rand.nextInt();
    }

    /**
     * returns a random integer using the default seed.
     */
    public static double getRandomDouble() {
        if (rand == null) {
            rand = new Random();
        } 

        return rand.nextDouble();
    }

    static SimpleDateFormat longFormat = null;
    static SimpleDateFormat shortFormat1 = null;
    static SimpleDateFormat shortFormat2 = null;
    static SimpleTimeZone gmtTz = null;
    static TimeZone defTz = null;
    static Date date = null;

    /**
     * returns a string representation of the time using GMT.
     * The date is in the form of Wed, 15 Nov 1995 04:58:08 GMT.
     */
    public static String GMTDate(long t) {
        if (longFormat == null) {
            longFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");

            if (date == null) {
                date = new Date();
            } 
        }
        if (gmtTz == null) {
            gmtTz = new SimpleTimeZone(0, "GMT");
        } 

        longFormat.setTimeZone(gmtTz);
        date.setTime(t);

        return longFormat.format(date) + " " + gmtTz.getID();
    }

    /**
     * returns a string representation of the time using the default locale.
     * The date is in the form of Wed, 15 Nov 1995 04:58:08 MET.
     */
    public static String localDate(long t) {
        if (longFormat == null) {
            longFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");

            if (date == null) {
                date = new Date();
            } 
        }
        if (defTz == null) {
            defTz = TimeZone.getDefault();
        } 

        /*
         * XXX there is a problem with the default timezone.
         */
        longFormat.setTimeZone(defTz);
        date.setTime(t);

        return longFormat.format(date) + " " + defTz.getID();
        ;
    }

    /**
     * returns a string representation of the time using the default locale.
     * The date is in the form of Wed, 15 Nov 04:58.
     */
    public static String shortDateWithWeekday(long t) {
        if (shortFormat1 == null) {
            shortFormat1 = new SimpleDateFormat("EEE d MMM HH:mm:ss");

            if (date == null) {
                date = new Date();
            } 
            if (defTz == null) {
                defTz = TimeZone.getDefault();
            } 

            /*
             * XXX there is a problem with the default timezone.
             */
            shortFormat1.setTimeZone(defTz);
        }

        date.setTime(t);

        return shortFormat1.format(date);
    }

    /**
     * returns a string representation of the time using the default locale.
     * The date is in the form of 15 Nov 04:58:08.
     */
    public static String shortDate(long t) {
        if (shortFormat2 == null) {
            shortFormat2 = new SimpleDateFormat("d MMM HH:mm:ss");

            if (date == null) {
                date = new Date();
            } 
            if (defTz == null) {
                defTz = TimeZone.getDefault();
            } 

            /*
             * XXX there is a problem with the default timezone.
             */
            shortFormat2.setTimeZone(defTz);
        }

        date.setTime(t);

        return shortFormat2.format(date);
    }

    static SimpleDateFormat parseFormat = null;
    static ParsePosition parsePos = null;

    /**
     * parses the date string in standard format, i.e.,
     * <ul>
     * <li> Sun, 06 Nov 1994 08:49:37 GMT --- RFC 822, updated by RFC 1123
     * <li> Sunday, 06-Nov-94 08:49:37 GMT --- RFC 850, obsoleted by RFC 1036
     * <li> Sun Nov  6 08:49:37 1994 --- ANSI C's asctime() format
     * </ul>
     * @param s the date string.
     */
    public static long parseGenericDate(String s) {
        if (parseFormat == null) {
            parseFormat = new SimpleDateFormat();
            parsePos = new ParsePosition(0);

            if (defTz == null) {
                defTz = TimeZone.getDefault();
            } 
        }

        /*
         * warning (bug?)
         * if the string contains more than one space chars as separator,
         * the parse will fail.
         */
        parseFormat.applyPattern("EEE, dd MMM yyyy HH:mm:ss z");
        parsePos.setIndex(0);

        Date d;

        try {
            d = parseFormat.parse(s, parsePos);
        } catch (Exception e) {
            d = null;
        }

        if (d != null) {
            return d.getTime();
        } 

        parseFormat.applyPattern("EEEEEEEEE, dd-MMM-yy HH:mm:ss z");
        parsePos.setIndex(0);

        try {
            d = parseFormat.parse(s, parsePos);
        } catch (Exception e) {
            d = null;
        }

        if (d != null) {
            return d.getTime();
        } 

        parseFormat.applyPattern("EEE MMM d HH:mm:ss yyyy");
        parsePos.setIndex(0);
        parseFormat.setTimeZone(defTz);

        try {
            d = parseFormat.parse(s, parsePos);
        } catch (Exception e) {
            d = null;
        }

        if (d != null) {
            return d.getTime();
        } 

        return 0L;
    }

}

