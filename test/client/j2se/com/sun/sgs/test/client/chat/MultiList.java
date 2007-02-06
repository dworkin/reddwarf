package com.sun.sgs.test.client.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * Represent multi-item selection lists in the GUI.  Insertion order
 * is maintained.  Each item may appear at most once per {@code MultiList}
 * instance.
 *
 * @param <T> the type of items in the list
 */
public class MultiList<T> extends JList
{
    private static final long serialVersionUID = 1L;

    private final Class<T> type;

    MultiList(Class<T> type, ListCellRenderer renderer) {
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
     * @see Set#add(Object)
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
     * @see Set#add(Object)
     * @see DefaultListModel#addElement(Object)
     */
    public boolean addItems(Collection<T> items) {
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
     * @see Set#remove(Object)
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