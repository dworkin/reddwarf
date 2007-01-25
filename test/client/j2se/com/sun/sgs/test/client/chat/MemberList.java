package com.sun.sgs.test.client.chat;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import com.sun.sgs.client.SessionId;

/**
 * Represent channel members and user lists in the GUI via their
 * client {@link SessionId}s.  Insertion order is maintained.  Each
 * {@code SessionId}, and hence each client, may appear at most once
 * per {@code MemberList} instance.
 */
public class MemberList extends JList
	implements ListCellRenderer
{
    private static final long serialVersionUID = 1L;

    MemberList() {
	super(new DefaultListModel());
	setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	setCellRenderer(this);
    }

    /**
     * Adds the specified client to this set if it is not already present
     * If this set already contains the client, the call leaves the set
     * unchanged and returns {@code false}.  If the specified client is {@code null},
     * a {@code NullPointerException} is thrown.
     *
     * @param client the client to be added to this set
     *
     * @return {@code true} if this set did not already contain the specified
     *         client
     * @throws NullPointerException if the specified client is {@code null}
     * 
     * @see Set#add(Object)
     * @see DefaultListModel#addElement(Object)
     */
    public boolean addClient(SessionId client) {
        synchronized (this) {
            DefaultListModel model = (DefaultListModel) getModel();
            if (model.contains(client)) {
                return false;
            }
            model.addElement(client);
        }
	repaint();
        return true;
    }

    /**
     * Removes the specified client from this set if it is present
     * Returns {@code true} if this set contained the client (or equivalently, if this set changed as a
     * result of the call).  (This set will not contain the client once the
     * call returns.)
     *
     * @param client the client to be removed from this set, if present
     * @return {@code true} if this set contained the specified client
     * @throws NullPointerException if the specified client is @{code null}
     * 
     * @see Set#remove(Object)
     * @see DefaultListModel#removeElement(Object)
     */
    public boolean removeClient(SessionId client) {
        synchronized (this) {
            DefaultListModel model = (DefaultListModel) getModel();
            if (! model.removeElement(client)) {
                return false;
            }
        }
        repaint();
        return true;
    }

    /**
     * Returns the client at the smallest selected cell index; <i>the
     * selected client</i> when only a single client is selected in the
     * list. When multiple clients are selected, it is simply the client at
     * the smallest selected index.  Returns {@code null} if there is no
     * selection.
     * 
     * @return the first selected client
     * 
     * @see JList#getSelectedValue()
     */
    public SessionId getSelectedClient() {
	return (SessionId) getSelectedValue();
    }

    /**
     * Returns a {@link List} of all the selected clients, in order
     * of their insertion into the set.
     *
     * @return the selected clients, or an empty list if nothing is selected
     *
     * @see JList#getSelectedValues()
     */
    public List<SessionId> getSelectedClients() {
	SessionId[] targets = (SessionId[]) getSelectedValues();
	if (targets == null) {
	    return null;
	}
	return Arrays.asList(targets);
    }

    /**
     * A single {@code JLabel} that is reused as the
     * ListCellRendererComponent for this {@code JList}.
     */
    private final JLabel textLabel = new JLabel();

    /**
     * {@inheritDoc}
     */
    public Component getListCellRendererComponent(JList list, Object value, int index,
	    boolean isSelected, boolean cellHasFocus)
    {
	textLabel.setText(value.toString());
	return textLabel;
    }
}