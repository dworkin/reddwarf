/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
