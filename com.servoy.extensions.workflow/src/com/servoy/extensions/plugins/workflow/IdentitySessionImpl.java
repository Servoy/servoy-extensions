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
import java.util.List;

import org.jbpm.api.identity.Group;
import org.jbpm.api.identity.User;
import org.jbpm.pvm.internal.identity.impl.GroupImpl;
import org.jbpm.pvm.internal.identity.impl.UserImpl;
import org.jbpm.pvm.internal.identity.spi.IdentitySession;

import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IUserManager;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;

/**
 * Identity provider based on Servoy groups users
 *
 * @author jblok
 */
public class IdentitySessionImpl implements IdentitySession 
{
	public static final String SERVOY_USERS_MAIL_DOMAIN = "servoy.users.mail.domain";
	private IUserManager userManager;
	private String domain;
	private String clientId;
	
    private void init() 
    {
    	if (clientId == null)
    	{
	    	IApplicationServerSingleton as = ApplicationServerRegistry.get();
	    	if (as != null)
	    	{
		    	clientId = as.getClientId();
		    	userManager = as.getUserManager();
		    	Object servoy_user_domain = as.getService(Settings.class).getProperty(SERVOY_USERS_MAIL_DOMAIN);
		    	if (servoy_user_domain != null) 
		    	{
		    		domain = servoy_user_domain.toString();
		    	}
		    	else
		    	{
		    		Debug.log("Property "+SERVOY_USERS_MAIL_DOMAIN+" not found, consider configuring this to enable emails");
		    	}
	    	}
    	}
    }

    public Group findGroupById(String groupname) 
    {
    	init();
    	
    	GroupImpl lGroup = null;
    	try 
    	{
			IDataSet groups = userManager.getGroups(clientId);
			if (groups != null)
			{
				for (int i = 0; i < groups.getRowCount(); i++) 
				{
					Object[] group_row = groups.getRow(i);
					if (Utils.equalObjects(group_row[1],groupname))
					{
						lGroup = new GroupImpl(groupname);
						lGroup.setName(group_row[1].toString());
						break;
					}
				}
			}
		} 
    	catch (Exception e) 
    	{
			Debug.error(e);
		}
    	return lGroup;
    }
    
    public List<Group> findGroupsByUser(String username) 
    {
    	init();
    	
    	List<Group> lGroups = new ArrayList<Group>();
    	try 
    	{
    		String userUID = userManager.getUserUID(clientId, username);
    		if (userUID != null)
    		{
				String[] groups = userManager.getUserGroups(clientId,userUID);
				if (groups != null)
				{
					for (int i = 0; i < groups.length; i++) 
					{
						String gname = groups[i];
						GroupImpl lGroup = new GroupImpl(gname);
						lGroup.setName(gname);
						lGroups.add(lGroup);
					}
				}
    		}
		} 
    	catch (Exception e) 
    	{
			Debug.error(e);
		}
    	return lGroups;
    }

    public List<Group> findGroupsByUserAndGroupType(String username, String iGroupType) 
    {
    	init();
    	
    	return findGroupsByUser(username);
    }

    public User findUserById(String username) 
    {
    	init();
    	
        UserImpl lUser = null;
    	try 
    	{
    		String userUID = userManager.getUserUID(clientId, username);
    		if (userUID != null)
    		{
    			lUser = new UserImpl();
    			lUser.setGivenName(username);
    			lUser.setId(userUID);
    			if (domain != null) lUser.setBusinessEmail(username+'@'+domain);
    		}
		} 
    	catch (Exception e) 
    	{
			Debug.error(e);
		}
    	return lUser;
    }

    public List<User> findUsersByGroup(String groupname) 
    {
    	init();
    	
        List<User> lUsers = new ArrayList<User>();
    	try 
    	{
    		IDataSet users = (groupname == null ? userManager.getUsers(clientId) : userManager.getUsersByGroup(clientId,groupname));
			if (users != null)
			{
				for (int i = 0; i < users.getRowCount(); i++) 
				{
					Object[] user_row = users.getRow(i);
					String username = user_row[1].toString();
					UserImpl lUser = new UserImpl();
	    			lUser.setGivenName(username);
	    			lUser.setId(user_row[0].toString());
//	    			lUser.setDbid(Utils.getAsLong(user_row[2]));
	    			if (domain != null) lUser.setBusinessEmail(username+'@'+domain);
	    			lUsers.add(lUser);
				}
			}
		} 
    	catch (Exception e) 
    	{
			Debug.error(e);
		}
    	return lUsers;
    }

    public List<User> findUsersById(String... usernames) 
    {
    	init();
    	
		List<User> lUsers = new ArrayList<User>(usernames.length);
		for (String lUserId : usernames) 
		{
			lUsers.add( findUserById(lUserId) );
		}
		return lUsers;
    }

    public List<User> findUsers() 
    {
    	init();
    	
    	return findUsersByGroup(null);
    }
    

    /* The following methods won't be implemented */
    public String createGroup(String arg0, String arg1, String arg2) {
            throw new UnsupportedOperationException();
    }

    public void createMembership(String arg0, String arg1, String arg2) {
            throw new UnsupportedOperationException();
    }

    public String createUser(String arg0, String arg1, String arg2, String arg3) {
            throw new UnsupportedOperationException();
    }

    public void deleteGroup(String arg0) {
            throw new UnsupportedOperationException();
    }

    public void deleteMembership(String arg0, String arg1, String arg2) {
            throw new UnsupportedOperationException();
    }

    public void deleteUser(String arg0) {
            throw new UnsupportedOperationException();
    }
}


