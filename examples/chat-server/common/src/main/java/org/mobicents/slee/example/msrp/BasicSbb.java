package org.mobicents.slee.example.msrp;

import java.nio.charset.Charset;

import javax.naming.*;
import javax.slee.*;
import javax.slee.facilities.*;
import javax.slee.nullactivity.*;

/**
 * {@code Sbb} default base class. Convenience code.
 * 
 * @author Tom Uijldert
 */
public abstract class BasicSbb implements Sbb {
    public static Charset utf8 = Charset.forName("UTF-8");

	private transient Tracer trc;

	protected SbbContext 	sbbContext;
	protected Context 		nameContext;

	protected NullActivityContextInterfaceFactory	nullAcif;
	protected NullActivityFactory					nullActivityFactory;
	protected ActivityContextNamingFacility		acNamingFacility;

	protected TimerFacility		timerFacility;
	protected static TimerOptions	timerOptions = new TimerOptions();

	/**
	 * Get an attached null activity. If none attached: create and attach
	 * @return	the attached null-activity.
	 */
	protected ActivityContextInterface getNullActivity() {
	    for (ActivityContextInterface aci : sbbContext.getActivities())
	        if (aci.getActivity() instanceof NullActivity)
	        	return aci;

	    /* Not present yet, create and attach. */
	    return createNullAci();
	}

	/**
	 * Create a null activity, attach to it and return its' interface.
	 * @return the aci or null on failure.
	 */
	protected ActivityContextInterface createNullAci() {
		NullActivity nullActivity = nullActivityFactory.createNullActivity();
	    ActivityContextInterface aci = null;
	    try {
	        aci = nullAcif.getActivityContextInterface(nullActivity);
	        aci.attach(sbbContext.getSbbLocalObject());
	        return aci;
	    } catch (Exception e) {
	        nullActivity.endActivity();
	        return null;
	    }
	}

	/**
	 * Set a timer on the attached null activity
	 * @param duration in milliseconds
	 * @return the timerID
	 */
	protected TimerID setTimer(long duration) {
		return setTimer(duration, getNullActivity());
	}

	/**
	 * Set a timer on given activity.
	 * @param duration in milliseconds
	 * @param aci the activity to put the timer on
	 * @return the timerID
	 */
	protected TimerID setTimer(long duration, ActivityContextInterface aci) {
	    try {
	        return timerFacility.setTimer(aci, null,
	                System.currentTimeMillis() + duration, timerOptions);
	    } catch (RuntimeException re) {
	    	trc.severe(	"Early warning system: RuntimeException setting timer " +
	    				"(beginning of the end?).\nReboot Proxy ASAP!.", re);
	    } catch (Exception e) {
	        trc.severe("setTimer(): Failed to start timer", e);
	    }
        return null;
	}

	/**
	 * Cancel given timer.
	 * @param timerID the timer to cancel
	 */
	protected void cancelTimer(TimerID timerID) {
	    if (timerID != null) {
	        try {
	            timerFacility.cancelTimer(timerID);
	        } catch (Exception e) {
	            trc.warning("cancelTimer() failed: " + e.getMessage());
	        }
	    }
	}

	/** Standard Sbb routines.
	 *  They deal with the life-cycle of an Sbb.
	 */
	//@{
	public final SbbLocalObject getMyIf() {
		return sbbContext.getSbbLocalObject();
	}

	public void setSbbContext(SbbContext context) {
		sbbContext = context;
		this.trc = context.getTracer("BasicSbb");
		try {
			nameContext = (Context) new InitialContext().lookup("java:comp/env");

			nullAcif = (NullActivityContextInterfaceFactory)
				nameContext.lookup("slee/nullactivity/activitycontextinterfacefactory");
			nullActivityFactory = (NullActivityFactory)
	    		nameContext.lookup("slee/nullactivity/factory");
	        acNamingFacility = (ActivityContextNamingFacility)
	        	nameContext.lookup("slee/facilities/activitycontextnaming");
			timerFacility = (TimerFacility)
				nameContext.lookup("slee/facilities/timer");
		}
		catch (Throwable e) {
			trc.severe(e.getMessage());
		}
	}

	public void unsetSbbContext() { sbbContext = null; }

	public void sbbCreate() {/* empty */}

	public void sbbPostCreate() {/* empty */}

	public void sbbActivate() {/* empty */}

	public void sbbPassivate() {/* empty */}

	public void sbbRemove() {/* empty */}

	public void sbbLoad() {/* empty */}

	public void sbbStore() {/* empty */}

	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {/* empty */}

	public void sbbRolledBack(RolledBackContext context) {
		trc.warning("sbbRolledBack(event = " + context.getEvent() + ")");
	}
	//@}
}
