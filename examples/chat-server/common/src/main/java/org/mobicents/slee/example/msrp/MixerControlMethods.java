/**
 * 
 */
package org.mobicents.slee.example.msrp;

import javax.sip.address.Address;
import javax.slee.SbbLocalObject;

/**
 * Commands a control agent can issue to this mixer and media stream handler.
 * @author tuijldert
 */
public interface MixerControlMethods {
	/**
	 * Set the parent (controlling agent) of this sbb
	 * @param parent the controlling agent
	 */
	public void setControlAgent(SbbLocalObject parent);

	/**
	 * Create a chat room (mixer)
	 * @param roomId the room identifier
	 */
	public void createMixer(Object roomId);

	/**
	 * Create the media stream (chat) for this connection
	 * @param room the room to connect to
	 * @param sdp the session description
	 */
	public void createMedia(Object room, String sdp);

	/**
	 * @return 	 The room identifier.
	 */
	public Object getRoom();

	/**
	 * Tear down the media connection.
	 */
	public void disconnect();

	/**
	 * Send the given text to the connected chatter
	 * @param prompts		the text to send
	 * @throws Exception	trouble in paradise.
	 */
	public void sendPrompts(String[] prompts) throws Exception;

	/**
	 * @return the session description currently in use.
	 */
	public String getSdp();

	/**
	 * Name the chat room.
	 * @param uri the name to use
	 */
	public void setConferenceName(String uri);

	/**
	 * Set the subject-line of this chat room
	 * @param subject the subject-line
	 */
	public void setSubject(String subject);

	/**
	 * Name the chat room member.
	 * @param entityName	the name to use
	 */
	public void setParticipant(Address entityName);

	/**
	 * Command to send conference info to the chatter.
	 */
	public void triggerInfoNotification();
}
