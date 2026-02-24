package com.ingestion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to compute SHA-256 hashes for file deduplication.
 */
public class Sha256Hasher {

	private static final String ALGORITHM = "SHA-256";
	private static final int BUFFER_SIZE = 8192;

	/**
	 * Compute SHA-256 hash of a file.
	 *
	 * @param filePath the path to the file
	 * @return the SHA-256 hash as a 64-character hex string
	 * @throws IOException if an error occurs while reading the file
	 */
	public static String computeHash(String filePath) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead;

			try (FileInputStream fis = new FileInputStream(filePath)) {
				while ((bytesRead = fis.read(buffer)) != -1) {
					digest.update(buffer, 0, bytesRead);
				}
			}

			return bytesToHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}

	/**
	 * Convert byte array to hex string.
	 *
	 * @param bytes the byte array
	 * @return the hex string representation
	 */
	private static String bytesToHex(byte[] bytes) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : bytes) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
}
