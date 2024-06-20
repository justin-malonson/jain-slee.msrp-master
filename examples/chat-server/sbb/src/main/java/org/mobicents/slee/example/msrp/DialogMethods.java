package org.mobicents.slee.example.msrp;

import javax.slee.SbbLocalObject;

public interface DialogMethods {

	/**
     * Points to <tt>parentSbb</tt> (the focus).
     * @param focus	 parent <tt>sbb</tt> to point to
     */
    public void setFocalPoint(SbbLocalObject focus);

    /**
     * @return call-id of this dialog
     */
    public String getId();

    /**
     * Disconnect media from mixer and end the dialog.
     */
    void disconnect();
}
