/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

/**
 * Provides an implementation of the interfaces in the {@link
 * com.sun.sgs.service.store.db} package based on <a href=
 * "http://www.oracle.com/database/berkeley-db/je/index.html">Berkeley DB Java
 * Edition</a>. <p>
 *
 * Operations on classes in this package will throw an {@link java.lang.Error
 * Error} if the underlying Berkeley DB database requires recovery.  In that
 * case, callers need to restart the application or create new instances of
 * these classes.
 */
package com.sun.sgs.impl.service.data.store.db.je;
