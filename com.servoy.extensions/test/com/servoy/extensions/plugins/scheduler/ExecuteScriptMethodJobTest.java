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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;

import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * The quartz job that calls the scheduled script method, run directly with a mocked job context.
 */
public class ExecuteScriptMethodJobTest
{
	private static JobExecutionContext context(Object methodContext, IClientPluginAccess access, SchedulerProvider provider, Object[] args)
	{
		JobDetail job = newJob(ExecuteScriptMethodJob.class).withIdentity("job", "client").build();
		job.getJobDataMap().put("methodcontext", methodContext);
		job.getJobDataMap().put("methodname", "run");
		job.getJobDataMap().put("args", args);
		job.getJobDataMap().put("access", access);
		job.getJobDataMap().put("scheduler", provider);
		JobExecutionContext context = mock(JobExecutionContext.class);
		when(context.getJobDetail()).thenReturn(job);
		return context;
	}

	@Test
	public void theScriptMethodIsExecutedAsynchronously() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		SchedulerProvider provider = new SchedulerProvider(new SchedulerPlugin());
		Object[] args = { "a" };

		new ExecuteScriptMethodJob().execute(context("scopes.globals", access, provider, args));

		verify(access).executeMethod("scopes.globals", "run", args, true, false);
		assertEquals("job", provider.js_getLastRunJobName());
	}

	@Test
	public void aNonStringContextIsNull() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);

		new ExecuteScriptMethodJob().execute(context(Integer.valueOf(1), access, new SchedulerProvider(new SchedulerPlugin()), null));

		verify(access).executeMethod(null, "run", null, true, false);
	}

	@Test
	public void aFailingMethodIsHandledByTheClient() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		IllegalStateException error = new IllegalStateException("boom");
		when(access.executeMethod(anyString(), anyString(), any(), anyBoolean(), anyBoolean())).thenThrow(error);

		new ExecuteScriptMethodJob().execute(context("scopes.globals", access, new SchedulerProvider(new SchedulerPlugin()), null));

		verify(access).handleException(isNull(), org.mockito.ArgumentMatchers.same(error));
	}
}
