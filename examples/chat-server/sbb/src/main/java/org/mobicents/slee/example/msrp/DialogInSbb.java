package org.mobicents.slee.example.msrp;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.*;
import javax.slee.facilities.*;

import org.mobicents.slee.example.msrp.events.ChatEvent;

import net.java.slee.resource.sip.*;

/**
 * Handle a chat dialog, incoming aspects only (establishing the dialog).

 * @author Tom Uijldert
 */
public abstract class DialogInSbb extends DialogSbb implements DialogInMethods {
	private transient Tracer trc;

	protected static final String[] welcome = {
		"Welcome to this simple chat server...",
		"don't forget to set your own nickname." };

	/**
	 * Fire event methods
	 * Events, fired by this sbb.
	 * 
	 * Implementation is done by SLEE.
	 * Refer to <tt>sbb-jar.xml</tt> for details on the event and type.
	 */
	//@{
	/* internal sequencing */
	public abstract void fireDialogTimeout(DialogTimeoutEvent event, ActivityContextInterface aci, javax.slee.Address address);
	//@}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.DialogInMethods#setupChatDialog(javax.sip.ServerTransaction)
	 * This is the first stop on the way to establishing a dialog with the
	 * chat room. We attach to the SIP transaction and attempt to create the
	 * media stream passing on the media description in the INVITE.
	 * A RINGING is sent back to signal the attempt.
	 */
	public void setupChatDialog(ServerTransaction st) throws Exception {

		if (getChatState() != null)
			throw new RuntimeException("Cannot start a chat dialog twice");
        setChatState(ChatDialogState.START);
        /*
	     * get request and remote sdp from server transaction.
         */
		Request req = st.getRequest();
		setSdp(new String(req.getRawContent()));
		/*
		 * get call ID and save.
		 */
		String callId = ((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId().toString();
		setCallId(callId);
		/*
		 * attach transaction context to me.
		 */
		ActivityContextInterface aci = sipAcif.getActivityContextInterface(st);
		aci.attach(getMyIf());
		replyToServerTransaction(Response.RINGING, st);

		createMediaConnectionOrAbort("setupChatDialog()");
	}

	/**
	 * Create a {@link MixerControlLocalObject} and have it initiate a chat-connection.
	 * @param functionName	name of calling function for logging purposes.
	 */
	private void createMediaConnectionOrAbort(final String functionName) {
		try {
			MixerControlLocalObject mediaLeg =
					(MixerControlLocalObject) getMixerRelation().create();
			mediaLeg.setControlAgent(getMyIf());
			mediaLeg.createMedia(getFocus().getMediaMixer(), getSdp());
		} catch (Exception e) {
			detachServerTransaction();
			throw new RuntimeException(functionName + " - Failed to create media stream.", e);
		}
	}

	/**
	 * Media stream created (see {@link DialogSbb#createComplete(String)}).
	 * @param event
	 * @param aci
	 */
	public void onMediaCreated(ChatEvent event, ActivityContextInterface aci)
	{
		if (getChatState() != ChatDialogState.START)
			throw new RuntimeException(
					"connectionCreated - Invalid state: " + getChatState());

		try {
			setChatState(ChatDialogState.EARLY);
			answer();
		} catch (Exception e) {
			trc.warning("Error while answering: " + e.getMessage());
			replyToServerTransaction(Response.SERVER_INTERNAL_ERROR);
			timedDisconnect();
			return;
		}
	}

	/**
	 * Answer incoming call.
	 * <br>
	 * Send OK response - indicating that this is a conference-, and start
	 * sending greeting.
	 */
	private void answer() throws Exception {
		ServerTransaction st = getServerTransaction();
		MixerControlLocalObject media = getMediaStream();
		String sdpData = media.getSdp();

		getAndAttachDialog(st);

		Address from =
				((FromHeader) st.getRequest().getHeader(FromHeader.NAME)).getAddress();
		media.setParticipant(from);

        Response rsp = makeReply(Response.OK, st, sdpData, true);
        indicateConference(rsp);
        st.sendResponse(rsp);
        detachServerTransaction();
        media.sendPrompts(welcome);

        setChatState(ChatDialogState.CHATTING);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.DialogSbb#createFailed(java.lang.String)
	 */
	public void createFailed(String reason) {
		super.createFailed(reason);
		setChatState(ChatDialogState.STOP);
		replyToServerTransaction(Response.SERVICE_UNAVAILABLE);
		releaseThisLeg();
	}

	public void onDialogTimeout(DialogTimeoutEvent event, ActivityContextInterface aci) {
    	if (aci.getActivity() instanceof DialogActivity) {
    		fireDialogTimeout(event, getMyContext(), null);	// re-fire on state-handling activity.
    		return;
    	}
    	if (getChatState() != ChatDialogState.EARLY) {
    		String cause = "No ACK received??? stop this leg.";
    		trc.warning(cause);
    		timedDisconnect();
    	}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.DialogSbb#sendByeRequest()
	 */
	protected void sendByeRequest() {
		sendBye();
	}

	public void setSbbContext(SbbContext context) {
		super.setSbbContext(context);
		this.trc = context.getTracer("DialogInSbb");
	}
}
