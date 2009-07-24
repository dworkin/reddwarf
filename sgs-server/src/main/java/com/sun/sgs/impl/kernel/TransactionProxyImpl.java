/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;


/**
 * This is a proxy that provides access to the currently active
 * <code>Transaction</code> and its owner. Because <code>Service</code>s are
 * the only components outside of the kernel that should have visibility into
 * this state, they are the only components in the system that are provided
 * a reference to the usable proxy.
 */
final class TransactionProxyImpl implements TransactionProxy {

    /**
     * This package-private constructor creates a new instance of
     * <code>TransactionProxyImpl</code>. It is package-private so that
     * only the kernel components can create instances for access to the
     * current <code>Transaction</code>.
     */
    TransactionProxyImpl() {
        
    }

    /**
     * {@inheritDoc}
     */
    public Transaction getCurrentTransaction() {
        return ContextResolver.getCurrentTransaction();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inTransaction() {
        return ContextResolver.inTransaction();
    }
    
    /**
     * {@inheritDoc}
     */
    public Identity getCurrentOwner() {
        return ContextResolver.getCurrentOwner();
    }

    /**
     * {@inheritDoc}
     */
    public <T extends Service> T getService(Class<T> type) {
        return ContextResolver.getContext().getService(type);
    }


}
