package com.sun.gi.channels;

import java.io.IOException;

/**
 * <p>Title: BadOperationException</p>
 * <p>Description: This exception is thrown by ChannelManager.readPacket and
 * ChannelManager.takePacket if they are used on inappropriate channels.</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class PacketMismatchException extends IOException {
    public PacketMismatchException() {
    }

}