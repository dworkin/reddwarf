/**
 * Provides an implementation of the interfaces in the {@link
 * com.sun.sgs.impl.service.data.store.db} package based on <a href=
 * "http://www.oracle.com/database/berkeley-db/db/index.html">Berkeley
 * DB</a>. <p>
 *
 * Classes in this package will throw an {@link Error} if the underlying
 * Berkeley DB database requires recovery.  In that case, callers need to
 * restart the associated server or create new instances of these classes.
 */
package com.sun.sgs.impl.service.data.store.db.bdbdb;
