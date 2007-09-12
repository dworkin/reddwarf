package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;

import com.sun.sgs.nio.channels.NetworkChannel;

interface AsyncChannelInternals
    extends NetworkChannel
{
    SelectableChannel getSelectableChannel();
}
