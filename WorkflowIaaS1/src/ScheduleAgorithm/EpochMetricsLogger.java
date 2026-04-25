package ScheduleAgorithm;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class EpochMetricsLogger implements Closeable
{
	private final BufferedWriter writer;

	public EpochMetricsLogger(Path path) throws IOException
	{
		this.writer = JsonSupport.newJsonlWriter(path);
	}

	public void append(Map<String, Object> metrics) throws IOException
	{
		JsonSupport.appendJsonLine(writer, metrics);
	}

	public void close() throws IOException
	{
		writer.close();
	}
}
