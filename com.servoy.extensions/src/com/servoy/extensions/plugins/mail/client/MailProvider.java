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
package com.servoy.extensions.plugins.mail.client;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import com.servoy.extensions.plugins.mail.IMailService;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.scripting.annotations.JSReadonlyProperty;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author jblok
 */
@ServoyDocumented(publicName = MailPlugin.PLUGIN_NAME, scriptingName = "plugins." + MailPlugin.PLUGIN_NAME)
public class MailProvider implements IReturnedTypesProvider, IScriptable, IJavaScriptType
{
	private final MailPlugin plugin;
	private IMailService mailService = null;
	private String sendMailException;

	MailProvider(MailPlugin plugin)
	{
		this.plugin = plugin;
	}

	// default constructor needed for documentation
	public MailProvider()
	{
		this.plugin = null;
	}

	/**
	 * @deprecated Replaced by {@link #receiveMail(Object[])}
	 */
	@Deprecated
	public MailMessage[] js_recieveMail(Object[] args)
	{
		//3 args are required
		if (args == null || args.length < 3) return null;
		for (int i = 0; i < 3; i++)
		{
			if (args[i] == null) return null;
		}
		String userName = args[0].toString();
		String password = args[1].toString();
		boolean leaveMsgsOnServer = Utils.getAsBoolean(args[2]);

		int receiveMode = 0;//0 is all
		if (args.length >= 4 && args[3] != null) receiveMode = Utils.getAsInteger(args[3]);
		Date onlyreceiveMsgWithSentDate = null;//null is all
		if (args.length >= 5 && args[4] != null && args[4] instanceof Date) onlyreceiveMsgWithSentDate = (Date)args[4];
		String[] overrideProperties = null;
		if (args.length >= 6 && args[5] != null)
		{
			if (args[5] instanceof Object[])
			{
				Object[] array = (Object[])args[5];
				overrideProperties = new String[array.length];
				System.arraycopy(array, 0, overrideProperties, 0, overrideProperties.length);
			}
			else if (args[5] instanceof String)
			{
				overrideProperties = new String[] { "mail.pop3.host=" + args[5] }; //$NON-NLS-1$
			}
		}
		return receiveMail(userName, password, Boolean.valueOf(leaveMsgsOnServer), Integer.valueOf(receiveMode), onlyreceiveMsgWithSentDate,
			overrideProperties);
	}

	/**
	 * Receive mails from pop3 account.
	 *
	 * @sample
	 * var msgs = plugins.mail.receiveMail('mylogin', 'secretpass',  true);
	 * if (msgs != null) //if is null error occurred!
	 * {
	 * 	for (var i = 0 ; i < msgs.length ; i++)
	 * 	{
	 * 		var msg = msgs[i]
	 * 		application.output(msg.getFromAddresses())
	 * 		application.output(msg.getRecipientAddresses())
	 * 		application.output(msg.getReplyAddresses())
	 * 		application.output(msg.getSentDate())
	 * 		application.output(msg.getHeaders())
	 * 		application.output(msg.getSubject())
	 * 		application.output(msg.getHtmlMsg())
	 * 		application.output(msg.getPlainMsg())
	 * 		var attachments = msg.getAttachments()
	 * 		if (attachments != null)
	 * 		{
	 * 			for (var j = 0 ; j < attachments.length ; j++)
	 * 			{
	 * 				var attachment = attachments[j]
	 * 				application.output(attachment.getName())
	 * 				var attachmentDataByteArray = attachment.getData()
	 * 				//write attachmentDataByteArray to a file...
	 * 			}
	 * 		}
	 * 	}
	 * }
	 *
	 *
	 * @param username
	 * @param password
	 * @param leaveMsgsOnServer
	 */
	public MailMessage[] js_receiveMail(String username, String password, Boolean leaveMsgsOnServer)
	{
		return receiveMail(username, password, leaveMsgsOnServer, Integer.valueOf(0), null, null);
	}

	/**
	 * Receive mails from pop3 account.
	 *
	 * @sample
	 * var receiveMode = 1;//0=FULL,1=HEADERS_ONLY,2=NO_ATTACHMENTS
	 * var msgs = plugins.mail.receiveMail('mylogin', 'secretpass',  true,  0);
	 * if (msgs != null) //if is null error occurred!
	 * {
	 * 	for (var i = 0 ; i < msgs.length ; i++)
	 * 	{
	 * 		var msg = msgs[i]
	 * 		application.output(msg.getFromAddresses())
	 * 		application.output(msg.getRecipientAddresses())
	 * 		application.output(msg.getReplyAddresses())
	 * 		application.output(msg.getSentDate())
	 * 		application.output(msg.getHeaders())
	 * 		application.output(msg.getSubject())
	 * 	}
	 * }
	 *
	 * @param username
	 * @param password
	 * @param leaveMsgsOnServer
	 * @param receiveMode
	 */
	public MailMessage[] js_receiveMail(String username, String password, Boolean leaveMsgsOnServer, Number receiveMode)
	{
		return receiveMail(username, password, leaveMsgsOnServer, receiveMode, null, null);
	}

	/**
	 * Receive mails from pop3 account.
	 *
	 * @sample
	 * //it is also possible to first receive the headers and later receive a full message with particular 'sentdate'
	 * //var receiveMode = 1;//0=FULL,1=HEADERS_ONLY,2=NO_ATTACHMENTS
	 * var msgs = plugins.mail.receiveMail('mylogin', 'secretpass',  true,  0,  theSentDateObjectFormPreviousHeaderLoading);
	 * if (msgs != null) //if is null error occurred!
	 * {
	 * 	for (var i = 0 ; i < msgs.length ; i++)
	 * 	{
	 * 		var msg = msgs[i]
	 * 		application.output(msg.getFromAddresses())
	 * 		application.output(msg.getRecipientAddresses())
	 * 		application.output(msg.getReplyAddresses())
	 * 		application.output(msg.getSentDate())
	 * 		application.output(msg.getHeaders())
	 * 		application.output(msg.getSubject())
	 * 	}
	 * }
	 *
	 * @param username
	 * @param password
	 * @param leaveMsgsOnServer
	 * @param receiveMode
	 * @param onlyReceiveMsgWithSentDate
	 */
	public MailMessage[] js_receiveMail(String username, String password, Boolean leaveMsgsOnServer, Number receiveMode, Date onlyReceiveMsgWithSentDate)
	{
		return receiveMail(username, password, leaveMsgsOnServer, receiveMode, onlyReceiveMsgWithSentDate, null);
	}

	/**
	 * Receive mails from pop3 account.
	 *
	 * @sample
	 * //it is also possible to first receive the headers and later receive a full message
	 * var receiveMode = 0;//0=FULL,1=HEADERS_ONLY,2=NO_ATTACHMENTS
	 * var pop3Host = 'myserver.com';
	 * var msgs = plugins.mail.receiveMail('mylogin', 'secretpass',  true,  receiveMode,  null, pop3Host);
	 * if (msgs != null) //if is null error occurred!
	 * {
	 * 	for (var i = 0 ; i < msgs.length ; i++)
	 * 	{
	 * 		var msg = msgs[i]
	 * 		application.output(msg.getFromAddresses())
	 * 		application.output(msg.getRecipientAddresses())
	 * 		application.output(msg.getReplyAddresses())
	 * 		application.output(msg.getSentDate())
	 * 		application.output(msg.getHeaders())
	 * 		application.output(msg.getSubject())
	 * 		application.output(msg.getHtmlMsg())
	 * 		application.output(msg.getPlainMsg())
	 * 		var attachments = msg.getAttachments()
	 * 		if (attachments != null)
	 * 		{
	 * 			for (var j = 0 ; j < attachments.length ; j++)
	 * 			{
	 * 				var attachment = attachments[j]
	 * 				application.output(attachment.getName())
	 * 				var attachmentDataByteArray = attachment.getData()
	 * 				//write attachmentDataByteArray to a file...
	 * 			}
	 * 		}
	 * 	}
	 * }
	 *
	 * @param username
	 * @param password
	 * @param leaveMsgsOnServer
	 * @param receiveMode
	 * @param onlyReceiveMsgWithSentDate
	 * @param pop3Host
	 */
	public MailMessage[] js_receiveMail(String username, String password, Boolean leaveMsgsOnServer, Number receiveMode, Date onlyReceiveMsgWithSentDate,
		String pop3Host)
	{
		String[] overrideProperties = null;
		if (pop3Host != null)
		{
			overrideProperties = new String[] { "mail.pop3.host=" + pop3Host }; //$NON-NLS-1$
		}
		return receiveMail(username, password, leaveMsgsOnServer, receiveMode, onlyReceiveMsgWithSentDate, overrideProperties);
	}

	/**
	 * Receive mails from pop3 account.
	 *
	 * @sample
	 * var receiveMode = 1;//0=FULL,1=HEADERS_ONLY,2=NO_ATTACHMENTS
	 *
	 * var properties = new Array();
	 * properties[0] = 'mail.pop3.port=995';
	 * properties[1] = 'mail.pop3.ssl.enable=true';
	 * properties[2] = 'mail.pop3.host=myserver.com';
	 * properties[3] = 'mail.pop3.user=user@myserver.com';
	 *
	 * var msgs = plugins.mail.receiveMail('mylogin', 'secretpass',  true,  receiveMode,  null, properties);
	 * if (msgs != null) //if is null error occurred!
	 * {
	 * 	for (var i = 0 ; i < msgs.length ; i++)
	 * 	{
	 * 		var msg = msgs[i]
	 * 		application.output(msg.getFromAddresses())
	 * 		application.output(msg.getRecipientAddresses())
	 * 		application.output(msg.getReplyAddresses())
	 * 		application.output(msg.getSentDate())
	 * 		application.output(msg.getHeaders())
	 * 		application.output(msg.getSubject())
	 * 	}
	 * }
	 *
	 * @param username
	 * @param password
	 * @param leaveMsgsOnServer
	 * @param receiveMode
	 * @param onlyReceiveMsgWithSentDate
	 * @param properties
	 */
	public MailMessage[] js_receiveMail(String username, String password, Boolean leaveMsgsOnServer, Number receiveMode, Date onlyReceiveMsgWithSentDate,
		String[] properties)
	{
		return receiveMail(username, password, leaveMsgsOnServer, receiveMode, onlyReceiveMsgWithSentDate, properties);
	}

	private MailMessage[] receiveMail(String username, String password, Boolean leaveMsgsOnServer, Number receiveMode, Date onlyReceiveMsgWithSentDate,
		String[] properties)
	{
		boolean _leaveMsgsOnServer = (leaveMsgsOnServer == null ? true : leaveMsgsOnServer.booleanValue());
		int _receiveMode = (receiveMode == null ? 0 : receiveMode.intValue());

		if (username == null || password == null) return null;

		//create if not yet created
		createMailService();
		//incase the server is not started in developer
		if (mailService != null)
		{
			//receive mail
			try
			{
				return mailService.receiveMail(plugin.getClientPluginAccess().getClientID(), username, password, _leaveMsgsOnServer, _receiveMode,
					onlyReceiveMsgWithSentDate, properties);
			}
			catch (Exception e)
			{
				Debug.error(e);
				return null;
			}
		}
		else
		{
			return null;//Todo throw app execption here? with "mail server not running"?
		}
	}


	/**
	 * Helper method to only get the plain addresses.
	 *
	 * @sample
	 * var plainArray = plugins.mail.getPlainMailAddresses('John Cobb <from_me@example.com>,Pete Cobb<from_pete@example.com>');
	 * application.output(plainArray[0]) //will return 'from_me@example.com'
	 *
	 * @param addressesString
	 */
	public String[] js_getPlainMailAddresses(String addresses)
	{
		if (addresses == null) return new String[0];

		List<String> retval = new ArrayList<String>();
		StringTokenizer tk = new StringTokenizer(addresses.toLowerCase(), " ;,<>[]()'\":\n\t\r"); //$NON-NLS-1$
		while (tk.hasMoreTokens())
		{
			String token = tk.nextToken();
			if (token.indexOf('@') != -1) retval.add(token);
		}

		return retval.toArray(new String[retval.size()]);
	}

	/**
	 * Checks whether the given e-mail address is valid or not.
	 *
	 * @sample
	 * plugins.mail.isValidEmailAddress("me@example.com");
	 *
	 * @param email
	 */
	public boolean js_isValidEmailAddress(String email)
	{
		return Utils.isValidEmailAddress(email);
	}

	private void createMailService()
	{
		if (mailService == null)
		{
			try
			{
				IClientPluginAccess access = plugin.getClientPluginAccess();
				mailService = (IMailService)access.getRemoteService(IMailService.class.getName());
			}
			catch (Exception ex)
			{
				Debug.error(ex);
			}
		}
	}

	/**
	 * Helper method, returns MailMessage object from binary or 7bits string.
	 *
	 * @sample
	 * var msg = plugins.mail.getMailMessage(myBlob);
	 * if (msg != null) //if is null error occurred!
	 * {
	 * 	application.output(msg.getFromAddresses())
	 * }
	 *
	 * @param binaryblob/string
	 */
	public MailMessage js_getMailMessage(Object data)
	{
		if (data == null) return null;

		//create if not yet created
		createMailService();

		//incase the server is not started in developer
		if (mailService != null)
		{
			try
			{
				if (data.getClass().isArray())
				{
					return mailService.createMailMessageFromBinary(plugin.getClientPluginAccess().getClientID(), (byte[])data);
				}
				else if (data instanceof String)
				{
					return mailService.createMailMessageFromBinary(plugin.getClientPluginAccess().getClientID(), ((String)data).getBytes());
				}
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
		}
		else
		{
			return null;//Todo throw app execption here? with "mail server not running"?
		}
		return null;
	}

	/**
	 * Creates a binary attachment object.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('logo1.gif',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createBinaryAttachment('logo2.gif',plugins.file.readFile('c:/temp/another_logo.gif'));
	 * var success = plugins.mail.sendMail('to_someone@example.com', 'John Cobb <from_me@example.org>', 'subject', 'msgText',null,null,new Array(attachment1,attachment2));
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param filename
	 * @param binarydata
	 */
	public Attachment js_createBinaryAttachment(String filename, byte[] binarydata)
	{
		if (filename == null || binarydata == null) return null;
		return new Attachment(filename, binarydata);
	}

	/**
	 * Creates a binary attachment object.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('logo1.gif',plugins.file.readFile('c:/temp/a_logo.gif', 'image/gif'));
	 * var attachment2 = plugins.mail.createBinaryAttachment('logo2.gif',plugins.file.readFile('c:/temp/another_logo.gif', 'image/gif'));
	 * var success = plugins.mail.sendMail('to_someone@example.com', 'John Cobb <from_me@example.org>', 'subject', 'msgText',null,null,new Array(attachment1,attachment2));
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param filename
	 * @param binarydata
	 * @param mimeType
	 */
	public Attachment js_createBinaryAttachment(String filename, byte[] binarydata, String mimeType)
	{
		if (filename == null || binarydata == null) return null;
		return new Attachment(filename, binarydata, mimeType);
	}

	/**
	 * Creates a text based attachment objec with the default 'text/plain' mimetype
	 *
	 * @sample
	 * var attachment = plugins.mail.createTextAttachment('readme.html','<html>bla bla bla');
	 * var success = plugins.mail.sendMail('to_someone@example.com', 'John Cobb <from_me@example.com>', 'subject', 'msgText',null,null,attachment);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param filename
	 * @param textdata
	 */
	public Attachment js_createTextAttachment(String filename, String textdata)
	{
		if (filename == null || textdata == null) return null;
		return js_createBinaryAttachment(filename, textdata.getBytes(), "text/plain");
	}

	/**
	 * Creates a text based attachment object.
	 *
	 * @sample
	 * var attachment = plugins.mail.createTextAttachment('readme.html','<html>bla bla bla', 'text/html');
	 * var success = plugins.mail.sendMail('to_someone@example.com', 'John Cobb <from_me@example.com>', 'subject', 'msgText',null,null,attachment);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param filename
	 * @param textdata
	 * @param mimeType
	 */
	public Attachment js_createTextAttachment(String filename, String textdata, String mimeType)
	{
		if (filename == null || textdata == null) return null;
		return js_createBinaryAttachment(filename, textdata.getBytes(), mimeType);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>,replyTo@example.com', 'subject', msgText);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText)
	{
		return sendMail(to, from, subject, msgText, null, null, null, null);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,'cc1@example.com,cc2@example.com');
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc)
	{
		return sendMail(to, from, subject, msgText, cc, null, null, null);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'bcc1@example.com');
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, null, null);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'bcc1@example.com,bcc2@example.com',[attachment1,attachment2]);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, attachments, null);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * //it is possbile to set all kind of smtp properties
	 * var properties = new Array()
	 * properties[0] = 'mail.smtp.host=myserver.com'
	 * // properties specification can be found at:https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',[attachment1,attachement2],properties);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 * @param overrideProperties An array of properties
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, attachments, overrideProperties);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var smtphost = 'myserver.com';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',[attachment1,attachement2],smtphost);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 * @param smtpHost The smtp host
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments, String smtpHost)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, attachments, getOverrideProperties(smtpHost));
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var attachment = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'bcc1@example.com,bcc2@example.com',attachment);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachment A single attachment
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment attachment)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, getAttachments(attachment), null);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var attachment = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * //it is possbile to set all kind of smtp properties
	 * var properties = new Array()
	 * properties[0] = 'mail.smtp.host=myserver.com'
	 * // properties specification can be found at:https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',attachment,properties);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachment A single attachment
	 * @param overrideProperties An array of properties
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment attachment,
		String[] overrideProperties)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, getAttachments(attachment), overrideProperties);
	}

	/**
	 * Send a mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * If you want to have a different reply address than from, you can specify this with the from parameter by adding another email after it.
	 *
	 * @sample
	 * var attachment = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var smtphost = 'myserver.com';
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',attachment,smtphost);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachment A single attachment
	 * @param smtpHost The smtp host
	 */
	public boolean js_sendMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment attachment, String smtpHost)
	{
		return sendMail(to, from, subject, msgText, cc, bcc, getAttachments(attachment), getOverrideProperties(smtpHost));
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>,replyTo@example.com', 'subject', msgText);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText)
	{
		return sendBulkMail(to, from, subject, msgText, null, null, null, null);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,'cc1@example.com,cc2@example.com');
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc)
	{
		return sendBulkMail(to, from, subject, msgText, cc, null, null, null);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'bcc1@example.com');
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, null, null);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'bcc1@example.com,bcc2@example.com',[attachment1,attachment2]);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, attachments, null);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * //it is possbile to set all kind of smtp properties
	 * var properties = new Array()
	 * properties[0] = 'mail.smtp.host=myserver.com'
	 * // properties specification can be found at:https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',[attachment1,attachement2],properties);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 * @param overrideProperties An array of properties
	 * @deprecated replaced with plugins.mail.sendBulkMail(to, from, subject, msgText, cc, bcc, attachments, overrideProperties)
	 */
	@Deprecated
	public boolean js_senBulkdMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, attachments, overrideProperties);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * //it is possbile to set all kind of smtp properties
	 * var properties = new Array()
	 * properties[0] = 'mail.smtp.host=myserver.com'
	 * // properties specification can be found at:https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',[attachment1,attachement2],properties);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 * @param overrideProperties An array of properties
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, attachments, overrideProperties);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment1 = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var attachment2 = plugins.mail.createTextAttachment('embedded','A text attachement');
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var smtphost = 'myserver.com';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',[attachment1,attachement2],smtphost);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachments The attachments
	 * @param smtpHost The smtp host
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment[] attachments, String smtpHost)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, attachments, getOverrideProperties(smtpHost));
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'bcc1@example.com,bcc2@example.com',attachment);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachment A single attachment
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment attachment)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, getAttachments(attachment), null);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * //it is possbile to set all kind of smtp properties
	 * var properties = new Array()
	 * properties[0] = 'mail.smtp.host=myserver.com'
	 * // properties specification can be found at:https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',attachment,properties);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachment A single attachment
	 * @param overrideProperties An array of properties
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment attachment,
		String[] overrideProperties)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, getAttachments(attachment), overrideProperties);
	}

	/**
	 * Send a bulk mail, if you make the msgText start with <html> the message will be sent in html (and you can use all html formatting).
	 * A bulk email makes it possible for one to not receive "out of office" emails back from receiver.
	 *
	 * @sample
	 * var attachment = plugins.mail.createBinaryAttachment('embedded',plugins.file.readFile('c:/temp/a_logo.gif'));
	 * var msgText = 'plain msg<html>styled html msg<img src="%%embedded%%"></html>';
	 * var smtphost = 'myserver.com';
	 * var success = plugins.mail.sendBulkMail('to_someone@example.com,to_someone_else@example.net', 'John Cobb <from_me@example.com>', 'subject', msgText,null,'unnamed@example.com',attachment,smtphost);
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert','Failed to send bulk mail','OK');
	 * }
	 *
	 * @param to A string containing 1 or multiple addresses separated by a comma.
	 * @param from A string containing an address and optional reply addresses, separated by commas.
	 * @param subject The subject of the bulk mail
	 * @param msgText The message text
	 * @param cc One or more addresses separated by a comma
	 * @param bcc One or more addresses separated by a comma
	 * @param attachment A single attachment
	 * @param smtpHost The smtp host
	 */
	public boolean js_sendBulkMail(String to, String from, String subject, String msgText, String cc, String bcc, Attachment attachment, String smtpHost)
	{
		return sendBulkMail(to, from, subject, msgText, cc, bcc, getAttachments(attachment), getOverrideProperties(smtpHost));
	}

	/**
	 * @param smtpHost
	 * @return
	 */
	private String[] getOverrideProperties(String smtpHost)
	{
		String[] overrideProperties = null;
		if (smtpHost != null)
		{
			overrideProperties = new String[] { "mail.smtp.host=" + smtpHost }; //$NON-NLS-1$
		}
		return overrideProperties;
	}

	/**
	 * @param attachment
	 * @return
	 */
	private Attachment[] getAttachments(Attachment attachment)
	{
		if (attachment != null)
		{
			Attachment[] attachments = new Attachment[1];
			attachments[0] = attachment;
			return attachments;
		}
		return null;
	}

	public boolean sendMail(String to, String fromAndReply, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties)
	{
		sendMailException = null;
		if (to == null || subject == null || msgText == null) return false;

		//create if not yet created
		createMailService();

		//incase the server is not started in developer
		if (mailService != null)
		{
			//send mail
			try
			{
				mailService.sendMail(plugin.getClientPluginAccess().getClientID(), to, fromAndReply, subject, msgText, cc, bcc, attachments,
					overrideProperties);
				return true;
			}
			catch (Exception mex)
			{
				Debug.error(mex);
				sendMailException = mex.getMessage();
				if (mex.getCause() != null)
				{
					String nested = mex.getCause().getMessage();
					if (!("".equals(nested))) sendMailException = sendMailException + "; " + nested; //$NON-NLS-1$ //$NON-NLS-2$
				}
				return false;
			}
		}
		else
		{
			return false;//Todo throw app execption here? with "mail server not running"?
		}
	}

	public boolean sendBulkMail(String to, String fromAndReply, String subject, String msgText, String cc, String bcc, Attachment[] attachments,
		String[] overrideProperties)
	{
		sendMailException = null;
		if (to == null || subject == null || msgText == null) return false;

		//create if not yet created
		createMailService();

		//incase the server is not started in developer
		if (mailService != null)
		{
			//send mail
			try
			{
				mailService.sendBulkMail(plugin.getClientPluginAccess().getClientID(), to, fromAndReply, subject, msgText, cc, bcc, attachments,
					overrideProperties);
				return true;
			}
			catch (Exception mex)
			{
				Debug.error(mex);
				sendMailException = mex.getMessage();
				if (mex.getCause() != null)
				{
					String nested = mex.getCause().getMessage();
					if (!("".equals(nested))) sendMailException = sendMailException + "; " + nested; //$NON-NLS-1$ //$NON-NLS-2$
				}
				return false;
			}
		}
		else
		{
			return false;//Todo throw app execption here? with "mail server not running"?
		}
	}


	/**
	 * @deprecated Replaced by {@link #getLastSendMailExceptionMsg()}
	 */
	@Deprecated
	public String js_getLastSendMailException()
	{
		return js_getLastSendMailExceptionMsg();
	}

	/**
	 * Get the exception that occurred in the last sendMail attempt (null if no exception occurred).
	 *
	 * @sample
	 * var success = plugins.mail.sendMail('to_someone@example.com,to_someone_else@example.org', 'John Cobb <from_me@example.com>', 'subject', 'my message',null,'unnamed@example.com');
	 * if (!success)
	 * {
	 * 	plugins.dialogs.showWarningDialog('Alert',plugins.mail.getLastSendMailExceptionMsg(),'OK');
	 * }
	 */
	public String js_getLastSendMailExceptionMsg()
	{
		return sendMailException;
	}

	/**
	 * Returns the value of the mail.from property which is set on the admin page.
	 * @sample
	 * var from = plugins.mail.from
	 */
	@JSReadonlyProperty
	public String getFrom()
	{
		createMailService();
		if (mailService != null)
		{
			try
			{
				return mailService.getPluginProperty("mail.from");
			}
			catch (RemoteException e)
			{
				Debug.error(e);
			}
		}
		return null;
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { MailMessage.class, Attachment.class };
	}
}
