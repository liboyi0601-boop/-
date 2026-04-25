package ScheduleAgorithm;

public final class ReplayExampleOrigin
{
	private final String suiteName;
	private final String benchmarkFamily;
	private final String workflowName;

	public ReplayExampleOrigin(String suiteName, String benchmarkFamily, String workflowName)
	{
		this.suiteName = suiteName;
		this.benchmarkFamily = benchmarkFamily;
		this.workflowName = workflowName;
	}

	public String getSuiteName()
	{
		return suiteName;
	}

	public String getBenchmarkFamily()
	{
		return benchmarkFamily;
	}

	public String getWorkflowName()
	{
		return workflowName;
	}
}
