/**
 * 
 */
package org.mobicents.slee.example.msrp.events;

/**
 * Indicates which leg is released.
 * 
 * @author Tom Uijldert
 */
public class LegDisconnectedEvent extends CustomEventType {
	private final String legId;

	public LegDisconnectedEvent(String legId) {
		super();
		this.legId = legId;
	}

	public boolean equals(Object obj) {
		if (obj != null && obj.getClass() == this.getClass())
			return (legId.equals(((LegDisconnectedEvent) obj).legId));
		return false;
	}

	public String getLegId() { return legId; }
}
