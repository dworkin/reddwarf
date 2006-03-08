/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.objectstore.tso.dataspace;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.monitor.AtomicUpdateTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.ClearTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.CloseTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.CreateTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.DestroyTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetAppIdTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetObjBytesTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.LockTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.LookupTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.ReleaseMultiTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.ReleaseTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.TraceRecord;

/**
 * Simple wrapper for a {@link DataSpace} implementation that gathers a
 * trace of its operation.
 */
public class MonitoredDataSpace implements DataSpace {
    private final DataSpace dataSpace;
    private volatile boolean loggingEnabled = false;
    private volatile ObjectOutputStream entryLog;

    /**
     * Simple, degenerate constructor that creates a wrapper that does
     * <em>not</em> gather any traces.
     * 
     * @param dataSpace the wrapped {@link DataSpace}
     * 
     * @throws NullPointerException if dataSpace is <code>null</code>
     */
    public MonitoredDataSpace(DataSpace dataSpace) {
        if (dataSpace == null) {
            throw new NullPointerException("dataSpace is null");
        }

        this.dataSpace = dataSpace;
        this.loggingEnabled = false;
        this.entryLog = null;
    }

    /**
     * Creates a wrapper that captures traces of the operations
     * performed by a {@link DataSpace}.
     * <p>
     * 
     * If the trace file already exists, it is truncated and
     * overwritten.
     * 
     * @param dataSpace the {@link DataSpace} to trace
     * 
     * @param traceFileName the path to the file in which to store the
     * trace
     * 
     * @throws NullPointerException if dataSpace is <code>null</code>
     * 
     * @throws IOException if there is an IO error with the trace file
     */
    public MonitoredDataSpace(DataSpace dataSpace, String traceFileName)
            throws IOException {
        if (dataSpace == null) {
            throw new NullPointerException("dataSpace is null");
        }

        this.dataSpace = dataSpace;

        FileOutputStream fos = new FileOutputStream(traceFileName);
        ObjectOutputStream oos = new ObjectOutputStream(fos);

        this.entryLog = oos;
        this.loggingEnabled = true;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

        byte[] bytes = dataSpace.getObjBytes(objectID);

        if (loggingEnabled()) {
            log(new GetObjBytesTraceRecord(startTime, objectID, bytes.length));
        }

        return bytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID) throws NonExistantObjectIDException {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;
        NonExistantObjectIDException re = null;

        try {
            dataSpace.lock(objectID);
        } catch (NonExistantObjectIDException e) {
            re = e;
        }

        if (loggingEnabled()) {
            log(new LockTraceRecord(startTime, objectID));
        }

        if (re != null) {
            throw re;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) throws NonExistantObjectIDException {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;
        NonExistantObjectIDException re = null;

        try {
            dataSpace.release(objectID);
        } catch (NonExistantObjectIDException e) {
            // XXX: note the error.
            re = e;
        }

        if (loggingEnabled()) {
            log(new ReleaseTraceRecord(startTime, objectID));
        }

        if (re != null) {
            throw re;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void release(Set<Long> objectIDs)
            throws NonExistantObjectIDException {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;
        NonExistantObjectIDException re = null;

        try {
            dataSpace.release(objectIDs);
        } catch (NonExistantObjectIDException e) {
            // XXX: note the error.
            re = e;
        }

        if (loggingEnabled()) {
            log(new ReleaseMultiTraceRecord(startTime, objectIDs));
        }

        if (re != null) {
            throw re;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap)
            throws DataSpaceClosedException {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;
        DataSpaceClosedException re = null;

        try {
            dataSpace.atomicUpdate(clear, updateMap);
        } catch (DataSpaceClosedException e) {
            re = e;
        }

        if (loggingEnabled()) {
            log(new AtomicUpdateTraceRecord(startTime, clear, updateMap));
        }

        if (re != null) {
            throw re;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Long lookup(String name) {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

        Long oid = dataSpace.lookup(name);

        if (loggingEnabled()) {
            log(new LookupTraceRecord(startTime, name, oid));
        }

        return oid;
    }

    /**
     * {@inheritDoc}
     */
    public long getAppID() {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

        long appId = dataSpace.getAppID();

        if (loggingEnabled()) {
            log(new GetAppIdTraceRecord(startTime, appId));
        }

        return appId;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

        dataSpace.clear();

        if (loggingEnabled()) {
            log(new ClearTraceRecord(startTime));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

        dataSpace.close();

        if (loggingEnabled()) {
            log(new CloseTraceRecord(startTime));
            closeLog();
        }
    }

    /**
     * {@inheritDoc}
     */
    public long create(byte[] data, String name) {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;
        long oid = DataSpace.INVALID_ID;

        oid = dataSpace.create(data, name);

        if (loggingEnabled()) {
            log(new CreateTraceRecord(startTime, data, name));
        }

        return oid;
    }

    /**
     * {@inheritDoc}
     */
    public void destroy(long objectID) throws NonExistantObjectIDException {
        long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;
        NonExistantObjectIDException seenException = null;

        try {
            dataSpace.destroy(objectID);
        } catch (NonExistantObjectIDException e) {
            seenException = e;
        }

        if (loggingEnabled()) {
            log(new DestroyTraceRecord(startTime, objectID));
        }

        if (seenException != null) {
            throw seenException;
        }
    }

    /**
     * Enables/disables logging.
     * 
     * @param val enable logging if <code>true</code>, disable
     * logging if <code>false</code>
     */
    public void enableLogging(boolean val) {
        loggingEnabled = val;
    }

    /**
     * Indicates whether logging is currently enabled.
     * 
     * @return <code>true<code> if logging is enabled, <code>false</code
     * otherwise
     */
    public boolean loggingEnabled() {
        return loggingEnabled;
    }

    private synchronized void closeLog() {

        if (!loggingEnabled() || (entryLog == null)) {
            return;
        }

        try {
            entryLog.flush();
            entryLog.close();
        } catch (IOException e) {
            // XXX: should do something intelligent. Doesn't.
        }

        entryLog = null;
        enableLogging(false);
    }

    /**
     * Logs a {@link TraceRecord} to the trace file.
     * 
     * @param entry the {@link TraceRecord} to store
     */
    private synchronized void log(TraceRecord entry) {
        if (loggingEnabled() && (entryLog != null)) {

            /*
             * Note: it is not expected that the objects will not
             * reference each other, so the stream is reset after each.
             * If it is not reset periodically, the ObjectOutputStream
             * will keep a reference to everything it has ever written,
             * and quickly sponge up all of memory.
             */

            try {
                entryLog.writeObject(entry);
                entryLog.reset();
            } catch (IOException e) {

                // XXX: should do something intelligent. Doesn't.

                try {
                    entryLog.close();
                } catch (IOException e2) {
                    // surrender
                }

                entryLog = null;
                enableLogging(false);
            }
        }
    }
}
