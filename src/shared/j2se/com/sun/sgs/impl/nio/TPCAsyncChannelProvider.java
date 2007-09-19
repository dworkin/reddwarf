/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class TPCAsyncChannelProvider extends AsyncProviderImpl {

    public TPCAsyncChannelProvider() {
        super();
    }

    @Override
    public TPCChannelGroup
    openAsynchronousChannelGroup(ExecutorService executor)
        throws IOException
    {
        return new TPCChannelGroup(this, executor);
    }
}
