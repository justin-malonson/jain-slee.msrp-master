/**
 * 
 */
package org.mobicents.slee.example.msrp;

import gov.nist.javax.sip.Utils;

import java.text.ParseException;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.sip.*;
import javax.sip.address.AddressFactory;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.SbbContext;
import javax.slee.facilities.Tracer;

import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.mobicents.slee.example.msrp.events.ChatEvent;

/**
 * @author tuijldert
 */
public abstract class GenericChatSbb extends BasicSbb {
	private static transient Tracer trc;

	public final static String ChatFlag = "message";
	public final static String IsFocusParam = "isfocus";
	public final static String EventParam = "event";
	public final static String ConferenceValue = "conference";
	public final static String NoValue = null;

	protected static final ChatEvent emptyChatEvent = new ChatEvent();

	protected SipActivityContextInterfaceFactory	sipAcif;
	protected SleeSipProvider						sipProvider;

	protected AddressFactory	addressFactory;
	protected MessageFactory	messageFactory;
	protected HeaderFactory	headerFactory;

	protected String getSipRaAcifName() {
		return "slee/resources/jainsip/1.2/acifactory";
	}

	protected String getSipRaProviderName() {
		return "slee/resources/jainsip/1.2/chatprovider";
	}

	public abstract ChildRelation getMixerRelation();

	public void setSbbContext(SbbContext context) {
		super.setSbbContext(context);
		trc = context.getTracer("GenericChatSbb");

		try {
	        sipAcif = (SipActivityContextInterfaceFactory)
		        	nameContext.lookup(getSipRaAcifName());
			sipProvider = (SleeSipProvider) nameContext.lookup(getSipRaProviderName());

			messageFactory = sipProvider.getMessageFactory();
	        addressFactory = sipProvider.getAddressFactory();
	        headerFactory  = sipProvider.getHeaderFactory();
		} catch (NamingException e) {
			trc.severe(e.getMessage());
		}
	}

	/**
	 * @return State handling context of this sbb.
	 */
	public ActivityContextInterface getMyContext() {
		return getNullActivity();
	}

	/**
	 * Retrieves the chat control interface for this Sbb.
	 */
	protected MixerControlLocalObject getMediaStream() {
		for (Object childSbb : getMixerRelation())
			return (MixerControlLocalObject) childSbb;
		return null;
	}

	/**
	 * @param req	the sdp-carrying request
	 * @return		whether the request contains an sdp chat-offer
	 */
	public static boolean isChatOffer(Request req) {
		ContentLengthHeader clh = req.getContentLength();
		if ((clh == null) || (clh.getContentLength() == 0))
			return false;				// Empty SDP defaults to tel-session
		String sdp = new String(req.getRawContent(), BasicSbb.utf8);
		return chatline.matcher(sdp).find();
	}
	private static Pattern chatline = Pattern.compile("^m=message", Pattern.MULTILINE);

	/* SIP stuff	*/
	/**
	 * Retrieve first server transaction activity from attached activities.
	 * @return the server transaction, null if none present.
	 */
	protected ServerTransaction getServerTransaction() {
	    for (ActivityContextInterface aci : sbbContext.getActivities()) {
	        if (aci.getActivity() instanceof ServerTransaction)
	            return (ServerTransaction) aci.getActivity();
	    }
	    return null;
	}

	/**
	 * Detach from server transaction activity.
	 */
	protected void detachServerTransaction() {
	    for (ActivityContextInterface aci : sbbContext.getActivities())
	        if (aci.getActivity() instanceof ServerTransaction)
	            aci.detach(sbbContext.getSbbLocalObject());
	}

	/**
	 * Get dialog of this server transaction or create one for it. Attach to sbb.
	 * @param st			the server transaction
	 * @throws SipException	something failed
	 */
	protected void getAndAttachDialog(ServerTransaction st) throws SipException {
		DialogActivity dialog = (DialogActivity) st.getDialog();
		if (dialog == null)
		    dialog = (DialogActivity) sipProvider.getNewDialog(st);
		sipAcif.getActivityContextInterface(dialog).attach(getMyIf());
	}

	/**
	 * Send response on attached server transaction with given status.
	 * @param status	the response status to return
	 */
	protected void replyToServerTransaction(int status) {
		replyToServerTransaction(status, getServerTransaction());
	}

	/**
	 * Send response on server transaction with given status.
	 * @param status the response status to return
	 * @param st     the server transaction
	 */
	protected void replyToServerTransaction(int status, ServerTransaction st) {
	    try {
	    	replyToServerTransaction(status, st, null, false);
	    } catch (Exception e) {
	        trc.severe("Failed to send response: " + e.getMessage());
	    }
	}

	/** Send a response based on server transaction & status.
	 * @param status
	 * @param st
	 * @param sdp
	 * @param addContact
	 * @throws ParseException
	 * @throws SipException
	 * @throws InvalidArgumentException
	 */
	protected void replyToServerTransaction(int status, ServerTransaction st,
			String sdp, boolean addContact) throws ParseException, SipException,
			InvalidArgumentException {
	    st.sendResponse(makeReply(status, st, sdp, addContact));
	}

	/**
	 * Create a response based on server transaction with given status.
	 * @param status
	 * @param st the server transaction we reply on
	 * @param sdp sdp data to include (null when not needed)
	 * @param addContact Should we add a contact-header?
	 * @throws ParseException Malformed request or header
	 * @throws SipException Something wrong while SIPping
	 * @throws InvalidArgumentException inherited from @c sendResponse()
	 */
	protected Response makeReply(int status, ServerTransaction st,
			String sdp, boolean addContact) throws ParseException {
		Response rsp;
		if (sdp != null) {          // include sdp when required
	        ContentTypeHeader cth = headerFactory.createContentTypeHeader("application", "sdp");
	        rsp = messageFactory.createResponse(status, st.getRequest(), cth, sdp);
	    } else {
	        rsp = messageFactory.createResponse(status, st.getRequest());
	    }
	    setToTag(st, rsp, status);

	    if (addContact) {         // add contact header when required
	        String address = sipProvider.getListeningPoints()[0].getIPAddress();
	        int port = sipProvider.getListeningPoints()[0].getPort();
	        rsp.setHeader(headerFactory.createContactHeader(
	                addressFactory.createAddress("sip:" + address + ":" + port)));
	    }
		return rsp;
	}

	protected static void setToTag(final ServerTransaction st, Response rsp, final int status)
									throws ParseException {
		Dialog d = st.getDialog();
		ToHeader to = (ToHeader) rsp.getHeader(ToHeader.NAME);

		if (d != null) {				// set tag to keep dialog
			String ltag = d.getLocalTag();
		    if ((to.getTag() == null) && (ltag != null)) {
		        to.setTag(ltag);
		    }
	        return;
		}
		if (status > 100 && status <= 200)
	        to.setTag(Utils.getInstance().generateTag());
	}

	/** Is this a response to an INVITE request?
	 * @param rsp	the response
	 * @return		true = yes this is a response to an INVITE.
	 */
	protected static boolean isInviteResponse(final Response rsp) {
		return ((CSeqHeader) rsp.getHeader(CSeqHeader.NAME)).getMethod().equals(Request.INVITE);
	}

	/**
	 * Retrieve dialog activity from the attached activities.
	 * @return the dialog, null if none present.
	 */
	protected DialogActivity getDialog() {
	    for (ActivityContextInterface aci : sbbContext.getActivities())
	        if (aci.getActivity() instanceof DialogActivity)
	            return (DialogActivity) aci.getActivity();
	    return null;
	}

	/**
	 * Modify contact header in SIP message to indicate conference capabilities.
	 * @param message			The SIP message to modify.
	 * @throws ParseException	Error in parameters.
	 */
	protected static void indicateConference(javax.sip.message.Message message)
			throws ParseException {
		ContactHeader cth = (ContactHeader) message.getHeader(ContactHeader.NAME);
		cth.setParameter(IsFocusParam, NoValue);
		cth.setParameter(EventParam, ConferenceValue);
	}

	/**
	 * Delete given dialog when not already in terminated state.
	 * @param dialog the dialog to delete
	 */
	protected void rmDialog(DialogActivity dialog) {
	    if (dialog.getState() != DialogState.TERMINATED)
	        dialog.delete();
	}

	/**
	 * Send OK response to a subscribe request.
	 * @param st		the server transaction of the request.
	 * @param expiry	the <tt>expire</tt> value to use.
	 */
	protected void replySubscriptionOk(ServerTransaction st, int expiry) {
	    try {
	    	ExpiresHeader expiresH = headerFactory.createExpiresHeader(expiry);
	        Response rsp = messageFactory.createResponse(Response.OK, st.getRequest());
	        rsp.setExpires(expiresH);
	
	        st.sendResponse(rsp);
	    } catch (Exception e) {
	        trc.severe("Failed to send response: " + e.getMessage());
	    }
	}

	/**
	 * send BYE on active dialog or server transaction. 
	 */
	protected void sendBye() {
		DialogActivity dialog = getDialog();
		if ((dialog != null) &&  (dialog.getState() != null) &&
			(dialog.getState() == DialogState.CONFIRMED)) { // BYE on an existing connection
			try {
				dialog.sendRequest(dialog.createRequest(Request.BYE));
			} catch (Exception e) {
				trc.warning("Exception sending BYE", e);
			}
		} else {
			ServerTransaction st = getServerTransaction();
			if (st != null)			// Terminate a pending INVITE.
				replyToServerTransaction(Response.REQUEST_TERMINATED, st);
		}
	}
}
