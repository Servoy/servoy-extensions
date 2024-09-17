package com.servoy.extensions.plugins.clientmanager;

import java.util.Date;

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.server.shared.IClientInformation;

/*
 * A scripting object containing Client information.
 */
@ServoyDocumented
@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
public class JSClientInformation implements IScriptable
{
	private final IClientInformation clientInformation;

	// only used by script engine.
	public JSClientInformation()
	{
		this.clientInformation = null;
	}

	public JSClientInformation(IClientInformation clientInformation)
	{
		this.clientInformation = clientInformation;
	}

	/**
	 * The type of the application started by this client.
	 *
	 * @sample
	 * var clients = plugins.clientmanager.getConnectedClients();
	 * application.output('There are ' + clients.length + ' connected clients.');
	 * for (var i = 0; i < clients.length; i++)
	 * {
	 * 	var client = clients[i];
	 * 	application.output('Client details:');
	 * 	application.output('	ID: ' + client.getClientID());
	 * 	application.output('	Application type: ' + client.getApplicationType());
	 * 	application.output('	Host address: ' + client.getHostAddress());
	 * 	application.output('	Host identifier: ' + client.getHostIdentifier());
	 * 	application.output('	Host name: ' + client.getHostName());
	 * 	application.output('	User name: ' + client.getUserName());
	 * 	application.output('	Used UID: ' + client.getUserUID());
	 * 	application.output('	Open solution: ' + client.getOpenSolutionName());
	 * 	application.output('	User login time and date: ' + client.getLoginTime());
	 * 	application.output('	User idle since: ' + client.getIdleTime());
	 * application.output('	Status line: ' + client.getStatusLine());
	 * }
	 *
	 * @see com.servoy.j2db.scripting.info.APPLICATION_TYPES
	 */
	public int js_getApplicationType()
	{
		return clientInformation.getApplicationType();
	}

	/**
	 * The ID of this client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getClientID()
	{
		return clientInformation.getClientID();
	}

	/**
	 * The time and date the user logged into the system.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public Date js_getLoginTime()
	{
		return clientInformation.getLoginTime();
	}

	/**
	 * The time and date since the user has been idle.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public Date js_getIdleTime()
	{
		return clientInformation.getIdleTime();
	}

	/**
	 * The name of the solution that is currently open by the client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getOpenSolutionName()
	{
		return clientInformation.getOpenSolutionName();
	}

	/**
	 * @deprecated As of release 5.2, replaced by {@link #getClientID()}.
	 */
	@Deprecated
	public String js_getClientId()
	{
		return js_getClientID();
	}

	/**
	 * The host address of this client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getHostAddress()
	{
		return clientInformation.getHostAddress();
	}

	/**
	 * The host identifier of this client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getHostIdentifier()
	{
		return clientInformation.getHostIdentifier();
	}

	/**
	 * The host name of this client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getHostName()
	{
		return clientInformation.getHostName();
	}

	/**
	 * The name of the user who is logged in at this client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getUserName()
	{
		return clientInformation.getUserName();
	}

	/**
	 * The ID of the user who is logged in at this client.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getUserUID()
	{
		return clientInformation.getUserUID();
	}

	/**
	 * Gets the array of client information strings as seen on the admin page.
	 * @return a String array with the client information
	 */
	@JSFunction
	public String[] getClientInfos()
	{
		return clientInformation.getClientInfos();
	}

	/**
	 * @deprecated As of release 5.2, replaced by {@link #getUserUID()}.
	 */
	@Deprecated
	public String js_getUserUid()
	{
		return clientInformation.getUserUID();
	}

	/**
	 * Gets the last date and time when a user has physically accessed the application. NGClient only!
	 * @return a date object or null if the client doesn't support this
	 *
	 * @sampleas js_getApplicationType()
	 */
	public Date js_getLastAccessedTime()
	{
		return clientInformation.getLastAccessedTime();
	}

	/**
	 * This returns the status line of a NGClient (other clients don't have a value for this)
	 *
	 * @return a string that is the status line as reported on the admin page.
	 *
	 * @sampleas js_getApplicationType()
	 */
	public String js_getStatusLine()
	{
		return clientInformation.getStatusLine();
	}

	@SuppressWarnings("nls")
	@Override
	public String toString()
	{
		return "JSClientInformation [" + clientInformation + "]";
	}

}
