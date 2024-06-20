/**
 * 
 */
package org.mobicents.slee.example.msrp.events;

import javax.net.msrp.ImState;
import javax.net.msrp.Message;
import javax.sip.address.Address;

/**
 * Carries any data relating to the specific event.
 * 
 * @author Tom Uijldert
 */
public class ChatEvent extends CustomEventType {
	private Object roomId;
	private String connectionId;
    private String content;
    private Message msg;
    private String sdp;
    private Address entity;
    private String nickname;
    private ImState composeState;

    public ChatEvent(Object roomId, String connectionId, String content, Message msg,
    				String sdp, Address entity, String nickname, ImState state) {
		super();
		this.roomId = roomId;
		this.connectionId = (String) connectionId;
        this.content = content;
        this.msg = msg;
        this.sdp = sdp;
        this.entity = entity;
        this.nickname = nickname;
        this.composeState = state;
	}

    public ChatEvent(Object roomId, String sdp) {
    	this(roomId, "", "", null, sdp, null, "", null);
    }

    public ChatEvent(String connectionId, Message msg) {
    	this(null, connectionId, "", msg, null, null, "", null);
    }

    public ChatEvent(String connectionId, String content) {
    	this(null, connectionId, content, null, null, null, "", null);
    }

    public ChatEvent() {
    	this(null, "", "", null, null, null, "", null);
    }

    public ChatEvent(String sdp) {
    	this(null, "", "", null, sdp, null, "", null);
    }

    public ChatEvent(String connectionId, Address entity, String nickname, ImState state) {
    	this(null, connectionId, "", null, null, entity, nickname, state);
    }

    public boolean equals(Object obj) {
		if (obj != null && obj.getClass() == this.getClass())
			return (roomId.equals(((ChatEvent) obj).roomId) &&
					connectionId.equals(((ChatEvent) obj).connectionId) &&
					content.equals(((ChatEvent) obj).content) &&
					msg.equals(((ChatEvent) obj).msg) &&
					sdp.equals(((ChatEvent) obj).sdp) &&
					entity.equals(((ChatEvent) obj).entity) &&
					nickname.equals(((ChatEvent) obj).nickname) &&
					composeState.equals(((ChatEvent) obj).composeState)
					);
		return false;
	}

	public Object getroomId() { return roomId; }
	public String getConnectionId() { return connectionId; }
	public String getContent() { return content; }
	public Message getMessage() { return msg; }
	public String getSdp() { return sdp; }
	public Address getEntity() { return entity; }
	public String getNickname() { return nickname; }
	public ImState getComposeState() { return composeState; }
}
