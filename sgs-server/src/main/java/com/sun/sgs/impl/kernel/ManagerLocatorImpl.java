/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.internal.ManagerLocator;

/**
 * Package-private implementation of {@code ManagerLocator} that is
 * to be used as the default locator for the
 * {@link com.sun.sgs.internal.InternalContext InternalContext}.
 * 
 * @see com.sun.sgs.internal.InternalContext#setManagerLocator
 */
class ManagerLocatorImpl implements ManagerLocator {

    public ChannelManager getChannelManager() {
        return ContextResolver.getChannelManager();
    }

    public DataManager getDataManager() {
        return ContextResolver.getDataManager();
    }

    public TaskManager getTaskManager() {
        return ContextResolver.getTaskManager();
    }

    public <T> T getManager(Class<T> type) {
        return ContextResolver.getManager(type);
    }

}
