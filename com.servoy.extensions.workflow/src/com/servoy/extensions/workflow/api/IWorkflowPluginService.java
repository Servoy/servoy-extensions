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

package com.servoy.extensions.workflow.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

import com.servoy.extensions.plugins.workflow.shared.Deployment;
import com.servoy.extensions.plugins.workflow.shared.TaskData;
import com.servoy.j2db.util.Pair;

/**
 * Public api for the workflow plugin.
 *
 * @author jblok
 */
public interface IWorkflowPluginService extends Remote 
{
	public static final String SOLUTION_PROPERTY_NAME = "servoySolutionName";

	public List<Deployment> getDeploymentList() throws RemoteException;
	public void suspendDeployment(String deploymentId) throws RemoteException;
	public String addProcessDefinition(String content) throws RemoteException;
	public String addProcessDefinition(String content, long timestamp) throws RemoteException;
	
	public String startProcess(String processName,String solutionName,Map<String,Object> variables) throws RemoteException;
//	public String getProcessExecutionId(String processName);
	public void terminateProcess(String executionId) throws RemoteException;
	
	public TaskData[] getUserTasks(String uid) throws RemoteException;
	public TaskData[] getGroupTasks(String uid) throws RemoteException;

	public Map<String, Object> getTaskVariables(String tid) throws RemoteException;
	public void save(TaskData td,Map<String, Object> variables) throws RemoteException;

	public void takeTask(String tid,String uid) throws RemoteException;
	public void releaseTask(String tid) throws RemoteException;
	public void completeTask(String tid, String outcome, Map<String, Object> variables) throws RemoteException;
	
	public void addMailTemplate(String templateName,String subject,String msgText) throws RemoteException;
	public Pair<String,String> getMailTemplate(String templateName) throws RemoteException;
}
