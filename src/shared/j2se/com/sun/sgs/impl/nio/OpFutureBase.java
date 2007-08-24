/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import com.sun.sgs.nio.channels.IoFuture;

abstract class OpFutureBase<R, A>
    implements IoFuture<R, A>, Cloneable
{
    @Override
    public IoFuture<R, ? super A> clone() {
        // TODO
        // Return a new IoFuture with the same base Future<R>
        // but an independent attachment initialized to this
        // Future's attachment.
        return null;
    }
}
