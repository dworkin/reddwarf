package com.sun.sgs.tutorial.client.lesson2;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.tutorial.client.lesson1.HelloUserClient;

/**
 * A simple GUI client that interacts with an SGS server-side app using
 * both direct messaging and channel broadcasts.
 * <p>
 * It presents a basic chat interface with an output area and input
 * field, and adds a channel selector to allow the user to choose which
 * method is used for sending data.
 *
 * @see HelloUserClient for a description of the properties understood
 *      by this client.
 */
public class HelloChannelClient extends HelloUserClient
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** Map that associates a channel name with a {@link ClientChannel}. */
    protected final Map<String, ClientChannel> channelsByName =
        new HashMap<String, ClientChannel>();

    /** The UI selector among direct messaging and different channels. */
    protected JComboBox channelSelector;

    /** The data model for the channel selector. */
    protected DefaultComboBoxModel channelSelectorModel;

    /** Sequence generator for counting channels. */
    protected final AtomicInteger channelNumberSequence =
        new AtomicInteger(1);

    // Main

    /**
     * Runs an instance of this client.
     *
     * @param args the command-line arguments (unused)
     */
    public static void main(String[] args) {
        new HelloChannelClient().login();
    }

    // HelloChannelClient methods

    /**
     * Creates a new client UI.
     */
    public HelloChannelClient() {
        super(HelloChannelClient.class.getSimpleName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation adds a channel selector component next
     * to the input text field to allow users to choose between
     * direct-to-server messages and channel broadcasts.
     */
    @Override
    protected void populateInputPanel(JPanel panel) {
        super.populateInputPanel(panel);
        channelSelectorModel = new DefaultComboBoxModel();
        channelSelectorModel.addElement("<DIRECT>");
        channelSelector = new JComboBox(channelSelectorModel);
        channelSelector.setFocusable(false);
        panel.add(channelSelector, BorderLayout.WEST);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a listener that formats and displays received channel
     * messages in the output text pane.
     */
    @Override
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        channelsByName.put(channel.getName(), channel);
        appendOutput("Joined to channel " + channel.getName());
        channelSelectorModel.addElement(channel.getName());
        return new HelloChannelListener();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Encodes the string entered by the user and sends it on a channel
     * or directly to the server, depending on the setting of the channel
     * selector.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        if (! simpleClient.isConnected())
            return;

        try {
            String text = getInputText();
            byte[] message = encodeString(text);
            String channelName =
                (String) channelSelector.getSelectedItem();
            if (channelName.equalsIgnoreCase("<DIRECT>")) {
                simpleClient.send(message);
            } else {
                ClientChannel channel = channelsByName.get(channelName);
                channel.send(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * A simple listener for channel events.
     */
    public class HelloChannelListener
        implements ClientChannelListener
    {
        /**
         * An example of per-channel state, recording the number of
         * channel joins when the client joined this channel.
         */
        private final int channelNumber;

        /**
         * Creates a new {@code HelloChannelListener}. Note that
         * the listener will be given the channel on its callback
         * methods, so it does not need to record the channel as
         * state during the join.
         */
        public HelloChannelListener() {
            channelNumber = channelNumberSequence.getAndIncrement();
        }

        /**
         * {@inheritDoc}
         * <p>
         * Displays a message when this client leaves a channel.
         */
        public void leftChannel(ClientChannel channel) {
            appendOutput("Removed from channel " + channel.getName());
        }

        /**
         * {@inheritDoc}
         * <p>
         * Formats and displays messages received on a channel.
         */
        public void receivedMessage(ClientChannel channel,
                SessionId sender, byte[] message)
        {
            appendOutput("[" + channel.getName() + "/ " + channelNumber +
                "] " + sender + ": " + decodeString(message));
        }
    }
}
