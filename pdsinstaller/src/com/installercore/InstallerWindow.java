package com.installercore;
import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.JFrame;
import java.awt.Dimension;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import com.dsinstaller.DSInstallerStrings;

import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.GridBagLayout;

public class InstallerWindow extends JFrame implements WindowListener {

	private static final long serialVersionUID = 1L;
	private JPanel jContentPane = null;
	private JLabel Sidebar = null;
	private JPanel mainPanel = null;
	/**
	 * This is the default constructor
	 */
	public InstallerWindow() {
		super();
		initialize();
		this.addWindowListener(this);
	}

	/**
	 * This method initializes the window.
	 * 
	 * @return void
	 */
	private void initialize() {
		this.setSize(686,550);
		this.setResizable(false);
		this.setBackground(Color.WHITE);
		this.setContentPane(getJContentPane());
		this.setTitle("Darkstar Installer");
	}

	/**
	 * This method initializes jContentPane
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane() {
		if (jContentPane == null) {
			BorderLayout borderLayout = new BorderLayout();
			borderLayout.setHgap(2);
			Sidebar = new JLabel();
			Sidebar.setIcon(new ImageIcon(this.getClass().getResource(DSInstallerStrings.SidebarImageLocation)));
			Sidebar.setDisplayedMnemonic(KeyEvent.VK_UNDEFINED);
			Sidebar.setMinimumSize(new Dimension(Sidebar.getIcon().getIconWidth(),Sidebar.getIcon().getIconHeight()));
			Sidebar.setMaximumSize(new Dimension(Sidebar.getIcon().getIconWidth(),Sidebar.getIcon().getIconHeight()));
			Sidebar.setPreferredSize(new Dimension(Sidebar.getIcon().getIconWidth(),Sidebar.getIcon().getIconHeight()));
			jContentPane = new JPanel();
			jContentPane.setLayout(borderLayout);
			jContentPane.add(Sidebar, BorderLayout.WEST);
			jContentPane.add(getMainPanel(), BorderLayout.CENTER);
			
			
			
		}
		return jContentPane;
	}

	/**
	 * This method initializes mainPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getMainPanel() {
		if (mainPanel == null) {
			mainPanel = new JPanel();
			mainPanel.setLayout(new GridBagLayout());
			mainPanel.setBackground(new Color(6,88,156));
		}
		return mainPanel;
	}
	
	/**
	 * This method changes the center panel to the one provided.
	 * @param newPanel The new panel.
	 */
	public void setCenterPanel(JPanel newPanel)
	{
		if(newPanel == null)
			return;
		jContentPane.setVisible(false);
		jContentPane.remove(mainPanel);
		mainPanel = newPanel;
		jContentPane.add(getMainPanel(), BorderLayout.CENTER);
		jContentPane.setVisible(true);
		this.setResizable(false);
		this.invalidate();
		this.repaint();
		
	}

	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	/**
	 * This closes the window appropriately.
	 */
	public void windowClosing(WindowEvent e) {
		System.exit(0);
	}

	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
