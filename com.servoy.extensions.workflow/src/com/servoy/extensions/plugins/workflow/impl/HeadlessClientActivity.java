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

package com.servoy.extensions.plugins.workflow.impl;

import java.util.Map;

import org.jbpm.api.activity.ActivityBehaviour;
import org.jbpm.api.activity.ActivityExecution;

import com.servoy.extensions.workflow.api.IWorkflowPluginService;
import com.servoy.j2db.server.headlessclient.HeadlessClientFactory;
import com.servoy.j2db.server.shared.IHeadlessClient;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.serialize.MapSerializer;

/**
 * An activity node to execute a global method bpm_<node_name> with workflow variables as argument in a servoy headlessclient from within the workflow.
 * If the return value is a string from the servoy method that value is used as transition name otherwise default. 
 *
 * <custom name="printdots" class="com.servoy.extensions.plugins.worflow.impl.HeadlessClientActivity">
 *   <transition to="end" /> <!-- 1th == default transition -->
 *   <transition name="bla" to="somewhereelse" />
 * </custom>
 *
 * @author jblok
 */
public class HeadlessClientActivity implements ActivityBehaviour
{
	public void execute(ActivityExecution ae) throws Exception 
	{
		Map<String, Object> variables = (Map<String, Object>) ae.getVariables();
		Object solutionName = variables.get(IWorkflowPluginService.SOLUTION_PROPERTY_NAME);
		if (solutionName == null)
		{
			String msg = "No variable present with name: "+IWorkflowPluginService.SOLUTION_PROPERTY_NAME;
			Debug.error(msg);
			throw new IllegalStateException(msg);
		}
		IHeadlessClient client = HeadlessClientFactory.createHeadlessClient(solutionName.toString(), null);
		Object jsObj = MapSerializer.convertFromMap(variables);
		Object transition = client.getPluginAccess().executeMethod(null, "bpm_"+ae.getActivityName(), new Object[]{jsObj}, false);
		//TODO investigate if we can get values from jsObj to store again
		if (transition instanceof String)
		{
			ae.take(transition.toString());
		}
		else
		{
			ae.takeDefaultTransition();
		}
	}
}
