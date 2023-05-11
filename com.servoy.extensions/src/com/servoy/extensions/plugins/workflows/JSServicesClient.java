/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2023 Servoy BV

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

import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.kie.server.client.SolverServicesClient;
import org.kie.server.client.UIServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.kie.server.client.admin.UserTaskAdminServicesClient;
import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

public class JSServicesClient implements IJavaScriptType, IScriptable
{

	private final KieServicesClient client;

	public JSServicesClient(KieServicesClient client)
	{
		this.client = client;
	}

	@JSFunction
	public ProcessServicesClient getProcessServicesClient()
	{
		return client.getServicesClient(ProcessServicesClient.class);
	}

	@JSFunction
	public UserTaskServicesClient getUserTaskServicesClient()
	{
		return client.getServicesClient(UserTaskServicesClient.class);
	}

	@JSFunction
	public QueryServicesClient getQueryServicesClient()
	{
		return client.getServicesClient(QueryServicesClient.class);
	}

	@JSFunction
	public RuleServicesClient getRuleServicesClient()
	{
		return client.getServicesClient(RuleServicesClient.class);
	}

	@JSFunction
	public SolverServicesClient getSolverServicesClient()
	{
		return client.getServicesClient(SolverServicesClient.class);
	}

	@JSFunction
	public UIServicesClient getUIServicesClient()
	{
		return client.getServicesClient(UIServicesClient.class);
	}

	@JSFunction
	public UserTaskAdminServicesClient getUserTaskAdminServicesClient()
	{
		return client.getServicesClient(UserTaskAdminServicesClient.class);
	}
}
