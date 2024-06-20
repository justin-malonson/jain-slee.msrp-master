/**
 * 
 */
package org.mobicents.slee.example.msrp;

import javax.slee.ActivityContextInterface;

/**
 * Represents a focus activity context.
 * @author tuijldert
 */
public interface FocusContext extends ActivityContextInterface {
	public String getFocusName();
	public void setFocusName(String name);
}
