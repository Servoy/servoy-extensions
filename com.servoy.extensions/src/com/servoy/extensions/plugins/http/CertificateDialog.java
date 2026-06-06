/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.extensions.plugins.http;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.TextArea;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * @author jcompagner
 *
 */
public class CertificateDialog extends JDialog
{
	private boolean accept = false;
	private TextArea textArea;

	public CertificateDialog(Window owner, InetSocketAddress remoteAddress, X509Certificate[] lastCertificates)
	{
		super(owner, ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setTitle("Unknown certificate encountered when connecting to " + remoteAddress.getHostName() + ", accept it?");
		setLayout(new BorderLayout(2, 2));
		getRootPane().setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(lastCertificates[0]);
		TreePath path = new TreePath(root);
		DefaultMutableTreeNode node = root;
		for (int i = 1; i < lastCertificates.length; i++)
		{
			DefaultMutableTreeNode child = new DefaultMutableTreeNode(lastCertificates[i]);
			node.add(child);
			node = child;
			if (i < lastCertificates.length - 1)
			{
				path = path.pathByAddingChild(child);
			}
		}
		JTree tree = new JTree(root);
		tree.setCellRenderer(new DefaultTreeCellRenderer()
		{
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				Object val = ((DefaultMutableTreeNode)value).getUserObject();
				if (val instanceof X509Certificate)
				{
					String subject = ((X509Certificate)val).getSubjectDN().getName();
					int index1 = subject.indexOf("CN=");
					int index2 = subject.indexOf(',', index1 + 3);
					if (index1 != -1 && index2 != -1)
					{
						subject = subject.substring(index1 + 3, index2);
					}
					val = subject;
				}
				return super.getTreeCellRendererComponent(tree, val, sel, expanded, leaf, row, hasFocus);
			}
		});
		tree.expandPath(path);
		tree.addTreeSelectionListener(new TreeSelectionListener()
		{
			@Override
			public void valueChanged(TreeSelectionEvent e)
			{
				DefaultMutableTreeNode selected = (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
				if (selected != null)
				{
					X509Certificate certificate = (X509Certificate)selected.getUserObject();
					StringBuilder sb = new StringBuilder();
					sb.append("Subject:\t\t");
					sb.append(certificate.getSubjectDN());
					sb.append('\n');
					sb.append("Issuer:\t\t");
					sb.append(certificate.getIssuerDN());
					sb.append('\n');
					sb.append("Validity Form:\t");
					sb.append(certificate.getNotBefore());
					sb.append(", To: ");
					sb.append(certificate.getNotAfter());
					sb.append('\n');
					sb.append("Version:\t\t");
					sb.append(certificate.getVersion());
					sb.append('\n');
					sb.append("SerialNumber:\t");
					sb.append(certificate.getSerialNumber());
					sb.append('\n');
					sb.append("Signature Algorithm:");
					sb.append(certificate.getSigAlgName());
					sb.append('\n');
					sb.append("Signature:\t");
					sb.append(bytesToHex(certificate.getSignature()));
					sb.append('\n');

					textArea.setText(sb.toString());
				}
				else
				{
					textArea.setText("");
				}
			}

			public String bytesToHex(byte[] bytes)
			{
				final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
				char[] hexChars = new char[bytes.length * 2];
				int v;
				for (int j = 0; j < bytes.length; j++)
				{
					v = bytes[j] & 0xFF;
					hexChars[j * 2] = hexArray[v >>> 4];
					hexChars[j * 2 + 1] = hexArray[v & 0x0F];
				}
				return new String(hexChars);
			}
		});
		textArea = new TextArea();
		textArea.setEditable(false);
		add(new JScrollPane(tree), BorderLayout.WEST);
		add(new JScrollPane(textArea), BorderLayout.EAST);

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CertificateDialog.this.setVisible(false);
			}
		});
		JButton accept = new JButton("Accept");
		accept.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CertificateDialog.this.accept = true;
				CertificateDialog.this.setVisible(false);
			}
		});
		panel.add(accept);
		panel.add(cancel);
		add(panel, BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(owner);

	}

	public boolean shouldAccept()
	{
		setVisible(true);
		return accept;
	}
}
