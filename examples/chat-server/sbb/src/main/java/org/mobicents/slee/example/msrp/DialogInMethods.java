package org.mobicents.slee.example.msrp;

import javax.sip.ServerTransaction;

/**
 * @author tuijldert
 */
public interface DialogInMethods {
	/**
	 * Handle an incoming chat request.
	 * @param st Transaction containing the INVITE.
	 * @throws Exception on SLEE errors, calling errors etc.
	 */
	void setupChatDialog(ServerTransaction st) throws Exception;
}
