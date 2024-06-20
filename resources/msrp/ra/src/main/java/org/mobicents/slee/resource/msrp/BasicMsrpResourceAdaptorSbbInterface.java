/*
 * JBoss, Home of Professional Open Source
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

import java.net.URI;

import javax.sdp.SessionDescription;
import javax.slee.facilities.Tracer;

import javax.net.msrp.SessionListener;
import javax.net.msrp.Session;
import javax.net.msrp.exceptions.ParseException;

/**
 * A straightforward implementation of the RA types' SBB interface.
 * @author tuijldert
 */
public class BasicMsrpResourceAdaptorSbbInterface implements MsrpResourceAdaptorSbbInterface {
	private final MsrpResourceAdaptor ra;
	private final Tracer trc;
	private boolean active = false;

	public BasicMsrpResourceAdaptorSbbInterface(MsrpResourceAdaptor ra) {
		this.ra = ra;
		this.trc = ra.getTracer();
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	private void checkState() throws IllegalStateException {
		if (!active) {
			throw new IllegalStateException("RA not active");
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpResourceAdaptorSbbInterface#connect(java.lang.String)
	 */
	@Override
	public MsrpSession connect(String sessionId) throws ParseException {
		checkState();

		if(sessionId == null) {
			throw new NullPointerException("null connection id");
		}
		MsrpActivityHandle handle = new MsrpActivityHandle(sessionId);
    	if (ra.getActivity(handle) == null) {

			try {
				Session session = Session.create(false, true, ra.getAddress());
				MsrpSession msession = ra.createActivity(sessionId, session, handle);
				SessionListener listener = new MsrpSessionListener(ra, msession);
				session.setListener(listener);
				return msession;
			} catch (Exception e) {
	    		throw new ParseException(e);
			}
    	} else {
    		String msg = "Connection already exists!";
    		if(trc.isInfoEnabled())
				trc.info(msg);
    		throw new ParseException(msg);
    	}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpResourceAdaptorSbbInterface#connect(java.lang.String, javax.sdp.SessionDescription)
	 */
	@Override
	public MsrpSession connect(String sessionId, SessionDescription sdp) throws ParseException {
		checkState();

		if(sessionId == null) {
			throw new NullPointerException("null connection id");
		}
		URI fromUri = BasicMsrpSession.getFromPath(sdp);
		MsrpActivityHandle handle = new MsrpActivityHandle(sessionId);
    	if (ra.getActivity(handle) == null) {
    		if(trc.isInfoEnabled())
    			trc.info("Connecting to " + fromUri);

			try {
				Session session = Session.create(false, true, fromUri, ra.getAddress());
				MsrpSession msession = ra.createActivity(sessionId, session, handle);
				SessionListener listener = new MsrpSessionListener(ra, msession);
				session.setListener(listener);
				return msession;
			} catch (Exception e) {
	    		throw new ParseException(e);
			}
    	} else {
    		String msg = "Connection already exists!";
    		if(trc.isInfoEnabled())
				trc.info(msg);
    		throw new ParseException(msg);
    	}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpResourceAdaptorSbbInterface#connect(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public MsrpSession connect(String sessionId, String to, String username, String password) {
		//TODO: implement secure version of setting up a session...
		return null;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.msrp.MsrpResourceAdaptorSbbInterface#getMsrpSession(java.lang.String)
	 */
	@Override
	public MsrpSession getMsrpSession(String sessionId) {
		checkState();
		return (MsrpSession) ra.getActivity(new MsrpActivityHandle(sessionId));
	}
}
