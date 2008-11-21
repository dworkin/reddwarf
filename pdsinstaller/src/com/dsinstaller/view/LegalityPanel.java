package com.dsinstaller.view;

import java.awt.Color;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

import com.dsinstaller.DSInstallerStrings;
import com.installercore.IInstallerCallback;
import com.installercore.IInstallerGUIStep;
import com.installercore.step.EmbeddedTextStep;
import com.installercore.step.StepException;

import java.awt.Dimension;
import java.awt.BorderLayout;
import javax.swing.JButton;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JLabel;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JTextArea;

public class LegalityPanel extends JPanel implements IInstallerGUIStep, ActionListener {

	IInstallerCallback myCallback;
	IInstallerGUIStep lastStep;
	private static final long serialVersionUID = 1L;
	private JPanel buttonPanel = null;
	private JPanel middlePanel = null;
	private JButton cancelButton = null;
	private JButton previousButton = null;
	private JButton nextButton = null;
	private JPanel checkPanel = null;
	private JPanel spacefillerPanel1 = null;
	private JPanel spacefillerPanel2 = null;
	private JPanel spacefillerPanel3 = null;
	private JCheckBox agreeCheck = null;
	private JLabel agreeLabel = null;
	private JTextArea legalTextArea = null;
	
	private InstallPathPanel nextStep = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	public InstallPathPanel getNext()
	{
		if(nextStep == null)
			nextStep = new InstallPathPanel();
			nextStep.setSize(new Dimension(433, 267));
		
		return nextStep;		
	}

	
	public JPanel getPanel() {
		return this;
	}

	/**
	 * You don't need to touch this one.
	 */
	public void setNextStepCallback(IInstallerCallback callback) {
		myCallback = callback;
	}

	/**
	 * You don't need to touch this one.
	 */
	public void stepRequested(IInstallerGUIStep previousStep) {
		if(lastStep == null)
			lastStep = previousStep;
	}

	/**
	 * This is the default constructor
	 */
	public LegalityPanel() {
		super();
		initialize();
		setActions();
		
		EmbeddedTextStep step = new EmbeddedTextStep(DSInstallerStrings.LegaleseTextLocation);
		try
		{
			step.run();
			legalTextArea.setText(step.getReadText());
		}
		catch (StepException e)
		{
			myCallback.moveToStep(new CompletePanel(e.getMessage()));
		}
	}
	
	public void setActions() {
		cancelButton.addActionListener(this);
		previousButton.addActionListener(this);
		nextButton.addActionListener(this);
		agreeCheck.addActionListener(
				new ActionListener()
				{
					public void actionPerformed(ActionEvent e) {
						nextButton.setEnabled(agreeCheck.isSelected());
						nextButton.invalidate();
						
					}	
				}
		);
	}
	
	// Mouse actions
	
	public void actionPerformed(ActionEvent e) {		
		if(ButtonTracker.mouseClicked(e, cancelButton))
		{
			new ExitDialog<IInstallerGUIStep>(myCallback, this);
		}
		else if(ButtonTracker.mouseClicked(e, previousButton))
		{
			myCallback.moveToStep( lastStep );
		}
		else if(ButtonTracker.mouseClicked(e, nextButton))
		{
			myCallback.moveToStep( getNext() );
		}
	}

	

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize() {
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setVgap(2);
		this.setLayout(borderLayout);
		this.setSize(620, 508);
		this.setBackground(new Color(255,255,255));
		this.add(getButtonPanel(), BorderLayout.SOUTH);
		this.add(getMiddlePanel(), BorderLayout.CENTER);
		this.legalTextArea.setWrapStyleWord(true);
		nextButton.setEnabled(false);
	}

	/**
	 * This method initializes buttonPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getButtonPanel() {
		if (buttonPanel == null) {
			FlowLayout flowLayout = new FlowLayout();
			flowLayout.setAlignment(java.awt.FlowLayout.RIGHT);
			flowLayout.setVgap(23);
			flowLayout.setHgap(5);
			GridLayout gridLayout = new GridLayout();
			gridLayout.setRows(2);
			buttonPanel = new JPanel();
			buttonPanel.setLayout(flowLayout);
			buttonPanel.setBackground(new Color(114,188,217));
			buttonPanel.setPreferredSize(new Dimension(200, 76));
			buttonPanel.add(getCancelButton(), null);
			buttonPanel.add(getPreviousButton(), null);
			buttonPanel.add(getNextButton(), null);
		}
		return buttonPanel;
	}

	/**
	 * This method initializes middlePanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getMiddlePanel() {
		if (middlePanel == null) {
			BorderLayout borderLayout1 = new BorderLayout();
			borderLayout1.setHgap(0);
			borderLayout1.setVgap(0);
			middlePanel = new JPanel();
			middlePanel.setLayout(borderLayout1);
			middlePanel.setPreferredSize(new Dimension(250, 120));
			middlePanel.setBackground(new Color(6,88,156));
			middlePanel.add(getCheckPanel(), BorderLayout.SOUTH);
			middlePanel.add(getSpacefillerPanel1(), BorderLayout.WEST);
			middlePanel.add(getSpacefillerPanel2(), BorderLayout.EAST);
			middlePanel.add(getSpacefillerPanel3(), BorderLayout.NORTH);
			middlePanel.add(getLegalTextArea(), BorderLayout.CENTER);
		}
		return middlePanel;
	}

	/**
	 * This method initializes cancelButton	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getCancelButton() {
		if (cancelButton == null) {
			cancelButton = new JButton();
			cancelButton.setName("cancelButton");
			cancelButton.setText("Cancel");
			cancelButton.setPreferredSize(new Dimension(115, 30));
		}
		return cancelButton;
	}

	/**
	 * This method initializes previousButton	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getPreviousButton() {
		if (previousButton == null) {
			previousButton = new JButton();
			previousButton.setName("previousButton");
			previousButton.setText("Previous");
			previousButton.setPreferredSize(new Dimension(115, 30));
		}
		return previousButton;
	}

	/**
	 * This method initializes nextButton	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getNextButton() {
		if (nextButton == null) {
			nextButton = new JButton();
			nextButton.setName("nextButton");
			nextButton.setText("Next");
			nextButton.setPreferredSize(new Dimension(115, 30));
		}
		return nextButton;
	}

	/**
	 * This method initializes checkPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getCheckPanel() {
		if (checkPanel == null) {
			agreeLabel = new JLabel();
			agreeLabel.setText("");
			agreeLabel.setForeground(Color.white);
			checkPanel = new JPanel();
			checkPanel.setLayout(new FlowLayout());
			checkPanel.setPreferredSize(new Dimension(0, 55));
			checkPanel.setBackground(new Color(6, 88, 156));
			checkPanel.add(getAgreeCheck(), null);
			checkPanel.add(agreeLabel, null);
		}
		return checkPanel;
	}

	/**
	 * This method initializes spacefillerPanel1	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getSpacefillerPanel1() {
		if (spacefillerPanel1 == null) {
			spacefillerPanel1 = new JPanel();
			spacefillerPanel1.setLayout(new GridBagLayout());
			spacefillerPanel1.setPreferredSize(new Dimension(35, 0));
			spacefillerPanel1.setBackground(new Color(6, 88, 156));
		}
		return spacefillerPanel1;
	}

	/**
	 * This method initializes spacefillerPanel2	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getSpacefillerPanel2() {
		if (spacefillerPanel2 == null) {
			spacefillerPanel2 = new JPanel();
			spacefillerPanel2.setLayout(new GridBagLayout());
			spacefillerPanel2.setPreferredSize(new Dimension(35, 0));
			spacefillerPanel2.setBackground(new Color(6, 88, 156));
		}
		return spacefillerPanel2;
	}

	/**
	 * This method initializes spacefillerPanel3	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getSpacefillerPanel3() {
		if (spacefillerPanel3 == null) {
			spacefillerPanel3 = new JPanel();
			spacefillerPanel3.setLayout(new GridBagLayout());
			spacefillerPanel3.setPreferredSize(new Dimension(0, 35));
			spacefillerPanel3.setBackground(new Color(6, 88, 156));
		}
		return spacefillerPanel3;
	}

	/**
	 * This method initializes agreeCheck	
	 * 	
	 * @return javax.swing.JCheckBox	
	 */
	private JCheckBox getAgreeCheck() {
		if (agreeCheck == null) {
			agreeCheck = new JCheckBox();
			agreeCheck.setBackground(new Color(6, 88, 156));
			agreeCheck.setForeground(Color.white);
			agreeCheck.setText("I agree to the terms of the agreement.");
		}
		return agreeCheck;
	}

	/**
	 * This method initializes legalTextArea	
	 * 	
	 * @return javax.swing.JTextArea	
	 */
	private JTextArea getLegalTextArea() {
		if (legalTextArea == null) {
			legalTextArea = new JTextArea();
			legalTextArea.setLineWrap(true);
			legalTextArea.setFont(new Font("Courier New", Font.PLAIN, 14));
			legalTextArea.setText("***********************************\nTHIS IS SUPPOSED TO BE LEGAL TEXT\n***********************************\nSo I'm not even going to pretend I know how legal text works.\nI'm just going to ramble endlessly about cats in this space,\nbecause cats are amazing and fuzzy creatures.  Until\na lawyer writes up the appropriate thing for this, we will\nall have to agree that cats are adorable.");
		}
		return legalTextArea;
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
