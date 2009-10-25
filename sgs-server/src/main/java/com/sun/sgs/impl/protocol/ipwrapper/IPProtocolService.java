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

package com.sun.sgs.impl.protocol.ipwrapper;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TransactionProxy;
import java.net.InetAddress;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Back end service for IPProtocolManager.
 */
public class IPProtocolService extends AbstractService {

    private static final String PKG_NAME =
                    "com.sun.sgs.impl.protocol.ipwrapper";

    public IPProtocolService(Properties properties,
                             ComponentRegistry systemRegistry,
                             TransactionProxy proxy)
    {
        super(properties, systemRegistry, txnProxy,
              new LoggerWrapper(Logger.getLogger(PKG_NAME)));
        logger.log(Level.CONFIG, "Starting IPProtocolService");
    }

    InetAddress getInetAddress() {
        Identity identity = txnProxy.getCurrentOwner();

        if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Requesting IP address for {0}",
                           identity);
        }

        if (identity instanceof IPIdentity) {
            final InetAddress address = ((IPIdentity)identity).getInetAddress();
            
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "IP address of {0} is {1}",
                           identity, address);
            }
            return address;
        }
        return null;
    }

    @Override
    protected void doReady() throws Exception { }

    @Override
    protected void doShutdown() { }

    @Override
    protected void handleServiceVersionMismatch(Version oldVersion,
                                                Version currentVersion) { }

}
