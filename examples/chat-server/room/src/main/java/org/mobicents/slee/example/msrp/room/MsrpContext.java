/**
 * 
 */
package org.mobicents.slee.example.msrp.room;

import java.util.HashMap;

import javax.slee.ActivityContextInterface;

/**
 * Interface to use a context as chat room rendez-vous.
 * To be used by media children in the same room.
 * @author Tom Uijldert
 */
public interface MsrpContext extends ActivityContextInterface {
	public String getConferenceName();
	public void setConferenceName(String name);

	/** current subject-line of this room */
	public String getSubject();
	public void setSubject(String subject);

	/** list of participants in this room */
	public HashMap<String, Participant> getParticipants();
	public void setParticipants(HashMap<String, Participant> userList);
}
