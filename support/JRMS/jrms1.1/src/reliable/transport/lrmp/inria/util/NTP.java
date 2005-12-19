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
 * NTP.java - NTP class.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 5 November 1996.
 * Updated: no.
 */
package inria.util;

/**
 * Translate UNIX time to NTP time.
 * NTP time is UTC time relative to 01/Jan/1900.
 */
public class NTP {

    /* about 1000*3600*24*365*70 */

    public static final long NtpOffsetSeconds = 2208988800L;
    public static final long NtpOffsetMillis = NtpOffsetSeconds * 1000L;
    static int NtpOffsetMillis32 = (int) (NtpOffsetSeconds << 16);

    /* overflow and underflow ??? */

    /**
     * converts UNIX time in milliseconds to NTP time, i.e., 64-bit fixed point
     * (with fraction point at bit 32). The low 32 bits are the fraction part in
     * 1/2^32 second units.
     * @param millis the UNIX time.
     */
    public static long ntp64(long millis) {
        millis += NtpOffsetMillis;

        return ((millis << 16) / 1000) << 16;
    }

    /**
     * converts UNIX time to 32 bit NTP time, i.e., 32-bit fixed point integer
     * (with fraction point at bit 16). The low 16 bits are the fraction part in
     * 1/2^16 second units.
     * @param millis the UNIX time.
     */
    public static int ntp32(long millis) {
        millis += NtpOffsetMillis;

        return (int) ((millis << 16) / 1000);
    }

    /**
     * converts 64 bit NTP time to UNIX time in milliseconds.
     * @param ntp the ntp time.
     */
    public static long millis(long ntp) {
        ntp -= ((ntp >> 7) * 3);
        ntp = (ntp + (1L << 21)) >> 22;

        return (ntp - NtpOffsetMillis);
    }

    /**
     * it does not make sense to convert 32 bit NTP time to UNIX time.
     * But it makes sense to make a difference between two 32 bit NTP time values.
     * This method is just to forbidden this conversion.
     * @param ntp the ntp time.
     */
    private static void millis(int ntp) {

        /*
         * subtract ntp offset.
         */
        if (ntp >= NtpOffsetMillis32) {
            ntp -= NtpOffsetMillis32;
        } else {
            ntp = (1 << 31) - NtpOffsetMillis32 + ntp + (1 << 31);
        }

        ntp -= ((ntp >> 7) * 3);
        ntp = (ntp + (1 << 5)) >> 6;
    }

    /**
     * converts milliseconds to 32 bit fixed point integer.
     * @param millis the milliseconds.
     */
    public static int millisToFixedPoint32(int millis) {

        /*
         * expressed in units of 1/65536 seconds (1/0x10000).
         * t32 = millis*2^16/1000
         * use the factorization 2^16 = 2^6 + 2 - 58/125 which gives the exact value
         * if no bit round error.
         */
        return (millis << 6) + (millis << 1) - millis * 58 / 125;
    }

    /**
     * converts a 32 bit fixed point integer to milliseconds.
     * @param fixed the 32 bit fixed point integer.
     */
    public static int fixedPoint32ToMillis(int fixed) {

        /* fixed*1000/2^16 */

        fixed -= ((fixed >> 7) * 3);

        return (fixed + (1 << 5)) >> 6;
    }

    public static void main(String args[]) {
        java.io.PrintStream out = System.out;
        int tt = 32767999;

        while (true) {
            int i = millisToFixedPoint32(tt);

            if (fixedPoint32ToMillis(i) != tt) {
                out.println("bad: " + tt + "/" + fixedPoint32ToMillis(i) 
                            + " " + Integer.toHexString(tt));
                out.println("bad: " + i + "/" + Integer.toHexString(i));

                break;
            }

            tt++;
        }

    /*
     * long t1 = System.currentTimeMillis();
     * int rtt = 4567;
     * int delay = 234567;
     * int d1 = millisToFixedPoint32(delay);
     * int d2 = delay + rtt;
     * while (true) {
     * long t2 = t1 + d2;
     * int ntp1 = ntp32(t1);
     * int ntp2 = ntp32(t2);
     * int rtt1 = fixedPoint32ToMillis(ntp2 - ntp1 - d1);
     * if (rtt1 != rtt) {
     * out.println("error: " + rtt1 + "/" + rtt);
     * out.println("error: " + ntp1 + "/" + ntp2);
     * out.println("error: " + d1 + "/" + fixedPoint32ToMillis(d1));
     * break;
     * }
     * t1 = t2;
     * }
     */
    }

}

