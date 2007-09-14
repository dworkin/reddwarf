package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;

interface AsyncChannelImpl {
    SelectableChannel channel();
}
