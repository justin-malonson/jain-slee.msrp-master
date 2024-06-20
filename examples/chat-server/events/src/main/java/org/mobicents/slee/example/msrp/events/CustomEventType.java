/**
 * 
 */
package org.mobicents.slee.example.msrp.events;

import java.rmi.server.UID;

/**
 * Event type that can be used by <tt>Sbb</tt> developers to create their own events.
 * Included is the minimum mandatory implementation according JAIN SLEE.
 *
 * @author tuijldert
 */
public abstract class CustomEventType {
	private int hashcodeId;

	public CustomEventType() {
		hashcodeId = (new UID()).hashCode();
	}

	public int hashCode() { return hashcodeId; }
}
