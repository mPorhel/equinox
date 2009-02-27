package org.eclipse.osgi.tests.securityadmin;

import java.io.IOException;
import org.eclipse.osgi.framework.adaptor.PermissionStorage;

public class TestPermissionStorage implements PermissionStorage {

	private String[] conditionPermissionInfo;

	public TestPermissionStorage(String[] conditionPermissionInfo) {
		this.conditionPermissionInfo = conditionPermissionInfo;
	}

	public String[] getConditionalPermissionInfos() throws IOException {
		return conditionPermissionInfo;
	}

	public String[] getLocations() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public String[] getPermissionData(String location) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public void saveConditionalPermissionInfos(String[] infos) throws IOException {
		conditionPermissionInfo = infos;

	}

	public void setPermissionData(String location, String[] data) throws IOException {
		// TODO Auto-generated method stub

	}

}
