package org.mobicents.slee.example.msrp.room;

import java.io.*;
import java.util.*;

import javax.sdp.*;
import javax.sip.address.*;
import javax.slee.ActivityContextInterface;
import javax.slee.SbbContext;
import javax.slee.SbbLocalObject;
import javax.slee.facilities.*;

import org.mobicents.slee.example.msrp.*;
import org.mobicents.slee.example.msrp.events.ChatEvent;
import org.mobicents.slee.resource.msrp.*;

import javax.net.msrp.exceptions.*;
import javax.net.msrp.*;

/**
 * MSRP chat conference mixer. I'm either the chat room
 * ({@link #createMixer(Object)}) or a media stream
 * ({@link #createMedia(Object, String)})
 * <P />
 * As a mixer, I create and control the chat room activity context.
 * Streams can subsequently fire chat events on it to automatically broadcast
 * messages to all other dialogs.
 * <P />
 * As a media stream I manage the MSRP media session toward the chatter.
 * Any received messages will be broadcasted via the mixer and any broadcast
 * will be passed on to the chatter.<br>
 * Control messages within the media stream, such as nicknames and typing
 * indication (message composition) will receive special treatment and mostly
 * result in a conference status change.
 * 
 * @author Tom Uijldert
 */
public abstract class MixerSbb extends BasicSbb implements MixerControlMethods {
    private transient Tracer trc;

    private static final String MPREFIX = "Mixer:";
	private static final String newLine = String.format("%n");

	private static final String WRAP_TYPE = "message/cpim";
	protected static final ChatEvent emptyChatEvent = new ChatEvent();

	protected static final int HowLong2Wait4KeepAlive = 35 * 1000;

	private MsrpActivityContextInterfaceFactory	msrpAcif;
	private MsrpResourceAdaptorSbbInterface		msrpIf;

	public abstract MsrpContext asSbbActivityContextInterface(ActivityContextInterface aci);

	private int	KeepAliveTick;

	/** @name Fire event methods
	 *  Events, fired by this sbb.
	 * 
	 *  Implementation is done by SLEE.
	 *  Refer to <tt>sbb-jar.xml</tt> for details on the event and type.
	 */
	//@{
	/* control command sequencing */
	public abstract void fireCreateMixer(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);
	public abstract void fireCreateMedia(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);

	/* internal events */
	public abstract void fireChat(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);
	public abstract void fireUpdateParticipant(ChatEvent event, ActivityContextInterface aci, javax.slee.Address address);
	public abstract void fireStatusUpdate(IncomingStatusMessage event, ActivityContextInterface aci, javax.slee.Address address);
	//@}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#createChatRoom(java.lang.Object)
	 */
	public void createMixer(Object roomId) {
		if (getController() == null)
			throw new IllegalStateException("control agent not set");

		if (getRoomId() != null)
			throw new UnsupportedOperationException("chatroom already in use");
		/* handle in proper context, re-fire on mine */
		ChatEvent event = new ChatEvent(roomId, null);
		fireCreateMixer(event, getMyContext(), null);
	}

	/**
	 * Request to create a chat room (== mixer).
	 * <br>
	 * Create the room context (using naming facility) and initialise data
	 * (list of participants, active recordings etc.).
	 * 
	 * @param event
	 * @param aci the -controlling- chat aci
	 * @see #createMixer(Object)
	 */
	public void onCreateMixer(ChatEvent event, ActivityContextInterface aci) {
		MixerFeedbackLocalObject controlAgent = getController();
		String roomId = (String) event.getroomId();
		if (roomId == null)				// not given: generate my own.
			roomId = UUID.randomUUID().toString();

		roomId = MPREFIX + roomId;
		// Create chat room context and bind to room-id.
		MsrpContext msrpContext = asSbbActivityContextInterface(createNullAci());
		try {
			acNamingFacility.bind(msrpContext, roomId);
		} catch (Throwable t) {
			trc.severe("failed to create media session", t);
			controlAgent.createFailed(t.getMessage());
			return;
		}
		setRoomId(roomId);
		setRoomContext(msrpContext);
		msrpContext.setParticipants(new HashMap<String, Participant>());
		setRecordings(new HashMap<String, File>());
		setState(State.ACTIVE);
		trc.fine(String.format("Chat room(%s) created.", roomId));

		controlAgent.createComplete(null);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#createMedia(java.lang.Object, java.lang.String)
	 */
	public void createMedia(Object room, String sdp) {
		if (getController() == null)
			throw new IllegalStateException("controlling sbb not set");

		if (getRoomId() != null)
			throw new UnsupportedOperationException("chat session already created");

		if ((room == null) || !(room instanceof String))
			throw new IllegalArgumentException("Invalid chatroom session: " + room);
		/* handle in proper context, re-fire on mine */
		ChatEvent event = new ChatEvent(room, sdp);
		fireCreateMedia(event, getMyContext(), null);
	}

	/**
	 * Request to create a chat media stream.
	 * <br>
	 * Attach to the given chat room and establish the MSRP session using the
	 * given media description.
	 * @param event
	 * @param aci the -controlling- chat aci
	 * @see #createMedia(Object, String)
	 */
	public void onCreateMedia(ChatEvent event, ActivityContextInterface aci) {
		Object roomId = event.getroomId();
		String sdp = event.getSdp();
		MixerFeedbackLocalObject controlAgent = getController();
		MsrpSession ms = null;

		setRoomId((String) roomId);
		setRoomContext(asSbbActivityContextInterface(acNamingFacility.lookup((String) roomId)));
		String connId = UUID.randomUUID().toString();	// generate a unique id
		setConnectionId(connId);
		setComposeState(ImState.idle);
		try {
			if (sdp != null) {
				setRemoteSdp(sdp);
				ms = msrpIf.connect(connId,
						SdpFactory.getInstance().createSessionDescription(sdp));
			} else
				ms = msrpIf.connect(connId);
			SessionDescription localSdp = ms.getLocalSdp();
			indicateChatroom(localSdp);
			setLocalSdp(localSdp.toString());
			msrpAcif.getActivityContextInterface(ms).attach(getMyIf());
			getRoomContext().attach(getMyIf());
			setState(State.LINKED);
			setIsAliveSent(false);
			setKeepAliveId(setTimer(KeepAliveTick * 1000, aci));
			trc.fine(from() + "connection created");
			controlAgent.createComplete(getLocalSdp());
		} catch (Exception e) {
			String reason = "Could not create media stream";
			trc.severe(reason, e);
			controlAgent.createFailed(reason);
		}
	}

	/**
	 * Indicate in the given session description that the "message" media
	 * is a chat room accepting nicknames.
	 * @param desc the session description to modify.
	 */
	private static void indicateChatroom(SessionDescription desc) {
		if (desc != null) {
		    try {
			    Vector<MediaDescription> mds = desc.getMediaDescriptions(false);
			    for (int i = 0; i < mds.size(); i++) {
			    	MediaDescription md = mds.get(i);
			    	if (md.getMedia().getMediaType().equals("message")) {
			    		md.setAttribute("accept-types",
		    				"message/cpim text/plain application/im-iscomposing+xml");
			    		md.setAttribute("chatroom", "nickname");
			    	}
			    }
			} catch (Exception e) { /* empty */ }
		}
	}

	/**
	 * @return the attached MSRP session.
	 */
	private MsrpSession getMsrpSession() {
		for (ActivityContextInterface aci : sbbContext.getActivities()) {
			if (aci.getActivity() instanceof MsrpSession)
				return (MsrpSession) aci.getActivity();
		}
		return null;
	}

	private static final String CRLF = "\r\n";

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#sendPrompts(java.lang.String[])
	 */
	public void sendPrompts(String[] prompts) throws Exception {
		if (prompts == null || prompts.length == 0)
			return;
		if (getController() == null)
			throw new IllegalStateException("no parent defined (yet)");

		StringBuilder text = new StringBuilder(prompts.length * 80);

		for (String p : prompts)
			text.append(p).append(CRLF);

		trc.fine("sendPrompts(size=[" + text.length() + "])");
		if (isMixer()) {	// broadcast
			ChatEvent ce = new ChatEvent(null, text.toString());
			fireChat(ce, getRoomContext(), null);
		} else {
			getMsrpSession().sendWrappedMessage(WRAP_TYPE,
					getRoomContext().getConferenceName(),
					getMember().getURI().toString(), text);
		}
	}

	/**
	 * Currently not used sample code to initiate recordings on a chat room.
	 * <br>
	 * Can be used by a controlling entity to record room session fragments.
	 * @param filename	the filename to write the recording to.
	 * @throws IOException
	 */
	public void startRecording(String filename) throws IOException {
		if (isMixer() && (getState() != State.STOP)) {
			if (getRecordings().containsKey(filename))
				throw new RuntimeException("Cannot record twice on " + filename);

			File fd = new File(filename);
			getRecordings().put(filename, fd);
		}
	}

	/**
	 * Stop a previously started recording, ending a recording session fragment.
	 * @param filename
	 * @return
	 * @throws IOException
	 * @see {@link #startRecording(String)}
	 */
	public long stopRecording(String filename) throws IOException {
		if (isMixer() && (getState() != State.STOP)) {
			File fd = getRecordings().remove(filename);
			if (fd != null) {
				long size = fd.length();
				return size;
			}
		}
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#setConferenceName(java.lang.String)
	 */
	public void setConferenceName(String uri) {
		if (isMixer())
			getRoomContext().setConferenceName(uri);
		else
			throw new RuntimeException("Only chat rooms can have a conference name");
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#setSubject(java.lang.String)
	 */
	public void setSubject(String subject) {
		if (isMixer())
			getRoomContext().setSubject(subject);
		else
			throw new RuntimeException("Only chat rooms can have a subject-line");
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#setParticipant(javax.sip.address.Address)
	 */
	public void setParticipant(Address entity) {
		if (isMixer())
			return;
		setMember(entity);
		updateParticipant();
	}

	/**
	 * Received an MSRP NICKNAME operation. Handle it.
	 * @param event
	 * @param aci the msrp aci
	 */
	public void onNickname(NicknameEvent event, ActivityContextInterface aci) {
		int result = ResponseCode.RC200;
		if (iskNicknameUnique(event.getNickname())) {
			setNickname(event.getNickname());
			updateParticipant();
		} else
			result = ResponseCode.RC425;
		try {
			getMsrpSession().sendNickResult(event.getRequest(), result, null);
		} catch (IllegalUseException e) {
			trc.severe("Error sending result to nickname request", e);
		}
	}

	/**
	 * @param nick the nickname to set
	 * @return whether it is a duplicate in this room or not.
	 */
	private boolean iskNicknameUnique(String nick) {
		for (Participant p : getRoomContext().getParticipants().values()) {
			if (p.getNickname().equalsIgnoreCase(nick))
				return false;
		}
		return true;
	}

	/**
	 * Broadcast a participants' update to the room.
	 */
	protected void updateParticipant() {
		ChatEvent ce = new ChatEvent(getConnectionId(), getMember(),
									getNickname(), getComposeState());
		fireUpdateParticipant(ce, getRoomContext(), null);
	}

	/**
	 * Receive the broadcasted participants update.
	 * If I'm the chat room (priority reception!), update the conference list.
	 * Otherwise send the new info to the chatter.
	 * 
	 * @param event
	 * @param aci the room aci
	 */
	public void onUpdateParticipant(ChatEvent event, ActivityContextInterface aci) {
		MsrpContext msrpAci = asSbbActivityContextInterface(aci);
		HashMap<String, Participant> participants = msrpAci.getParticipants();
		if (isMixer()) {
			trc.fine(from() + "onUpdateParticipant(" + event.getEntity() + ")");
			Participant p = new Participant(event.getEntity(),
								event.getNickname(), event.getComposeState());
			participants.put(event.getConnectionId(), p);
			msrpAci.setParticipants(participants);
		} else
			notifyConferenceChange(participants);
	}

	/**
	 * MSRP SEND operation received, broadcast to others.
	 * <br>
	 * 1 thing's for sure: user is no longer composing a message.
	 * 
	 * @param event
	 * @param aci the msrp aci
	 */
	public void onMessageIn(IncomingMessage event, ActivityContextInterface aci) {
		ChatEvent ce;

		if (getState() == State.STOP)
			return;
		if (event.isComplete()) {
			trc.fine(String.format("%sonMessageIn(id=[%s], size=[%d])",
							from(), event.getMessageID(), event.getSize()));
			if (event.getSize() > 0) {
				trc.fine(event.getContent());
			}
			endActive();
			ce = new ChatEvent(getConnectionId(), event);
			fireChat(ce, getRoomContext(), null);
		}
	}

	/**
	 * If participant was actively composing, end it and possibly notify others.
	 */
	private void endActive() {
		if (getComposeState() == ImState.active) {
			cancelTimer();
			setComposeState(ImState.idle);
			updateParticipant();
		}
	}

	/**
	 * Broadcasted chat received.
	 * <br>
	 * Send to chatter if I'm not the chat room (just record in that case)
	 * nor the broadcaster.
	 * <P />
	 * Note: depending on user agents, one still might want to send the
	 * broadcast to the caster (echo functionality).
	 * Again, left as an exercise to the reader.
	 * @param event
	 * @param aci the room aci
	 */
	public void onChat(ChatEvent event, ActivityContextInterface aci) {
		if (isMixer()) {
			if (getRecordings().size() > 0) {
				MsrpContext roomContext = asSbbActivityContextInterface(aci);
				Participant chatter = roomContext.getParticipants().get(event.getConnectionId());
				try {
					recordChatLine(chatter, event);
				} catch (IOException e) {
					trc.severe("Stop recording: error writing chat-content - " +
							e.getMessage());
				}
			}
		} else if (!isMe(event.getConnectionId())) {
			State state = getState();
			if (state == State.UNLINKED || state == State.STOP)
				return;
			try {
				Message msg = event.getMessage();
				if (msg == null) {
					trc.fine(from() + "onChat(Sending, size=[" + event.getContent().length() + "])");
					getMsrpSession().sendMessage(event.getContent());
				} else {
					trc.fine(from() + "onChat(Sending, size=[" + msg.getRawContent().length() + "])");
					getMsrpSession().sendMessage(msg.getContentType(), msg.getRawContent().getBytes(utf8));
				}
			} catch (Exception e) {
				trc.severe("could not send chat line.", e);
			}
		}
	}

	/**
	 * More example code: recording to storage using some xml formatting.
	 * 
	 * @param chatter
	 * @param event		content of chat event.
	 * @throws IOException
	 */
	private void recordChatLine(Participant chatter, ChatEvent event) throws IOException {
		final Message msg = event.getMessage();
		String content = msg == null ? event.getContent() : msg.getRawContent();
		File fd;
		FileWriter writer;

		for (String name : getRecordings().keySet()) {
			fd = getRecordings().get(name);
			writer = null;
			try {
				writer = new FileWriter(fd, true);
				writer.write(formatLine(chatter, content));
			} catch (IOException e) {
				getRecordings().remove(name);
				throw e;
			} finally {
				if (writer != null)
					writer.close();
			}
		}
	}

	/**
	 * Record the actual chat line and who said it.
	 * @param chatter
	 * @param message
	 * @return
	 */
	private static String formatLine(Participant chatter, String message) {
		StringBuilder sb = new StringBuilder(50 + message.length());

		sb.append("<message entity=\"").append(chatter.getEntity().toString())
		  .append("\" nickname=\"").append(chatter.getNickname()).append("\">")
		  .append(message).append("</message>").append(newLine);
		return(sb.toString());
	}

	/**
	 * Compose indication received (MSRP status message).
	 * <br>
	 * Modelled after <tt>RFC3994</tt>: Update composing state as indicated.
	 * Either refresh, ignore or update (on transition).
	 * 
	 * @param event the composing indication
	 * @param aci the msrp or chat aci
	 */
	public void onStatusUpdate(IncomingStatusMessage event, ActivityContextInterface aci) {
		if (aci.getActivity() instanceof MsrpSession) {
			/* handle in proper context, re-fire on mine */
			fireStatusUpdate(event, getMyContext(), null);
			return;
		}
		long refresh;
		ImState currentState = getComposeState();
		if (currentState == ImState.idle) {
			if (event.getState() == currentState)
				return;					/* no change, ignore update	*/
			else {						/* transition to active		*/
				setComposeState(event.getState());
				refresh = normalisePeriod(event.getRefresh());
				setTimerId(setTimer(refresh, aci));
				updateParticipant();
			}
		} else {						/* active 	*/
			cancelTimer();
			if (event.getState() == currentState) {
				refresh = normalisePeriod(event.getRefresh());
				setTimerId(setTimer(refresh, aci));
			} else {
				setComposeState(event.getState());
				updateParticipant();
			}
		}
	}

	/**
	 * Check refresh period and adjust accordingly.
	 * @param refresh period in seconds
	 * @return normalised value in milliseconds.
	 */
	private long normalisePeriod(int refresh) {
		if (refresh == 0)
			refresh = 120;
		else if (refresh < 60)
			refresh = 60;
		return refresh * 1000;
	}

	/* (non-Javadoc)
	 * @see net.sysmx.ChatControlChildMethods#triggerInfoNotification()
	 */
	public void triggerInfoNotification() {
		if (isMixer())
			throw new UnsupportedOperationException("not allowed on conference");
		notifyConferenceChange(getRoomContext().getParticipants());
	}

	/**
	 * Send given participants list to the controlling parent.
	 * @param participants the list to send.
	 */
	private void notifyConferenceChange(Map<String, Participant> participants) {
		String info = getConferenceInfo(participants);
		getController().conferenceNotification(info);
	}

	private static final String CONF_INFO =
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<conference-info xmlns=\"urn:ietf:params:xml:ns:conference-info\" entity=\"%s\" state=\"full\" version=\"1\">" +
        "<conference-description> <subject>%s</subject> </conference-description>" +
        "<conference-state> <user-count>%d</user-count> </conference-state>" +
        "<users>%s </users> </conference-info>";

	/**
	 * Format participants list into conference info.
	 * @param participants the list to format
	 * @return the formatted info
	 */
	private String getConferenceInfo(Map<String, Participant> participants) {
		StringBuilder userlist = new StringBuilder();
		for (Participant p : participants.values()) {
			userlist.append(p.toString());
		}
		return String.format(CONF_INFO, getRoomId(), getRoomContext().getSubject(),
				participants.size(), userlist);
	}

	public void onReportReceived(ReportEvent event, ActivityContextInterface aci) {
		// TODO: implement
	}

	public void onConnectionLost(ConnectionLostEvent event, ActivityContextInterface aci) {
		getController().mediaReleased();
	}

	/**
	 * Either a heartbeat or a composition expiry, handle it.
	 * 
	 * @param event
	 * @param aci
	 */
	public void onTimer(TimerEvent event, ActivityContextInterface aci) {
		trc.fine(from() + "onTimer");
		if (event.getTimerID().equals(getKeepAliveId())) {
			if (getIsAliveSent()) {
				disconnect();
			} else {
				setIsAliveSent(true);
				setKeepAliveId(setTimer(HowLong2Wait4KeepAlive, aci));
//				connectionChange();
			}
		} else {
			setTimerId(null);
			setComposeState(ImState.idle);
			updateParticipant();
		}
	}

    protected void cancelTimer() {
        cancelTimer(getTimerId());
        setTimerId(null);
    }

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#onDisconnect()
	 */
	public void disconnect() {
		if (isMixer()) {
			try {
				acNamingFacility.unbind(getRoomId());
			} catch (Throwable t) { ; }
		} else {
			logDebug("onDisconnect()");
			getMsrpSession().disconnect();
		}
		sbbContext.getSbbLocalObject().remove();
	}

	private void logDebug(String msg) {
		if (trc.isFineEnabled())
			trc.fine(msg);
	}

    public void setSbbContext(SbbContext context) {
		super.setSbbContext(context);
		this.trc = context.getTracer("MixerSbb");
		try {
			msrpAcif = (MsrpActivityContextInterfaceFactory)
						nameContext.lookup("slee/resources/msrp/1.0/acifactory");
			msrpIf = (MsrpResourceAdaptorSbbInterface)
					nameContext.lookup("slee/resources/msrp/1.0/sbbinterface");
			KeepAliveTick = (Integer) nameContext.lookup("KeepAliveTick");
		} catch (Throwable e) {
			trc.severe(e.getMessage());
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#setControlAgent(javax.slee.SbbLocalObject)
	 */
	public void setControlAgent(SbbLocalObject agent) {
		if (getController() == null) {
			setController((MixerFeedbackLocalObject) agent);
			setMyContext(createNullAci());
		} else {
			throw new IllegalStateException("control agent already set");
		}
	}

	/**
	 * Logging convenience
	 * @return the origin of this log message.
	 */
	private final String from() {
		return String.format("[room*%s-[%s]]: ", getRoomId(), getConnectionId());
	}

	/**
	 * @return whether I'm the mixer or not.
	 */
	private boolean isMixer() {
		return getConnectionId() == null;
	}

	/**
	 * @param connectionId
	 * @return whether the given connectionId is mine.
	 */
	private boolean isMe(String connectionId) {
		return (getConnectionId().equals(connectionId));
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#getRoom()
	 */
	public Object getRoom() {
		return getRoomId();
	}

	/* (non-Javadoc)
	 * @see org.mobicents.slee.example.msrp.MixerControlMethods#getSdp()
	 */
	public String getSdp() {
		return getLocalSdp();
	}

    /** Handling of CMP fields.
	 *  Implemented by SLEE, refer to <tt>sbb-jar.xml</tt> for specifics on the fields.
	 */
	//@{
	public abstract void setController(MixerFeedbackLocalObject controllerSbb);
	public abstract MixerFeedbackLocalObject getController();

	public abstract void setMyContext(ActivityContextInterface context);
	public abstract ActivityContextInterface getMyContext();

	public abstract void setRoomId(String value);
	public abstract String getRoomId();

	public abstract void setRoomContext(MsrpContext aci);
	public abstract MsrpContext getRoomContext();

	public abstract void setConnectionId(String value);
	public abstract String getConnectionId();

	public abstract void setLocalSdp(String value);
	public abstract String getLocalSdp();

	public abstract void setRemoteSdp(String sdp);
	public abstract String getRemoteSdp();

	public abstract void setState(State state);
	public abstract State getState();

	public abstract void setMember(Address entity);
	public abstract Address getMember();

	public abstract void setNickname(String nickname);
	public abstract String getNickname();

	public abstract void setComposeState(ImState state);
	public abstract ImState getComposeState();

	public abstract void setTimerId(TimerID value);
	public abstract TimerID getTimerId();

	public abstract void setKeepAliveId(TimerID value);
	public abstract TimerID getKeepAliveId();

	public abstract void setIsAliveSent(boolean yes);
	public abstract boolean getIsAliveSent();

	public abstract void setRecordings(HashMap<String, File> recordings);
	public abstract HashMap<String, File> getRecordings();
	//@}
}
