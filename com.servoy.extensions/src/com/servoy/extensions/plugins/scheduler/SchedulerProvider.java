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

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.impl.matchers.GroupMatcher.groupEquals;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import org.mozilla.javascript.Function;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import com.servoy.j2db.Messages;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * @author jcompagner
 */
@ServoyDocumented(publicName = SchedulerPlugin.PLUGIN_NAME, scriptingName = "plugins." + SchedulerPlugin.PLUGIN_NAME)
public class SchedulerProvider implements IScriptable
{
	private static Object schedulerLock = new Object();
	private static Scheduler scheduler;
	private final SchedulerPlugin plugin;

	private String lastRunned;

	/**
	 * @param app
	 */
	public SchedulerProvider(SchedulerPlugin plugin)
	{
		this.plugin = plugin;
	}

	public void stopScheduler()
	{
		if (scheduler != null)
		{
			try
			{
				int type = plugin.getClientPluginAccess().getApplicationType();
				if (type == IClientPluginAccess.CLIENT || type == IClientPluginAccess.RUNTIME)
				{
					scheduler.shutdown(false);
					scheduler = null;
				}
				else
				{
					String id = plugin.getClientPluginAccess().getClientID();
					scheduler.deleteJobs(new ArrayList<>(scheduler.getJobKeys(groupEquals(id))));
				}
			}
			catch (SchedulerException e)
			{
				Debug.error(e);
			}
		}
	}

	public static void shutdown()
	{
		if (scheduler != null)
		{
			try
			{
				scheduler.shutdown(false);
			}
			catch (SchedulerException e)
			{
				Debug.error(e);
			}
			scheduler = null;
		}
	}

	/**
	 * Adds a job to the scheduler.
	 *
	 * @sample
	 * // add a job that runs at the given date (20 seconds in the future)
	 * // and repeats that every 20 seconds for 40 times or the enddate is reached (0 for no repeats = just one call)
	 * var startDate = new Date();
	 * startDate.setTime(startDate.getTime()+20000);
	 * var endDate = new Date(startDate.getTime()+100000);
	 * plugins.scheduler.addJob('in20seconds',startDate,method,20000,40,endDate)
	 *
	 * @param jobname
	 * @param startDate
	 * @param method
	 */
	public void js_addJob(String jobname, Date startDate, Function method)
	{
		js_addJob(jobname, startDate, method, 0, 0, null, null);
	}

	/**
	 * @clonedesc js_addJob(String, Date, Function)
	 * @sampleas js_addJob(String, Date, Function)
	 *
	 * @param jobname
	 * @param startDate
	 * @param method
	 * @param arguments
	 */
	public void js_addJob(String jobname, Date startDate, Function method, Object[] arguments)
	{
		js_addJob(jobname, startDate, method, 0, 0, null, arguments);
	}

	/**
	 * @clonedesc js_addJob(String, Date, Function)
	 * @sampleas js_addJob(String, Date, Function)
	 *
	 * @param jobname
	 * @param startDate
	 * @param method
	 * @param repeatInterval ms
	 */
	public void js_addJob(String jobname, Date startDate, Function method, int repeatInterval)
	{
		js_addJob(jobname, startDate, method, repeatInterval, -1, null, null);
	}

	/**
	 * @clonedesc js_addJob(String, Date, Function)
	 * @sampleas js_addJob(String, Date, Function)
	 *
	 * @param jobname
	 * @param startDate
	 * @param method
	 * @param repeatInterval ms
	 * @param repeatCount
	 */
	public void js_addJob(String jobname, Date startDate, Function method, int repeatInterval, int repeatCount)
	{
		js_addJob(jobname, startDate, method, repeatInterval, repeatCount, null, null);
	}

	/**
	 * @clonedesc js_addJob(String, Date, Function)
	 * @sampleas js_addJob(String, Date, Function)
	 *
	 * @param jobName
	 * @param startDate
	 * @param method
	 * @param repeatInterval ms
	 * @param repeatCount
	 * @param endDate
	 */
	public void js_addJob(String jobName, Date startDate, Function method, int repeatInterval, int repeatCount, Date endDate)
	{
		js_addJob(jobName, startDate, method, repeatInterval, repeatCount, endDate, null);
	}

	/**
	 * @clonedesc js_addJob(String, Date, Function)
	 * @sampleas js_addJob(String, Date, Function)
	 *
	 * @param jobname
	 * @param startDate
	 * @param method
	 * @param repeatInterval ms
	 * @param repeatCount
	 * @param endDate
	 * @param arguments
	 */
	public void js_addJob(String jobname, Date startDate, Function method, int repeatInterval, int repeatCount, Date endDate, Object[] arguments)
	{
		testScheduler();

		synchronized (schedulerLock)
		{
			JobDetail jobDetail = createJob(jobname, method, arguments);

			SimpleTrigger trigger = createTrigger(jobDetail, startDate, endDate)
				.withSchedule(simpleSchedule()
					.withRepeatCount(repeatCount == -1 ? SimpleTrigger.REPEAT_INDEFINITELY : repeatCount)
					.withIntervalInMilliseconds(repeatInterval))
				.build();

			try
			{
				scheduler.scheduleJob(jobDetail, trigger);
			}
			catch (SchedulerException e)
			{
				Debug.error(e);
				throw new RuntimeException(Messages.getString("servoy.plugin.scheduler.cannotStart") + e.getMessage()); //$NON-NLS-1$
			}
		}
	}

	/**
	 * @deprecated Replaced by {@link #getLastRunJobName()}.
	 */
	@Deprecated
	public String js_getLastRunnedJobName()
	{
		return lastRunned;
	}

	/**
	 * Returns the last job run from the scheduler.
	 *
	 * @sample
	 * plugins.scheduler.getLastRunJobName();
	 */
	public String js_getLastRunJobName()
	{
		return lastRunned;
	}

	void setLastRunJobName(String lastRunned)
	{
		this.lastRunned = lastRunned;
	}

	/**
	 * Returns an array with the current jobs.
	 *
	 * @sample
	 * plugins.scheduler.getCurrentJobNames()
	 */
	public String[] js_getCurrentJobNames()
	{
		testScheduler();
		try
		{
			String id = plugin.getClientPluginAccess().getClientID();
			return scheduler.getJobKeys(groupEquals(id)).stream().map(JobKey::getName).toArray(String[]::new);
		}
		catch (SchedulerException e)
		{
			Debug.error(e);
		}
		return new String[0];
	}

	/**
	 * Adds a cron job to the scheduler. A cron job must have at least one minute between each execution (otherwise it won't execute).
	 *
	 * @sample
	 * // see: http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html for more info
	 * // add a job that runs every 20 minutes after the hour (0,20,40)
	 * plugins.scheduler.addCronJob('20mins','0 0/20 * * * ?',method)
	 * // add a job that runs every day at 23:30 between now and 5 days from now
	 * var dateNow = new Date();
	 * var date5Days = new Date(dateNow.getTime()+5*24*60*60*1000);
	 * plugins.scheduler.addCronJob('23:30','0 30 23 ? * *',method,dateNow,date5Days)
	 *
	 * @param jobname
	 * @param cronTimings
	 * @param method
	 *
	 * @link http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html
	 */
	public void js_addCronJob(String jobname, String cronTimings, Function method)
	{
		js_addCronJob(jobname, cronTimings, method, null, null, null);
	}

	/**
	 * @clonedesc js_addCronJob(String, String, Function)
	 * @sampleas js_addCronJob(String, String, Function)
	 *
	 * @param jobname
	 * @param cronTimings
	 * @param method
	 * @param startDate
	 */
	public void js_addCronJob(String jobname, String cronTimings, Function method, Date startDate)
	{
		js_addCronJob(jobname, cronTimings, method, startDate, null, null);
	}

	/**
	 * @clonedesc js_addCronJob(String, String, Function)
	 * @sampleas js_addCronJob(String, String, Function)
	 *
	 * @param jobname
	 * @param cronTimings
	 * @param method
	 * @param startDate
	 * @param endDate
	 */
	public void js_addCronJob(String jobname, String cronTimings, Function method, Date startDate, Date endDate)
	{
		js_addCronJob(jobname, cronTimings, method, startDate, endDate, null);
	}

	/**
	 * @clonedesc js_addCronJob(String, String, Function)
	 * @sampleas js_addCronJob(String, String, Function)
	 *
	 * @param jobname
	 * @param cronTimings
	 * @param method
	 * @param startDate
	 * @param endDate
	 * @param arguments
	 */
	public void js_addCronJob(String jobname, String cronTimings, Function method, Date startDate, Date endDate, Object[] arguments)
	{
		testScheduler();

		synchronized (schedulerLock)
		{
			JobDetail jobDetail = createJob(jobname, method, arguments);

			try
			{
				CronTrigger cronTrigger = createTrigger(jobDetail, startDate, endDate)
					.withSchedule(cronSchedule(new CronExpression(cronTimings)))
					.build();

				scheduler.scheduleJob(jobDetail, cronTrigger);
			}
			catch (ParseException e)
			{
				Debug.error(e);
				throw new RuntimeException(Messages.getString("servoy.plugin.scheduler.invalidTimings", new Object[] { cronTimings, e.getMessage() })); //$NON-NLS-1$
			}
			catch (SchedulerException e)
			{
				Debug.error(e);
				throw new RuntimeException(Messages.getString("servoy.plugin.scheduler.cannotScheduleJob", new Object[] { jobname, e.getMessage() })); //$NON-NLS-1$
			}
		}
	}

	private JobDetail createJob(String jobname, Function method, Object[] arguments)
	{
		String id = plugin.getClientPluginAccess().getClientID();

		JobDetail jobDetail = newJob(ExecuteScriptMethodJob.class)
			.withIdentity(jobKey(jobname, id))
			.build();

		jobDetail.getJobDataMap().put("scheduler", this); //$NON-NLS-1$
		FunctionDefinition functionDef = new FunctionDefinition(method);
		jobDetail.getJobDataMap().put("methodname", functionDef.getMethodName()); //$NON-NLS-1$
		jobDetail.getJobDataMap().put("methodcontext", functionDef.getContextName()); //$NON-NLS-1$
		jobDetail.getJobDataMap().put("args", arguments); //$NON-NLS-1$
		jobDetail.getJobDataMap().put("access", plugin.getClientPluginAccess()); //$NON-NLS-1$
		return jobDetail;
	}

	private TriggerBuilder<Trigger> createTrigger(JobDetail jobDetail, Date startDate, Date endDate)
	{
		return newTrigger()
			.forJob(jobDetail)
			.startAt(startDate == null ? new Date() : startDate)
			.endAt(endDate);
	}


	private void testScheduler()
	{
		synchronized (schedulerLock)
		{
			if (scheduler == null)
			{
				try
				{
					scheduler = org.quartz.impl.StdSchedulerFactory.getDefaultScheduler();
					scheduler.start();
				}
				catch (Exception e)
				{
					Debug.error(e);
					throw new RuntimeException(Messages.getString("servoy.plugin.scheduler.cannotStart") + e.getMessage()); //$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Removes a job from the scheduler.
	 *
	 * @sample
	 * // removes a job 'myjob' from the scheduler
	 * plugins.scheduler.removeJob('myjob');
	 *
	 * @param jobname
	 */
	public boolean js_removeJob(String jobname)
	{
		if (scheduler != null)
		{
			try
			{
				String id = plugin.getClientPluginAccess().getClientID();
				return scheduler.deleteJob(jobKey(jobname, id));
			}
			catch (SchedulerException e)
			{
				Debug.error("Error removing scheduler job: " + e.getMessage()); //$NON-NLS-1$
			}
		}
		return false;
	}
}
