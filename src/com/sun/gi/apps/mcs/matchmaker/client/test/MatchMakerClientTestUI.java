package com.sun.gi.apps.mcs.matchmaker.client.test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;


import com.sun.gi.apps.mcs.matchmaker.client.FolderDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.GameDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.IGameChannel;
import com.sun.gi.apps.mcs.matchmaker.client.IGameChannelListener;
import com.sun.gi.apps.mcs.matchmaker.client.ILobbyChannel;
import com.sun.gi.apps.mcs.matchmaker.client.ILobbyChannelListener;
import com.sun.gi.apps.mcs.matchmaker.client.IMatchMakingClientListener;
import com.sun.gi.apps.mcs.matchmaker.client.LobbyDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.MatchMakingClient;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.SGSUUID;

public class MatchMakerClientTestUI extends JFrame implements IMatchMakingClientListener {

	private MatchMakingClient mmClient;
	private ClientConnectionManager manager;
	private DefaultMutableTreeNode root;
	private JTree tree;
	private DefaultTreeModel treeModel;
	private LobbyPanel lobbyPanel;
	private GamePanel gamePanel;
	private HashMap<String, LobbyDescriptor> lobbyMap;
	
	private JButton connectButton;
	
	public MatchMakerClientTestUI() {
		super();
		
		setStatus("Not Connected");
		
		lobbyMap = new HashMap<String, LobbyDescriptor>();
		
		JPanel treePanel = doTreeLayout();
		
		connectButton = new JButton("Connect");
		connectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (connectButton.getText().equals("Connect")) {
					connect();
				}
				else {
					manager.disconnect();
				}
			}
		});
		
		JButton joinLobby = new JButton("Join Lobby");
		joinLobby.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				LobbyDescriptor lobby = getSelectedLobby();
				if (lobby == null) {
					return;
				}
				mmClient.joinLobby(lobby.getLobbyID().toByteArray(), "secret");
			}
		});
		
		JButton createGame = new JButton("Create Game");
		createGame.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				lobbyPanel.createGame();
			}
		});
		
		JButton joinGame = new JButton("Join Game");
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(connectButton);
		buttonPanel.add(joinLobby);
		buttonPanel.add(createGame);
		buttonPanel.add(joinGame);
		
		JSplitPane rightPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		rightPane.setDividerLocation(300);
		rightPane.setTopComponent(lobbyPanel = new LobbyPanel());
		rightPane.setBottomComponent(gamePanel = new GamePanel());
		
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setDividerLocation(200);
		splitPane.setLeftComponent(treePanel);
		splitPane.setRightComponent(rightPane);
		
		JTextField chatField = new JTextField(35);
		JButton sendTextButton = new JButton("Send Text");
		JButton sendPrivateTextButton = new JButton("Send Private Text");
		
		JTextArea incomingText = new JTextArea(3, 40);
		
		JPanel chatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		chatPanel.add(chatField);
		chatPanel.add(sendTextButton);
		chatPanel.add(sendPrivateTextButton);
		
		
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(new JScrollPane(incomingText), BorderLayout.NORTH);
		bottomPanel.add(chatPanel, BorderLayout.CENTER);
		bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		
		add(splitPane, BorderLayout.CENTER);
		add(bottomPanel, BorderLayout.SOUTH);
		
		setBounds(300, 200, 700, 600);
		
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (manager != null) {
					manager.disconnect();
				}
				System.exit(0);
			}
		});
		
		setVisible(true);
	}
	
	private LobbyDescriptor getSelectedLobby() {
		Object selection = tree.getLastSelectedPathComponent();
		LobbyDescriptor lobby = null;
		if (selection != null && selection instanceof LobbyNode) {
			lobby = ((LobbyNode) selection).getDescriptor();
		}
		return lobby;
	}
	
	private JPanel doTreeLayout() {
		treeModel = createTreeModel();
		tree = new JTree(treeModel);
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				TreePath path = e.getNewLeadSelectionPath();
				if (path != null) {
					TreeNode node = (TreeNode) path.getLastPathComponent();
				}
			}
		});
		tree.setRootVisible(true);
		tree.setShowsRootHandles(true);

		JPanel p = new JPanel(new BorderLayout());
		p.add(new JScrollPane(tree), BorderLayout.CENTER);
		
		return p;
	}
	
	public DefaultTreeModel createTreeModel() {
		root = new DefaultMutableTreeNode("Folders");

		return new DefaultTreeModel(root);
	}
	
	private void setStatus(String status) {
		setTitle("Match Maker Client Test: " + status);
	}
	
	public void connect() {
		try {
		    manager = new ClientConnectionManagerImpl("MatchMakerTest",
			      new URLDiscoverer(
				  new File("resources/FakeDiscovery.xml").toURI().toURL()));
		    mmClient = new MatchMakingClient(manager);
		    mmClient.setListener(this);
		    String[] classNames = manager.getUserManagerClassNames();
		    manager.connect(classNames[0]);
		} catch (Exception e) {
		    e.printStackTrace();
		    return;
		}
	}
	
	public void listedFolder(SGSUUID folderID, FolderDescriptor[] subFolders, LobbyDescriptor[] lobbies) {

		DefaultMutableTreeNode node = findFolderNode(folderID, root);
		if (node == null) {
			node = root;
		}
		
		for (FolderDescriptor f : subFolders) {
			treeModel.insertNodeInto(new FolderNode(f), node, node.getChildCount());
		}
		for (LobbyDescriptor l : lobbies) {
			treeModel.insertNodeInto(new LobbyNode(l), node, node.getChildCount());
			lobbyMap.put(l.getChannelName(), l);
		}
		for (FolderDescriptor f : subFolders) {
			mmClient.listFolder(f.getFolderID().toByteArray());
		}
	}
	
	private FolderNode findFolderNode(SGSUUID folderID, DefaultMutableTreeNode node) {
		for (int i = 0; i < node.getChildCount(); i++) {
			if (node.getChildAt(i) instanceof FolderNode) {
				FolderNode curNode = (FolderNode) node.getChildAt(i);
				if (curNode.getFolderID().equals(folderID)) {
					return curNode;
				}
				else if (curNode.getChildCount() > 0) {
					FolderNode subFolder = findFolderNode(folderID, curNode);
					if (subFolder != null) {
						return subFolder;
					}
				}
			}
		}
		return null;
	}
	
	public void foundUserName(String userName, byte[] userID) {
		// TODO Auto-generated method stub

	}

	public void foundUserID(String userName, byte[] userID) {
		// TODO Auto-generated method stub

	}

	public void joinedLobby(ILobbyChannel channel) {
		lobbyPanel.setLobby(channel);
	}

	public void joinedGame(IGameChannel channel) {
		// TODO Auto-generated method stub

	}

	public void connected(byte[] myID) {
		setStatus("Connected");
		connectButton.setText("Disconnect");
		mmClient.listFolder(null);
	}
	
	public void disconnected() {
		setStatus("Disconnected");
		connectButton.setText("Connect");
	}

	public void validationRequest(Callback[] callbacks) {
    	for (Callback cb : callbacks) {
    		if (cb instanceof NameCallback) {
    			String value = JOptionPane.showInputDialog(this, "Enter Username", "guest1");
    			((NameCallback) cb).setName(value);
    		}
    	}
    	mmClient.sendValidationResponse(callbacks);
	}

	public static void main(String[] args) {
		new MatchMakerClientTestUI();
	}
	
	private class FolderNode extends DefaultMutableTreeNode {

		private FolderDescriptor folder;

		public FolderNode(FolderDescriptor f) {
			folder = f;
			
		}

		public boolean isLeaf() {
			return false;
		}

		public String toString() {
			return folder.getName();
		}

		public SGSUUID getFolderID() {
			return folder.getFolderID();
		}

	}	
	
	private class LobbyNode extends DefaultMutableTreeNode {

		private LobbyDescriptor lobby;

		public LobbyNode(LobbyDescriptor l) {
			lobby = l;
			
		}

		public boolean isLeaf() {
			return true;
		}

		public String toString() {
			return lobby.getName();
		}

		public SGSUUID getLobbyID() {
			return lobby.getLobbyID();
		}
		
		public LobbyDescriptor getDescriptor() {
			return lobby;
		}

	}
	
	private class LobbyPanel extends JPanel implements ILobbyChannelListener {
		
		private JLabel lobbyName;
		private JLabel lobbyDescription;
		private JLabel numUserLabel;
		private JCheckBox isPasswordProtected;
		private ILobbyChannel channel;
		private int numUsers = 0;
		private int maxUsers = 0;
		private DefaultListModel userListModel;
		private DefaultListModel gameListModel;
		private JList userList;
		private GameParametersTableModel parametersModel;
		private HashMap<String, Object> gameParameters;
		
		private HashMap<byte[], String> userMap; 
		
		LobbyPanel() {
			super(new BorderLayout());
			
			userMap = new HashMap<byte[], String>();
			
			JPanel topPanel = new JPanel();
			topPanel.add(new JLabel("Current Lobby:"));
			topPanel.add(lobbyName = new JLabel());
			topPanel.add(lobbyDescription = new JLabel());
			
			JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			centerPanel.add(numUserLabel = new JLabel("Users: 0/?"));
			centerPanel.add(isPasswordProtected = new JCheckBox("Password Protected"));
			isPasswordProtected.setEnabled(false);
			
			int listHeight = 150;
			JTable gameParametersTable = new JTable(parametersModel = new GameParametersTableModel());
			JScrollPane tablePane = new JScrollPane(gameParametersTable);
			tablePane.setPreferredSize(new Dimension(200, listHeight));
			
			JPanel gameParametersPanel = new JPanel(new BorderLayout());
			gameParametersPanel.add(new JLabel("Game Params"), BorderLayout.NORTH);
			gameParametersPanel.add(tablePane, BorderLayout.CENTER);
			
			JScrollPane gameListPane = new JScrollPane(new JList(gameListModel = new DefaultListModel()));
			gameListPane.setPreferredSize(new Dimension(150, listHeight));
			
			JPanel gameListPanel = new JPanel(new BorderLayout());
			gameListPanel.add(new JLabel("Game List"), BorderLayout.NORTH);
			gameListPanel.add(gameListPane, BorderLayout.CENTER);
			
			JScrollPane userListPane = new JScrollPane(userList = new JList(userListModel = new DefaultListModel()));
			userListPane.setPreferredSize(new Dimension(150, listHeight));
			
			JPanel userListPanel = new JPanel(new BorderLayout());
			userListPanel.add(new JLabel("User List"), BorderLayout.NORTH);
			userListPanel.add(userListPane, BorderLayout.CENTER);
			
			JPanel bottomPanel = new JPanel(new BorderLayout());
			bottomPanel.add(userListPanel, BorderLayout.WEST);
			bottomPanel.add(gameListPanel, BorderLayout.CENTER);
			bottomPanel.add(gameParametersPanel, BorderLayout.EAST);
			
			add(topPanel, BorderLayout.NORTH);
			add(centerPanel, BorderLayout.CENTER);
			add(bottomPanel, BorderLayout.SOUTH);
		}
		
		void setLobby(ILobbyChannel channel) {
			LobbyPanel.this.channel = channel;
			channel.setListener(LobbyPanel.this);
			LobbyDescriptor lobby = lobbyMap.get(channel.getName());
			lobbyName.setText(lobby.getName());
			lobbyDescription.setText(lobby.getDescription());
			isPasswordProtected.setSelected(lobby.isPasswordProtected());
			maxUsers = lobby.getMaxUsers();
			numUsers = lobby.getNumUsers();
			updateNumUsers();
			channel.requestGameParameters();
		}
		
		void createGame() {
			channel.createGame("My Game", "My description", null, gameParameters);
		}
		
		private void updateNumUsers() {
			numUserLabel.setText("Users: " + numUsers + "/" + maxUsers);
		}
		
		public void playerEntered(byte[] player, String name) {
			System.out.println("Lobby Panel: Player entered " + name);
			userMap.put(player, name);
			numUsers++;
			updateNumUsers();
			userListModel.addElement(name);
			userList.repaint();
		}
		
		public void playerLeft(byte[] player) {
			numUsers--;
			updateNumUsers();
			String name = userMap.remove(player);
			System.out.println("Lobby Panel: Player Left " + name);			
			userListModel.removeElement(name);
			userList.repaint();
		}
		
		public void receiveText(byte[] from, String text, boolean wasPrivate) {
			
		}
		
		public void receivedGameParameters(HashMap<String, Object> parameters) {
			gameParameters = parameters;
			Iterator<String> iterator = parameters.keySet().iterator();
			while (iterator.hasNext()) {
				String curKey = iterator.next();
				parametersModel.addParameter(curKey, parameters.get(curKey));
			}
			parametersModel.fireTableDataChanged();
		}
		
		public void createGameFailed(String name, String reason) {
			System.out.println("LobbyPanel createGameFailed: " + name + " reason " + reason);
		}
		
		public void gameCreated(GameDescriptor game) {
			gameListModel.addElement(game.getName());
		}
		
		public void playerJoinedGame(GameDescriptor game, byte[] player) {
			
		}
	}
	
	private class GameParametersTableModel extends AbstractTableModel {
		
		private List<String> params;
		private List values;
		
		GameParametersTableModel() {
			params = new LinkedList<String>();
			values = new LinkedList();
		}
		
		public void addParameter(String param, Object value) {
			params.add(param);
			values.add(value);
		}
		
		public int getColumnCount() {
			return 2;
		}
		
		public int getRowCount() {
			return params.size();
		}
		
		public Object getValueAt(int row, int col) {
			if (col == 0) {
				return params.get(row);
			}
			return values.get(row);
		}
		
		public String getColumnName(int col) {
			return col == 0 ? "Parameter" : "Value";
		}
	}
	
	private class GamePanel extends JPanel implements IGameChannelListener {
		
		private JLabel gameName;
		private JLabel gameDescription;
		private JLabel numUserLabel;
		private JCheckBox isPasswordProtected;
		private IGameChannel channel;
		
		GamePanel() {
			super(new BorderLayout());
			
			JPanel topPanel = new JPanel();
			topPanel.add(new JLabel("Current Game:"));
			topPanel.add(gameName = new JLabel());
			topPanel.add(gameDescription = new JLabel());
			
			JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			centerPanel.add(numUserLabel = new JLabel("Users: 0/?"));
			centerPanel.add(isPasswordProtected = new JCheckBox("Password Protected"));
			isPasswordProtected.setEnabled(false);
			
			add(topPanel, BorderLayout.NORTH);
			add(centerPanel, BorderLayout.CENTER);
		}
		
		public void setGame(IGameChannel channel) {
			GamePanel.this.channel = channel;
		}
		
		public void playerEntered(byte[] player, String name) {
			
		}
		
		public void playerLeft(byte[] player) {
			
		}
		
		public void receiveText(byte[] from, String text, boolean wasPrivate) {
			
		}
		
		
	}

}
