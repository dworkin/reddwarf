/*
 * COPYRIGHT 1995 BY: MASSACHUSETTS INSTITUTE OF TECHNOLOGY (MIT), INRIA
 * 
 * This W3C software is being provided by the copyright holders under the
 * following license. By obtaining, using and/or copying this software, you
 * agree that you have read, understood, and will comply with the following
 * terms and conditions:
 * 
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose and without fee or royalty is hereby granted,
 * provided that the full text of this NOTICE appears on ALL copies of the
 * software and documentation or portions thereof, including modifications,
 * that you make.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS MAKE NO
 * REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT
 * NOT LIMITATION, COPYRIGHT HOLDERS MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE
 * SOFTWARE OR DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS,
 * COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. COPYRIGHT HOLDERS WILL BEAR NO
 * LIABILITY FOR ANY USE OF THIS SOFTWARE OR DOCUMENTATION.
 * 
 * The name and trademarks of copyright holders may NOT be used in advertising
 * or publicity pertaining to the software without specific, written prior
 * permission. Title to copyright in this software and any associated
 * documentation will at all times remain with copyright holders.
 */

/*
 * LrmpPacket.java - Lrmp data packet for applications.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 April 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.net.*;
import inria.util.*;

/**
 * encapsulates an LRMP data packet. Applications should use this object to
 * send data to and receive data from LRMP. LRMP provides packet-level reliability,
 * i.e., a packet can be sent either reliably or at best effort. The packet
 * reliability should be specified at creation.
 * <p>
 * Packet segmentation/reassembly is not supported. Upper layers should properly
 * segment large data blocks to LRMP packets.
 * <p>
 * Application data is contained in the buffer returned by the method getDataBuffer()
 * and starts at the offset returned by the method getOffset().
 * <p>
 * When sending a packet, application data should be filled from this offset
 * with the maximum length returned by getMaxDataLength(). The effective length
 * of application data should be set using setDataLength().
 * <p>
 * When a packet is received, the application data length can be obtained by
 * getDataLength().
 */
public final class LrmpPacket implements Cloneable {
    protected static final int padBit = 0x20;
    protected static final int strtBit = 0x02;
    protected static final int endBit = 0x01;

    /**
     * LRMP maximum transmission unit including the packet header. The header
     * length is 16 bytes for reliable packets and 8 bytes for unreliable
     * packets.
     */
    public static final int MTU = 1400;

    /**
     * The original packet sender.
     */
    protected LrmpEntity source;

    /**
     * The packet sender. It may be different from the original sender
     * if it's a retransmission.
     */
    protected LrmpEntity sender;

    /**
     * the data buffer.
     */
    protected byte buff[];

    /**
     * offset to application data.
     */
    protected int offset;

    /**
     * the length of application data in the packet.
     */
    protected int datalen;

    /**
     * the maximum length (bytes) available in the allocated buffer for application data.
     */
    protected int maxDataLen;

    /**
     * per-packet reliability, default value is true.
     */
    protected boolean reliable;

    /**
     * the start marker.
     */
    protected boolean first = false;

    /**
     * the end marker.
     */
    protected boolean end = false;

    /**
     * sequence number.
     */
    protected long seqno;

    /**
     * scope of transmission.
     */
    protected int scope;

    /**
     * time of transmission.
     */
    protected long rcvSendTime;

    /**
     * retransmission flag.
     */
    protected boolean retransmit = false;

    /**
     * retransmission id.
     */
    protected int retransmitID = 0;

    /**
     * Constructs a reliable LrmpPacket. The data buffer is initialized for application
     * data with the maximum length possible. The available size in the allocated buffer
     * for application data can be obtained through the getMaxDataLength() method. The
     * application should fill data in the buffer from the offset LrmpPacket.getOffset().
     */
    public LrmpPacket() {
        this(true);
    }

    /**
     * Constructs an LrmpPacket. The data buffer is initialized for application data
     * with the maximum length possible. The available size in the allocated buffer
     * for application data can be obtained through the getMaxDataLength() method. The
     * application should fill data in the buffer from the offset LrmpPacket.getOffset().
     * @param reliable the reliability.
     */
    public LrmpPacket(boolean reliable) {
        this(reliable, MTU);
    }

    /**
     * Constructs a reliable LrmpPacket. The data buffer is initialized for application
     * data with the given length. If this length plus Lrmp header length is larger
     * than the maximum transmission unit (MTU) of LRMP, it will be set to the LRMP MTU.
     * The available size in the allocated buffer for application data can be obtained
     * through the getMaxDataLength() method. The application should fill data in the
     * buffer from the offset LrmpPacket.getOffset().
     * @param length the length of application data.
     */
    public LrmpPacket(int length) {
        this(true, length);
    }

    /**
     * Constructs an LrmpPacket. The data buffer is initialized for application data
     * with the given length. If this length plus Lrmp header length is larger
     * than the maximum transmission unit (MTU) of LRMP, it will be set to the LRMP MTU.
     * The available size in the allocated buffer for user application can be obtained
     * through the getMaxDataLength() method. The application should fill data in the
     * buffer from the offset LrmpPacket.getOffset().
     * @param reliable the reliability.
     * @param length the application data length.
     */
    public LrmpPacket(boolean reliable, int length) {
        this.reliable = reliable;

        if (reliable) {
            offset = 16;
        } else {
            offset = 8;
        }

        int size = offset + length;

        /* mod 4 */

        size = (size + 3) & 0xfffc;

        if (size > MTU) {
            size = MTU;
        } 

        buff = new byte[size];
        maxDataLen = size - offset;
        datalen = 0;
    }

    /**
     * Constructs an LrmpPacket from received data.
     * @param buff the data buffer.
     * @param offset the offset.
     * @param len the packet length including the packet header.
     */
    protected LrmpPacket(boolean reliable, byte[] buff, int offset, int len) {
        this.buff = buff;
        this.reliable = reliable;

        if (reliable) {
            datalen = len - 16;
            this.offset = offset + 16;
        } else {
            datalen = len - 8;
            this.offset = offset + 8;
        }

        /* padding */

        if ((buff[offset] & 0x20) > 0) {
            datalen -= (buff[offset + len - 1] & 0xff);
        } 
        if (backward) {
            if ((buff[offset] & LrmpPacket.strtBit) > 0) {
                first = true;
            } 
            if ((buff[offset] & LrmpPacket.endBit) > 0) {
                end = true;
            } 
        }

        scope = buff[offset + 1] & 0xff;
        rcvSendTime = System.currentTimeMillis();
    }

    /**
     * Sets the length of application data.
     * @param i the length of application data in number of bytes.
     */
    public void setDataLength(int i) {
        datalen = i;
    }

    /**
     * Returns the offset to application data. It should be used to put application
     * data to the data buffer when sending, and as the starting offset of application
     * data for received packets.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Returns the maximum length available for application data in the buffer.
     */
    public int getMaxDataLength() {
        return maxDataLen;
    }

    /**
     * Returns the length of application data.
     */
    public int getDataLength() {
        return datalen;
    }

    /**
     * Returns the data buffer.
     */
    public byte[] getDataBuffer() {
        return buff;
    }

    /**
     * Returns the network address of the original packet sender.
     */
    public InetAddress getAddress() {
        return source.getAddress();
    }

    /**
     * Returns the packet source, i.e., the original packet sender.
     */
    public LrmpEntity getSource() {
        return source;
    }

    /**
     * Returns the identifier of the original packet sender.
     */
    public int getSourceID() {
        return source.getID();
    }

    /**
     * Returns the reliable flag.
     */
    public boolean isReliable() {
        return reliable;
    }

    /**
     * Sets the start merker.
     * @deprecated it is removed.
     */
    public void setFirst(boolean f) {
        first = f;
    }

    /**
     * Sets the end merker.
     * @deprecated it is removed.
     */
    public void setLast(boolean f) {
        end = f;
    }

    /**
     * Returns the start merker.
     * @deprecated it is removed.
     */
    public boolean isFirstOfBlock() {
        return first;
    }

    /**
     * Returns the end merker.
     * @deprecated it is removed.
     */
    public boolean isLastOfBlock() {
        return end;
    }

    /**
     * Returns the start merker.
     * @deprecated it is removed.
     */
    public boolean isFirst() {
        return first;
    }

    /**
     * Returns the end merker.
     * @deprecated it is removed.
     */
    public boolean isLast() {
        return end;
    }

    /**
     * sets the reliable flag.
     */
    protected void setReliable(boolean b) {
        reliable = b;
    }

    /**
     * prepares the packet to be delivered to the application.
     */
    protected void setSource(LrmpEntity s) {
        source = s;
    }

    static boolean backward = true;

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param resend
     *
     * @return
     *
     * @see
     */
    protected int formatDataPacket(boolean resend) {
        retransmit = resend;

        int headerlen;

        if (reliable) {
            headerlen = 16;
        } else {
            headerlen = 8;
        }

        /* mod 4 */

        int len = (datalen + headerlen + 3) & 0xfffc;
        int start = offset - headerlen;

        buff[start + 1] = (byte) scope;

        if (resend) {
            buff[start] |= LrmpImpl.R_DATA_PT;

            Utilities.intToByte(sender.getID(), buff, start + 4);
            Utilities.intToByte(source.getID(), buff, start + 8);
        } else {
            buff[start] = (byte) (LrmpImpl.Version << 6);

            if (first) {
                buff[start] |= (byte) strtBit;
            } 
            if (end) {
                buff[start] |= (byte) endBit;
            } 

            /* fill the length field */

            buff[start + 2] = (byte) ((len >> 8) & 0xff);
            buff[start + 3] = (byte) (len & 0xff);

            Utilities.intToByte(source.getID(), buff, start + 4);

            if (reliable) {

                /*
                 * for backward compatibility XXXXX
                 */
                int timestamp;

                if (backward) {
                    timestamp = source.getID();
                } else {
                    timestamp = NTP.ntp32(System.currentTimeMillis());
                }

                Utilities.intToByte(timestamp, buff, start + 8);
                Utilities.intToByte((int) seqno, buff, start + 12);
            } else {
                buff[start] |= LrmpImpl.U_DATA_PT;
            }

            /* padding */

            int pad = len - (datalen + headerlen);

            if (pad > 0) {
                buff[start] |= (byte) padBit;

                for (int i = start + len - 2; i > (start + len - pad); i--) {
                    buff[i] = 0;
                }

                buff[start + len - 1] = (byte) pad;
            }
        }

        return len;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param evs
     *
     * @see
     */
    protected void appendNack(LrmpLossEvent evs[]) {
        int start = offset;

        for (int i = 0; i < evs.length; i++) {
            LrmpLossEvent ev = evs[i];

            if (ev == null) {
                continue;
            } 
            if (offset == start) {
                buff[offset] = (byte) ((LrmpImpl.Version << 6) 
                                       | LrmpImpl.NACK_PT);
                offset++;
                buff[offset] = (byte) scope;
                offset += 3;

                Utilities.intToByte(ev.reporter.getID(), buff, offset);

                offset += 4;

                Utilities.intToByte(NTP.ntp32(System.currentTimeMillis()), 
                                    buff, offset);

                offset += 4;
            }

            Utilities.intToByte(ev.source.getID(), buff, offset);

            offset += 4;

            Utilities.intToByte((int) ev.low, buff, offset);

            offset += 4;

            Utilities.intToByte(ev.bitmask, buff, offset);

            offset += 4;
        }

        int len = offset - start;

        if (len > 0) {

            /* fill the length field */

            buff[start + 2] = (byte) ((len >> 8) & 0xff);
            buff[start + 3] = (byte) (len & 0xff);
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    protected void appendNack(LrmpLossEvent ev) {
        int start = offset;

        buff[offset] = (byte) ((LrmpImpl.Version << 6) | LrmpImpl.NACK_PT);
        offset++;
        buff[offset] = (byte) scope;
        offset += 3;

        Utilities.intToByte(ev.reporter.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(NTP.ntp32(System.currentTimeMillis()), buff, 
                            offset);

        offset += 4;

        Utilities.intToByte(ev.source.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte((int) ev.low, buff, offset);

        offset += 4;

        Utilities.intToByte(ev.bitmask, buff, offset);

        offset += 4;

        int len = offset - start;

        /* fill the length field */

        buff[start + 2] = (byte) ((len >> 8) & 0xff);
        buff[start + 3] = (byte) (len & 0xff);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     * @param whoami
     * @param firstReply
     * @param bitmReply
     *
     * @see
     */
    protected void appendNackReply(LrmpLossEvent ev, LrmpSender whoami, 
                                   int firstReply, int bitmReply) {
        int start = offset;

        buff[offset] = (byte) ((LrmpImpl.Version << 6) | LrmpImpl.R_NACK_PT);
        offset++;
        buff[offset] = (byte) scope;
        offset += 3;

        Utilities.intToByte(whoami.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(ev.reporter.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(ev.timestamp, buff, offset);

        offset += 4;

        /*
         * expressed in units of 1/65536 seconds (1/0x10000).
         */
        int delay = (int) (System.currentTimeMillis() - ev.rcvSendTime);

        delay = NTP.millisToFixedPoint32(delay);

        Utilities.intToByte(delay, buff, offset);

        offset += 4;

        Utilities.intToByte(ev.source.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(firstReply, buff, offset);

        offset += 4;

        Utilities.intToByte(bitmReply, buff, offset);

        offset += 4;

        int len = offset - start;

        if (len > 0) {

            /* fill the length field */

            buff[start + 2] = (byte) ((len >> 8) & 0xff);
            buff[start + 3] = (byte) (len & 0xff);
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param whoami
     *
     * @see
     */
    protected void appendSenderReport(LrmpSender whoami) {
        int start = offset;

        buff[offset] = (byte) ((LrmpImpl.Version << 6) | LrmpImpl.SR_PT);
        offset++;
        buff[offset] = (byte) scope;
        offset += 3;

        Utilities.intToByte(whoami.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(NTP.ntp32(System.currentTimeMillis()), buff, 
                            offset);

        offset += 4;

        Utilities.intToByte((int) whoami.expected(), buff, offset);

        offset += 4;

        Utilities.intToByte(whoami.packets, buff, offset);

        offset += 4;

        Utilities.intToByte(whoami.bytes, buff, offset);

        offset += 4;

        /* fill the length field */

        int len = offset - start;

        buff[start + 2] = (byte) ((len >> 8) & 0xff);
        buff[start + 3] = (byte) (len & 0xff);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param whoami
     * @param prob
     * @param period
     *
     * @see
     */
    protected void appendRRSelection(LrmpSender whoami, int prob, 
                                     int period) {
        int start = offset;

        buff[offset] = (byte) ((LrmpImpl.Version << 6) | LrmpImpl.RS_PT);
        offset++;
        buff[offset] = (byte) scope;
        offset += 3;

        Utilities.intToByte(whoami.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(NTP.ntp32(System.currentTimeMillis()), buff, 
                            offset);

        offset += 4;

        /* probability */

        buff[offset] = (byte) ((prob >> 8) & 0xff);
        offset++;
        buff[offset] = (byte) (prob & 0xff);
        offset++;

        /* period */

        buff[offset] = (byte) ((period >> 8) & 0xff);
        offset++;
        buff[offset] = (byte) (period & 0xff);
        offset++;
        buff[offset] = (byte) 0xff;
        offset++;
        buff[offset] = (byte) 0xff;
        offset++;
        buff[offset] = (byte) 0xff;
        offset++;
        buff[offset] = (byte) 0xff;
        offset++;

        /* fill the length field */

        int len = offset - start;

        buff[start + 2] = (byte) ((len >> 8) & 0xff);
        buff[start + 3] = (byte) (len & 0xff);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param sender
     * @param whoami
     *
     * @see
     */
    protected void appendReceiverReport(LrmpSender sender, 
                                        LrmpSender whoami) {
        int start = offset;

        buff[offset] = (byte) ((LrmpImpl.Version << 6) | LrmpImpl.RR_PT);
        offset++;
        buff[offset] = (byte) scope;
        offset += 3;

        Utilities.intToByte(whoami.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(sender.getID(), buff, offset);

        offset += 4;

        Utilities.intToByte(sender.rrTimestamp, buff, offset);

        offset += 4;

        int delay = (int) (System.currentTimeMillis() - sender.rrSelectTime);

        delay = NTP.millisToFixedPoint32(delay);

        Utilities.intToByte(delay, buff, offset);

        offset += 4;

        Utilities.intToByte((int) sender.expected(), buff, offset);

        offset += 4;

        int absLost = (int) (sender.maxseq - sender.startseq + 1 
                             - (sender.packets - sender.duplicates));
        int relativeLost = absLost - sender.rrAbsLost;

        sender.rrAbsLost = absLost;

        if (relativeLost > 0) {
            int expected = (int) (sender.maxseq - sender.rrMaxSeqno);

            sender.rrMaxSeqno = sender.maxseq;

            if (expected > relativeLost) {
                buff[offset++] = (byte) ((relativeLost << 8) / expected);
            } else {
                buff[offset++] = (byte) 0xff;
            }
        } else {
            buff[offset++] = 0;
        }
        if (Logger.debug) {
            Logger.debug(this, 
                         "send RR lost/rate:" + absLost + "/" 
                         + buff[offset - 1] / 256.0 + " max/init:" 
                         + sender.maxseq + "/" + sender.startseq 
                         + " packs/dup:" + sender.packets + "/" 
                         + sender.duplicates);
        }
        if (absLost > 0) {
            buff[offset++] = (byte) ((absLost >> 16) & 0xff);
            buff[offset++] = (byte) ((absLost >> 8) & 0xff);
            buff[offset++] = (byte) (absLost & 0xff);
        } else {
            buff[offset++] = 0;
            buff[offset++] = 0;
            buff[offset++] = 0;
        }

        /* fill the length field */

        int len = offset - start;

        buff[start + 2] = (byte) ((len >> 8) & 0xff);
        buff[start + 3] = (byte) (len & 0xff);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     *
     * @return
     *
     * @see
     */
    protected boolean equals(LrmpPacket pack) {
        return (source.equals(pack.source) && seqno == pack.seqno);
    }

    /**
     * Creates a new object of the same class as this object. The new object
     * has a new buffer which holds the same data as this object. To increase
     * the efficiency, the length of buffer in the new object is determined from
     * the effective data length of this object.
     */
    public Object clone() {
        try {
            LrmpPacket obj = (LrmpPacket) super.clone();
            int len = datalen;

            if (reliable) {
                len += 16;
            } else {
                len += 8;
            }

            obj.buff = new byte[len];

            System.arraycopy(buff, 0, obj.buff, 0, len);

            return obj;
        } catch (CloneNotSupportedException e) {

            // this shouldn't happen, since we are Cloneable

            throw new InternalError();
        }
    }

}

