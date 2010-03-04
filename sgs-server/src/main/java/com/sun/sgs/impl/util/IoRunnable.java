/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util;

import java.io.IOException;

/**
 * A task to encapsulate IO-related operations to be executed within the
 * context of {@link AbstractService#runIoTask AbstractService.runIoTask}.
 */
public interface IoRunnable {

    /**
     * Runs IO-related operations to be executed within the context of
     * {@link AbstractService#runIoTask AbstractService.runIoTask}.
     *
     * @throws	IOException if an IOException occurs while running this
     *		method 
     */
   void run() throws IOException;
}
