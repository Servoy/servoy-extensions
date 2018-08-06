/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2012 Servoy BV

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

package com.servoy.extensions.plugins.headlessclient;

import java.util.ArrayList;
import java.util.List;

import com.servoy.j2db.util.Debug;

/**
 * Class that dispatches a call to the correct instance of server plugin (in case of clustering).
 * @author acostescu
 */
public class ServerPluginDispatcher<E>
{

	/**
	 * Classes that implement this interface will be in cluster shared memory - so instrument them with Terracotta correctly.
	 * @author acostescu
	 *
	 * @param <E> the object of target server that a call needs to use in order to execute.
	 */
	public static interface Call<E, R>
	{
		/**
		 * Return type, and the exception
		 */
		R executeCall(E correctServerObject) throws Exception;
	}

	/**
	 * Children this class will be in cluster shared memory.
	 * @author acostescu
	 */
	private static class CallWrapper<E, R>
	{
		private final Call<E, R> call;
		private String exceptionMsg = null;
		private RuntimeException exception = null;
		private R result = null;

		public CallWrapper(Call<E, R> call)
		{
			this.call = call;
		}

		public void executeCall(E correctServerObject)
		{
			synchronized (this) // Terracotta WRITE lock
			{
				try
				{
					result = call.executeCall(correctServerObject);
				}
				catch (ExceptionWrapper e)
				{
					exception = e;
				}
				catch (ClientNotFoundException e)
				{
					exception = e;
				}
				catch (Throwable e)
				{
					// we can't be sure that this exception's class is instrumented and can be shared with terracotta DSO
					Debug.warn(e);
					exceptionMsg = e.getClass().getCanonicalName() + ": " + e.getMessage(); //$NON-NLS-1$
				}
				finally
				{
					notifyAll(); // tell calling server plugin that he's got a value
				}
			}
		}
	}

	private final List<CallWrapper<E, ? >> callQueueOfThisPlugin; // cache to avoid one more unnecessary readLock on clusterWideCallQueue when dispatching messages
	private final E thisServerObject;

	public ServerPluginDispatcher(E thisServerObject)
	{

		this.thisServerObject = thisServerObject;

		callQueueOfThisPlugin = new ArrayList<CallWrapper<E, ? >>();
	}

	/**
	 * Should only be called on/by the Servoy server this object originated from. (as 'stop' is terracotta transient)
	 */
	public void shutdown()
	{
		synchronized (callQueueOfThisPlugin) // Terracotta WRITE lock (for notify)
		{
			callQueueOfThisPlugin.notifyAll();
		}
	}

	public <R> void callOnAllServers(Call<E, R> call)
	{
		CallWrapper<E, R> callWrapper = new CallWrapper<E, R>(call);
		callWrapper.executeCall(thisServerObject);
	}

	/**
	 * @return if waitForExecution is false it will return null, otherwise it will wait for the method to get called and return it's result.
	 */
	public <R> R callOnCorrectServer(Call<E, R> call)
	{
		CallWrapper<E, R> callWrapper = new CallWrapper<E, R>(call);

		callWrapper.executeCall(thisServerObject);

		if (callWrapper.exception != null)
		{
			throw callWrapper.exception;
		}
		else if (callWrapper.exceptionMsg != null)
		{
			throw new RuntimeException(callWrapper.exceptionMsg);
		}
		return callWrapper.result;
	}

}