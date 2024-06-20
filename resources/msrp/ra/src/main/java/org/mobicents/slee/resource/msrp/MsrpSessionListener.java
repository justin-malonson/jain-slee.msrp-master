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

//import javax.slee.facilities.Tracer;

import javax.net.msrp.*;
import javax.net.msrp.events.MessageAbortedEvent;
import javax.net.msrp.ConnectionLostEvent;
import javax.net.msrp.IncomingMessage;
import javax.net.msrp.Message;
import javax.net.msrp.Session;
import javax.net.msrp.Transaction;
import javax.net.msrp.ReportEvent;

/**
 * Catches events from the MSRP stack.
 * @author tuijldert
 */
class MsrpSessionListener implements SessionListener {
	private MsrpResourceAdaptor ra;
//	private final Tracer trc;

	private MsrpSession session;

	public MsrpSessionListener(MsrpResourceAdaptor ra, MsrpSession session) {
		this.ra = ra;
//		this.trc = ra.getTracer();
		this.session = session;
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#acceptHook(javax.net.msrp.Session, javax.net.msrp.IncomingMessage)
	 */
	@Override
	public boolean acceptHook(Session session, IncomingMessage message) {
		if (!this.session.getSession().equals(session))
			return false;
		if (message.getSize() > BasicMsrpSession.MAX_SIZE)	// too big.
			return false;
		message.setDataContainer(new MemoryDataContainer((int) message.getSize()));
		return true;
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#receivedMessage(javax.net.msrp.Session, javax.net.msrp.IncomingMessage)
	 */
	@Override
	public void receivedMessage(Session session, IncomingMessage message) {
		MsrpActivityHandle handle = new MsrpActivityHandle(this.session.getSessionId());
		ra.fireEvent(message, handle);
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#receivedReport(javax.net.msrp.Session, javax.net.msrp.Transaction)
	 */
	@Override
	public void receivedReport(Session session, Transaction report) {
		MsrpActivityHandle handle = new MsrpActivityHandle(this.session.getSessionId());
		ReportEvent event = new ReportEvent(session, report.getStatusHeader());
		ra.fireEvent(event, handle);
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#abortedMessageEvent(javax.net.msrp.events.MessageAbortedEvent)
	 */
	@Override
	public void abortedMessageEvent(MessageAbortedEvent abortEvent) {
		MsrpActivityHandle handle = new MsrpActivityHandle(this.session.getSessionId());
		ra.fireEvent(abortEvent, handle);
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#updateSendStatus(javax.net.msrp.Session, javax.net.msrp.Message, long)
	 */
	@Override
	public void updateSendStatus(Session session, Message message, long numberBytesSent) {
        MsrpActivityHandle handle = new MsrpActivityHandle(this.session.getSessionId());
        UpdateSendEvent event = new UpdateSendEvent(session, message, numberBytesSent);
        ra.fireEvent(event, handle);
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#connectionLost(javax.net.msrp.Session, java.lang.Throwable)
	 */
	@Override
	public void connectionLost(Session session, Throwable cause) {
		MsrpActivityHandle handle = new MsrpActivityHandle(this.session.getSessionId());
		ConnectionLostEvent event = new ConnectionLostEvent(session, cause);
		ra.fireEvent(event, handle);
		ra.endActivity(this.session.getSessionId());
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#receivedNickname(javax.net.msrp.Session, javax.net.msrp.Transaction)
	 */
	@Override
	public void receivedNickname(Session session, Transaction request) {
		MsrpActivityHandle handle = new MsrpActivityHandle(this.session.getSessionId());
		NicknameEvent event = new NicknameEvent(session, request);
		ra.fireEvent(event, handle);
	}

	/* (non-Javadoc)
	 * @see javax.net.msrp.SessionListener#receivedNickNameResult(javax.net.msrp.Session, javax.net.msrp.TransactionResponse)
	 */
	@Override
	public void receivedNickNameResult(Session session, TransactionResponse result) {
		// TODO Auto-generated method stub
	}
}
