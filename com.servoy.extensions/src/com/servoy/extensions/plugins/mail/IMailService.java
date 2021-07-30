/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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
package com.servoy.extensions.plugins.mail;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;

import com.servoy.extensions.plugins.mail.client.Attachment;
import com.servoy.extensions.plugins.mail.client.MailMessage;

/**
 * RMI interface
 * @author jblok
 */
public interface IMailService extends Remote
{
	@Deprecated
	public static final String SERVICE_NAME = IMailService.class.getName();

	public static final int FULL = 0;//recieveMode
	public static final int HEADERS_ONLY = 1;//recieveMode
	public static final int NO_ATTACHMENTS = 2;//recieveMode

	public void sendMail(String clientId, String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties) throws RemoteException, Exception;

	/**
	 * Same as sendMail but marked as buld, so no "out of office" replies are sent
	 */
	public void sendBulkMail(String clientId, String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties) throws RemoteException, Exception;

	public MailMessage[] receiveMail(String clientId, String userName, String password, boolean leaveMsgsOnServer, int recieveMode,
		Date onlyRecieveMsgWithSentDate, String[] overrideProperties) throws RemoteException;

	public MailMessage createMailMessageFromBinary(String clientId, byte[] data) throws RemoteException;

	public String getPluginProperty(String propertyName);
}
