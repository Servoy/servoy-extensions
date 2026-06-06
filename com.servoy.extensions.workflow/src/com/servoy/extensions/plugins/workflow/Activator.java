package com.servoy.extensions.plugins.workflow;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.sql.DataSource;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

import com.servoy.extensions.plugins.workflow.client.WorkflowPlugin;
import com.servoy.extensions.workflow.api.IWorkflowPluginService;
import com.servoy.j2db.persistence.IServerInternal;
import com.servoy.j2db.plugins.IPluginManager;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.server.starter.IServerStarter;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;

public class Activator implements BundleActivator
{
	private String jbpmServerName = "jbpm";

	private WorkflowServer server = new WorkflowServer();

	private  ServiceTracker<IPluginManager,ServiceReference<IPluginManager>> serviceTracker;
	private ServiceRegistration<IWorkflowPluginService> workflowPluginServiceRegistration;

	@Override
	public void start(BundleContext context) throws Exception 
	{
		//make sure db servers are present
		ServiceReference<IServerStarter> sref = context.getServiceReference(IServerStarter.class);
		IServerStarter ss = (IServerStarter) context.getService(sref);
		ss.init();

		//get correct server name from properties
		ServiceReference<Settings> ref = context.getServiceReference(Settings.class);
		Settings settings = context.getService(ref);
		String jbpmServerPropertyValue = settings.getProperty(WorkflowServer.JBPM_SERVERNAME_PROPERTY, jbpmServerName);  
		if (jbpmServerPropertyValue != null && jbpmServerPropertyValue.length() != 0) jbpmServerName = jbpmServerPropertyValue;

		final String username = settings.getProperty("mail.smtp.username"); 
		final String password = settings.getProperty("mail.smtp.password"); 
		if (username != null && password != null)
		{
			//expose the mail session, similar to servoy mail plugin
			Session session = Session.getInstance(settings, new Authenticator() 
			{
				@Override
				public PasswordAuthentication getPasswordAuthentication()
				{
					return new PasswordAuthentication(username, password);
				}
			});
			context.registerService(Session.class.getName(), session, null);
		}
		else
		{
			Debug.warn("Mail properties mail.smtp.username or mail.smtp.password not found, no jbpm mail capabilities!");
		}
		
		String jndi_datasource = "osgi:service/"+DataSource.class.getName()+"/(name="+jbpmServerName+")";
		String jndi_server = "osgi:service/"+IServerInternal.class.getName()+"/(name="+jbpmServerName+")";

		//bring up the workflow engine
		server.init(jndi_server, jndi_datasource);
		
		//expose the server side service interface
		workflowPluginServiceRegistration = context.registerService(IWorkflowPluginService.class, server, null);
		
		ServiceReference<IPluginManager> reference = context.getServiceReference(IPluginManager.class);
		if (reference != null)
		{
			addClientExtension(context,reference);
		}
		else
		{
			//wait for it
			serviceTracker = new ServiceTracker<IPluginManager,ServiceReference<IPluginManager>>(context, IPluginManager.class, null)
			{
				@Override
				public ServiceReference<IPluginManager> addingService(ServiceReference<IPluginManager> reference) 
				{
					try 
					{
						addClientExtension(context,reference);
					} 
					catch (PluginException e) 
					{
						Debug.error(e);
					}
					return super.addingService(reference);
				}
			};
		}
	}

	private void addClientExtension(BundleContext context, ServiceReference<IPluginManager> reference) throws PluginException
	{
		//expose the servoy (smart) client (rmi) plugin
		IPluginManager pluginManager = (IPluginManager) context.getService(reference);
		URL pluginURL = getClass().getResource("/resources/_workflow.jar");
		List<URL> extensionURLs = new ArrayList<URL>();
		extensionURLs.add(getClass().getResource("/lib/jbpm.jar"));
		extensionURLs.add(getClass().getResource("/lib/juel-engine.jar"));
		extensionURLs.add(getClass().getResource("/lib/juel-impl.jar"));
		extensionURLs.add(getClass().getResource("/lib/juel.jar"));
		pluginManager.addClientExtension(WorkflowPlugin.class.getName(),pluginURL,extensionURLs.toArray(new URL[extensionURLs.size()]));
	}
	
	@Override
	public void stop(BundleContext context) throws Exception 
	{
		workflowPluginServiceRegistration.unregister();
		server.close();
		serviceTracker.close();
	}
}
