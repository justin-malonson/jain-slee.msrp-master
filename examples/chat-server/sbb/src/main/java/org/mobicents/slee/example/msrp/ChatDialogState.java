/**
 * 
 */
package org.mobicents.slee.example.msrp;

/**
 * @author tuijldert
 */
public enum ChatDialogState {
	CHATTING,				/**< default active state					*/
	CONN_MODIFY,			/**< modifying connection					*/
	EARLY,					/**< early dialog							*/
	EARLY_PROMPT,			/**< sending early prompts					*/
	HANGUP_DELAY,			/**< setup failed, wait 4 event before exit	*/
	START,                  /**< start state                          	*/
	STOP,                   /**< stop state                          	*/
	WAIT4ANSWER, 			/**< INVITE sent, wait 4 answer				*/
}
