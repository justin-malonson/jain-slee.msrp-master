/**
 * 
 */
package org.mobicents.slee.example.msrp.room;

import java.io.Serializable;

import javax.net.msrp.ImState;
import javax.sip.address.Address;

/**
 *  Data on participant of a conference.
 *  
 * @author Tom Uijldert
 */
public class Participant implements Serializable {

	private static final long serialVersionUID = 1L;
	private Address entity;
	private String nickname;
	private ImState composeState;

	/**
	 * 
	 */
	public Participant(Address entity, String nickname, ImState state) {
		if (entity == null)
			throw new NullPointerException("Entity-URL should be filled");
		this.entity = entity;
		this.nickname = nickname == null ? "" : nickname;
		this.composeState = state == null ? ImState.idle : state;
	}

	public String toString() {
		return "\t\t<user entity=\"" + entity.getURI() + "\" state=\"full\">\r\n" +
				(entity.getDisplayName() == null ?
						"" :
						"\t\t\t<display-text>" + entity.getDisplayName() + "</display-text>\r\n") +
				"\t\t\t<nickname>" +  nickname + "</nickname>\r\n" +
				"\t\t\t<isComposing><state>" + composeState.name() + "</state></isComposing>\r\n" +
				"\t\t</user>\r\n";
	}

	public Address	getEntity()			{ return entity; }
	public String	getNickname() 		{ return nickname; }
	public ImState	getComposeState()	{ return composeState; }
}
