package workflow;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum BenchmarkFamily
{
	CYBERSHAKE("cybershake", "CyberShake",
			Arrays.asList("CyberShake_30.xml", "CyberShake_50.xml", "CyberShake_100.xml", "CyberShake_1000.xml")),
	MONTAGE("montage", "Montage",
			Arrays.asList("Montage_25.xml", "Montage_50.xml", "Montage_100.xml", "Montage_1000.xml")),
	INSPIRAL("inspiral", "Inspiral",
			Arrays.asList("Inspiral_30.xml", "Inspiral_50.xml", "Inspiral_100.xml", "Inspiral_1000.xml")),
	SIPHT("sipht",
			"Sipht",
			Arrays.asList("Sipht_30.xml", "Sipht_60.xml", "Sipht_100.xml", "Sipht_1000.xml"));

	private static final Path SOURCE_XML_DIRECTORY = Paths.get("XML Example");

	private final String familyName;
	private final String workflowNamePrefix;
	private final List<String> sourceXmlFiles;

	private BenchmarkFamily(String familyName, String workflowNamePrefix, List<String> sourceXmlFiles)
	{
		this.familyName = familyName;
		this.workflowNamePrefix = workflowNamePrefix;
		this.sourceXmlFiles = Collections.unmodifiableList(new ArrayList<String>(sourceXmlFiles));
	}

	public String getFamilyName()
	{
		return familyName;
	}

	public String getWorkflowNamePrefix()
	{
		return workflowNamePrefix;
	}

	public Path getSourceXmlDirectory()
	{
		return SOURCE_XML_DIRECTORY;
	}

	public List<String> getSourceXmlFiles()
	{
		return sourceXmlFiles;
	}

	public List<Path> getSourceXmlPaths()
	{
		List<Path> paths = new ArrayList<Path>(sourceXmlFiles.size());
		for(String sourceXmlFile: sourceXmlFiles)
		{
			paths.add(SOURCE_XML_DIRECTORY.resolve(sourceXmlFile));
		}
		return Collections.unmodifiableList(paths);
	}

	public static BenchmarkFamily fromName(String familyName)
	{
		for(BenchmarkFamily family: values())
		{
			if(family.familyName.equalsIgnoreCase(familyName))
			{
				return family;
			}
		}
		throw new IllegalArgumentException("Unknown benchmark family: " + familyName);
	}

	public static BenchmarkFamily fromWorkflowName(String workflowName)
	{
		if(workflowName == null)
		{
			return null;
		}

		for(BenchmarkFamily family: values())
		{
			if(workflowName.startsWith(family.workflowNamePrefix + "_"))
			{
				return family;
			}
		}
		return null;
	}
}
