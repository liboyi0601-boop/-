package ScheduleAgorithm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public enum RegressionSuite
{
	GOLDEN("golden", "workloads/golden-100w.dat", 100, 0L,
			new ExperimentMetrics(100, 6484, 6484, 442, 937.6280, 0.8951, 26.4496, 0.0370, 0.2200, 0.2072, 0L)),
	AUX_SMALL("aux-small", "workloads/aux-small-20w-seed11.dat", 20, 11L, null),
	AUX_MEDIUM("aux-medium", "workloads/aux-medium-50w-seed23.dat", 50, 23L, null);

	private static final DecimalFormat FOUR_DIGITS = new DecimalFormat("0.0000");

	private final String suiteName;
	private final String workloadPath;
	private final int expectedWorkflowCount;
	private final long seed;
	private final ExperimentMetrics expectedMetrics;

	private RegressionSuite(String suiteName, String workloadPath, int expectedWorkflowCount, long seed,
			ExperimentMetrics expectedMetrics)
	{
		this.suiteName = suiteName;
		this.workloadPath = workloadPath;
		this.expectedWorkflowCount = expectedWorkflowCount;
		this.seed = seed;
		this.expectedMetrics = expectedMetrics;
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

	public static List<RegressionSuite> defaultSuites()
	{
		return Arrays.asList(values());
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
