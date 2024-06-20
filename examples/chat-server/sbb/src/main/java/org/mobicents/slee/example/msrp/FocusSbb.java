/**
 * Implements a simple chat room server.
 * The following list of RFC's is applicable here:
 * <ul>
 * <li><tt>RFC3261</tt> the SIP control protocol</li>
 * <li><tt>RFC4975</tt> (and ..76) the MSRP chat protocol</li>
 * <li><tt>RFC4353</tt> SIP framework for conferencing (how to control conferences in general)</li>
 * <li><tt>draft-ietf-simple-chat-nn</tt> How to do multi-party chat using MSRP</li>
 * <li><tt>RFC3265</tt> SIP event notification (for status updates)</li>
 * <li><tt>RFC4575</tt> SIP event package for conference state (for conference status updates)</li>
 * </ul>
 * SIP is used as a control protocol to initiate chats
 * in chat rooms. The media used in these chats is MSRP. Conferencing comes
 * complete with status updates, nicknames etc.<BR>
 * <P />
 * <tt>FocusSbb</tt> is the conference focus (<tt>RFC4353</tt>) that in turn
 * uses <tt>MixerSbb</tt> to create the media mixer mixing chat streams.
 * <P />
 * For every user that wants to join a chat room, a <tt>DialogSbb</tt> is created
 * that manages the dialog between the focus and that particular user.
 * <P />
 * <tt>DialogSbb</tt> in turn creates another <tt>MixerSbb</tt>, capable of
 * handling the MSRP chat session between the user (participant) and the mixer.
 * <P />
 * Thus, the combination of <tt>MixerSbb</tt>-as-mixer in unison with
 * <tt>MixerSbb</tt>-as-chat-session implement the actual chat room.
 * <P />
 * The <tt>FocusSbb</tt> - <tt>DialogSbb</tt> combination together control status
 * handling of the session, room and participants.<br>
 * This is mainly done by implementing the Conference Notification Service
 * (<tt>RFC3265</tt> and <tt>RFC4575</tt>) using event subscriptions and
 * notifications.
 * <P />
 * Refer to <tt>draft-ietf-simple-chat-nn</tt> for more details on how a chat
 * room can be implemented with details on additional SIP parameters,
 * media description, MSRP nicknames and conference information documents.
 * <br>
 * This example is implemented using version 08 of the draft.
 * 
 * @author Tom Uijldert
 */
package org.mobicents.slee.example.msrp;

import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.slee.*;
import javax.slee.facilities.Tracer;
import javax.slee.nullactivity.NullActivity;

import org.mobicents.slee.example.msrp.events.*;

import net.java.slee.resource.sip.CancelRequestEvent;

/**
 * Implements the chat room focus.
 * <br>
 * Any action starts here with an initial SIP <tt>INVITE</tt> reaching this server.
 * <br>
 * Provided this <tt>INVITE</tt> is not a telephone call but contains a chat media
 * description, the target username in the <tt>To:</tt>-header is presumed to be
 * holding the name of the chat room this client wants to join.
 * <br>
 * When this room is not created yet, a mixer is created and the client session
 * (MSRP) will be connected.
 * <br>
 * Otherwise, the <tt>INVITE</tt> will be re-fired on the already created room.
 * 
 * @author Tom Uijldert
 */
public abstract class FocusSbb extends GenericChatSbb implements FocusMethods, MixerFeedbackMethods
{
	private transient Tracer trc;

	private static final String FPREFIX = "Focus:";

	private static final String SUBJECT = "MSRP chat";

	public abstract FocusContext asSbbActivityContextInterface(ActivityContextInterface aci);

	public abstract ChildRelation getDialogIn();

	/**
	 * @name Fire event methods
	 * Events, fired by this sbb.
	 *
	 * Implementation is done by SLEE.
	 * Refer to @c sbb-jar.xml for details on the event and type.
	 */
	//@{
	public abstract void fireInvite(RequestEvent event, ActivityContextInterface aci, javax.slee.Address address);

	/* mixer feedback sequencing */
	public abstract void fireChatRoomCreated(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);

	/* internal sequencing */
	public abstract void fireLegDisconnected(LegDisconnectedEvent event, ActivityContextInterface aci, javax.slee.Address address);
	//@}

	/**
	 * Initial event selector method for invite ("is this a new chat request?").
	 * If there is a dialog already, this is not an initial event but
	 * a re-invite.<br>
	 * Discard any non-chat calls.<br>
	 * Re-firing on existing focus is not initial.
	 * @param ies
	 * @return ies
	 */
	public InitialEventSelector filterForNewChatRequests(InitialEventSelector ies) {
		Object event = ies.getEvent();

		if (event instanceof RequestEvent) {
			if (((RequestEvent) event).getDialog() != null)
			{
				ies.setInitialEvent(false); // This a re-INVITE, not initial.
			}
			else if (!isChatOffer(((RequestEvent) event).getRequest()))
			{
				ies.setInitialEvent(false);	// Not a chat, leave it
			}
			else if (ies.getActivity() instanceof NullActivity)
			{
				ies.setInitialEvent(false);	// Re-fired on existing focus.
			}
			else
			{
				ies.setActivityContextSelected(true);
			}
		}
		return ies;
	}

	/**
	 * Somebody wants to chat.
	 * <br>
	 * Yes, this is a new chat request.<br>
	 * Using the naming facility we detect whether there is a room already.<br>
	 * If not, create one. Otherwise, re-fire on the existing <tt>FocusContext</tt>.
	 * @param event
	 * @param aci
	 */
	public void onInvite(RequestEvent event, ActivityContextInterface aci) {
		if (aci.getActivity() instanceof NullActivity) {	// re-fired?
			/*
			 * New invite to an existing chat room. Try adding the new participant.
			 */
			addMember(event.getServerTransaction());
		} else {
			/*
			 * initial invite, search for existing room.
			 * This'd be the ideal place to implement some other room-management
			 * mechanism. Here, the To-header user part is used as room name.
			 * Pick any alternative if you want. 
			 */
			javax.sip.address.Address to =
					((ToHeader) event.getRequest().getHeader(ToHeader.NAME)).getAddress();
			String roomName = ((SipURI) to.getURI()).getUser();
			if (roomName == null || roomName.length() < 1) {
				createFailed("no or invalid room name specified");
				return;
			}
			ActivityContextInterface existingFocus =
					acNamingFacility.lookup(FPREFIX + roomName);
			if (existingFocus != null) {
				/*
				 * There is a chat room already, re-fire invite on existing focus.
				 */
				fireInvite(event, existingFocus, null);
				detachServerTransaction();
				return;
			} else {
				/*
				 * New invite for a new chat room, create the room and
				 * link this chatter to it.
				 */
				FocusContext focus = asSbbActivityContextInterface(createNullAci());
				try {
					acNamingFacility.bind(focus, FPREFIX + roomName);
					focus.setFocusName(roomName);
					setFocusName(roomName);
				} catch (Exception e) {
					createFailed(e.getMessage());
					return;
				}
				try {					// create chat room focal-point
					MixerControlLocalObject room = (MixerControlLocalObject)
												getMixerRelation().create();
					room.setControlAgent(getMyIf());
					room.createMixer(roomName);
				} catch (Exception e) {
					try {
						acNamingFacility.unbind(FPREFIX + roomName);
					} catch (Exception e1) { ; }
					createFailed(e.getMessage());
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#createComplete(java.lang.String)
	 */
	public void createComplete(String sdp) {
		/* Handle callback in the proper context, re-fire on focus. */
		fireChatRoomCreated(emptyChatEvent, getFocusContext(), null);
	}

	/**
	 * We now have a chat room (see {@link #createComplete(String)}).
	 * <br>
	 * Set the conference name and subject of this room.
	 * Create a child to add the first chatter.
	 * @param event
	 * @param aci
	 */
	public void onChatRoomCreated(ChatEvent event, ActivityContextInterface aci) {
		try {
	        ServerTransaction st = getServerTransaction();
			setConferenceName(((ToHeader) st.getRequest().getHeader(ToHeader.NAME))
					.getAddress().getURI().toString());
			setSubject(SUBJECT);
			addMember(st);
		} catch (Exception e) {
			cleanup();
		} finally {
			detachServerTransaction();
		}
	}

	/**
	 * Create a new dialog handler for the incoming chatter.
	 * @param st	transaction of the original INVITE
	 */
	private void addMember(ServerTransaction st) {
        if (st == null)
			throw new RuntimeException("Missing server transaction");

        DialogInLocalObject member = null;
		try {
			member = (DialogInLocalObject) getDialogIn().create();
			member.setFocalPoint(getMyIf());
			member.setupChatDialog(st);
		} catch (Exception e) {
			logError("member not added - " + e.getMessage());
			replyToServerTransaction(Response.SERVER_INTERNAL_ERROR, st);
        	if (member != null)
        		member.disconnect();
        	throw new RuntimeException("Error adding member", e);
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#createFailed(java.lang.String)
	 */
	public void createFailed(String reason) {
		logError("Chat room creation failed - " + reason);
		replyToServerTransaction(Response.SERVICE_UNAVAILABLE, getServerTransaction());
		getMyIf().remove();
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#conferenceNotification(java.lang.String)
	 */
	public void conferenceNotification(String info) {
		throw new UnsupportedOperationException("not implemented for focus");
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerFeedbackMethods#mediaReleased()
	 */
	public void mediaReleased() {
		throw new UnsupportedOperationException("This is the focus, not a chat dialog");
	}

	public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
		// TODO: implement
	}

	public void onBye(RequestEvent event, ActivityContextInterface aci) {
		// TODO: implement
	}

    /* (non-Javadoc)
     * @see org.mobicents.slee.example.msrp.FocusMethods#legDisconnected(java.lang.String)
     */
    public void legDisconnected(String id) {
		/* Handle callback in the proper context, re-fire on focus. */
    	fireLegDisconnected(new LegDisconnectedEvent(id), getFocusContext(), null);
    }

    /**
     * A chatter left the conference (see {@link #legDisconnected(String)}).
     * <br>
     * Turn off lights in room when last chatter left.
     * @param event
     * @param aci
     */
    public void onLegDisconnected(LegDisconnectedEvent event, ActivityContextInterface aci) {
		if (getDialogIn().isEmpty()) {
			trc.info("Last chat, cleaning up.");
			cleanup();
		}
    }

	/**
	 * clean up mixer, room and associated activities before removing myself.
	 */
	private void cleanup() {
		getMediaStream().disconnect();
		if (getFocusName() != null)
			try {
				acNamingFacility.unbind(FPREFIX + getFocusName());
			} catch (Exception e) { /* empty */; }

		endActivities();
		getMyIf().remove();
	}

	/**
	 * End any activities controlled by me.
	 */
	private void endActivities() {
		for (ActivityContextInterface aci : sbbContext.getActivities()) {
			Object activity = aci.getActivity();
			if (activity instanceof NullActivity)
				((NullActivity) activity).endActivity();
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.FocusMethods#getMediaMixer()
	 */
	public Object getMediaMixer() {
		return getMediaStream().getRoom();
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.FocusMethods#setConferenceName(java.lang.String)
	 */
	public void setConferenceName(String name) {
		getMediaStream().setConferenceName(name);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.FocusMethods#setSubject(java.lang.String)
	 */
	public void setSubject(String subject) {
		getMediaStream().setSubject(subject);
	}

    public ActivityContextInterface getFocusContext() {
    	return getNullActivity();
    }

	private void logError(String errmsg) {
		trc.severe(String.format("[%s] - %s", getFocusName(), errmsg));
	}

	public void setSbbContext(SbbContext context) {
		super.setSbbContext(context);
		this.trc = context.getTracer("Focus");
	}

    /** Handling of CMP fields.
	 *  Implemented by SLEE, refer to <tt>sbb-jar.xml</tt> for specifics on the fields.
	 */
	//@{
    public abstract void setFocusName(String name);
    public abstract String getFocusName();
	//@}
}
