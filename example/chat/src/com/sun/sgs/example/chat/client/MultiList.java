/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.example.chat.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * GUI component that handles multi-item selection lists.  Insertion order
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
     * @param renderer the {@code ListCellRenderer} for items in the list
     */
    public MultiList(Class<T> type, ListCellRenderer renderer) {
	super(new DefaultListModel());
	setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	setCellRenderer(renderer);
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
