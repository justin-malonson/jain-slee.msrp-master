/*
 * Copyright 2012 by the @authors tag.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.slee.resource.msrp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;

import javax.slee.Address;
import javax.slee.SLEEException;
import javax.slee.facilities.EventLookupFacility;
import javax.slee.facilities.Tracer;
import javax.slee.resource.ActivityAlreadyExistsException;
import javax.slee.resource.ActivityHandle;
import javax.slee.resource.ConfigProperties;
import javax.slee.resource.FailureReason;
import javax.slee.resource.FireableEventType;
import javax.slee.resource.InvalidConfigurationException;
import javax.slee.resource.Marshaler;
import javax.slee.resource.ReceivableService;
import javax.slee.resource.ResourceAdaptor;
import javax.slee.resource.ResourceAdaptorContext;
import javax.slee.resource.SleeEndpoint;
import javax.slee.resource.StartActivityException;

import javax.net.msrp.Session;

/**
 * A resource adaptor for the MSRP protocol.<BR>
 * Inspiration:<BR>
 * 	&nbsp;- java.net/projects/msrp<BR>
 * Shamelessly copied from MSRP-stack of João André Pereira Antunes.<BR>
 * Using the MSRP-stack, this subsystem implements an MSRP JSLEE Resource Adaptor.
 * <BR>
 * Thus, it enables Sbb's to intercept MSRP messages and file transfers as they see fit.
 * 
 * @author tuijldert
 */
public class MsrpResourceAdaptor implements ResourceAdaptor {
	private transient Tracer trc;

	private ResourceAdaptorContext raContext;
	private SleeEndpoint sleeEndpoint;
	private BasicMsrpResourceAdaptorSbbInterface sbbIf;

	/** Look up the event id of incoming events */
	private EventLookupFacility eventLookup;

	/** cache the eventIDs, avoiding lookup in container */
	private EventIDCache eventIdCache;

	/**
	 * tells the RA if an event with a specified ID should be filtered or not
	 */
	private EventIDFilter eventIDFilter;

	private static final String IPADDRESS_CONFIG_PROPERTY = "slee.resource.msrp.ipaddress";
    private static final String DEFAULT_MSRP_IPADDRESS = "0.0.0.0";

//    private static final int DEFAULT_MSRP_PORT = 2855;

    private String address;

	private transient ConcurrentHashMap<MsrpActivityHandle, MsrpSession> activities;

	public MsrpResourceAdaptor() { }

	public ResourceAdaptorContext getResourceAdaptorContext() {
		return raContext;
	}

	public SleeEndpoint getSleeEndpoint() {
		return sleeEndpoint;
	}

	public Tracer getTracer() {
		return trc;
	}

	public InetAddress getAddress() throws UnknownHostException {
			return InetAddress.getByName(address);
	}

	// lifecycle methods ---------------
	public void setResourceAdaptorContext(ResourceAdaptorContext ctxt) {
		raContext = ctxt;
		trc = ctxt.getTracer(MsrpResourceAdaptor.class.getSimpleName());

		eventIdCache = new EventIDCache(ctxt.getTracer(EventIDCache.class.getSimpleName()));
		eventIDFilter = new EventIDFilter();
		sleeEndpoint = ctxt.getSleeEndpoint();
		eventLookup = ctxt.getEventLookupFacility();
		sbbIf = new BasicMsrpResourceAdaptorSbbInterface(this);
	}

	public void raConfigure(ConfigProperties properties) {
		address = DEFAULT_MSRP_IPADDRESS;
		if (properties.getProperty(IPADDRESS_CONFIG_PROPERTY) != null)
			address = (String) properties.getProperty(IPADDRESS_CONFIG_PROPERTY).getValue();
	}

	public void raActive() {
    	this.activities = new ConcurrentHashMap<MsrpActivityHandle, MsrpSession>();
    	sbbIf.setActive(true);

    	if (trc.isFineEnabled())
        	trc.fine(String.format("MSRP RA starting as host %s.", address));
	}

	public void raStopping() {
		/* empty */
	}

	public void raInactive() {
		sbbIf.setActive(false);
		for (ActivityHandle handle : activities.keySet()) {
			endActivity(handle);
		}
		activities = null;
	}

	public void raUnconfigure() {
		/* empty */
	}

	public void unsetResourceAdaptorContext() {
		raContext = null;
		trc = null;
		eventIdCache = null;
		eventIDFilter = null;
		sleeEndpoint = null;
		eventLookup = null;
	}

	// config management methods -------
	public void raVerifyConfiguration(ConfigProperties properties)
			throws InvalidConfigurationException {
		try {
			InetAddress.getByName(address);
		} catch (UnknownHostException uhe) {
			throw new InvalidConfigurationException("Unknown host: " + address);
		}
	}

	public void raConfigurationUpdate(ConfigProperties properties) {
		throw new UnsupportedOperationException();
	}

	// event filtering methods ---------
	public void serviceActive(ReceivableService service) {
		eventIDFilter.serviceActive(service);
	}

	public void serviceStopping(ReceivableService service) {
		eventIDFilter.serviceStopping(service);
	}

	public void serviceInactive(ReceivableService service) {
		eventIDFilter.serviceInactive(service);
	}

	// mandatory callbacks -------------
	public void administrativeRemove(ActivityHandle handle) {
		/* empty */
	}

	public Object getActivity(ActivityHandle activityHandle) {
		return activities.get(activityHandle);
	}

	public ActivityHandle getActivityHandle(Object activity) {
		MsrpActivityHandle handle = null;
		if (activity instanceof MsrpSession) {
			handle = new MsrpActivityHandle(((MsrpSession) activity).getSessionId());
			if (!activities.containsKey(handle)) {
				handle = null;
			}
		}
		return handle;
	}

	// optional call-backs -------------
	public void activityEnded(ActivityHandle handle) {
		/* empty */
	}

	public void activityUnreferenced(ActivityHandle activityHandle) {
		/* empty */
	}

	public void eventProcessingFailed(ActivityHandle arg0,
			FireableEventType arg1, Object arg2, Address arg3,
			ReceivableService arg4, int arg5, FailureReason arg6) {
		/* empty */
	}

	public void eventProcessingSuccessful(ActivityHandle arg0,
			FireableEventType arg1, Object arg2, Address arg3,
			ReceivableService arg4, int arg5) {
		/* empty */
	}

	public void eventUnreferenced(ActivityHandle arg0, FireableEventType arg1,
			Object event, Address arg3, ReceivableService arg4, int arg5) {
		/* empty */
	}

	public void queryLiveness(ActivityHandle activityHandle) {
		MsrpSession session = activities.get(activityHandle);
		if ((session == null) || !((Session) session.getSession()).isActive())
			endActivity(activityHandle);
	}

	// interface accessors -------------
	public Object getResourceAdaptorInterface(String arg0) {
		return sbbIf;
	}

	public Marshaler getMarshaler() {
		return null;
	}

	// ra logic ------------------------
	MsrpSession createActivity(String sessionId, Session session, MsrpActivityHandle handle) throws
					ActivityAlreadyExistsException, NullPointerException,
					IllegalStateException, SLEEException, StartActivityException {

		MsrpSession activity = new BasicMsrpSession(sessionId, session, this);
		// lookup the activity and check if already exists
		if (activities.get(handle) == null) {
			activities.put(handle, activity);
			sleeEndpoint.startActivity(handle, activity);
			if (trc.isFineEnabled())
				trc.fine("Started Msrp session activity: " + activity.getSessionId());
		} else {
			throw new ActivityAlreadyExistsException(
					"Duplicate activity: " + activity);
		}
		return activity;
	}

	void endActivity(String sessionId) {
		MsrpActivityHandle handle = new MsrpActivityHandle(sessionId);
		if (activities.get(handle) != null) {
			activities.remove(handle);
			endActivity(handle);
		}
	}

	public void fireEvent(Object event, MsrpActivityHandle handle) {
    	if (trc.isFineEnabled())
    		trc.fine("New Msrp-RA event: " + event.getClass().getName());

        final FireableEventType eventType = eventIdCache.getEventType(eventLookup, event);
        try {
        	sleeEndpoint.fireEvent(handle, eventType, event, null, null);
        } catch (Throwable e) {
            trc.severe("Failed to fire event.", e);
        }
    }

	private void endActivity(ActivityHandle handle) {
		try {
			sleeEndpoint.endActivity(handle);
		} catch (Throwable e) {
			trc.severe("Failed to end activity " + handle, e);
		}
	}
}
