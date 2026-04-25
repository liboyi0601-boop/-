package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import share.PerformanceValue;
import workflow.WTask;
import workflow.Workflow;

public final class ExperimentMetrics
{
	private final int workflowCount;
	private final int totalTaskCount;
	private final int finishedTaskCount;
	private final int usedVmCount;
	private final double totalCost;
	private final double resourceUtilization;
	private final double taskDeviation;
	private final double workflowDeviation;
	private final double violationCount;
	private final double violationTime;
	private final long scheduleTimeMs;

	public ExperimentMetrics(int workflowCount, int totalTaskCount, int finishedTaskCount, int usedVmCount,
			double totalCost, double resourceUtilization, double taskDeviation, double workflowDeviation,
			double violationCount, double violationTime, long scheduleTimeMs)
	{
		this.workflowCount = workflowCount;
		this.totalTaskCount = totalTaskCount;
		this.finishedTaskCount = finishedTaskCount;
		this.usedVmCount = usedVmCount;
		this.totalCost = totalCost;
		this.resourceUtilization = resourceUtilization;
		this.taskDeviation = taskDeviation;
		this.workflowDeviation = workflowDeviation;
		this.violationCount = violationCount;
		this.violationTime = violationTime;
		this.scheduleTimeMs = scheduleTimeMs;
	}

	public static ExperimentMetrics fromWorkflows(List<Workflow> workflows)
	{
		int totalTaskCount = 0;
		int finishedTaskCount = 0;

		for(Workflow workflow: workflows)
		{
			totalTaskCount += workflow.getTaskList().size();
			for(WTask task: workflow.getTaskList())
			{
				if(task.getFinishFlag())
				{
					finishedTaskCount++;
				}
			}
		}

		return new ExperimentMetrics(
				workflows.size(),
				totalTaskCount,
				finishedTaskCount,
				PerformanceValue.totalVmCount,
				PerformanceValue.TotalCost,
				PerformanceValue.ResourceUtilization,
				PerformanceValue.taskDeviation,
				PerformanceValue.workflowDeviation,
				PerformanceValue.ViolationCount,
				PerformanceValue.ViolationTime,
				PerformanceValue.ScheduleTime);
	}

	public int getWorkflowCount()
	{
		return workflowCount;
	}

	public int getTotalTaskCount()
	{
		return totalTaskCount;
	}

	public int getFinishedTaskCount()
	{
		return finishedTaskCount;
	}

	public int getUsedVmCount()
	{
		return usedVmCount;
	}

	public double getTotalCost()
	{
		return totalCost;
	}

	public double getResourceUtilization()
	{
		return resourceUtilization;
	}

	public double getTaskDeviation()
	{
		return taskDeviation;
	}

	public double getWorkflowDeviation()
	{
		return workflowDeviation;
	}

	public double getViolationCount()
	{
		return violationCount;
	}

	public double getViolationTime()
	{
		return violationTime;
	}

	public long getScheduleTimeMs()
	{
		return scheduleTimeMs;
	}

	public Map<String, Object> toMap()
	{
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("workflowCount", workflowCount);
		map.put("totalTaskCount", totalTaskCount);
		map.put("finishedTaskCount", finishedTaskCount);
		map.put("usedVmCount", usedVmCount);
		map.put("totalCost", totalCost);
		map.put("resourceUtilization", resourceUtilization);
		map.put("taskDeviation", taskDeviation);
		map.put("workflowDeviation", workflowDeviation);
		map.put("violationCount", violationCount);
		map.put("violationTime", violationTime);
		map.put("scheduleTimeMs", scheduleTimeMs);
		return map;
	}
}
