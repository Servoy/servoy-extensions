/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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

package com.servoy.extensions.plugins.http;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jcompagner
 */
public class CertificatesHolder implements Serializable
{
	private final List<X509Certificate[]> certificateChains = new ArrayList<X509Certificate[]>();

	public CertificatesHolder()
	{
	}

	public void add(X509Certificate[] certificateChain)
	{
		certificateChains.add(certificateChain);
	}

	public boolean isValid(X509Certificate[] certificateChain)
	{
		for (X509Certificate[] x509Certificates : certificateChains)
		{
			if (x509Certificates.length == certificateChain.length)
			{
				boolean equals = true;
				for (int i = 0; i < x509Certificates.length; i++)
				{
					if (!x509Certificates[i].equals(certificateChain[i]))
					{
						equals = false;
						break;
					}
				}
				if (equals) return true;
			}
		}
		return false;
	}
}
