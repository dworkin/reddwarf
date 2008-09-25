package com.dsinstaller.view;

import java.awt.Color;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

import com.dsinstaller.view.ValidationIcon.ValidationType;
import com.installercore.IInstallerCallback;
import com.installercore.IInstallerGUIStep;
import com.installercore.step.PickInstallPathStep;
import com.installercore.step.StepException;
import com.installercore.step.ValidateInstallationPathStep;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.BorderLayout;
import javax.swing.JButton;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class InstallPathPanel extends JPanel implements IInstallerGUIStep, ActionListener {

	IInstallerCallback myCallback;
	IInstallerGUIStep lastStep;
	private final JFileChooser fc = new JFileChooser();
	private static final long serialVersionUID = 1L;
	private JPanel buttonPanel = null;
	private JPanel middlePanel = null;
	private JButton cancelButton = null;
	private JButton previousButton = null;
	private JButton nextButton = null;
	private JLabel jLabel = null;  //  @jve:decl-index=0:visual-constraint="724,98"
	private JLabel instructionLabel = null;
	private JPanel filepickPanel = null;
	private ValidationIcon successIconLabel = null;
	private JTextField fileselectionBox = null;
	private JButton picklocationButton = null;
	
	private String installPath = null;
	
	InstallPanel nextStep = null;
	public InstallPanel getNext(String installPath)
	{
		nextStep = new InstallPanel(installPath);
		
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
	public InstallPathPanel() {
		super();
		initialize();
		setActions();
		runInitialSteps();
	}
	
	public void runInitialSteps()
	{
		PickInstallPathStep fips = new PickInstallPathStep();
		try
		{
			fips.run();
			installPath = fips.getInstallPath();
			installPath = makePathIntoFolder(installPath);
		}
		catch(StepException e)
		{
			installPath = "";
		}
		fileselectionBox.setText(installPath);
		validateInstallation();
	}
	
	public void setActions() {
		cancelButton.addActionListener(this);
		previousButton.addActionListener(this);
		nextButton.addActionListener(this);
		picklocationButton.addActionListener(this);
		fileselectionBox.addCaretListener(new TextTimer(500, new ActionListener()
		{
			public void actionPerformed(ActionEvent e) {
				installPath = fileselectionBox.getText();
				validateInstallation();
			}
	
		}));
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
			File fs = new File(this.fileselectionBox.getText());
			if(fs.exists() && fs.isDirectory())
			{
				myCallback.moveToStep( getNext(installPath) );
			}
			else
			{
				int result = JOptionPane.showConfirmDialog(
						this,
						"Path does not exist. Would you like it to be created?",
						"Project Darkstar Installer",
						JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.WARNING_MESSAGE);
				
				switch(result)
				{
				case(JOptionPane.YES_OPTION):
					myCallback.moveToStep( getNext(installPath) );
					break;
				case(JOptionPane.NO_OPTION):
					break;
				}
			}
		}
		else if(e.getSource().equals(picklocationButton))
		{
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = fc.showOpenDialog(this);

	        if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File file = fc.getSelectedFile();
	            installPath = file.getAbsolutePath();
	            installPath = makePathIntoFolder(installPath);
	            this.fileselectionBox.setText(installPath);
	        }
	        validateInstallation();
		}
	}

	private void validateInstallation()
	{
		try
        {
        	ValidateInstallationPathStep vips = 
        		new ValidateInstallationPathStep(installPath);
        	vips.run();
        	successIconLabel.setValidation(ValidationType.SUCCESS);
        	nextButton.setEnabled(true);
        } catch (StepException e) {
        	successIconLabel.setValidation(ValidationType.ERROR);
        	nextButton.setEnabled(false);
        }
        nextButton.invalidate();
	}
	
	private String makePathIntoFolder(String path)
	{
		if(!path.endsWith(File.separator))
		{
			path += File.separator;
		}
		return path;
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
			GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
			gridBagConstraints1.gridx = 1;
			gridBagConstraints1.gridheight = 2;
			gridBagConstraints1.gridy = 1;
			GridBagConstraints gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 1;
			gridBagConstraints.gridy = 0;
			instructionLabel = new JLabel();
			instructionLabel.setForeground(Color.white);
			// TODO: Move this string into a resource. 
			instructionLabel.setText("Please select the location where you'd like Darkstar to be installed.");
			instructionLabel.setName("instructionLabel");
			middlePanel = new JPanel();
			middlePanel.setLayout(new GridBagLayout());
			middlePanel.setPreferredSize(new Dimension(250, 120));
			middlePanel.setBackground(new Color(6,88,156));
			middlePanel.add(instructionLabel, gridBagConstraints);
			middlePanel.add(getFilepickPanel(), gridBagConstraints1);
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
	 * This method initializes jLabel	
	 * 	
	 * @return javax.swing.JLabel	
	 */
	@SuppressWarnings("unused")
	private JLabel getJLabel() {
		if (jLabel == null) {
			jLabel = new JLabel();
			jLabel.setText("JLabel");
		}
		return jLabel;
	}

	/**
	 * This method initializes filepickPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getFilepickPanel() {
		if (filepickPanel == null) {
			FlowLayout flowLayout1 = new FlowLayout();
			flowLayout1.setVgap(0);
			flowLayout1.setHgap(0);
			successIconLabel = new ValidationIcon(ValidationType.ERROR);
			filepickPanel = new JPanel();
			filepickPanel.setBackground(new Color(6, 88, 156));
			filepickPanel.setLayout(flowLayout1);
			filepickPanel.setPreferredSize(new Dimension(500, 32));
			filepickPanel.add(successIconLabel, null);
			filepickPanel.add(getFileselectionBox(), null);
			filepickPanel.add(getPicklocationButton(), null);
		}
		return filepickPanel;
	}

	/**
	 * This method initializes fileselectionBox	
	 * 	
	 * @return javax.swing.JTextField	
	 */
	private JTextField getFileselectionBox() {
		if (fileselectionBox == null) {
			fileselectionBox = new JTextField();
			fileselectionBox.setPreferredSize(new Dimension(300, 20));
		}
		return fileselectionBox;
	}

	/**
	 * This method initializes picklocationButton	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getPicklocationButton() {
		if (picklocationButton == null) {
			picklocationButton = new JButton();
			picklocationButton.setPreferredSize(new Dimension(115, 20));
			picklocationButton.setText("Browse...");
		}
		return picklocationButton;
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
