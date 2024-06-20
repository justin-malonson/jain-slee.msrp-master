/*
 * Copyright 2012 by the @authors tag.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.slee.resource.msrp;

import javax.net.msrp.exceptions.ParseException;
import javax.sdp.SessionDescription;

/**
 * This is the MSRP Resource Adaptor's Interface that Sbbs can use.
 * 
 * @author T. Uijldert
 * @version 1.0
 * 
 */
public interface MsrpResourceAdaptorSbbInterface {

	/**
	 * Retrieves the MSRP session with the specified id.
	 * @param sessionId the session identification 
	 * @return the session
	 */
	public MsrpSession getMsrpSession(String sessionId);

	/**
	 * Creates a new -passive- (server) MSRP session.
	 * It will wait for a connection by @c sdp contents.
	 * 
	 * @param sessionId Identifies the session to create.
	 * @param sdp {@link SessionDescription} containing from-&lt;msrp-uri&gt;.
	 * @return the msrp session created
	 * @throws ParseException something went wrong.
	 */
	public MsrpSession connect(String sessionId, SessionDescription sdp) throws ParseException;

	/**
	 * Create a new -active- (client) MSRP session...
	 * Should be followed by a {@link MsrpSession#setRemoteSdp(SessionDescription)} to complete.
	 * 
	 * @param sessionId	Identifies the session to create
	 * @return the msrp session created
	 * @throws ParseException something went wrong
	 */
	public MsrpSession connect(String sessionId) throws ParseException;

	/**
	 * Create a active (client) MSRP session, secured.
	 * @param sessionId Identifies the session to create
	 * @param to
	 * @param username
	 * @param password
	 * @return
	 */
	public MsrpSession connect(String sessionId, String to, String username, String password);
}
