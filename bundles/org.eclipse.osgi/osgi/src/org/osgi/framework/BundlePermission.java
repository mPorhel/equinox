/*
 * $Header: /cvshome/build/org.osgi.framework/src/org/osgi/framework/BundlePermission.java,v 1.6 2005/04/27 19:51:31 twatson Exp $
 *
 * Copyright (c) OSGi Alliance (2004, 2005). All Rights Reserved.
 * 
 * Implementation of certain elements of the OSGi Specification may be subject
 * to third party intellectual property rights, including without limitation,
 * patent rights (such a third party may or may not be a member of the OSGi
 * Alliance). The OSGi Alliance is not responsible and shall not be held
 * responsible in any manner for identifying or failing to identify any or all
 * such third party intellectual property rights.
 * 
 * This document and the information contained herein are provided on an "AS IS"
 * basis and THE OSGI ALLIANCE DISCLAIMS ALL WARRANTIES, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO ANY WARRANTY THAT THE USE OF THE INFORMATION
 * HEREIN WILL NOT INFRINGE ANY RIGHTS AND ANY IMPLIED WARRANTIES OF
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT WILL THE
 * OSGI ALLIANCE BE LIABLE FOR ANY LOSS OF PROFITS, LOSS OF BUSINESS, LOSS OF
 * USE OF DATA, INTERRUPTION OF BUSINESS, OR FOR DIRECT, INDIRECT, SPECIAL OR
 * EXEMPLARY, INCIDENTIAL, PUNITIVE OR CONSEQUENTIAL DAMAGES OF ANY KIND IN
 * CONNECTION WITH THIS DOCUMENT OR THE INFORMATION CONTAINED HEREIN, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH LOSS OR DAMAGE.
 * 
 * All Company, brand and product names may be trademarks that are the sole
 * property of their respective owners. All rights reserved.
 */

package org.osgi.framework;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.security.Permission;
import java.security.BasicPermission;
import java.security.PermissionCollection;

/**
 * A bundle's authority to require or provide a bundle or to receive or attach
 * fragments.
 * 
 * <p>
 * A bundle symbolic name defines a unique fully qualified name.
 * <p>
 * For example:
 * 
 * <pre>
 * <code>
 * org.osgi.example.bundle
 * </code>
 * </pre>
 * 
 * <p>
 * <code>BundlePermission</code> has four actions: <code>PROVIDE</code>,
 * <code>REQUIRE</code>,<code>HOST</code>, and <code>FRAGMENT</code>. The
 * <code>PROVIDE</code> action implies the <code>REQUIRE</code> action.
 * 
 * @since 1.3
 */

public final class BundlePermission extends BasicPermission {

	/**
	 * Comment for <code>serialVersionUID</code>
	 */
	private static final long	serialVersionUID	= 3257846601685873716L;

	/**
	 * The action string <code>provide</code>.
	 */
	public final static String	PROVIDE				= "provide";

	/**
	 * The action string <code>require</code>.
	 */
	public final static String	REQUIRE				= "require";

	/**
	 * The action string <code>host</code>.
	 */
	public final static String	HOST				= "host";

	/**
	 * The action string <code>fragment</code>.
	 */
	public final static String	FRAGMENT			= "fragment";

	private final static int	ACTION_PROVIDE		= 0x00000001;
	private final static int	ACTION_REQUIRE		= 0x00000002;
	private final static int	ACTION_HOST			= 0x00000004;
	private final static int	ACTION_FRAGMENT		= 0x00000008;
	private final static int	ACTION_ALL			= ACTION_PROVIDE
															| ACTION_REQUIRE
															| ACTION_HOST
															| ACTION_FRAGMENT;
	private final static int	ACTION_NONE			= 0;
	private final static int	ACTION_ERROR		= 0x80000000;

	/**
	 * The actions mask.
	 */
	private transient int		action_mask			= ACTION_NONE;

	/**
	 * The actions in canonical form.
	 * 
	 * @serial
	 */
	private String				actions				= null;

	/**
	 * Defines the authority to provide and/or require and or specify a host
	 * fragment symbolic name within the OSGi environment.
	 * <p>
	 * Bundle Permissions are granted over all possible versions of a bundle.
	 * 
	 * A bundle that needs to provide a bundle must have the appropriate
	 * <code>BundlePermission</code> for the symbolic name; a bundle that requires
	 * a bundle must have the appropriate <code>BundlePermssion</code> for that
	 * symbolic name; a bundle that specifies a fragment host must have the
	 * appropriate <code>BundlePermission</code> for that symbolic name.
	 * 
	 * @param symbolicName the bundle symbolic name.
	 * @param actions <code>PROVIDE</code>,<code>REQUIRE</code>,<code>HOST</code>,
	 *        <code>FRAGMENT</code> (canonical order).
	 */

	public BundlePermission(String symbolicName, String actions) {
		this(symbolicName, getMask(actions));
	}

	/**
	 * Bundle private constructor used by BundlePermissionCollection.
	 * 
	 * @param symbolicName the bundle symbolic name
	 * @param mask the action mask
	 */
	BundlePermission(String symbolicName, int mask) {
		super(symbolicName);
		init(mask);
	}

	/**
	 * Called by constructors and when deserialized.
	 * 
	 * @param mask
	 */
	private void init(int mask) {
		if ((mask == ACTION_NONE) || ((mask & ACTION_ALL) != mask)) {
			throw new IllegalArgumentException("invalid action string");
		}

		action_mask = mask;
	}

	/**
	 * Parse action string into action mask.
	 * 
	 * @param actions Action string.
	 * @return action mask.
	 */
	private static int getMask(String actions) {
		boolean seencomma = false;

		int mask = ACTION_NONE;

		if (actions == null) {
			return (mask);
		}

		char[] a = actions.toCharArray();

		int i = a.length - 1;
		if (i < 0)
			return (mask);

		while (i != -1) {
			char c;

			// skip whitespace
			while ((i != -1)
					&& ((c = a[i]) == ' ' || c == '\r' || c == '\n'
							|| c == '\f' || c == '\t'))
				i--;

			// check for the known strings
			int matchlen;

			if (i >= 6 && (a[i - 6] == 'p' || a[i - 6] == 'P')
					&& (a[i - 5] == 'r' || a[i - 5] == 'R')
					&& (a[i - 4] == 'o' || a[i - 4] == 'O')
					&& (a[i - 3] == 'v' || a[i - 3] == 'V')
					&& (a[i - 2] == 'i' || a[i - 2] == 'I')
					&& (a[i - 1] == 'd' || a[i - 1] == 'D')
					&& (a[i] == 'e' || a[i] == 'E')) {
				matchlen = 7;
				mask |= ACTION_PROVIDE | ACTION_REQUIRE;
			}
			else
				if (i >= 6 && (a[i - 6] == 'r' || a[i - 6] == 'R')
						&& (a[i - 5] == 'e' || a[i - 5] == 'E')
						&& (a[i - 4] == 'q' || a[i - 4] == 'Q')
						&& (a[i - 3] == 'u' || a[i - 3] == 'U')
						&& (a[i - 2] == 'i' || a[i - 2] == 'I')
						&& (a[i - 1] == 'r' || a[i - 1] == 'R')
						&& (a[i] == 'e' || a[i] == 'E')) {
					matchlen = 7;
					mask |= ACTION_REQUIRE;
				}
				else
					if (i >= 3 && (a[i - 3] == 'h' || a[i - 3] == 'H')
							&& (a[i - 2] == 'o' || a[i - 2] == 'O')
							&& (a[i - 1] == 's' || a[i - 1] == 'S')
							&& (a[i] == 't' || a[i] == 'T')) {
						matchlen = 4;
						mask |= ACTION_HOST;
					}
					else
						if (i >= 7 && (a[i - 7] == 'f' || a[i - 7] == 'F')
								&& (a[i - 6] == 'r' || a[i - 6] == 'R')
								&& (a[i - 5] == 'a' || a[i - 5] == 'A')
								&& (a[i - 4] == 'g' || a[i - 4] == 'G')
								&& (a[i - 3] == 'm' || a[i - 3] == 'M')
								&& (a[i - 2] == 'e' || a[i - 2] == 'E')
								&& (a[i - 1] == 'n' || a[i - 1] == 'N')
								&& (a[i] == 't' || a[i] == 'T')) {
							matchlen = 8;
							mask |= ACTION_FRAGMENT;
						}
						else {
							// parse error
							throw new IllegalArgumentException(
									"invalid permission: " + actions);
						}

			// make sure we didn't just match the tail of a word
			// like "ackbarfrequire". Also, skip to the comma.
			seencomma = false;
			while (i >= matchlen && !seencomma) {
				switch (a[i - matchlen]) {
					case ',' :
						seencomma = true;
					/* FALLTHROUGH */
					case ' ' :
					case '\r' :
					case '\n' :
					case '\f' :
					case '\t' :
						break;
					default :
						throw new IllegalArgumentException(
								"invalid permission: " + actions);
				}
				i--;
			}

			// point i at the location of the comma minus one (or -1).
			i -= matchlen;
		}

		if (seencomma) {
			throw new IllegalArgumentException("invalid permission: " + actions);
		}

		return (mask);
	}

	/**
	 * Determines if the specified permission is implied by this object.
	 * 
	 * <p>
	 * This method checks that the symbolic name of the target is implied by the
	 * symbolic name of this object. The list of <code>BundlePermission</code>
	 * actions must either match or allow for the list of the target object to
	 * imply the target <code>BundlePermission</code> action.
	 * <p>
	 * The permission to provide a bundle implies the permission to require the
	 * named symbolic name.
	 * 
	 * <pre>
	 * 
	 *  
	 *   
	 *    x.y.*,&quot;provide&quot; -&gt; x.y.z,&quot;provide&quot; is true
	 *    *,&quot;require&quot; -&gt; x.y, &quot;require&quot;      is true
	 *    *,&quot;provide&quot; -&gt; x.y, &quot;require&quot;      is true
	 *    x.y,&quot;provide&quot; -&gt; x.y.z, &quot;provide&quot;  is false
	 *    
	 *   
	 *  
	 * </pre>
	 * 
	 * @param p The target permission to interrogate.
	 * @return <code>true</code> if the specified <code>BundlePermission</code>
	 *         action is implied by this object; <code>false</code> otherwise.
	 */

	public boolean implies(Permission p) {
		if (p instanceof BundlePermission) {
			BundlePermission target = (BundlePermission) p;

			return (((action_mask & target.action_mask) == target.action_mask) && super
					.implies(p));
		}

		return (false);
	}

	/**
	 * Returns the canonical string representation of the
	 * <code>BundlePermission</code> actions.
	 * 
	 * <p>
	 * Always returns present <code>BundlePermission</code> actions in the
	 * following order: <code>PROVIDE</code>,<code>REQUIRE</code>,<code>HOST</code>,
	 * <code>FRAGMENT.
	 * @return Canonical string representation of the <code>BundlePermission</code> actions.
	 */

	public String getActions() {
		if (actions == null) {
			StringBuffer sb = new StringBuffer();
			boolean comma = false;

			if ((action_mask & ACTION_PROVIDE) == ACTION_PROVIDE) {
				sb.append(PROVIDE);
				comma = true;
			}

			if ((action_mask & ACTION_REQUIRE) == ACTION_REQUIRE) {
				if (comma)
					sb.append(',');
				sb.append(REQUIRE);
				comma = true;
			}

			if ((action_mask & ACTION_HOST) == ACTION_HOST) {
				if (comma)
					sb.append(',');
				sb.append(HOST);
			}

			if ((action_mask & ACTION_FRAGMENT) == ACTION_FRAGMENT) {
				if (comma)
					sb.append(',');
				sb.append(FRAGMENT);
			}

			actions = sb.toString();
		}

		return (actions);
	}

	/**
	 * Returns a new <code>PermissionCollection</code> object suitable for storing
	 * <code>BundlePermission</code> objects.
	 * 
	 * @return A new <code>PermissionCollection</code> object.
	 */
	public PermissionCollection newPermissionCollection() {
		return (new BundlePermissionCollection());
	}

	/**
	 * Determines the equality of two <code>BundlePermission</code> objects.
	 * 
	 * This method checks that specified bundle has the same bundle symbolic
	 * name and <code>BundlePermission</code> actions as this
	 * <code>BundlePermission</code> object.
	 * 
	 * @param obj The object to test for equality with this
	 *        <code>BundlePermission</code> object.
	 * @return <code>true</code> if <code>obj</code> is a <code>BundlePermission</code>,
	 *         and has the same bundle symbolic name and actions as this
	 *         <code>BundlePermission</code> object; <code>false</code> otherwise.
	 */
	public boolean equals(Object obj) {
		if (obj == this) {
			return (true);
		}

		if (!(obj instanceof BundlePermission)) {
			return (false);
		}

		BundlePermission p = (BundlePermission) obj;

		return ((action_mask == p.action_mask) && getName().equals(p.getName()));
	}

	/**
	 * Returns the hash code value for this object.
	 * 
	 * @return A hash code value for this object.
	 */

	public int hashCode() {
		return (getName().hashCode() ^ getActions().hashCode());
	}

	/**
	 * Returns the current action mask.
	 * <p>
	 * Used by the BundlePermissionCollection class.
	 * 
	 * @return Current action mask.
	 */
	int getMask() {
		return (action_mask);
	}

	/**
	 * WriteObject is called to save the state of the <code>BundlePermission</code>
	 * object to a stream. The actions are serialized, and the superclass takes
	 * care of the name.
	 */

	private synchronized void writeObject(java.io.ObjectOutputStream s)
			throws IOException {
		// Write out the actions. The superclass takes care of the name
		// call getActions to make sure actions field is initialized
		if (actions == null)
			getActions();
		s.defaultWriteObject();
	}

	/**
	 * readObject is called to restore the state of the BundlePermission from a
	 * stream.
	 */
	private synchronized void readObject(java.io.ObjectInputStream s)
			throws IOException, ClassNotFoundException {
		// Read in the action, then initialize the rest
		s.defaultReadObject();
		init(getMask(actions));
	}
}

/**
 * Stores a set of <code>BundlePermission</code> permissions.
 * 
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 */

final class BundlePermissionCollection extends PermissionCollection {

	/**
	 * Comment for <code>serialVersionUID</code>
	 */
	private static final long	serialVersionUID	= 3258407326846433079L;

	/**
	 * Table of permissions.
	 * 
	 * @serial
	 */
	private Hashtable			permissions;

	/**
	 * Boolean saying if "*" is in the collection.
	 * 
	 * @serial
	 */
	private boolean				all_allowed;

	/**
	 * Create an empty BundlePermissions object.
	 * 
	 */

	public BundlePermissionCollection() {
		permissions = new Hashtable();
		all_allowed = false;
	}

	/**
	 * Adds a permission to the <code>BundlePermission</code> objects. The key for
	 * the hash is the symbolic name.
	 * 
	 * @param permission The <code>BundlePermission</code> object to add.
	 * 
	 * @exception IllegalArgumentException If the permission is not a
	 *            <code>BundlePermission</code> instance.
	 * 
	 * @exception SecurityException If this <code>BundlePermissionCollection</code>
	 *            object has been marked read-only.
	 */

	public void add(Permission permission) {
		if (!(permission instanceof BundlePermission))
			throw new IllegalArgumentException("invalid permission: "
					+ permission);
		if (isReadOnly())
			throw new SecurityException("attempt to add a Permission to a "
					+ "readonly PermissionCollection");

		BundlePermission bp = (BundlePermission) permission;
		String name = bp.getName();

		BundlePermission existing = (BundlePermission) permissions.get(name);

		if (existing != null) {
			int oldMask = existing.getMask();
			int newMask = bp.getMask();
			if (oldMask != newMask) {
				permissions.put(name, new BundlePermission(name, oldMask
						| newMask));

			}
		}
		else {
			permissions.put(name, permission);
		}

		if (!all_allowed) {
			if (name.equals("*"))
				all_allowed = true;
		}
	}

	/**
	 * Determines if the specified permissions implies the permissions expressed
	 * in <code>permission</code>.
	 * 
	 * @param permission The Permission object to compare with this
	 *        <code>BundlePermission</code> object.
	 * 
	 * @return <code>true</code> if <code>permission</code> is a proper subset of a
	 *         permission in the set; <code>false</code> otherwise.
	 */

	public boolean implies(Permission permission) {
		if (!(permission instanceof BundlePermission))
			return (false);

		BundlePermission bp = (BundlePermission) permission;
		BundlePermission x;

		int desired = bp.getMask();
		int effective = 0;

		// short circuit if the "*" Permission was added
		if (all_allowed) {
			x = (BundlePermission) permissions.get("*");
			if (x != null) {
				effective |= x.getMask();
				if ((effective & desired) == desired)
					return (true);
			}
		}

		// strategy:
		// Check for full match first. Then work our way up the
		// name looking for matches on a.b.*

		String name = bp.getName();

		x = (BundlePermission) permissions.get(name);

		if (x != null) {
			// we have a direct hit!
			effective |= x.getMask();
			if ((effective & desired) == desired)
				return (true);
		}

		// work our way up the tree...
		int last, offset;

		offset = name.length() - 1;

		while ((last = name.lastIndexOf(".", offset)) != -1) {

			name = name.substring(0, last + 1) + "*";
			x = (BundlePermission) permissions.get(name);

			if (x != null) {
				effective |= x.getMask();
				if ((effective & desired) == desired)
					return (true);
			}
			offset = last - 1;
		}

		// we don't have to check for "*" as it was already checked
		// at the top (all_allowed), so we just return false
		return (false);
	}

	/**
	 * Returns an enumeration of all <code>BundlePermission</code> objects in the
	 * container.
	 * 
	 * @return Enumeration of all <code>BundlePermission</code> objects.
	 */

	public Enumeration elements() {
		return (permissions.elements());
	}
}
