package com.dsinstaller.view;

import java.awt.Color;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

import com.installercore.IInstallerCallback;
import com.installercore.IInstallerGUIStep;
import com.installercore.metadata.MetadataDatabase;
import com.installercore.step.RunMetadataStep;
import com.installercore.step.StepException;
import com.installercore.step.TextFieldStringReportCallback;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

public class InstallPanel extends JPanel implements IInstallerGUIStep {
	
	IInstallerCallback myCallback;
	IInstallerGUIStep lastStep;
	private static final long serialVersionUID = 1L;
	private JPanel buttonPanel = null;
	private JPanel middlePanel = null;
	private JPanel jPanel = null;
	private JPanel jPanel1 = null;
	private JPanel jPanel11 = null;
	private JPanel jPanel12 = null;
	private JProgressBar installProgress = null;
	private JLabel instructionLabel = null;
	private JLabel progressLabel = null;
	private JTextField progressTextField = null;
	Thread worker;
	String installPath = "";

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
	public InstallPanel(String installPath) {
		super();
		this.installPath = installPath;
		initialize();
		installProgress.setMaximum(MetadataDatabase.count());
		installProgress.setMinimum(0);
		installProgress.setValue(0);
		install();
	}
	
	private void install()
	{
			worker = new Thread(new Runnable()
			{

				public void run() {
					try
					{
						RunMetadataStep step = new RunMetadataStep(	
							installPath,
							new TextFieldStringReportCallback(progressTextField), new Runnable()
							{
								public void run() {
									myCallback.moveToStep(new CompletePanel());
								}
							},
							new Runnable()
							{
								public void run() {
									installProgress.setValue(installProgress.getValue()+1);
								}	
							}
						);
						step.run();
					}
					catch(StepException exception)
					{
						myCallback.moveToStep(new CompletePanel(exception.getMessage()));
					}
					
				}
			});
			worker.start();
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
		this.setSize(566, 417);
		this.setBackground(new Color(255,255,255));
		this.add(getButtonPanel(), BorderLayout.SOUTH);
		this.add(getMiddlePanel(), BorderLayout.CENTER);
		progressTextField.setBorder(BorderFactory.createEmptyBorder());
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
			GridBagConstraints gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 0;
			middlePanel = new JPanel();
			middlePanel.setLayout(new GridBagLayout());
			middlePanel.setPreferredSize(new Dimension(250, 120));
			middlePanel.setBackground(new Color(6,88,156));
			middlePanel.add(getJPanel(), gridBagConstraints);
		}
		return middlePanel;
	}

	/**
	 * This method initializes jPanel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getJPanel() {
		if (jPanel == null) {
			jPanel = new JPanel();
			jPanel.setLayout(new BorderLayout());
			jPanel.setPreferredSize(new Dimension(450, 75));
			jPanel.add(getJPanel1(), BorderLayout.NORTH);
			jPanel.add(getJPanel11(), BorderLayout.SOUTH);
			jPanel.add(getJPanel12(), BorderLayout.CENTER);
		}
		return jPanel;
	}

	/**
	 * This method initializes jPanel1	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getJPanel1() {
		if (jPanel1 == null) {
			instructionLabel = new JLabel();
			instructionLabel.setForeground(Color.white);
			instructionLabel.setText("Project Darkstar is being installed.");
			instructionLabel.setName("instructionLabel");
			jPanel1 = new JPanel();
			jPanel1.setLayout(new FlowLayout());
			jPanel1.setPreferredSize(new Dimension(450, 25));
			jPanel1.setBackground(new Color(6, 88, 156));
			jPanel1.add(instructionLabel, null);
		}
		return jPanel1;
	}

	/**
	 * This method initializes jPanel11	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getJPanel11() {
		if (jPanel11 == null) {
			progressLabel = new JLabel();
			progressLabel.setText("");
			jPanel11 = new JPanel();
			jPanel11.setLayout(new FlowLayout());
			jPanel11.setPreferredSize(new Dimension(450, 25));
			jPanel11.setBackground(new Color(6, 88, 156));
			jPanel11.add(getProgressTextField(), null);
			jPanel11.add(progressLabel, null);
		}
		return jPanel11;
	}

	/**
	 * This method initializes jPanel12	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getJPanel12() {
		if (jPanel12 == null) {
			FlowLayout flowLayout1 = new FlowLayout();
			flowLayout1.setHgap(5);
			flowLayout1.setVgap(2);
			jPanel12 = new JPanel();
			jPanel12.setBackground(new Color(6, 88, 156));
			jPanel12.setLayout(flowLayout1);
			jPanel12.setPreferredSize(new Dimension(0, 0));
			jPanel12.add(getInstallProgress(), null);
		}
		return jPanel12;
	}

	/**
	 * This method initializes installProgress	
	 * 	
	 * @return javax.swing.JProgressBar	
	 */
	private JProgressBar getInstallProgress() {
		if (installProgress == null) {
			installProgress = new JProgressBar();
			installProgress.setPreferredSize(new Dimension(400, 20));
			installProgress.setForeground(Color.red);
			installProgress.setBackground(Color.white);
			installProgress.setValue(50);
		}
		return installProgress;
	}

	/**
	 * This method initializes progressTextField	
	 * 	
	 * @return javax.swing.JTextField	
	 */
	private JTextField getProgressTextField() {
		if (progressTextField == null) {
			progressTextField = new JTextField();
			progressTextField.setPreferredSize(new Dimension(400, 20));
			progressTextField.setBackground(new Color(6, 88, 156));
			progressTextField.setName("");
			progressTextField.setForeground(Color.white);
			progressTextField.setEditable(false);
		}
		return progressTextField;
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
