package com.openclassrooms.tourguide.helper;

public class InternalTestHelper {

	private InternalTestHelper() {
		throw new IllegalStateException("Utility class");
	}

	// Set this default up to 100,000 for testing
	private static int internalUserNumber = 100;
	
	public static void setInternalUserNumber(int internalUserNumber) {
		InternalTestHelper.internalUserNumber = internalUserNumber;
	}
	
	public static int getInternalUserNumber() {
		return internalUserNumber;
	}
}
