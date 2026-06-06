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
import java.util.Date;
import java.util.Map;

import com.servoy.extensions.plugins.workflow.VariablesTypeHelper;
import com.servoy.extensions.plugins.workflow.shared.TaskData;
import com.servoy.extensions.workflow.api.IWorkflowPluginService;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.serialize.MapSerializer;

/**
 * Javascript wrapper for a task object
 * 
 * @author jblok
 */
@ServoyDocumented
public class JSTask 
{
	private IWorkflowPluginService workflowService;
	private TaskData td;
	
	public JSTask(IWorkflowPluginService workflowService, TaskData td) 
	{
		this.workflowService = workflowService;
		this.td = td;
	}

	public String getID()
	{
		return td.taskId;
	}

	public String getExecutionID()
	{
		return td.executionId;
	}

	private Object variablesObject;
	public Object getVariablesObject()
	{
		if (variablesObject == null)
		{
			try 
			{
				Map<String,Object> variables = workflowService.getTaskVariables(td.taskId);
				VariablesTypeHelper.convertToServoyTypes(variables);
				variablesObject = MapSerializer.convertFromMap(variables);
			} 
			catch (RemoteException e) 
			{
				Debug.error(e);
			}
		}
		return variablesObject;
	}
	
	public void save(Object jsVariablesObject)
	{
		try 
		{
			Map<String,Object> variables = MapSerializer.convertToMap(jsVariablesObject);
			workflowService.save(td,variables);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
		}
	}
	
	public boolean take(String uid)
	{
		try 
		{
			if (!uid.equals(td.assignee))
			{
				workflowService.takeTask(td.taskId, uid);
			}
			return true;
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			return false;
		}
	}
	
	public void release()
	{
		try 
		{
			workflowService.releaseTask(td.taskId);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
		}
	}
		
	public void complete(String outcome, Object jsVariablesObject)
	{
		try 
		{
			Map<String,Object> variables = MapSerializer.convertToMap(jsVariablesObject);
			workflowService.completeTask(td.taskId, outcome, variables);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
		}
	}
	
	public void setDescription(String description)
	{
		td.description = description;
	}
	
	public void setDueDate(Date dueDate)
	{
		td.dueDate = dueDate;
	}
	
	public void setName(String name)
	{
		td.name = name;
	}
	
	public void setPriority(int priority)
	{
		td.priority = priority;
	}
	
	public void setProgress(int progress)
	{
		td.progress = progress;
	}

	public String getDescription()
	{
		return td.description;
	}
	
	public Date getDueDate()
	{
		return td.dueDate;
	}
	
	public String getName()
	{
		return td.name;
	}
	
	public int getPriority()
	{
		return td.priority;
	}
	
	public int getProgress()
	{
		return td.progress;
	}

	public String getAssignee()
	{
		return td.assignee;
	}

	public Date getCreationTime()
	{
		return td.creationTime;
	}
	
	public String getActivityName()
	{
		return td.activityName;
	}
}
