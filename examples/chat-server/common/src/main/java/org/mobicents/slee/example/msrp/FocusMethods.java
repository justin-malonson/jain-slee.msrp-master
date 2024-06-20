package org.mobicents.slee.example.msrp;

public interface FocusMethods
{
	/**
	 * @return the chat room mixer.
	 */
	public Object getMediaMixer();

    /**
     * Callback indicating a chat room member has left the room.
     * @param id the call id of the member.
     */
    public void legDisconnected(String id);

    /**
     * Set the name of this chat room.
     * @param name	the name to use.
     */
    public void setConferenceName(String name);

    /**
     * Set the subject-line of this chat room
     * @param subject the subject-line
     */
    public void setSubject(String subject);
}
