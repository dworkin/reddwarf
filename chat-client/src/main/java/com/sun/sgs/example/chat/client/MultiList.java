/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.example.chat.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;

/**
 * GUI component that handles selection lists.  Insertion order
 * is maintained, and items in the list must be unique.
 *
 * @param <T> the type of items in the list
 */
public class MultiList<T> extends JList
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The type of items in this {@code MultiList}. */
    private final Class<T> type;

    /**
     * Creates a new {@code MultiList} with the specified item type
     * and item renderer.
     *
     * @param type the type of items in the {@code MultiList}
     */
    public MultiList(Class<T> type) {
	super(new DefaultListModel());
	setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.type = type;
    }

    /**
     * Adds the specified item to this set if it is not already present.
     * If this set already contains the item, the call leaves the set
     * unchanged and returns {@code false}.
     *
     * @param item the item to be added to this set
     *
     * @return {@code true} if this set did not already contain the specified
     *         item
     * @throws NullPointerException if the specified item is {@code null}
     * 
     * @see java.util.Set#add(Object)
     * @see DefaultListModel#addElement(Object)
     */
    public boolean addItem(T item) {
        synchronized (this) {
            DefaultListModel model = (DefaultListModel) getModel();
            if (model.contains(item)) {
                return false;
            }
            model.addElement(item);
        }
	repaint();
        return true;
    }

    /**
     * Adds the specified items to this set if they are not already present.
     * If this set already contains any of the items, the call leaves the set
     * unchanged and returns {@code false}.
     *
     * @param items the items to be added to this set
     *
     * @return {@code true} if this set did not already contain any of
     *         the specified items
     * @throws NullPointerException if any of the items is {@code null}
     * 
     * @see java.util.Set#addAll(Collection)
     */
    public boolean addAllItems(Collection<? extends T> items) {
        synchronized (this) {
            DefaultListModel model = (DefaultListModel) getModel();
            for (T item : items) {
                if (model.contains(item)) {
                    return false;
                }
            }
            model.ensureCapacity(items.size());
            for (T item : items) {
                model.addElement(item);
            }
        }
        repaint();
        return true;
    }

    /**
     * Removes the specified item from this set if it is present
     * Returns {@code true} if this set contained the item (or equivalently,
     * if this set changed as a result of the call).  (This set will not
     * contain the item once the call returns.)
     *
     * @param item the item to be removed from this set, if present
     * @return {@code true} if this set contained the specified item
     * @throws NullPointerException if the specified item is @{code null}
     * 
     * @see java.util.Set#remove(Object)
     * @see DefaultListModel#removeElement(Object)
     */
    public boolean removeItem(T item) {
        synchronized (this) {
            DefaultListModel model = (DefaultListModel) getModel();
            if (! model.removeElement(item)) {
                return false;
            }
        }
        repaint();
        return true;
    }

    /**
     * Removes all items from this set.
     * 
     * @see java.util.Set#clear()
     * @see DefaultListModel#removeAllElements()
     */
    public void removeAllItems() {
        synchronized (this) {
            DefaultListModel model = (DefaultListModel) getModel();
            model.removeAllElements();
        }
        repaint();
    }

    /**
     * Returns the item at the smallest selected cell index (the <i>
     * selected item</i>) when only a single item is selected in the
     * list. When multiple item are selected, it is simply the item at
     * the smallest selected index.  Returns {@code null} if there is no
     * selection.
     * 
     * @return the first selected item
     * 
     * @see JList#getSelectedValue()
     */
    public T getSelected() {
	return type.cast(getSelectedValue());
    }

    /**
     * Returns a {@link List} of all the selected items, in order
     * of their insertion into the set.
     *
     * @return the selected items, or an empty list if nothing is selected
     *
     * @see JList#getSelectedValues()
     */
    public List<T> getAllSelected() {
	Object[] targets = getSelectedValues();
	if (targets == null) {
	    return null;
	}
        ArrayList<T> result = new ArrayList<T>(targets.length);
        for (Object target : targets) {
            result.add(type.cast(target));
        }
	return result;
    }
}
