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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

import javax.sdp.Connection;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;

import javax.net.msrp.Session;
import javax.net.msrp.Transaction;
import javax.net.msrp.exceptions.ParseException;
import javax.net.msrp.exceptions.IllegalUseException;

/**
 * Straightforward implementation of the {@link MsrpSession}.
 * <BR>
 * This is the Activity Object for the MSRP Resource Adaptor.
 * 
 * @author T. Uijldert
 * @version 1.0
 */
public class BasicMsrpSession implements MsrpSession {
    private static Charset utf8 = Charset.forName("UTF-8");

    protected static final int MAX_SIZE = 10240;

	private static final String PATH_ATTRIBUTE = "path";
	private static final String ACCEPTT_ATTRIBUTE = "accept-types";
	private static final String MSIZE_ATTRIBUTE = "max-size";
	private static final String[] formats = { "*" };

	private static final long serialVersionUID = 1L;

	private MsrpResourceAdaptor ra;
	private String sessionId;
	private Session session;
	private final String toString;
	private ArrayList<URI> toList;

	public BasicMsrpSession(String sessionId, Session session, MsrpResourceAdaptor ra) {
		this.sessionId = sessionId;
		this.session = session;
		this.ra = ra;
		this.toString = "MsrpSession { connectionId="+sessionId+",connection="+session.toString()+"}";
	}

	/**
	 * @param sdp
	 * @return
	 * @throws ParseException
	 */
	protected static URI getFromPath(SessionDescription sdp) throws ParseException {
		URI fromUri = null;
		String msrpPath;
		try {
			msrpPath = ((MediaDescription) sdp.getMediaDescriptions(false).firstElement())
											.getAttribute(PATH_ATTRIBUTE);
			fromUri = new URI(msrpPath);
		} catch (Exception e) {
    		throw new ParseException(e);
		}
		return fromUri;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#getLocalSdp()
	 */
	public SessionDescription getLocalSdp() {
		try {
			return getLocalSdp(session.getURI());
		} catch (SdpException e) {
			return null;
		}
	}

	protected static SessionDescription getLocalSdp(URI localUri) throws SdpException {
		SessionDescription localSdp;

		SdpFactory sf = SdpFactory.getInstance();
		localSdp = sf.createSessionDescription();
        localSdp.getOrigin().setSessionId(new Date().getTime());
        localSdp.getOrigin().setSessionVersion(1);
		localSdp.setConnection(sf.createConnection(Connection.IN, Connection.IP4, localUri.getHost()));
		// TODO: also cater for TLS...
		MediaDescription medium = sf.createMediaDescription("message", localUri.getPort(), 0, "TCP/MSRP", formats);
		medium.setAttribute(PATH_ATTRIBUTE, localUri.toString());
		// TODO: subject to negotiation...
		medium.setAttribute(ACCEPTT_ATTRIBUTE, "text/*");
		medium.setAttribute(MSIZE_ATTRIBUTE, Integer.toString(MAX_SIZE));
		Vector<MediaDescription> media = new Vector<MediaDescription>(1);
		media.add(medium);
		localSdp.setMediaDescriptions(media);
		return localSdp;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#setRemoteSdp(javax.sdp.SessionDescription)
	 */
	public void setRemoteSdp(SessionDescription sdp)
			throws SdpException, URISyntaxException, IOException {
		/* TODO: (Oct. 2012) -when MSRP implementation is more elaborate- possibly
		 * scan for changed or added paths and forward these changes to the
		 * library. For now, use a one-shot.
		 */
		if (toList != null)
			return;
		@SuppressWarnings("unchecked")
		Vector<MediaDescription> media = sdp.getMediaDescriptions(false);
		if (media.size() > 0) {
			toList = new ArrayList<URI>(media.size());
			for (MediaDescription md : media) {
				toList.add(new URI(md.getAttribute(PATH_ATTRIBUTE)));
			}
			session.setToPath(toList);
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#sendMessage(java.lang.CharSequence)
	 */
	public void sendMessage(CharSequence message) {
		sendMessage(message.toString().getBytes(utf8));
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#sendMessage(byte[])
	 */
	public void sendMessage(byte[] message) {
		sendMessage("text/plain", message);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#sendMessage(java.lang.String, byte[])
	 */
	public void sendMessage(String contentType, byte[] message) {
		session.sendMessage(contentType, message);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#sendWrappedMessage(java.lang.String, java.lang.String, java.lang.String, java.lang.CharSequence)
	 */
	public void sendWrappedMessage(	String wrapType, String from,
									String to, CharSequence message) {
		session.sendWrappedMessage(	wrapType, from, to,
									"text/plain", message.toString().getBytes(utf8));
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#sendNickResult(javax.net.msrp.Transaction, int, java.lang.String)
	 */
	public void sendNickResult(Transaction reqResp, int response, String comment)
									throws IllegalUseException {
		session.sendNickResult(reqResp, response, comment);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpSession#disconnect()
	 */
	public void disconnect() {
		if (session != null)
			session.tearDown();
		if (sessionId != null)
			ra.endActivity(sessionId);
	}

	public String getSessionId() { return sessionId; }

	public Object getSession() { return session; }

	public boolean equals(Object o) {
		if (o != null && o.getClass() == this.getClass()) {
			return ((BasicMsrpSession)o).sessionId.equals(this.sessionId);
		}
        return false;
	}

	public int hashCode() { return sessionId.hashCode(); }

	public String toString() { return toString; }
}
