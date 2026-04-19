package ScheduleAgorithm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class WorkloadFingerprint
{
	private WorkloadFingerprint()
	{
	}

	public static String fromFile(Path path) throws IOException
	{
		return sha256(Files.readAllBytes(path));
	}

	public static String sha256(byte[] data)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(data);
			StringBuilder builder = new StringBuilder();
			for(byte hashByte: hash)
			{
				builder.append(String.format("%02x", hashByte));
			}
			return builder.toString();
		}
		catch(NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
