package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import workflow.WTask;
import workflow.Workflow;

public final class DeadlineViolationMetrics
{
	public static final String MAP_KEY = "deadlineViolationMetrics";
	public static final double DEFAULT_EPSILON = 1e-9;
	public static final String FINISH_TIME_SOURCE = "max-task-realFinishTime";
	public static final String RAW_VIOLATION_TIME_SEMANTICS = "legacy/raw signed deadline slack ratio";

	private final double violationTimeRaw;
	private final double positiveViolationTime;
	private final double positiveViolationTimeRatio;
	private final double meanPositiveViolationTimeRatio;
	private final double signedDeadlineSlackRatio;
	private final double earlyFinishSlackRatio;
	private final int violatedWorkflowCount;
	private final double violationCountRatioCorrected;

	private DeadlineViolationMetrics(double violationTimeRaw, double positiveViolationTime,
			double positiveViolationTimeRatio, double meanPositiveViolationTimeRatio,
			double signedDeadlineSlackRatio, double earlyFinishSlackRatio,
			int violatedWorkflowCount, double violationCountRatioCorrected)
	{
		this.violationTimeRaw = finite(violationTimeRaw);
		this.positiveViolationTime = nonNegative(positiveViolationTime);
		this.positiveViolationTimeRatio = nonNegative(positiveViolationTimeRatio);
		this.meanPositiveViolationTimeRatio = nonNegative(meanPositiveViolationTimeRatio);
		this.signedDeadlineSlackRatio = finite(signedDeadlineSlackRatio);
		this.earlyFinishSlackRatio = nonNegative(earlyFinishSlackRatio);
		this.violatedWorkflowCount = Math.max(0, violatedWorkflowCount);
		this.violationCountRatioCorrected = nonNegative(violationCountRatioCorrected);
	}

	public static DeadlineViolationMetrics fromWorkflows(List<Workflow> workflows, ExperimentMetrics legacyMetrics)
	{
		double rawViolationTime = legacyMetrics == null ? 0.0 : legacyMetrics.getViolationTime();
		return fromWorkflows(workflows, rawViolationTime);
	}

	public static DeadlineViolationMetrics fromWorkflows(List<Workflow> workflows, double rawViolationTime)
	{
		if(workflows == null || workflows.isEmpty())
		{
			return new DeadlineViolationMetrics(rawViolationTime, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0);
		}

		double totalPositiveViolationTime = 0.0;
		double totalWindow = 0.0;
		double positiveRatioSum = 0.0;
		double signedRatioSum = 0.0;
		double earlySlackRatioSum = 0.0;
		int violatedWorkflowCount = 0;

		for(Workflow workflow: workflows)
		{
			double finishTime = resolveWorkflowFinishTime(workflow);
			double deadline = workflow.getDeadline();
			double arrivalTime = workflow.getArrivalTime();
			double window = Math.max(DEFAULT_EPSILON, deadline - arrivalTime);
			double signedDelta = finishTime - deadline;
			double positiveViolation = Math.max(0.0, signedDelta);
			double earlySlack = Math.max(0.0, -signedDelta);

			if(positiveViolation > 0.0)
			{
				violatedWorkflowCount++;
			}
			totalPositiveViolationTime += positiveViolation;
			totalWindow += window;
			positiveRatioSum += positiveViolation / window;
			signedRatioSum += signedDelta / window;
			earlySlackRatioSum += earlySlack / window;
		}

		double workflowCount = workflows.size();
		return new DeadlineViolationMetrics(
				rawViolationTime,
				totalPositiveViolationTime,
				totalPositiveViolationTime / Math.max(DEFAULT_EPSILON, totalWindow),
				positiveRatioSum / workflowCount,
				signedRatioSum / workflowCount,
				earlySlackRatioSum / workflowCount,
				violatedWorkflowCount,
				violatedWorkflowCount / workflowCount);
	}

	public double getViolationTimeRaw()
	{
		return violationTimeRaw;
	}

	public double getPositiveViolationTime()
	{
		return positiveViolationTime;
	}

	public double getPositiveViolationTimeRatio()
	{
		return positiveViolationTimeRatio;
	}

	public double getMeanPositiveViolationTimeRatio()
	{
		return meanPositiveViolationTimeRatio;
	}

	public double getSignedDeadlineSlackRatio()
	{
		return signedDeadlineSlackRatio;
	}

	public double getEarlyFinishSlackRatio()
	{
		return earlyFinishSlackRatio;
	}

	public int getViolatedWorkflowCount()
	{
		return violatedWorkflowCount;
	}

	public double getViolationCountRatioCorrected()
	{
		return violationCountRatioCorrected;
	}

	public Map<String, Object> toMap()
	{
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("violationTimeRaw", violationTimeRaw);
		map.put("positiveViolationTime", positiveViolationTime);
		map.put("positiveViolationTimeRatio", positiveViolationTimeRatio);
		map.put("meanPositiveViolationTimeRatio", meanPositiveViolationTimeRatio);
		map.put("signedDeadlineSlackRatio", signedDeadlineSlackRatio);
		map.put("earlyFinishSlackRatio", earlyFinishSlackRatio);
		map.put("violatedWorkflowCount", Integer.valueOf(violatedWorkflowCount));
		map.put("violationCountRatioCorrected", violationCountRatioCorrected);
		return map;
	}

	public static Map<String, Object> buildMetricHygieneMetadata()
	{
		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("enabled", Boolean.TRUE);
		metadata.put("legacyViolationTimePreserved", Boolean.TRUE);
		metadata.put("correctedPositiveDeadlineMetricsEnabled", Boolean.TRUE);
		metadata.put("epsilon", Double.valueOf(DEFAULT_EPSILON));
		metadata.put("finishTimeSource", FINISH_TIME_SOURCE);
		metadata.put("violationTimeRawSemantics", RAW_VIOLATION_TIME_SEMANTICS);
		return metadata;
	}

	private static double resolveWorkflowFinishTime(Workflow workflow)
	{
		int finishTime = Integer.MIN_VALUE;
		for(WTask task: workflow.getTaskList())
		{
			if(task.getRealFinishTime() >= 0 && task.getRealFinishTime() > finishTime)
			{
				finishTime = task.getRealFinishTime();
			}
		}
		if(finishTime != Integer.MIN_VALUE)
		{
			return finishTime;
		}
		if(workflow.getFinishTime() >= 0)
		{
			return workflow.getFinishTime();
		}
		return workflow.getArrivalTime();
	}

	private static double nonNegative(double value)
	{
		return Math.max(0.0, finite(value));
	}

	private static double finite(double value)
	{
		if(Double.isNaN(value) || Double.isInfinite(value))
		{
			return 0.0;
		}
		return value;
	}
}
