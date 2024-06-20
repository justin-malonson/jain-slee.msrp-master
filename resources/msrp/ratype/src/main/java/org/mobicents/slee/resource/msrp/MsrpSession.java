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

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;

import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

import javax.net.msrp.Transaction;
import javax.net.msrp.exceptions.IllegalUseException;

/**
 * This represents a generic -not bound with any specific API- MSRP session.
 * @author T. Uijldert
 * @version 1.0
 */
public interface MsrpSession extends Serializable {

	/**
	 * Get the current local endpoint {@link SessionDescription} of this session.
	 * @return the {@link SessionDescription} that can be used in the SDP.
	 */
	public SessionDescription getLocalSdp();

	/**
	 * Set the remote endpoint {@link SessionDescription} for this session.
	 * @param sdp	the {@link SessionDescription}
	 * @throws SdpException problems with the offered {@link SessionDescription}
	 * @throws URISyntaxException problems with the offered to-path(s)
	 * @throws IOException problems establishing session.
	 */
	public void setRemoteSdp(SessionDescription sdp) throws SdpException, URISyntaxException, IOException;

	/**
	 * Do an MSRP SEND with given message as payload.
	 * @param message the payload
	 */
	public void sendMessage(CharSequence message);

	/**
	 * Do an MSRP SEND with given message as payload.
	 * @param message the payload
	 */
	public void sendMessage(byte[] message);

	/**
	 * Do an MSRP SEND with given message as payload.
	 * @param contentType MIME type of the payload
	 * @param message the payload
	 */
	public void sendMessage(String contentType, byte[] message);

	/**
	 * Do an MSRP SEND with the given wrapped message as payload.
	 * @param wrapType	MIME type of the wrapping.
	 * @param from from address
	 * @param to to address
	 * @param message the payload to wrap
	 */
	public void sendWrappedMessage(String wrapType, String from, String to, CharSequence message);

	/**
	 * Send a result to a NICKNAME request
	 * @param reqResp the originating request (transaction)
	 * @param reponse the status to return
	 * @param comment an additional clarifying comment to the returned status
	 * @throws IllegalUseException invalid argument or state 
	 */
	public void sendNickResult(Transaction reqResp, int reponse, String comment)
										throws IllegalUseException;

	/**
	 * End the session.
	 */
	public void disconnect();

	/**
	 * @return the unique identification of this session.
	 */
	public String getSessionId();

	/**
	 * @return the actual MSRP session.
	 */
	public Object getSession();
}
