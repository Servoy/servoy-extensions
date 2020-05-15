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

package com.servoy.extensions.plugins.workflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Environment;
import org.jbpm.api.Configuration;
import org.jbpm.api.DeploymentQuery;
import org.jbpm.api.Execution;
import org.jbpm.api.ExecutionService;
import org.jbpm.api.NewDeployment;
import org.jbpm.api.ProcessEngine;
import org.jbpm.api.ProcessInstance;
import org.jbpm.api.RepositoryService;
import org.jbpm.api.TaskService;
import org.jbpm.api.task.Task;
import org.jbpm.pvm.internal.email.impl.MailTemplate;
import org.jbpm.pvm.internal.email.impl.MailTemplateRegistry;

import com.servoy.extensions.plugins.workflow.shared.Deployment;
import com.servoy.extensions.plugins.workflow.shared.TaskData;
import com.servoy.extensions.workflow.api.IWorkflowPluginService;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.persistence.IServerInternal;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IUserManager;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;

/**
 * The workflow server being a servoy server plugin.
 *
 * @author jblok
 */
public class WorkflowServer implements IWorkflowPluginService
{
	public static final String JBPM_SERVERNAME_PROPERTY = "jbpm_servername";

	private String jndi_jbpm_server;
	private String jndi_datasource;
	
	public void init(String jndi_jbpm_server,String jndi_datasource) throws Exception
	{
		this.jndi_jbpm_server = jndi_jbpm_server;
		this.jndi_datasource = jndi_datasource;
		
		createProcessEngine();
	}

	private void checkAndCreateGroupsIfNeeded(String xml) throws Exception 
	{
		IApplicationServerSingleton as = ApplicationServerRegistry.get(); 
		if (as != null)
		{
			IUserManager userManager = as.getUserManager();//getService(IUserManager.class);
			Set<String> servoyGroupNames = new HashSet<String>();
			String clientId = as.getClientId();
			IDataSet groups_ds = userManager.getGroups(clientId);
			if (groups_ds != null)
			{
				for (int i = 0; i < groups_ds.getRowCount(); i++) 
				{
					Object[] group_row = groups_ds.getRow(i);
					servoyGroupNames.add(String.valueOf(group_row[1]));
				}
			}
	
			String scontent = xml.toLowerCase().replace('\'', '"');
			
			int idx1 = 0;
			while ((idx1 = scontent.indexOf("candidate-groups",idx1)) != -1)
			{
				int idx2 = scontent.indexOf('"',idx1);
				int idx3 = scontent.indexOf('"',idx2+1);
				idx1++; //to pass into next iteration
	
				String groups = xml.substring(idx2+1,idx3).trim();
				String[] grps = groups.split(",");
				for (int i = 0; i < grps.length; i++) 
				{
					String group = grps[i].trim();
					if (!servoyGroupNames.contains(group))
					{
						userManager.createGroup(clientId, group);
						servoyGroupNames.add(group);
					}
				}
			}
		}
		else
		{
			Debug.error("Could not checkAndCreateGroupsIfNeeded");
		}
	}

	public void close() throws Exception
	{
		if (processEngine != null) processEngine.close();
	}

	public String addProcessDefinition(String content)
	{
		return addProcessDefinition(content, 0);
	}
	public String addProcessDefinition(String content,long timestamp)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			RepositoryService repositoryService = processEngine.getRepositoryService();
			NewDeployment nd = repositoryService.createDeployment();
			checkAndCreateGroupsIfNeeded(content);
			String name = getProcessXMLName(content);
			name = getFileName(name);
			nd.addResourceFromString(name, content);
			nd.setName(name);
			nd.setTimestamp(timestamp);
			return nd.deploy();
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	private String getFileName(String pname) 
	{
		return pname + ".jpdl.xml";//workarround for stupid check inside jbpm
	}

	private String getProcessXMLName(String xml)
	{
		String scontent = xml.toLowerCase().replace('\'', '"');
		int idx1 = scontent.indexOf("name");
		int idx2 = scontent.indexOf('"',idx1);
		int idx3 = scontent.indexOf('"',idx2+1);
		return xml.substring(idx2+1,idx3).trim();
	}
//	public boolean removeProcessDefinition(String name, boolean force)
//	{
//		RepositoryService repositoryService = processEngine.getRepositoryService();
//		repositoryService.deleteDeployme
//	}

	public String startProcess(String processName,String solutionName,Map<String,Object> variables)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			ExecutionService executionService = processEngine.getExecutionService();
			variables.put(SOLUTION_PROPERTY_NAME, solutionName);
			
			VariablesTypeHelper.convertToJBPMTypes(variables);
			ProcessInstance processInstance = executionService.startProcessInstanceByKey(processName, variables);
			return processInstance.getId();
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public void terminateProcess(String executionId)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			ExecutionService executionService = processEngine.getExecutionService();
			executionService.endProcessInstance(executionId, Execution.STATE_ENDED);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

//	public String getProcessExecutionId(String processName)
//	{
//		ExecutionService executionService = processEngine.getExecutionService();
//		ProcessInstance processInstance = executionService.findProcessByName(processName);
//		return processInstance.getId();
//	}
	
	public TaskData[] getUserTasks(String username) 
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
			List<Task> tasks = taskService.findPersonalTasks(username);
			return getTasks(tasks);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}
	
	private	TaskData[] getTasks(List<Task> tasks)
	{
		TaskData[] tids = new TaskData[tasks.size()];
		for (int i = 0; i < tids.length; i++) 
		{
			Task t = tasks.get(i);
			TaskData td = new TaskData();
			td.taskId = t.getId();
			td.executionId = t.getExecutionId();
			td.assignee = t.getAssignee();
			td.activityName = t.getActivityName();
			td.name = t.getName();
			td.priority = t.getPriority();
			td.progress = t.getProgress();
			td.creationTime = (t.getCreateTime() != null ? new Date(t.getCreateTime().getTime()) : null);
			td.dueDate = (t.getDuedate() != null ? new Date(t.getDuedate().getTime()) : null);
			td.description = t.getDescription();
			tids[i] = td;
		}
		return tids;
	}
	
	public TaskData[] getGroupTasks(String username)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
			List<Task> tasks = taskService.findGroupTasks(username);
			return getTasks(tasks);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public Map<String, Object> getTaskVariables(String tid)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
			return taskService.getVariables(tid,taskService.getVariableNames(tid));
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public void takeTask(String tid,String uid)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
//			List<Participation> parts = taskService.getTaskParticipations(tid);
//			Iterator<Participation> it = parts.iterator();
//			while (it.hasNext()) 
//			{
//				Participation participation = it.next();
//				if (participation.getType() == Participation.OWNER && uid.equals(participation.getUserId()))
//				{
//					return;//prevent JbpmException: task already taken by <uid> (which is stupid to error on, when uid is already the owner)
//				}
//			}
			taskService.takeTask(tid, uid);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}
	
	public void releaseTask(String tid)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
			taskService.assignTask(tid, null);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public void save(TaskData td,Map<String, Object> variables)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
			Task task = taskService.getTask(td.taskId);
			if (task != null)
			{
				task.setDescription(td.description);
				task.setDuedate(td.dueDate);
				task.setName(td.name);
				task.setPriority(td.priority);
				task.setProgress(td.progress);
				taskService.saveTask(task);
			}
			if (variables != null) 
			{
				VariablesTypeHelper.convertToJBPMTypes(variables);
				taskService.setVariables(td.taskId, variables);
			}
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public void completeTask(String tid, String outcome, Map<String, Object> variables) 
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			TaskService taskService = processEngine.getTaskService();
			VariablesTypeHelper.convertToJBPMTypes(variables);
			taskService.completeTask(tid, outcome, variables);
		} 
		catch (Exception e) 
		{
			Debug.error(e);
			throw new RuntimeException(e.getMessage());
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}
	
	private volatile ProcessEngine processEngine;
	private synchronized ProcessEngine createProcessEngine() throws Exception
	{
		if (processEngine == null)
		{
			Context ctx = new InitialContext();
			IServerInternal jbpm_server = (IServerInternal) ctx.lookup(jndi_jbpm_server);
			if (jbpm_server == null) 
			{
				throw new IllegalStateException("jbpm database server not found, config the server plugin properties and restart");
			}
			
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			try
			{
				Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
				
				SessionFactory sessionFactory =null ; // RAGTEST 
//						new org.hibernate.cfg.Configuration()
//				.setProperty(Environment.DATASOURCE,jndi_datasource)
//				.setProperty(Environment.DIALECT, jbpm_server.getDialectClassName())
//                .addResource("jbpm.execution.hbm.xml",Configuration.class.getClassLoader())
//                .addResource("jbpm.history.hbm.xml",Configuration.class.getClassLoader())
//                .addResource("jbpm.identity.hbm.xml",Configuration.class.getClassLoader())
//                .addResource("jbpm.repository.hbm.xml",Configuration.class.getClassLoader())
//                .addResource("jbpm.task.hbm.xml",Configuration.class.getClassLoader())
//                .configure(getClass().getResource("/jbpm.hibernate.cfg.xml"))
//                .buildSessionFactory();
				
			    Configuration conf = new Configuration();
			    conf.setHibernateSessionFactory(sessionFactory);
				conf.setUrl(this.getClass().getResource("/jbpm.cfg.xml"));
				processEngine = conf.buildProcessEngine();
				
				//Workarround classload problem to make sure all services are pre loaded (still needed?)
				processEngine.getExecutionService();
				processEngine.getHistoryService();
				processEngine.getTaskService();
				processEngine.getRepositoryService();
			}
			finally
			{
				Thread.currentThread().setContextClassLoader(cl);
			}
		}
		return processEngine;
	}

	public Pair<String,String> getMailTemplate(String templateName)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			MailTemplateRegistry mailTemplateRegistry = processEngine.get(MailTemplateRegistry.class);
			MailTemplate template = mailTemplateRegistry.getTemplate(templateName);
			if (template != null)
			{
				String text = (template.getText() == null ? "" : template.getText());
				String html = (template.getHtml() == null ? "" : template.getHtml());
				return new Pair<String,String>(template.getSubject(),text+html);
			}
			return null;
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}
	
	public void addMailTemplate(String templateName,String subject,String msgText)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			MailTemplateRegistry mailTemplateRegistry = processEngine.get(MailTemplateRegistry.class);
			MailTemplate template = mailTemplateRegistry.getTemplate(templateName);
			if (template != null)
			{
				Debug.warn("Overwriting mail template "+templateName);
			}
			template = new MailTemplate();
			template.setSubject(subject);
	
			int htmlIndex = msgText.toLowerCase().indexOf("<html"); //$NON-NLS-1$
			boolean hasHTML = (htmlIndex != -1);
			boolean hasPlain = !hasHTML || htmlIndex > 0;
			String plain = hasHTML ? msgText.substring(0, htmlIndex) : msgText;
			String html = hasHTML ? msgText.substring(htmlIndex) : null;
			if (hasPlain) template.setText(plain);
			if (hasHTML) template.setHtml(html);
	
			mailTemplateRegistry.addTemplate(templateName, template);
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public List<Deployment> getDeploymentList()
	{
		List<Deployment> retval = new ArrayList<Deployment>();
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			
			DeploymentQuery dq = processEngine.getRepositoryService().createDeploymentQuery();
			Iterator<org.jbpm.api.Deployment> it = dq.list().iterator();
			while (it.hasNext()) 
			{
				org.jbpm.api.Deployment d = it.next();
				retval.add(new Deployment(d.getId(), d.getName(), d.getState(), d.getTimestamp()));
			}
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
		return retval;
	}

	public void suspendDeployment(String deploymentId)
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			
			processEngine.getRepositoryService().suspendDeployment(deploymentId);
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}			
	}
}