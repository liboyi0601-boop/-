package ScheduleAgorithm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import workflow.BenchmarkFamily;

public enum RegressionSuite
{
	GOLDEN("golden", "workloads/golden-100w.dat", 100, 0L,
			new ExperimentMetrics(100, 6484, 6484, 442, 937.6280, 0.8951, 26.4496, 0.0370, 0.2200, 0.2072, 0L),
			null, null, null),
	AUX_SMALL("aux-small", "workloads/aux-small-20w-seed11.dat", 20, 11L, null,
			null, null, null),
	AUX_MEDIUM("aux-medium", "workloads/aux-medium-50w-seed23.dat", 50, 23L, null,
			null, null, null),
	BENCH_CYBERSHAKE_SMALL("bench-cybershake-small",
			"workloads/benchmarks/bench-cybershake-small-20w.dat", 20, 0L, null,
			Arrays.asList(BenchmarkFamily.CYBERSHAKE),
			BenchmarkFamily.CYBERSHAKE.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/cybershake-template-set.dat")),
	BENCH_CYBERSHAKE_MEDIUM("bench-cybershake-medium",
			"workloads/benchmarks/bench-cybershake-medium-50w.dat", 50, 0L, null,
			Arrays.asList(BenchmarkFamily.CYBERSHAKE),
			BenchmarkFamily.CYBERSHAKE.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/cybershake-template-set.dat")),
	BENCH_MONTAGE_SMALL("bench-montage-small",
			"workloads/benchmarks/bench-montage-small-20w.dat", 20, 0L, null,
			Arrays.asList(BenchmarkFamily.MONTAGE),
			BenchmarkFamily.MONTAGE.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/montage-template-set.dat")),
	BENCH_MONTAGE_MEDIUM("bench-montage-medium",
			"workloads/benchmarks/bench-montage-medium-50w.dat", 50, 0L, null,
			Arrays.asList(BenchmarkFamily.MONTAGE),
			BenchmarkFamily.MONTAGE.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/montage-template-set.dat")),
	BENCH_INSPIRAL_SMALL("bench-inspiral-small",
			"workloads/benchmarks/bench-inspiral-small-20w.dat", 20, 0L, null,
			Arrays.asList(BenchmarkFamily.INSPIRAL),
			BenchmarkFamily.INSPIRAL.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/inspiral-template-set.dat")),
	BENCH_INSPIRAL_MEDIUM("bench-inspiral-medium",
			"workloads/benchmarks/bench-inspiral-medium-50w.dat", 50, 0L, null,
			Arrays.asList(BenchmarkFamily.INSPIRAL),
			BenchmarkFamily.INSPIRAL.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/inspiral-template-set.dat")),
	BENCH_SIPHT_SMALL("bench-sipht-small",
			"workloads/benchmarks/bench-sipht-small-20w.dat", 20, 0L, null,
			Arrays.asList(BenchmarkFamily.SIPHT),
			BenchmarkFamily.SIPHT.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/sipht-template-set.dat")),
	BENCH_SIPHT_MEDIUM("bench-sipht-medium",
			"workloads/benchmarks/bench-sipht-medium-50w.dat", 50, 0L, null,
			Arrays.asList(BenchmarkFamily.SIPHT),
			BenchmarkFamily.SIPHT.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/sipht-template-set.dat")),
	BENCH_MIXED_SMALL("bench-mixed-small", "workloads/benchmarks/bench-mixed-small-20w.dat", 20, 0L, null,
			Arrays.asList(BenchmarkFamily.CYBERSHAKE, BenchmarkFamily.MONTAGE, BenchmarkFamily.INSPIRAL,
					BenchmarkFamily.SIPHT),
			BenchmarkFamily.CYBERSHAKE.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/bench-mixed-template-set.dat")),
	BENCH_MIXED_MEDIUM("bench-mixed-medium", "workloads/benchmarks/bench-mixed-medium-50w.dat", 50, 0L, null,
			Arrays.asList(BenchmarkFamily.CYBERSHAKE, BenchmarkFamily.MONTAGE, BenchmarkFamily.INSPIRAL,
					BenchmarkFamily.SIPHT),
			BenchmarkFamily.CYBERSHAKE.getSourceXmlDirectory(),
			Paths.get("workloads/benchmarks/templates/bench-mixed-template-set.dat"));

	private static final DecimalFormat FOUR_DIGITS = new DecimalFormat("0.0000");

	private final String suiteName;
	private final String workloadPath;
	private final int expectedWorkflowCount;
	private final long seed;
	private final ExperimentMetrics expectedMetrics;
	private final List<BenchmarkFamily> benchmarkFamilies;
	private final Path sourceXmlDirectory;
	private final Path templatePath;

	private RegressionSuite(String suiteName, String workloadPath, int expectedWorkflowCount, long seed,
			ExperimentMetrics expectedMetrics, List<BenchmarkFamily> benchmarkFamilies, Path sourceXmlDirectory,
			Path templatePath)
	{
		this.suiteName = suiteName;
		this.workloadPath = workloadPath;
		this.expectedWorkflowCount = expectedWorkflowCount;
		this.seed = seed;
		this.expectedMetrics = expectedMetrics;
		this.benchmarkFamilies = benchmarkFamilies == null
				? Collections.<BenchmarkFamily>emptyList()
				: Collections.unmodifiableList(new ArrayList<BenchmarkFamily>(benchmarkFamilies));
		this.sourceXmlDirectory = sourceXmlDirectory;
		this.templatePath = templatePath;
	}

	public String getSuiteName()
	{
		return suiteName;
	}

	public Path getWorkloadPath()
	{
		return Paths.get(workloadPath);
	}

	public int getExpectedWorkflowCount()
	{
		return expectedWorkflowCount;
	}

	public long getSeed()
	{
		return seed;
	}

	public ExperimentMetrics getExpectedMetrics()
	{
		return expectedMetrics;
	}

	public boolean isGolden()
	{
		return this == GOLDEN;
	}

	public boolean isBenchmark()
	{
		return !benchmarkFamilies.isEmpty();
	}

	public Path getTemplatePath()
	{
		return templatePath;
	}

	public Path getSourceXmlDirectory()
	{
		return sourceXmlDirectory;
	}

	public List<String> getSourceXmlFiles()
	{
		List<String> files = new ArrayList<String>();
		for(BenchmarkFamily benchmarkFamily: benchmarkFamilies)
		{
			for(String sourceXmlFile: benchmarkFamily.getSourceXmlFiles())
			{
				if(!files.contains(sourceXmlFile))
				{
					files.add(sourceXmlFile);
				}
			}
		}
		return Collections.unmodifiableList(files);
	}

	public List<BenchmarkFamily> getBenchmarkFamilies()
	{
		return benchmarkFamilies;
	}

	public static List<RegressionSuite> defaultSuites()
	{
		return Arrays.asList(GOLDEN, AUX_SMALL, AUX_MEDIUM);
	}

	public static RegressionSuite fromName(String suiteName)
	{
		for(RegressionSuite suite: values())
		{
			if(suite.suiteName.equals(suiteName))
			{
				return suite;
			}
		}
		throw new IllegalArgumentException("Unknown regression suite: " + suiteName);
	}

	public Map<String, Object> buildDatasetMetadata(Integer workflowCount, Integer taskCount)
	{
		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("suiteName", suiteName);
		metadata.put("workloadPath", workloadPath);
		metadata.put("generatedWorkflowFile", workloadPath);
		if(templatePath != null)
		{
			metadata.put("templatePath", templatePath.toString());
		}
		if(sourceXmlDirectory != null)
		{
			metadata.put("sourceDirectory", sourceXmlDirectory.toString());
		}
		if(!getSourceXmlFiles().isEmpty())
		{
			metadata.put("sourceXmlFiles", getSourceXmlFiles());
		}
		if(benchmarkFamilies.size() == 1)
		{
			metadata.put("benchmarkFamily", benchmarkFamilies.get(0).getFamilyName());
		}
		else if(!benchmarkFamilies.isEmpty())
		{
			metadata.put("benchmarkFamilies", benchmarkFamilyNames());
		}
		if(workflowCount != null)
		{
			metadata.put("workflowCount", workflowCount);
		}
		else
		{
			metadata.put("workflowCount", Integer.valueOf(expectedWorkflowCount));
		}
		if(taskCount != null)
		{
			metadata.put("taskCount", taskCount);
		}
		return metadata;
	}

	public void assertMatches(ExperimentMetrics metrics)
	{
		if(expectedMetrics == null)
		{
			return;
		}

		assertEquals("workflowCount", expectedMetrics.getWorkflowCount(), metrics.getWorkflowCount());
		assertEquals("totalTaskCount", expectedMetrics.getTotalTaskCount(), metrics.getTotalTaskCount());
		assertEquals("finishedTaskCount", expectedMetrics.getFinishedTaskCount(), metrics.getFinishedTaskCount());
		assertEquals("usedVmCount", expectedMetrics.getUsedVmCount(), metrics.getUsedVmCount());
		assertEquals("totalCost", expectedMetrics.getTotalCost(), metrics.getTotalCost());
		assertEquals("resourceUtilization", expectedMetrics.getResourceUtilization(), metrics.getResourceUtilization());
		assertEquals("taskDeviation", expectedMetrics.getTaskDeviation(), metrics.getTaskDeviation());
		assertEquals("workflowDeviation", expectedMetrics.getWorkflowDeviation(), metrics.getWorkflowDeviation());
		assertEquals("violationCount", expectedMetrics.getViolationCount(), metrics.getViolationCount());
		assertEquals("violationTime", expectedMetrics.getViolationTime(), metrics.getViolationTime());
	}

	private List<String> benchmarkFamilyNames()
	{
		List<String> familyNames = new ArrayList<String>(benchmarkFamilies.size());
		for(BenchmarkFamily benchmarkFamily: benchmarkFamilies)
		{
			familyNames.add(benchmarkFamily.getFamilyName());
		}
		return Collections.unmodifiableList(familyNames);
	}

	private void assertEquals(String label, int expected, int actual)
	{
		if(expected != actual)
		{
			throw new IllegalStateException(label + " mismatch: expected " + expected + ", actual " + actual);
		}
	}

	private void assertEquals(String label, double expected, double actual)
	{
		String expectedString = FOUR_DIGITS.format(expected);
		String actualString = FOUR_DIGITS.format(actual);
		if(!expectedString.equals(actualString))
		{
			throw new IllegalStateException(label + " mismatch: expected " + expectedString
					+ ", actual " + actualString);
		}
	}
}
