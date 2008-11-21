package com.dsinstaller.view;

import java.awt.Color;
import javax.swing.JPanel;

import com.dsinstaller.DSInstallerStrings;
import com.installercore.IInstallerCallback;
import com.installercore.IInstallerGUIStep;
import com.installercore.step.EmbeddedTextStep;
import com.installercore.step.InitializeStep;
import com.installercore.step.StepException;

import java.awt.Dimension;
import java.awt.BorderLayout;
import javax.swing.JButton;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;

public class WelcomePanel extends JPanel implements IInstallerGUIStep, ActionListener {

	IInstallerCallback myCallback;  //  @jve:decl-index=0:
	IInstallerGUIStep lastStep;
	private static final long serialVersionUID = 1L;
	private JPanel buttonPanel = null;
	private JPanel middlePanel = null;
	private JButton cancelButton = null;
	private JButton nextButton = null;
	private JLabel welcomeLabel = null;
	
	private EmbeddedTextStep step;
	private String displayText;
	
	private LegalityPanel nextStep = null;
	public LegalityPanel getNext()
	{
		if(nextStep == null)
			nextStep = new LegalityPanel();
		
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
		//No previous step
	}

	/**
	 * This is the default constructor
	 */
	public WelcomePanel() {
		super();
		setup();		
		initialize();
		setActions();
		runInitialSteps();
	}
	
	public void runInitialSteps()
	{
		InitializeStep is = new InitializeStep(DSInstallerStrings.metadataLocation, DSInstallerStrings.sysvarsLocation);
		try {
			is.run();
		} catch (StepException e) {
			myCallback.moveToStep(new CompletePanel(e.getMessage()));
		}
	}
	
	public void setActions() {
		nextButton.addActionListener(this);
		cancelButton.addActionListener(this);
	}
	
	// Mouse actions
	
	public void actionPerformed(ActionEvent e) {		
		if(ButtonTracker.mouseClicked(e, cancelButton))
		{
			new ExitDialog<IInstallerGUIStep>(myCallback, this);
		}
		else if(ButtonTracker.mouseClicked(e, nextButton))
		{
			myCallback.moveToStep( getNext() );
		}
	}


	
	private void setup()
	{
		try {
			step = new EmbeddedTextStep(DSInstallerStrings.WelcomeTextLocation);
			step.run();
			this.displayText = step.getReadText();
		} catch (StepException e) {
			this.displayText = "Welcome!";
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
		this.setSize(467, 343);
		this.setBackground(new Color(255,255,255));
		this.add(getButtonPanel(), BorderLayout.SOUTH);
		this.add(getMiddlePanel(), BorderLayout.CENTER);
		
		try
		{
			/**
			 * GENERATE METADATA BASE
			 */
		}
		catch(Exception e)
		{
			myCallback.moveToStep(new CompletePanel(e.getMessage()));
		}
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
			FlowLayout flowLayout1 = new FlowLayout();
			flowLayout1.setVgap(70);
			welcomeLabel = new JLabel();
			welcomeLabel.setText(this.displayText);
			welcomeLabel.setForeground(Color.white);
			middlePanel = new JPanel();
			middlePanel.setPreferredSize(new Dimension(250, 120));
			middlePanel.setLayout(flowLayout1);
			middlePanel.setBackground(new Color(6,88,156));
			middlePanel.add(welcomeLabel, null);
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

}  //  @jve:decl-index=0:visual-constraint="10,10"
