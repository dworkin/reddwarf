package com.sun.sgs.test.client.chat;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import com.sun.sgs.client.SessionId;

//=== Represent channel members and user lists in the GUI ===

public class MemberList extends JList
	implements ListCellRenderer, MouseListener
{
    private static final long serialVersionUID = 1L;
    
    private final ChatClient myChatClient;

    MemberList(ChatClient chatClient) {
	super(new DefaultListModel());
	myChatClient = chatClient;
	
	setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	setCellRenderer(this);

	addMouseListener(this);
    }

    public void addClient(SessionId member) {
	((DefaultListModel) getModel()).addElement(member);
	repaint();
    }

    public void removeClient(SessionId member) {
	((DefaultListModel) getModel()).removeElement(member);
	repaint();
    }

    public SessionId getSelectedClient() {
	return (SessionId) getSelectedValue();
    }

    public Collection<SessionId> getSelectedClients() {
	SessionId[] targets = (SessionId[]) getSelectedValues();
	if (targets == null) {
	    return null;
	}
	return Arrays.asList(targets);
    }

    private final JLabel textLabel = new JLabel();

    public Component getListCellRendererComponent(JList list, Object value, int index,
	    boolean isSelected, boolean cellHasFocus)
    {
	textLabel.setText(String.format("%.8s", value.toString()));
	return textLabel;
    }

    public void mouseClicked(MouseEvent evt) {
	if (evt.getClickCount() == 2) {
	    myChatClient.doDCCMessage();
	}
    }

    public void mouseEntered(MouseEvent e)  { /* unused */ }
    public void mouseExited(MouseEvent e)   { /* unused */ }
    public void mousePressed(MouseEvent e)  { /* unused */ }
    public void mouseReleased(MouseEvent e) { /* unused */ }
}