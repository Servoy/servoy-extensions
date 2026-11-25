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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.quartz.JobKey.jobKey;
import static org.quartz.impl.matchers.GroupMatcher.groupEquals;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Function;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;

/**
 * The scheduler plugin with the real (in memory, RAMJobStore) quartz scheduler. Every job starts far in the future, so
 * nothing fires during a test; the tests check the scheduled jobs and triggers. The static scheduler is shut down after
 * each test.
 */
public class SchedulerProviderTest
{
	/** A start date that is never reached by a test run. */
	private static final Date FUTURE = new Date(4102444800000L); // 2100-01-01
	private static final Date LATER = new Date(4133980800000L); // 2101-01-01

	private IClientPluginAccess access;
	private SchedulerPlugin plugin;
	private SchedulerProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IClientPluginAccess.class);
		when(access.getClientID()).thenReturn("client-1");
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.NG_CLIENT));
		plugin = new SchedulerPlugin();
		plugin.load();
		plugin.initialize(access);
		provider = (SchedulerProvider)plugin.getScriptObject();
	}

	@AfterEach
	public void tearDown()
	{
		SchedulerProvider.shutdown();
	}

	static Function method(String scope, String name)
	{
		Function f = mock(Function.class);
		when(f.get("_scopename_", f)).thenReturn(scope);
		when(f.get("_methodname_", f)).thenReturn(name);
		return f;
	}

	private static Scheduler scheduler() throws Exception
	{
		Field field = SchedulerProvider.class.getDeclaredField("scheduler");
		field.setAccessible(true);
		return (Scheduler)field.get(null);
	}

	private static Trigger trigger(String job, String client) throws Exception
	{
		List< ? extends Trigger> triggers = scheduler().getTriggersOfJob(jobKey(job, client));
		assertEquals(1, triggers.size());
		return triggers.get(0);
	}

	// --- plugin ---

	@Test
	public void pluginDescription()
	{
		assertEquals("scheduler", plugin.getName());
		assertNull(plugin.getProperties());
		assertNull(plugin.getPreferencePanels());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(provider, plugin.getScriptObject());
		assertSame(access, plugin.getClientPluginAccess());
	}

	@Test
	public void serverPluginShutsTheSchedulerDown() throws Exception
	{
		provider.js_addJob("job", FUTURE, method("globals", "run"));
		SchedulerServerPlugin server = new SchedulerServerPlugin();
		server.load();
		server.initialize(mock(IServerAccess.class));
		assertEquals("Scheduler shutdown plugin", server.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertNull(server.getRequiredPropertyNames());

		server.unload();

		assertNull(scheduler());
		// a second shutdown without scheduler does nothing
		SchedulerProvider.shutdown();
	}

	// --- simple jobs ---

	@Test
	public void aOneShotJob() throws Exception
	{
		provider.js_addJob("once", FUTURE, method("globals", "run"));

		JobDetail job = scheduler().getJobDetail(jobKey("once", "client-1"));
		assertEquals(ExecuteScriptMethodJob.class, job.getJobClass());
		assertEquals("run", job.getJobDataMap().get("methodname"));
		assertEquals("scopes.globals", job.getJobDataMap().get("methodcontext"));
		assertNull(job.getJobDataMap().get("args"));
		assertSame(access, job.getJobDataMap().get("access"));
		assertSame(provider, job.getJobDataMap().get("scheduler"));
		SimpleTrigger trigger = (SimpleTrigger)trigger("once", "client-1");
		assertEquals(FUTURE, trigger.getStartTime());
		assertEquals(0, trigger.getRepeatCount());
		assertNull(trigger.getEndTime());
		assertTrue(scheduler().isStarted());
	}

	@Test
	public void argumentsAreStoredWithTheJob() throws Exception
	{
		Object[] args = { "a", Integer.valueOf(1) };

		provider.js_addJob("withArgs", FUTURE, method("globals", "run"), args);

		assertArrayEquals(args, (Object[])scheduler().getJobDetail(jobKey("withArgs", "client-1")).getJobDataMap().get("args"));
	}

	@Test
	public void aRepeatingJobRunsIndefinitely() throws Exception
	{
		provider.js_addJob("every", FUTURE, method("globals", "run"), 60000);

		SimpleTrigger trigger = (SimpleTrigger)trigger("every", "client-1");
		assertEquals(60000, trigger.getRepeatInterval());
		assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, trigger.getRepeatCount());
	}

	@Test
	public void aRepeatCountAndEndDate() throws Exception
	{
		provider.js_addJob("limited", FUTURE, method("globals", "run"), 1000, 3);
		provider.js_addJob("ending", FUTURE, method("globals", "run"), 1000, 5, LATER);

		assertEquals(3, ((SimpleTrigger)trigger("limited", "client-1")).getRepeatCount());
		Trigger ending = trigger("ending", "client-1");
		assertEquals(LATER, ending.getEndTime());
		assertEquals(5, ((SimpleTrigger)ending).getRepeatCount());
	}

	@Test
	public void withoutStartDateTheJobStartsNow() throws Exception
	{
		// start the scheduler and put it in standby, so the job that starts now is not fired on a quartz thread
		provider.js_getCurrentJobNames();
		scheduler().standby();
		long before = System.currentTimeMillis();

		provider.js_addJob("now", null, method("globals", "run"), 3_600_000, 0, null, null);

		Date start = trigger("now", "client-1").getStartTime();
		assertTrue(start.getTime() >= before - 1000 && start.getTime() <= System.currentTimeMillis() + 1000, start.toString());
	}

	@Test
	public void aRepeatWithoutIntervalIsRejected()
	{
		// repeat indefinitely with interval 0 is invalid for quartz
		RuntimeException e = assertThrows(RuntimeException.class, () -> provider.js_addJob("bad", FUTURE, method("globals", "run"), 0));
		assertNotNull(e.getMessage());
	}

	@Test
	public void aDuplicateJobNameIsRejected()
	{
		provider.js_addJob("dup", FUTURE, method("globals", "run"));

		// the message starts with the generic "cannot start" text, followed by the quartz duplicate name error
		RuntimeException e = assertThrows(RuntimeException.class, () -> provider.js_addJob("dup", FUTURE, method("globals", "run")));
		assertTrue(e.getMessage().contains("dup"), e.getMessage());
	}

	// --- cron jobs ---

	@Test
	public void cronJobs() throws Exception
	{
		provider.js_addCronJob("cron", "0 0 12 * * ?", method("globals", "noon"));
		provider.js_addCronJob("cronStart", "0 0 12 * * ?", method("globals", "noon"), FUTURE);
		provider.js_addCronJob("cronEnd", "0 0 12 * * ?", method("globals", "noon"), FUTURE, LATER);
		provider.js_addCronJob("cronArgs", "0 0 12 * * ?", method("globals", "noon"), FUTURE, LATER, new Object[] { "x" });

		assertEquals("0 0 12 * * ?", ((CronTrigger)trigger("cron", "client-1")).getCronExpression());
		assertEquals(FUTURE, trigger("cronStart", "client-1").getStartTime());
		assertEquals(LATER, trigger("cronEnd", "client-1").getEndTime());
		assertArrayEquals(new Object[] { "x" }, (Object[])scheduler().getJobDetail(jobKey("cronArgs", "client-1")).getJobDataMap().get("args"));
	}

	@Test
	public void anInvalidCronExpressionIsRejected()
	{
		RuntimeException e = assertThrows(RuntimeException.class, () -> provider.js_addCronJob("cron", "not a cron", method("globals", "noon")));
		assertNotNull(e.getMessage());
	}

	@Test
	public void aDuplicateCronJobIsRejected()
	{
		provider.js_addCronJob("cron", "0 0 12 * * ?", method("globals", "noon"), FUTURE);

		assertThrows(RuntimeException.class, () -> provider.js_addCronJob("cron", "0 0 12 * * ?", method("globals", "noon"), FUTURE));
	}

	// --- job names and removal ---

	@Test
	public void jobNamesAreKeptPerClient() throws Exception
	{
		provider.js_addJob("a", FUTURE, method("globals", "run"));
		provider.js_addCronJob("b", "0 0 12 * * ?", method("globals", "run"), FUTURE);
		SchedulerProvider other = otherClient("client-2");
		other.js_addJob("a", FUTURE, method("globals", "run"));

		String[] names = provider.js_getCurrentJobNames();
		Arrays.sort(names);
		assertArrayEquals(new String[] { "a", "b" }, names);
		assertArrayEquals(new String[] { "a" }, other.js_getCurrentJobNames());
	}

	private SchedulerProvider otherClient(String clientId) throws Exception
	{
		IClientPluginAccess otherAccess = mock(IClientPluginAccess.class);
		when(otherAccess.getClientID()).thenReturn(clientId);
		when(Integer.valueOf(otherAccess.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.NG_CLIENT));
		SchedulerPlugin otherPlugin = new SchedulerPlugin();
		otherPlugin.initialize(otherAccess);
		return (SchedulerProvider)otherPlugin.getScriptObject();
	}

	@Test
	public void removeJob()
	{
		assertFalse(provider.js_removeJob("none"));
		provider.js_addJob("job", FUTURE, method("globals", "run"));

		assertTrue(provider.js_removeJob("job"));
		assertFalse(provider.js_removeJob("job"));
		assertEquals(0, provider.js_getCurrentJobNames().length);
	}

	@Test
	public void lastRunJobName()
	{
		assertNull(provider.js_getLastRunJobName());

		provider.setLastRunJobName("job");

		assertEquals("job", provider.js_getLastRunJobName());
		assertEquals("job", provider.js_getLastRunnedJobName());
	}

	// --- stopping ---

	@Test
	public void closingTheSolutionRemovesOnlyTheJobsOfThisClient() throws Exception
	{
		provider.js_addJob("mine", FUTURE, method("globals", "run"));
		SchedulerProvider other = otherClient("client-2");
		other.js_addJob("theirs", FUTURE, method("globals", "run"));

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", "sol", null));

		assertTrue(scheduler().getJobKeys(groupEquals("client-1")).isEmpty());
		assertEquals(1, scheduler().getJobKeys(groupEquals("client-2")).size());
		// a new provider is created on the next use
		assertFalse(provider == plugin.getScriptObject());
	}

	@Test
	public void otherPropertyChangesAreIgnored() throws Exception
	{
		provider.js_addJob("mine", FUTURE, method("globals", "run"));

		plugin.propertyChange(new PropertyChangeEvent(this, "solution", null, "sol"));
		plugin.propertyChange(new PropertyChangeEvent(this, "other", "sol", null));

		assertEquals(1, provider.js_getCurrentJobNames().length);
		assertSame(provider, plugin.getScriptObject());
	}

	@Test
	public void unloadRemovesTheJobs() throws Exception
	{
		provider.js_addJob("mine", FUTURE, method("globals", "run"));

		plugin.unload();

		assertTrue(scheduler().getJobKeys(groupEquals("client-1")).isEmpty());
		assertNotNull(scheduler());
	}

	@Test
	public void aSmartClientShutsTheWholeSchedulerDown() throws Exception
	{
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.CLIENT));
		provider.js_addJob("mine", FUTURE, method("globals", "run"));

		provider.stopScheduler();

		assertNull(scheduler());
		// without scheduler stopping does nothing
		provider.stopScheduler();
		plugin.unload();
	}
}
