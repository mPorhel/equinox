/*******************************************************************************
 * Copyright (c) 2005, 2013 Cognos Incorporated, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Cognos Incorporated - initial API and implementation
 *     IBM Corporation - bug fixes and enhancements
 *******************************************************************************/
package org.eclipse.equinox.internal.cm;

import java.util.*;
import java.util.Map.Entry;
import org.osgi.framework.*;
import org.osgi.service.cm.*;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * ManagedServiceTracker tracks... ManagedServices and notifies them about related configuration changes
 */
class ManagedServiceTracker extends ServiceTracker<ManagedService, ManagedService> {

	final ConfigurationAdminFactory configurationAdminFactory;
	private final ConfigurationStore configurationStore;

	// managedServiceReferences guards both managedServices and managedServiceReferences
	private final Map<String, ManagedService> managedServices = new HashMap<String, ManagedService>();
	private final Map<String, ServiceReference<ManagedService>> managedServiceReferences = new HashMap<String, ServiceReference<ManagedService>>();

	private final SerializedTaskQueue queue = new SerializedTaskQueue("ManagedService Update Queue"); //$NON-NLS-1$

	public ManagedServiceTracker(ConfigurationAdminFactory configurationAdminFactory, ConfigurationStore configurationStore, BundleContext context) {
		super(context, ManagedService.class.getName(), null);
		this.configurationAdminFactory = configurationAdminFactory;
		this.configurationStore = configurationStore;
	}

	protected void notifyDeleted(ConfigurationImpl config) {
		config.checkLocked();
		String pid = config.getPid(false);
		ServiceReference<ManagedService> reference = getManagedServiceReference(pid);
		if (reference != null && config.bind(reference.getBundle()))
			asynchUpdated(getManagedService(pid), null);
	}

	protected void notifyUpdated(ConfigurationImpl config) {
		config.checkLocked();
		String pid = config.getPid();
		ServiceReference<ManagedService> reference = getManagedServiceReference(pid);
		if (reference != null && config.bind(reference.getBundle())) {
			Dictionary<String, Object> properties = config.getProperties();
			configurationAdminFactory.modifyConfiguration(reference, properties);
			asynchUpdated(getManagedService(pid), properties);
		}
	}

	public ManagedService addingService(ServiceReference<ManagedService> reference) {
		String pid = (String) reference.getProperty(Constants.SERVICE_PID);
		if (pid == null)
			return null;

		ManagedService service = context.getService(reference);
		if (service == null)
			return null;

		synchronized (configurationStore) {
			add(reference, pid, service);
		}
		return service;
	}

	public void modifiedService(ServiceReference<ManagedService> reference, ManagedService service) {
		String pid = (String) reference.getProperty(Constants.SERVICE_PID);
		synchronized (configurationStore) {
			if (getManagedService(pid) == service)
				return;
			String previousPid = getPidForManagedService(service);
			remove(reference, previousPid);
			addingService(reference);
		}
	}

	public void removedService(ServiceReference<ManagedService> reference, ManagedService service) {
		String pid = (String) reference.getProperty(Constants.SERVICE_PID);
		synchronized (configurationStore) {
			remove(reference, pid);
		}
		context.ungetService(reference);
	}

	private void add(ServiceReference<ManagedService> reference, String pid, ManagedService service) {
		ConfigurationImpl config = configurationStore.findConfiguration(pid);
		if (config == null) {
			if (trackManagedService(pid, reference, service)) {
				asynchUpdated(service, null);
			}
		} else {
			try {
				config.lock();
				if (trackManagedService(pid, reference, service)) {
					if (config.getFactoryPid() != null) {
						configurationAdminFactory.log(LogService.LOG_WARNING, "Configuration for " + Constants.SERVICE_PID + "=" + pid + " should only be used by a " + ManagedServiceFactory.class.getName()); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
					} else if (config.isDeleted()) {
						asynchUpdated(service, null);
					} else if (config.bind(reference.getBundle())) {
						Dictionary<String, Object> properties = config.getProperties();
						configurationAdminFactory.modifyConfiguration(reference, properties);
						asynchUpdated(service, properties);
					} else {
						configurationAdminFactory.log(LogService.LOG_WARNING, "Configuration for " + Constants.SERVICE_PID + "=" + pid + " could not be bound to " + reference.getBundle().getLocation()); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
					}
				}
			} finally {
				config.unlock();
			}
		}
	}

	private void remove(ServiceReference<ManagedService> reference, String pid) {
		ConfigurationImpl config = configurationStore.findConfiguration(pid);
		if (config == null) {
			untrackManagedService(pid, reference);
		} else {
			try {
				config.lock();
				untrackManagedService(pid, reference);
			} finally {
				config.unlock();
			}
		}
	}

	private boolean trackManagedService(String pid, ServiceReference<ManagedService> reference, ManagedService service) {
		synchronized (managedServiceReferences) {
			if (managedServiceReferences.containsKey(pid)) {
				String message = ManagedService.class.getName() + " already registered for " + Constants.SERVICE_PID + "=" + pid; //$NON-NLS-1$ //$NON-NLS-2$
				configurationAdminFactory.log(LogService.LOG_WARNING, message);
				return false;
			}
			managedServiceReferences.put(pid, reference);
			managedServices.put(pid, service);
			return true;
		}
	}

	private void untrackManagedService(String pid, ServiceReference<ManagedService> reference) {
		synchronized (managedServiceReferences) {
			managedServiceReferences.remove(pid);
			managedServices.remove(pid);
		}
	}

	private ManagedService getManagedService(String pid) {
		synchronized (managedServiceReferences) {
			return managedServices.get(pid);
		}
	}

	private ServiceReference<ManagedService> getManagedServiceReference(String pid) {
		synchronized (managedServiceReferences) {
			return managedServiceReferences.get(pid);
		}
	}

	private String getPidForManagedService(Object service) {
		synchronized (managedServiceReferences) {
			for (Entry<String, ManagedService> entry : managedServices.entrySet()) {
				if (entry.getValue() == service)
					return entry.getKey();
			}
			return null;
		}
	}

	private void asynchUpdated(final ManagedService service, final Dictionary<String, ?> properties) {
		queue.put(new Runnable() {
			public void run() {
				try {
					service.updated(properties);
				} catch (ConfigurationException e) {
					// we might consider doing more for ConfigurationExceptions
					Throwable cause = e.getCause();
					configurationAdminFactory.log(LogService.LOG_ERROR, e.getMessage(), cause != null ? cause : e);
				} catch (Throwable t) {
					configurationAdminFactory.log(LogService.LOG_ERROR, t.getMessage(), t);
				}
			}
		});
	}
}