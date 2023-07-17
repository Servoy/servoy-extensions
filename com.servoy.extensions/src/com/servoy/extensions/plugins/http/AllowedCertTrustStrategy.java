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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.hc.core5.ssl.TrustStrategy;

import com.servoy.j2db.J2DBGlobals;
import com.servoy.j2db.util.Debug;

class AllowedCertTrustStrategy implements TrustStrategy
{

	private X509Certificate[] lastCertificates;
	private CertificatesHolder holder;

	/*
	 * (non-Javadoc)
	 *
	 * @see org.apache.http.conn.ssl.TrustStrategy#isTrusted(java.security.cert.X509Certificate[], java.lang.String)
	 */
	@Override
	public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
	{
		this.lastCertificates = chain;
		return getCertificatesHolder().isValid(chain);
	}

	/**
	 *
	 */
	public X509Certificate[] getAndClearLastCertificates()
	{
		X509Certificate[] tmp = lastCertificates;
		lastCertificates = null;
		return tmp;
	}

	/**
	 * @return the holder
	 */
	public CertificatesHolder getCertificatesHolder()
	{
		if (holder == null)
		{
			File file = new File(System.getProperty("user.home"), J2DBGlobals.CLIENT_LOCAL_DIR + "servoy.ks"); //$NON-NLS-1$//$NON-NLS-2$
			if (file.exists())
			{
				ObjectInputStream ois = null;
				try
				{
					ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
					holder = (CertificatesHolder)ois.readObject();
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
				finally
				{
					try
					{
						if (ois != null) ois.close();
					}
					catch (Exception e)
					{
					}
				}
			}
			if (holder == null) holder = new CertificatesHolder();
		}
		return holder;
	}

	public void add(X509Certificate[] certificates)
	{
		getCertificatesHolder();
		holder.add(certificates);
		File file = new File(System.getProperty("user.home"), J2DBGlobals.CLIENT_LOCAL_DIR + "servoy.ks"); //$NON-NLS-1$//$NON-NLS-2$
		if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
		ObjectOutputStream oos = null;
		try
		{
			oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			oos.writeObject(holder);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		finally
		{
			try
			{
				if (oos != null) oos.close();
			}
			catch (Exception e)
			{
			}
		}
	}

}