/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.app;

/**
 * An interface that managed objects should implement in order to be notified
 * when they are being removed from the {@link DataManager}.  When the {@link
 * DataManager#removeObject DataManager.removeObject} method is called on an
 * object that implements this interface, that method will first call {@link
 * #removingObject removingObject} before removing the object from the data
 * manager.  Managed objects containing references to other managed objects
 * that should be removed when the main object is removed can implement the
 * {@code removingObject} method to remove those referred-to objects.  The
 * {@code removingObject} method will not be called if this object has already
 * been removed or is not currently managed by the data manager. <p>
 *
 * Note that the implementation of {@code removingObject} should make sure that
 * it only removes objects that are logically "owned" by the main object, and
 * that objects are not removed more than once.  In particular, the
 * implementation should not call {@code DataManager.removeObject} on the main
 * object itself.
 *
 * @see DataManager#removeObject DataManager.removeObject
 */
public interface ManagedObjectRemoval extends ManagedObject {

    /**
     * Performs additional operations that are needed when this object is
     * removed.
     */
    void removingObject();
}
