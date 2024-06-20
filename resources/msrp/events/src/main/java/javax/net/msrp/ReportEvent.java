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
package javax.net.msrp;

import javax.net.msrp.Session;
import javax.net.msrp.StatusHeader;

/**
 * Includes the status code and possible namespace included in the report.
 * @author tuijldert
 */
@SuppressWarnings("serial")
public class ReportEvent extends BaseEvent {

	private StatusHeader status;

	public ReportEvent(Session session, StatusHeader status) {
		super(session);
		this.status = status;
	}

	public int getStatusCode() {
		return status.getStatusCode();
	}

	public int getNamespace() {
		return status.getNamespace();
	}

	public String getComment() {
		return status.getComment();
	}
}