/**
 * 
 */
package org.mobicents.slee.example.msrp.room;

/**
 * Mixer/Media stream state.
 * @author Tom Uijldert
 */
public enum State {
	ACTIVE,					/**< default chat room state			*/
	LINKED,					/**< linked to chat room				*/
	START,
	STOP,
	UNLINKED,				/**< unlinked from chat room			*/
}
