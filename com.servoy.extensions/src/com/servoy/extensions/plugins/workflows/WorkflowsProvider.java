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

package com.servoy.extensions.plugins.workflows;

import java.util.List;

import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.kie.server.client.SolverServicesClient;
import org.kie.server.client.UIServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.kie.server.client.admin.UserTaskAdminServicesClient;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>WorkflowsProvider</code> class, enable interaction with
 * Kie Services for workflow management. It offers methods to create clients and manage user tasks.</p>
 *
 * <h2>Functionality</h2>
 * <p>The <code>createClient</code> method initializes a <code>KieServicesClient</code> using the
 * provided deployment URL, user credentials, and a JSON marshalling format. This client serves as
 * the primary interface for interacting with Kie Services. The <code>createServicesClient</code>
 * method extends this functionality by wrapping the <code>KieServicesClient</code> in a
 * <code>JSServicesClient</code>, adding scripting support.</p>
 *
 * <p>For task management, <code>getAllUserTasks</code> retrieves user tasks from the service, with
 * options to specify pagination parameters such as page number and size. These tasks are fetched
 * using the <code>UserTaskServicesClient</code>. The provider supports fetching additional services
 * such as <code>ProcessServicesClient</code>, <code>QueryServicesClient</code>, and
 * <code>UserTaskAdminServicesClient</code> for comprehensive workflow operations.</p>
 */
@ServoyDocumented(publicName = WorkflowsPlugin.PLUGIN_NAME, scriptingName = "plugins." + WorkflowsPlugin.PLUGIN_NAME)
public class WorkflowsProvider implements IScriptable, IReturnedTypesProvider
{
	private final WorkflowsPlugin plugin;

	public WorkflowsProvider(WorkflowsPlugin workflowPlugin)
	{
		plugin = workflowPlugin;
	}

	@JSFunction
	public KieServicesClient createClient(String deploymentUrl, String user, String password)
	{
		return getServicesClient(deploymentUrl, user, password);
	}


	@JSFunction
	public JSServicesClient createServicesClient(String deploymentUrl, String user, String password)
	{
		KieServicesClient client = getServicesClient(deploymentUrl, user, password);
		return new JSServicesClient(client);
	}

	private KieServicesClient getServicesClient(String deploymentUrl, String user, String password)
	{
		KieServicesConfiguration config = KieServicesFactory.newRestConfiguration(
			deploymentUrl, user, password);
		config.setMarshallingFormat(MarshallingFormat.JSON);
		KieServicesClient client = KieServicesFactory.newKieServicesClient(config);
		return client;
	}

	@JSFunction
	public List<TaskSummary> getAllUserTasks(String deploymentUrl, String user, String password)
	{
		return getAllUserTasks(deploymentUrl, user, password, 0, 10);
	}

	@JSFunction
	public List<TaskSummary> getAllUserTasks(String deploymentUrl, String user, String password, int page, int pageSize)
	{
		KieServicesClient client = getServicesClient(deploymentUrl, user, password);
		UserTaskServicesClient taskClient = client.getServicesClient(UserTaskServicesClient.class);
		return taskClient.findTasks(user, Integer.valueOf(page), Integer.valueOf(pageSize));
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { JSServicesClient.class, KieServicesClient.class, TaskSummary.class, UserTaskServicesClient.class, ProcessServicesClient.class, QueryServicesClient.class, RuleServicesClient.class, SolverServicesClient.class, UIServicesClient.class, UserTaskAdminServicesClient.class };
	}
}

