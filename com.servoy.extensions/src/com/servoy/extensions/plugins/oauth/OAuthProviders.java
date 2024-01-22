/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

package com.servoy.extensions.plugins.oauth;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IConstantsObject;

/**
 * Contains supported OAuth apis.
 * @author emera
 */
@ServoyDocumented(scriptingName = "OAuthProviders")
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class OAuthProviders implements IConstantsObject
{
	//scribe-java
	public static final String MICROSOFT_AD = "com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api";
	public static final String FACEBOOK = "com.github.scribejava.apis.FacebookApi";
	public static final String LINKEDIN = "com.github.scribejava.apis.LinkedInApi20";

	//scribe-java not tested by us
	public static final String ASANA = "com.github.scribejava.apis.Asana20Api";
	public static final String AUTOMATIC = "com.github.scribejava.apis.AutomaticAPI";
	public static final String BOX = "com.github.scribejava.apis.BoxApi20";
	public static final String DATAPORTEN = "com.github.scribejava.apis.DataportenApi";
	public static final String DISCORD = "com.github.scribejava.apis.DiscordApi";
	public static final String DROPBOX = "com.github.scribejava.apis.DropboxApi";
	public static final String FITBIT = "com.github.scribejava.apis.FitbitApi20";
	public static final String FOURSQUARE = "com.github.scribejava.apis.Foursquare2Api";
	public static final String FRAPPE = "com.github.scribejava.apis.FrappeApi";
	public static final String GENIUS = "com.github.scribejava.apis.GeniusApi";
	public static final String GITHUB = "com.github.scribejava.apis.GitHubApi";
	public static final String GOOGLE = "com.github.scribejava.apis.GoogleApi20";
	public static final String HIORG = "com.github.scribejava.apis.HiOrgServerApi20";
	public static final String IMGUR = "com.github.scribejava.apis.ImgurApi";
	public static final String LIVE = "com.github.scribejava.apis.LiveApi";
	public static final String MEETUP = "com.github.scribejava.apis.MeetupApi20";
	public static final String PINTEREST = "com.github.scribejava.apis.PinterestApi";
	public static final String SALESFORCE = "com.github.scribejava.apis.SalesforceApi";
	public static final String STACKEXCHANGE = "com.github.scribejava.apis.StackExchangeApi";
	public static final String VIADEO = "com.github.scribejava.apis.ViadeoApi";
	public static final String VKONTAKTE = "com.github.scribejava.apis.VkontakteApi";
	public static final String WUNDERLIST = "com.github.scribejava.apis.WunderlistAPI";
	//TODO only in the latest version, did not upgrade because it has a bug.. public static final String XERO = "com.github.scribejava.apis.XeroApi20";
	public static final String YAHOO = "com.github.scribejava.apis.YahooApi20";

	//added by us
	public static final String INTUIT = "com.servoy.extensions.plugins.oauth.apis.IntuitApi";
	public static final String OKTA = "com.servoy.extensions.plugins.oauth.apis.OktaApi";
	public static final String UPS = "com.servoy.extensions.plugins.oauth.apis.UPSApi";

}