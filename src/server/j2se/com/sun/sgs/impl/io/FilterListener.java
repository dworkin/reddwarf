package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;

interface FilterListener {
    void filteredMessageReceived(ByteBuffer buf);
    void sendUnfiltered(ByteBuffer buf);
}
