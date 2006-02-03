package com.sun.gi.utils.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface ReadWriteSelectorHandler
	extends SelectorHandler {

    public void handleRead(SelectionKey key) throws IOException;

    public void handleWrite(SelectionKey key) throws IOException;
}
