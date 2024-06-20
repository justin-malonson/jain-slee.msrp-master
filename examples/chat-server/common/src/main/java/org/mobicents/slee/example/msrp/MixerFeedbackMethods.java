/**
 * 
 */
package org.mobicents.slee.example.msrp;

/**
 * Callbacks to be implemented by the controlling agent on
 * the state of mixer/media stream handler.
 * @author tuijldert
 */
public interface MixerFeedbackMethods {

	/**
	 * Requested creation (of conference, media stream...) is done and successfull.
	 * @param sdp	the session description that is actually in use now.
	 */
	public void createComplete(String sdp);

	/**
	 * Requested creation (of conference, media stream...) has failed.
	 * @param reason and here's why.
	 */
	public void createFailed(String reason);

	/**
	 * The media stream has stopped.
	 */
	public void mediaReleased();

	/**
	 * Supply the control agent with the conference info
	 * @param info the conference info.
	 */
	public void conferenceNotification(String info);
}
