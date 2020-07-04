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
package com.servoy.extensions.plugins.scheduler;

import java.util.Arrays;

import org.quartz.CronTrigger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.Debug;

/**
 * @author jcompagner
 */
@DisallowConcurrentExecution
public class ExecuteScriptMethodJob implements Job
{
	@SuppressWarnings("nls")
	public void execute(JobExecutionContext jobContext) throws JobExecutionException
	{
		final String name = jobContext.getJobDetail().getKey().getName();
		JobDataMap jdm = jobContext.getJobDetail().getJobDataMap();
		Object o = jdm.get("methodcontext");
		final String methodcontext;
		if (o instanceof String)
		{
			methodcontext = (String)o;
		}
		else
		{
			methodcontext = null;
		}
		final String methodname = (String)jdm.get("methodname");
		final Object[] args = (Object[])jdm.get("args");
		final IClientPluginAccess access = (IClientPluginAccess)jdm.get("access");
		final SchedulerProvider provider = (SchedulerProvider)jdm.get("scheduler");

		provider.setLastRunJobName(name);
		if (Debug.tracing())
		{
			String trigger;
			if (jobContext.getTrigger() instanceof CronTrigger)
			{
				trigger = " cron trigger: " + ((CronTrigger)jobContext.getTrigger()).getCronExpression() + " (" + jobContext.getTrigger() + ')';
			}
			else
			{
				trigger = " trigger: " + jobContext.getTrigger();
			}
			Debug.trace("Executing job: " + name + " scheduled method: " + methodname + " of context: " + methodcontext + " args: " + Arrays.toString(args) +
				trigger);
		}
		try
		{
			access.executeMethod(methodcontext, methodname, args, true, false);
		}
		catch (Exception e1)
		{
			Debug.error("Error executing scheduled method: " + methodname + " of context: " + methodcontext, e1);
			access.handleException(null, e1);
		}
	}
}
