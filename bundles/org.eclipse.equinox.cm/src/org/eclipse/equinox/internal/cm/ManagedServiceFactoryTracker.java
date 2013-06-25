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
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * ManagedServiceFactoryTracker tracks... ManagedServiceFactory(s) and notifies them about related configuration changes
 */
class ManagedServiceFactoryTracker extends ServiceTracker<ManagedServiceFactory, ManagedServiceFactory> {

	final ConfigurationAdminFactory configurationAdminFactory;
	private final ConfigurationStore configurationStore;

	// managedServiceFactoryReferences guards both managedServiceFactories and managedServiceFactoryReferences
	private final Map<String, ManagedServiceFactory> managedServiceFactories = new HashMap<String, ManagedServiceFactory>();
	private final Map<String, ServiceReference<ManagedServiceFactory>> managedServiceFactoryReferences = new HashMap<String, ServiceReference<ManagedServiceFactory>>();

	private final SerializedTaskQueue queue = new SerializedTaskQueue("ManagedServiceFactory Update Queue"); //$NON-NLS-1$

	public ManagedServiceFactoryTracker(ConfigurationAdminFactory configurationAdminFactory, ConfigurationStore configurationStore, BundleContext context) {
		super(context, ManagedServiceFactory.class.getName(), null);
		this.configurationAdminFactory = configurationAdminFactory;
		this.configurationStore = configurationStore;
	}

	protected void notifyDeleted(ConfigurationImpl config) {
		config.checkLocked();
		String factoryPid = config.getFactoryPid(false);
		ServiceReference<ManagedServiceFactory> reference = getManagedServiceFactoryReference(factoryPid);
		if (reference != null && config.bind(reference.getBundle()))
			asynchDeleted(getManagedServiceFactory(factoryPid), config.getPid(false));
	}

	protected void notifyUpdated(ConfigurationImpl config) {
		config.checkLocked();
		String factoryPid = config.getFactoryPid();
		ServiceReference<ManagedServiceFactory> reference = getManagedServiceFactoryReference(factoryPid);
		if (reference != null && config.bind(reference.getBundle())) {
			Dictionary<String, Object> properties = config.getProperties();
			configurationAdminFactory.modifyConfiguration(reference, properties);
			asynchUpdated(getManagedServiceFactory(factoryPid), config.getPid(), properties);
		}
	}

	public ManagedServiceFactory addingService(ServiceReference<ManagedServiceFactory> reference) {
		String factoryPid = (String) reference.getProperty(Constants.SERVICE_PID);
		if (factoryPid == null)
			return null;

		ManagedServiceFactory service = context.getService(reference);
		if (service == null)
			return null;

		synchronized (configurationStore) {
			add(reference, factoryPid, service);
		}
		return service;
	}

	public void modifiedService(ServiceReference<ManagedServiceFactory> reference, ManagedServiceFactory service) {
		String factoryPid = (String) reference.getProperty(Constants.SERVICE_PID);
		synchronized (configurationStore) {
			if (getManagedServiceFactory(factoryPid) == service)
				return;
			String previousPid = getPidForManagedServiceFactory(service);
			remove(reference, previousPid);
			addingService(reference);
		}
	}

	public void removedService(ServiceReference<ManagedServiceFactory> reference, ManagedServiceFactory service) {
		String factoryPid = (String) reference.getProperty(Constants.SERVICE_PID);
		synchronized (configurationStore) {
			remove(reference, factoryPid);
		}
		context.ungetService(reference);
	}

	private void add(ServiceReference<ManagedServiceFactory> reference, String factoryPid, ManagedServiceFactory service) {
		ConfigurationImpl[] configs = configurationStore.getFactoryConfigurations(factoryPid);
		try {
			for (int i = 0; i < configs.length; ++i)
				configs[i].lock();

			if (trackManagedServiceFactory(factoryPid, reference, service)) {
				for (int i = 0; i < configs.length; ++i) {
					if (configs[i].isDeleted()) {
						// ignore this config
					} else if (configs[i].bind(reference.getBundle())) {
						Dictionary<String, Object> properties = configs[i].getProperties();
						configurationAdminFactory.modifyConfiguration(reference, properties);
						asynchUpdated(service, configs[i].getPid(), properties);
					} else {
						configurationAdminFactory.log(LogService.LOG_WARNING, "Configuration for " + Constants.SERVICE_PID + "=" + configs[i].getPid() + " could not be bound to " + reference.getBundle().getLocation()); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
					}
				}
			}
		} finally {
			for (int i = 0; i < configs.length; ++i)
				configs[i].unlock();
		}
	}

	private void remove(ServiceReference<ManagedServiceFactory> reference, String factoryPid) {
		ConfigurationImpl[] configs = configurationStore.getFactoryConfigurations(factoryPid);
		try {
			for (int i = 0; i < configs.length; ++i)
				configs[i].lock();
			untrackManagedServiceFactory(factoryPid, reference);
		} finally {
			for (int i = 0; i < configs.length; ++i)
				configs[i].unlock();
		}
	}

	private boolean trackManagedServiceFactory(String factoryPid, ServiceReference<ManagedServiceFactory> reference, ManagedServiceFactory service) {
		synchronized (managedServiceFactoryReferences) {
			if (managedServiceFactoryReferences.containsKey(factoryPid)) {
				configurationAdminFactory.log(LogService.LOG_WARNING, ManagedServiceFactory.class.getName() + " already registered for " + Constants.SERVICE_PID + "=" + factoryPid); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			}
			managedServiceFactoryReferences.put(factoryPid, reference);
			managedServiceFactories.put(factoryPid, service);
			return true;
		}
	}

	private void untrackManagedServiceFactory(String factoryPid, ServiceReference<ManagedServiceFactory> reference) {
		synchronized (managedServiceFactoryReferences) {
			managedServiceFactoryReferences.remove(factoryPid);
			managedServiceFactories.remove(factoryPid);
		}
	}

	private ManagedServiceFactory getManagedServiceFactory(String factoryPid) {
		synchronized (managedServiceFactoryReferences) {
			return managedServiceFactories.get(factoryPid);
		}
	}

	private ServiceReference<ManagedServiceFactory> getManagedServiceFactoryReference(String factoryPid) {
		synchronized (managedServiceFactoryReferences) {
			return managedServiceFactoryReferences.get(factoryPid);
		}
	}

	private String getPidForManagedServiceFactory(Object service) {
		synchronized (managedServiceFactoryReferences) {
			for (Entry<String, ManagedServiceFactory> entry : managedServiceFactories.entrySet()) {
				if (entry.getValue() == service)
					return entry.getKey();
			}
			return null;
		}
	}

	private void asynchDeleted(final ManagedServiceFactory service, final String pid) {
		queue.put(new Runnable() {
			public void run() {
				try {
					service.deleted(pid);
				} catch (Throwable t) {
					configurationAdminFactory.log(LogService.LOG_ERROR, t.getMessage(), t);
				}
			}
		});
	}

	private void asynchUpdated(final ManagedServiceFactory service, final String pid, final Dictionary<String, Object> properties) {
		queue.put(new Runnable() {
			public void run() {
				try {
					service.updated(pid, properties);
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