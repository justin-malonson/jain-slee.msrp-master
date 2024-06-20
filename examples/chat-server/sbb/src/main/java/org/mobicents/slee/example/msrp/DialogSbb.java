package org.mobicents.slee.example.msrp;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.slee.*;
import javax.slee.facilities.*;
import javax.slee.nullactivity.NullActivity;

import org.mobicents.slee.example.msrp.events.ChatEvent;

import net.java.slee.resource.sip.DialogActivity;
/**
 * Handle the established dialog of a conference participant.
 * 
 * @author Tom Uijldert
 */
public abstract class DialogSbb extends GenericChatSbb implements DialogMethods, MixerFeedbackMethods {
	private transient Tracer trc;

	/** Fire event methods
	 *  Events, fired by this sbb.
	 * 
	 *  Implementation is done by SLEE.
	 *  Refer to <tt>sbb-jar.xml</tt> for details on the event and type.
	 */
	//@{
	/* mixer feedback sequencing */
	public abstract void fireMediaCreated(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);
	public abstract void fireMediaReleased(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);
	public abstract void fireConferenceNotification(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);

	/* SIP sequencing	*/
	public abstract void fireBye(RequestEvent event, ActivityContextInterface aci, Address address);
	public abstract void fireSubscribe(RequestEvent event, ActivityContextInterface aci, Address address);
	//@}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#createComplete(java.lang.String)
	 */
	public void createComplete(String sdp) {
		/* Handle callback in the proper context, re-fire on dialog context. */
		ChatEvent event = new ChatEvent(sdp);
		fireMediaCreated(event, getMyContext(), null);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#createFailed(java.lang.String)
	 */
	public void createFailed(String reason) {
		trc.severe("media stream creation failed - " + reason);
	}

    /**
     * Chatter subscribes to (or polls) conference status.
	 * <br>
	 * Trigger the sending of a status message and set the expiry counter
	 * to keep track of active subscription.
	 * <P />
	 * Note that SIP signalling is very basic: no ACK's are being checked on
	 * dialog creation nor OK responses on notifications or handling of
	 * re-invites and the like.
	 * This is left as an exercise to the reader.
     * @param event
     * @param aci
     */
    public void onSubscribe(RequestEvent event, ActivityContextInterface aci) {
		if (aci.getActivity() instanceof DialogActivity) {
			/* Handle in proper context, re-fire on dialog context. */
			fireSubscribe(event, getMyContext(), null);
			return;
		}
        String eventPackage = ((EventHeader) event.getRequest().getHeader(EventHeader.NAME)).getEventType();
        if (!(eventPackage.equals("conference"))) {
        	replyToServerTransaction(Response.BAD_EVENT, event.getServerTransaction());
        	return;
        }
        /*
         * This is a possible authentication point.
         * What type of user are you and are you allowed to subscribe to this
         * info (or whatever).
         * For this example we just accept anything.
         */
        setSubscriptionId(
        		((EventHeader) event.getRequest().getHeader(EventHeader.NAME))
        		.getEventId());
        int expiry;
        if (event.getRequest().getExpires() == null)
        	expiry = 3600;				/* default */
        else
        	expiry = event.getRequest().getExpires().getExpires();

        if (expiry == 0)
        	setExpiryMoment(0);
        else {
            // hmmm, possibly check for too small as well?
        	if (expiry > 3600)
            	expiry = 3600;
            setExpiryMoment(System.currentTimeMillis() / 1000 + expiry);
        }
        replySubscriptionOk(event.getServerTransaction(), expiry);
        // generate a first (possibly last) NOTIFY with conference info
        getMediaStream().triggerInfoNotification();
    }

    /* (non-Javadoc)
     * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#conferenceNotification(java.lang.String)
     */
    public void conferenceNotification(String info) {
		/* Handle callback in proper context, re-fire. */
    	ChatEvent event = new ChatEvent(null, info);
    	fireConferenceNotification(event, getMyContext(), null);
    }

    /**
     * Conference status changed. Update conference info toward chatter.
     * 
     * @param event
     * @param aci
     * @see #conferenceNotification(String)
     */
    public void onConferenceNotification(ChatEvent event, ActivityContextInterface aci) {
    	if (getExpiryMoment() == -1)
    		return;						/* Ignore when not subscribed */
		logCommand("onConferenceNotification");
		String info = event.getContent();
		sendConferenceInfo(getDialog(), info);
	}

    /**
     * Send a NOTIFY with complete conference info to chatter.
     * @param dialog the dialog to send it on
     * @param info the conference info
     */
    private void sendConferenceInfo(DialogActivity dialog, String info) {
		String subscriptionState;
        long expire = getExpiryMoment() - (System.currentTimeMillis() / 1000);
        if (expire < 0)
        	expire = 0;
        if (expire == 0)
        	subscriptionState = "terminated;reason=timeout";
        else
        	subscriptionState = String.format("active;expires=%d", expire);
        try {
			Request notify = dialog.createRequest(Request.NOTIFY);
			EventHeader eh = headerFactory.createEventHeader("conference");
			if (getSubscriptionId() != null)
				eh.setEventId(getSubscriptionId());
			notify.setHeader(eh);
			notify.setHeader(headerFactory.createSubscriptionStateHeader(subscriptionState));
			notify.setContent(info, headerFactory.createContentTypeHeader(
										"application", "conference-info+xml"));
			dialog.sendRequest(notify);
		} catch (Exception e) {
            trc.warning("Failed to send message waiting notification", e);
		} finally {
	        if (expire == 0)
	        	setExpiryMoment(-1);	/* subscription done (timeout) */
		}
	}

	/**
	 * Chatter ends the session. Respond and start tear-down of dialog
	 * @param event
	 * @param aci
	 */
	public void onBye(RequestEvent event, ActivityContextInterface aci) {
		if (aci.getActivity() instanceof DialogActivity) {
	       	logCommand("onBye:sending OK response");
			try {
				event.getServerTransaction().sendResponse(
						messageFactory.createResponse(Response.OK, event.getRequest()));
			} catch (Exception e) {
				trc.warning("onBye:couldn't send OK response - " + e.getMessage());
			}
			fireBye(event, getMyContext(), null);
			return;
		}
		timedDisconnect();
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#mediaReleased()
	 */
	public void mediaReleased() {
		fireMediaReleased(emptyChatEvent, getMyContext(), null);
	}

	/**
	 * Start a tear-down action when not already initiated.
	 * Otherwise just signal the end of this dialog to the conference.
	 * @param event
	 * @param aci
	 * @see #mediaReleased()
	 */
	public void onMediaReleased(ChatEvent event, ActivityContextInterface aci) {
		logCommand("onMediaReleased");
		if (getChatState() == ChatDialogState.STOP) {
			releaseThisLeg();
		} else {
			timedDisconnect();
		}
	}

	/**
	 * End this dialog and signal it to the conference.
	 */
	protected void releaseThisLeg() {
		cancelTimer();
		endActivities();
		getFocus().legDisconnected(getCallId());
		getMyIf().remove();
	}

	/**
	 * End any activities controlled by me.
	 */
	protected void endActivities() {
		for (ActivityContextInterface aci : sbbContext.getActivities()) {
			Object activity = aci.getActivity();
			if (activity instanceof DialogActivity) {
				rmDialog((DialogActivity) activity);
			} else if (activity instanceof NullActivity)
				((NullActivity) activity).endActivity();
		}
	}

	/**
	 * Start tear down of this dialog and session.
	 * Wait for session end or force after 2 second delay.
	 */
	protected void timedDisconnect() {
		disconnect();
		armTimer(2);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.DialogMethods#disconnect()
	 */
	public void disconnect() {
		cancelTimer();
		setChatState(ChatDialogState.STOP);
		sendByeRequest();
		disconnectMedia();
	}

	/**
	 * Instruct media session to end.
	 */
	private void disconnectMedia() {
		MixerControlLocalObject link = getMediaStream();
		if (link != null)
			link.disconnect();
	}

    protected void armTimer(int duration) {
        setTimerId(setTimer(duration * 1000));
    }

    protected void cancelTimer() {
        cancelTimer(getTimerId());
        setTimerId(null);
    }

	/**
	 * If not ended within 2 seconds, just release this dialog from the conference.
	 * @param event
	 * @param aci
	 */
	public void onTimer(TimerEvent event, ActivityContextInterface aci) {
		logCommand("onTimer()");
		setTimerId(null);
		if (getChatState() == ChatDialogState.STOP)
			releaseThisLeg();
	}

	/**
     * send <tt>BYE</tt> message.
	 * A <tt>BYE</tt> request is sent to the caller when the connection
	 * will be terminated.
	 */
	protected abstract void sendByeRequest();

	private void logCommand(final String method) {
		if (trc.isInfoEnabled())
	    	trc.info(String.format(
	    			"[%s] - %s(state=[%s])",
	    			getCallId(), method, getChatState()));
	}

	public void setSbbContext(SbbContext context) {
		super.setSbbContext(context);
		this.trc = context.getTracer("DialogSbb");
	}

	public void setFocalPoint(SbbLocalObject focus) {
		setFocus((FocusLocalObject) focus);
		getMyContext();					// force activity creation
		setExpiryMoment(-1);
	}

	/** Handling of CMP fields
	 *  Implemented by SLEE, refer to <tt>sbb-jar.xml</tt> for specifics on the fields.
	 */
	//@{
	public abstract void setFocus(FocusLocalObject parent);
	public abstract FocusLocalObject getFocus();

	public abstract void setCallId(String callId);
	public abstract String getCallId();

    public abstract void setChatState(ChatDialogState state);
	public abstract ChatDialogState getChatState();

	public abstract void setTimerId(TimerID value);
	public abstract TimerID getTimerId();

	public abstract void setSdp(String sdp);
	public abstract String getSdp();

    public abstract void setSubscriptionId(String id);
    public abstract String getSubscriptionId();

    public abstract void setExpiryMoment(long timestamp);
    public abstract long getExpiryMoment();
    //@}
}
