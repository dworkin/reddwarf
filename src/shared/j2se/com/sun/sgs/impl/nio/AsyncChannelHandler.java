/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectableChannel;

interface AsyncChannelHandler
    extends Closeable
{
    SelectableChannel getSelectableChannel();
    void channelSelected(int ops) throws IOException;
}
