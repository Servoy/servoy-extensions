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
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.ByteArrayBody;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.MimeTypes;
import com.servoy.j2db.util.Utils;

/**
 * @author pbakker
 */
public class BaseEntityEnclosingRequest extends BaseRequest
{
	private String bodyContent;
	private String bodyMimeType = ContentType.TEXT_PLAIN.getMimeType();
	protected String charset = "UTF8";

	private List<FileInfo> files;
	private List<NameValuePair> params;
	private boolean forceMultipart = false;

	public BaseEntityEnclosingRequest()
	{
	}

	public BaseEntityEnclosingRequest(String url, CloseableHttpAsyncClient hc, HttpUriRequestBase method, HttpPlugin httpPlugin, Builder requestConfigBuilder,
		BasicCredentialsProvider proxyCredentialsProvider)
	{
		super(url, hc, method, httpPlugin, requestConfigBuilder, proxyCredentialsProvider);
		clearFiles();
	}

	protected final void clearFiles()
	{
		files = new ArrayList<FileInfo>();
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
		this.bodyContent = content;
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
		this.bodyContent = content;
		this.bodyMimeType = mimeType;
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

		if (files.size() == 0 && !forceMultipart)
		{
			if (params != null)
			{
				entity = new UrlEncodedFormEntity(params, Charset.forName(charset));
			}
			else if (!Utils.stringIsEmpty(bodyContent))
			{
				entity = new StringEntity(bodyContent, ContentType.create(bodyMimeType, charset));
				bodyContent = null;
			}
		}
		else if (files.size() == 1 && (params == null || params.size() == 0) && !forceMultipart)
		{
			FileInfo info = files.get(0);
			if (info.file instanceof File)
			{
				File f = (File)info.file;
				String contentType = info.mimeType != null ? info.mimeType : MimeTypes.getContentType(Utils.readFile(f, 32), f.getName());
				entity = new FileEntity(f, ContentType.create(contentType != null ? contentType : "binary/octet-stream")); //$NON-NLS-1$
			}
			else if (info.file instanceof JSFile)
			{
				JSFile f = (JSFile)info.file;
				String contentType = info.mimeType != null ? info.mimeType : f.js_getContentType();
				entity = new InputStreamEntity(f.getAbstractFile().getInputStream(), f.js_size(),
					ContentType.create(contentType != null ? contentType : "binary/octet-stream")); //$NON-NLS-1$
			}
			else
			{
				Debug.error("could not add file to post request unknown type: " + info);
			}
		}
		else
		{
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.setMode(HttpMultipartMode.LEGACY);

			// For File parameters
			for (FileInfo info : files)
			{
				Object file = info.file;
				if (file instanceof File)
				{
					String contentType = info.mimeType != null ? info.mimeType
						: MimeTypes.getContentType(Utils.readFile((File)file, 32), ((File)file).getName());
					builder.addPart(info.parameterName,
						new FileBody((File)file, contentType != null ? ContentType.create(contentType) : ContentType.DEFAULT_BINARY));
				}
				else if (file instanceof JSFile)
				{
					String contentType = info.mimeType != null ? info.mimeType : ((JSFile)file).js_getContentType();
					builder.addPart(info.parameterName, new ByteArrayBody(Utils.getBytesFromInputStream(((JSFile)file).getAbstractFile().getInputStream()),
						ContentType.create(contentType != null ? contentType : "binary/octet-stream"), ((JSFile)file).js_getName()));
				}
				else
				{
					Debug.error("could not add file to post request unknown type: " + info);
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
	 * Force this request to prepare a "Content-Type: multipart/form-data" formatted message
	 * even if only one file or only a number of parameter were added to it.<br/><br/>
	 *
	 * It is useful because some servers require this (they only support multipart - even if you don't need to send multiple things).
	 * Before Servoy 2021.03 you could force it to send multipart by adding a dummy parameter together with a single file (or the other way around) - if the server didn't object to that dummy content...<br/><br/>
	 *
	 * Default value: false. (if you only add one file or only parameters it will not generate a multipart request)
	 *
	 * @since 2021.03
	 * @param forceMultipart if true, this request will send a multipart/form-data message even if you only add one file or only parameters. If false (default) it will send multipart only in case of multiple files or one file plus at least one parameter.
	 */
	public void js_forceMultipart(boolean forceMultipart)
	{
		this.forceMultipart = forceMultipart;
	}

	/**
	 * Add a file to the post; it will try to get the correct mime type from the file name or the first bytes.<br/><br/>
	 *
	 * If you add a single file then this will be a single file (so not a multi-part) post. If you want/need multi-part
	 * then you have to either add multiple files or a file and at least a parameter via addParameter(...).
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
				files.add(new FileInfo(parameterName, fileName, f, null));
				return true;
			}
		}
		return false;
	}

	/**
	 * Add a file to the post with a given mime type; could also be used to force the default 'application/octet-stream' on it,
	 * because this plugin will try to guess the correct mime type for the given file otherwise (based on the name or the bytes).<br/><br/>
	 *
	 * If you add a single file then this will be a single file (so not a multi-part) post. If you want/need multi-part
	 * then you have to either add multiple files or both a file and one or more parameters using addParameter(...).
	 *
	 * @sample
	 * poster.addFile('myFileParamName','manual.doc','c:/temp/manual_01a.doc', 'application/msword')
	 * poster.addFile(null,'postXml.xml','c:/temp/postXml.xml', 'text/xml') // sets the xml to post
	 *
	 * var f = plugins.file.convertToJSFile('./somefile02.txt')
	 * if (f && f.exists()) poster.addFile('myTxtFileParamName','somefile.txt', f, 'text/plain')
	 *
	 * f = plugins.file.convertToJSFile('./anotherfile_v2b.txt')
	 * if (f && f.exists()) poster.addFile('myOtherTxtFileParamName', f, 'text/plain')
	 *
	 * @param parameterName
	 * @param fileName
	 * @param fileLocation
	 * @param mimeType The mime type that must be used could be the real default ('application/octet-stream') if the files one (by name or by its first bytes) is not good.
	 */
	public boolean js_addFile(String parameterName, String fileName, String fileLocation, String mimeType)
	{
		if (fileLocation != null)
		{
			File f = new File(fileLocation);
			if (f.exists())
			{
				files.add(new FileInfo(parameterName, fileName, f, mimeType));
				return true;
			}
		}
		return false;
	}

	/**
	 * Add a file to the post; it will try to get the correct mime type from the file name or the first bytes.<br/><br/>
	 *
	 * If you add a single file then this will be a single file (so not a multi-part) post. If you want/need multi-part
	 * then you have to either add multiple files or both a file and one or more parameters using addParameter(...).
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
			files.add(new FileInfo(parameterName, ((JSFile)jsFile).js_getName(), jsFile, null));
			return true;
		}
		return false;
	}

	/**
	 * Add a file to the post with a given mime type; could also be used to force the default 'application/octet-stream' on it,
	 * because this plugin will try to guess the correct mime type for the given file otherwise (based on the name or the bytes).<br/><br/>
	 *
	 * If you add a single file then this will be a single file (so not a multi-part) post. If you want/need multi-part
	 * then you have to either add multiple files or both a file and one or more parameters using addParameter(...).
	 *
	 * @sample
	 * poster.addFile('myFileParamName','manual.doc','c:/temp/manual_01a.doc', 'application/msword')
	 * poster.addFile(null,'postXml.xml','c:/temp/postXml.xml', 'text/xml') // sets the xml to post
	 *
	 * var f = plugins.file.convertToJSFile('./somefile02.txt')
	 * if (f && f.exists()) poster.addFile('myTxtFileParamName','somefile.txt', f, 'text/plain')
	 *
	 * f = plugins.file.convertToJSFile('./anotherfile_v2b.txt')
	 * if (f && f.exists()) poster.addFile('myOtherTxtFileParamName', f, 'text/plain')
	 *
	 * @param parameterName
	 * @param jsFile
	 * @param mimeType The mime type that must be used could be the real default ('application/octet-stream') if the files one (by name or by its first bytes) is not good.
	 */
	public boolean js_addFile(String parameterName, Object jsFile, String mimeType)
	{
		if (jsFile instanceof JSFile && ((JSFile)jsFile).js_exists())
		{
			files.add(new FileInfo(parameterName, ((JSFile)jsFile).js_getName(), jsFile, mimeType));
			return true;
		}
		return false;
	}


	/**
	 * Add a file to the post; it will try to get the correct mime type from the file name or the first bytes.<br/><br/>
	 *
	 * If you add a single file then this will be a single file (so not a multi-part) post. If you want/need multi-part
	 * then you have to either add multiple files or both a file and one or more parameters using addParameter(...).
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
			files.add(new FileInfo(parameterName, fileName, jsFile, null));
			return true;
		}
		return false;
	}

	/**
	 * Add a file to the post with a given mime type; could also be used to force the default 'application/octet-stream' on it,
	 * because this plugin will try to guess the correct mime type for the given file otherwise (based on the name or the bytes).<br/><br/>
	 *
	 * If you add a single file then this will be a single file (so not a multi-part) post. If you want/need multi-part
	 * then you have to either add multiple files or both a file and one or more parameters using addParameter(...).
	 *
	 * @sample
	 * poster.addFile('myFileParamName','manual.doc','c:/temp/manual_01a.doc', 'application/msword')
	 * poster.addFile(null,'postXml.xml','c:/temp/postXml.xml', 'text/xml') // sets the xml to post
	 *
	 * var f = plugins.file.convertToJSFile('./somefile02.txt')
	 * if (f && f.exists()) poster.addFile('myTxtFileParamName','somefile.txt', f, 'text/plain')
	 *
	 * f = plugins.file.convertToJSFile('./anotherfile_v2b.txt')
	 * if (f && f.exists()) poster.addFile('myOtherTxtFileParamName', f, 'text/plain')
	 *
	 * @param parameterName
	 * @param fileName
	 * @param jsFile
	 * @param mimeType The mime type that must be used could be the real default ('application/octet-stream') if the files one (by name or by its first bytes) is not good.
	 */
	public boolean js_addFile(String parameterName, String fileName, Object jsFile, String mimeType)
	{
		if (jsFile instanceof JSFile && ((JSFile)jsFile).js_exists())
		{
			files.add(new FileInfo(parameterName, fileName, jsFile, mimeType));
			return true;
		}
		return false;
	}

	/**
	 * Add a parameter to the post.<br/><br/>
	 *
	 * If there is also at least one file added to this request using addFile(...) then a multi-part post will be generated.
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
