package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;

import com.sun.sgs.nio.channels.NetworkChannel;

interface AsyncChannelImpl extends NetworkChannel {
    SelectableChannel channel();
    void selected(int ops);
    void setException(int op, Throwable t);
}
