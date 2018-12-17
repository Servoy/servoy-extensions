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

package com.servoy.extensions.plugins.http;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Utils;

/**
 * @author pbakker
 */
public class BaseEntityEnclosingRequest extends BaseRequest
{
	private String content;
	private String mimeType = ContentType.TEXT_PLAIN.getMimeType();
	protected String charset = HTTP.UTF_8;

	private Map<Pair<String, String>, Object> files;
	private List<NameValuePair> params;


	public BaseEntityEnclosingRequest()
	{
	}

	public BaseEntityEnclosingRequest(String url, CloseableHttpClient hc, HttpRequestBase method, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, method, httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
		clearFiles();
	}

	protected final void clearFiles()
	{
		files = new HashMap<Pair<String, String>, Object>();
	}

	/**
	 * Set the body of the request.
	 *
	 * @sample
	 * method.setBodyContent(content)
	 *
	 * @param content
	 */
	public void js_setBodyContent(String content)
	{
		this.content = content;
	}

	/**
	 * Set the body of the request and content mime type.
	 *
	 * @sample
	 * method.setBodyContent(content, 'text/xml')
	 *
	 * @param content
	 * @param mimeType
	 */
	public void js_setBodyContent(String content, String mimeType)
	{
		this.content = content;
		this.mimeType = mimeType;
	}


	/**
	 * Set the charset used when posting. If this is null or not called it will use the default charset (UTF-8).
	 *
	 * @sample
	 * var client = plugins.http.createNewHttpClient();
	 * var poster = client.createPostRequest('https://twitter.com/statuses/update.json');
	 * poster.addParameter('status',scopes.globals.textToPost);
	 * poster.addParameter('source','Test Source');
	 * poster.setCharset('UTF-8');
	 * var httpCode = poster.executeRequest(scopes.globals.twitterUserName, scopes.globals.twitterPassword).getStatusCode() // httpCode 200 is ok
	 *
	 * @param charset
	 */
	public void js_setCharset(String s)
	{
		this.charset = s;
	}

	@Override
	protected HttpEntity buildEntity() throws Exception
	{
		HttpEntity entity = null;
		if (files.size() == 0)
		{
			if (params != null)
			{
				entity = new UrlEncodedFormEntity(params, charset);
			}
			else if (!Utils.stringIsEmpty(content))
			{
				entity = new StringEntity(content, ContentType.create(mimeType, charset));
				content = null;
			}
		}
		else if (files.size() == 1 && (params == null || params.size() == 0))
		{
			Object f = files.values().iterator().next();
			if (f instanceof File)
			{
				entity = new FileEntity((File)f, ContentType.create("binary/octet-stream")); //$NON-NLS-1$
			}
			else if (f instanceof JSFile)
			{
				entity = new InputStreamEntity(((JSFile)f).getAbstractFile().getInputStream(), ((JSFile)f).js_size(),
					ContentType.create("binary/octet-stream")); //$NON-NLS-1$
			}
			else
			{
				Debug.error("could not add file to post request unknown type: " + f);
			}
		}
		else
		{
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

			// For File parameters
			for (Entry<Pair<String, String>, Object> e : files.entrySet())
			{
				Object file = e.getValue();
				if (file instanceof File)
				{
					builder.addPart(e.getKey().getLeft(), new FileBody((File)file));
				}
				else if (file instanceof JSFile)
				{
					builder.addPart(e.getKey().getLeft(), new ByteArrayBody(Utils.getBytesFromInputStream(((JSFile)file).getAbstractFile().getInputStream()),
						ContentType.create("binary/octet-stream"), ((JSFile)file).js_getName()));
				}
				else
				{
					Debug.error("could not add file to post request unknown type: " + file);
				}
			}
			// add the parameters
			if (params != null)
			{
				Iterator<NameValuePair> it = params.iterator();
				while (it.hasNext())
				{
					NameValuePair nvp = it.next();
					// For usual String parameters
					builder.addPart(nvp.getName(), new StringBody(nvp.getValue(), ContentType.create("text/plain", Charset.forName(charset))));
				}
			}
			entity = builder.build();
		}

		// entity may have been set already, see PutRequest.js_setFile
		return entity;
	}


	/**
	 * Add a file to the post.
	 *
	 * @sample
	 * poster.addFile('myFileParamName','manual.doc','c:/temp/manual_01a.doc')
	 * poster.addFile(null,'postXml.xml','c:/temp/postXml.xml') // sets the xml to post
	 *
	 * var f = plugins.file.convertToJSFile('./somefile02.txt')
	 * if (f && f.exists()) poster.addFile('myTxtFileParamName','somefile.txt', f)
	 *
	 * f = plugins.file.convertToJSFile('./anotherfile_v2b.txt')
	 * if (f && f.exists()) poster.addFile('myOtherTxtFileParamName', f)
	 *
	 * @param parameterName
	 * @param fileName
	 * @param fileLocation
	 */
	public boolean js_addFile(String parameterName, String fileName, String fileLocation)
	{
		if (fileLocation != null)
		{
			File f = new File(fileLocation);
			if (f.exists())
			{
				files.put(new Pair<String, String>(parameterName, fileName), f);
				return true;
			}
		}
		return false;
	}

	/**
	 * Add a file to the post.
	 *
	 * @sample
	 * poster.addFile('myFileParamName','manual.doc','c:/temp/manual_01a.doc')
	 * poster.addFile(null,'postXml.xml','c:/temp/postXml.xml') // sets the xml to post
	 *
	 * var f = plugins.file.convertToJSFile('./somefile02.txt')
	 * if (f && f.exists()) poster.addFile('myTxtFileParamName','somefile.txt', f)
	 *
	 * f = plugins.file.convertToJSFile('./anotherfile_v2b.txt')
	 * if (f && f.exists()) poster.addFile('myOtherTxtFileParamName', f)
	 *
	 * @param parameterName
	 * @param jsFile
	 */
	public boolean js_addFile(String parameterName, Object jsFile)
	{
		if (jsFile instanceof JSFile && ((JSFile)jsFile).js_exists())
		{
			files.put(new Pair<String, String>(parameterName, ((JSFile)jsFile).js_getName()), jsFile);
			return true;
		}
		return false;
	}

	/**
	 * Add a file to the post.
	 *
	 * @sample
	 * poster.addFile('myFileParamName','manual.doc','c:/temp/manual_01a.doc')
	 * poster.addFile(null,'postXml.xml','c:/temp/postXml.xml') // sets the xml to post
	 *
	 * var f = plugins.file.convertToJSFile('./somefile02.txt')
	 * if (f && f.exists()) poster.addFile('myTxtFileParamName','somefile.txt', f)
	 *
	 * f = plugins.file.convertToJSFile('./anotherfile_v2b.txt')
	 * if (f && f.exists()) poster.addFile('myOtherTxtFileParamName', f)
	 *
	 * @param parameterName
	 * @param fileName
	 * @param jsFile
	 */
	public boolean js_addFile(String parameterName, String fileName, Object jsFile)
	{
		if (jsFile instanceof JSFile && ((JSFile)jsFile).js_exists())
		{
			files.put(new Pair<String, String>(parameterName, fileName), jsFile);
			return true;
		}
		return false;
	}

	/**
	 * Add a parameter to the post.
	 *
	 * @sample
	 * poster.addParameter('name','value')
	 * poster.addParameter(null,'value') //sets the content to post
	 *
	 * @param name
	 * @param value
	 */
	public boolean js_addParameter(String name, String value)
	{
		if (name != null)
		{
			if (params == null)
			{
				params = new ArrayList<NameValuePair>();
			}
			params.add(new BasicNameValuePair(name, value));
			return true;
		}

		if (value != null)
		{
			js_setBodyContent(value);
			return true;
		}

		return false;
	}

}
