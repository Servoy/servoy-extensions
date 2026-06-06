/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2011 Servoy BV

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

package com.servoy.extensions.plugins.workflow.client;

import java.rmi.RemoteException;
import java.util.Map;

import com.servoy.extensions.plugins.workflow.shared.TaskData;
import com.servoy.extensions.workflow.api.IWorkflowPluginService;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.serialize.MapSerializer;

/**
 * The plugin api to control the workflow actions from a servoy client
 * 
 * @author jblok
 */
@ServoyDocumented(publicName = WorkflowPlugin.PLUGIN_NAME, scriptingName = "plugins." + WorkflowPlugin.PLUGIN_NAME)
public class WorkflowProvider implements IScriptable, IReturnedTypesProvider
{
	private WorkflowPlugin plugin;
	private IWorkflowPluginService _workflowService;
	
	public WorkflowProvider(WorkflowPlugin workflowPlugin) 
	{
		plugin = workflowPlugin;
	}
	
	private IWorkflowPluginService getWorkflowService()
	{
		if (_workflowService == null)
		{
			try
			{
				IClientPluginAccess access = plugin.getClientPluginAccess();
				_workflowService = (IWorkflowPluginService)access.getRemoteService(IWorkflowPluginService.class.getName()); //$NON-NLS-1$
			}
			catch (Exception ex)
			{
				Debug.error(ex);
			}
		}
		return _workflowService;
	}
	
	public String js_addProcessDefinition(String content)
	{
		try
		{
			return getWorkflowService().addProcessDefinition(content);
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
			return null;
		}
	}
	
//	public void js_removeProcessDefinition()
//	{
//		
//	}
	
	public String js_startProcess(String processName,String solutionName,Object jsVariablesObject)
	{
		try
		{
			Map<String,Object> variables = MapSerializer.convertToMap(jsVariablesObject);
			return getWorkflowService().startProcess(processName,solutionName,variables);
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
			return null;
		}
	}

	@Deprecated
	public void js_terminateProcess(String executionId)
	{
		try
		{
			getWorkflowService().terminateProcess(executionId);
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
		}
	}

//	public JSProcess js_getProcess(String processName)
//	{
//		String pid = workflowService.getProcess(processName);
//		return new JSProcess(pid);
//	}
	
	public JSTask[] js_getUserTasks(String username)
	{
		try
		{
			TaskData[] tds = getWorkflowService().getUserTasks(username);
			return getTasks(tds);
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
			return null;
		}
	}
	
	private JSTask[] getTasks(TaskData[] tds)
	{
		JSTask[] tasks = new JSTask[tds.length];
		for (int i = 0; i < tds.length; i++) 
		{
			tasks[i] = new JSTask(getWorkflowService(),tds[i]);
		}
		return tasks;
	}

	public JSTask[] js_getGroupTasks(String username)
	{
		try
		{
			TaskData[] tds = getWorkflowService().getGroupTasks(username);
			return getTasks(tds);
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
			return null;
		}
	}

	public boolean js_addMailTemplate(String templateName,String subject,String msgText)
	{
		try
		{
			getWorkflowService().addMailTemplate(templateName, subject, msgText);
			return true;
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
			return false;
		}
	}
	
	public String[] js_getMailTemplate(String templateName)
	{
		try
		{
			Pair<String,String> subj_msg = getWorkflowService().getMailTemplate(templateName);
			if (subj_msg != null) return new String[]{subj_msg.getLeft(),subj_msg.getRight()};
		} 
		catch (RemoteException e) 
		{
			Debug.error(e);
		}
		return null;
	}
	
	public Class<?>[] getAllReturnedTypes() 
	{
		return new Class[]{JSTask.class};
	}

}

