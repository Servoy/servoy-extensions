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

package com.servoy.extensions.plugins.workflow.shared;

import java.io.Serializable;

/**
 * Make an serializable deployment
 * @author jblok
 */
public class Deployment implements Serializable
{
	private static final long serialVersionUID = -4008347272918863939L;

	public String id;
	public String name;
	public String state;
	public long timestamp;
	
	public Deployment(String id,String name,String state,long timestamp) 
	{
		this.id = id;
		this.name = name;
		this.state = state;
		this.timestamp = timestamp;
	}
	
	public String getId() 
	{
		return id;
	}

	public String getName() 
	{
		return name;
	}

	public String getState() 
	{
		return state;
	}

	public long getTimestamp() 
	{
		return timestamp;
	}
}
