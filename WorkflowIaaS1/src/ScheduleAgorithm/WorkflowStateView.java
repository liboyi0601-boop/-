package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkflowStateView
{
	private final int workflowId;
	private final String workflowName;
	private final int arrivalTime;
	private final int makespan;
	private final int deadline;
	private final boolean startedFlag;
	private final int finishTime;
	private final boolean successfulOrNot;
	private final int taskCount;
	private final int allocatedTaskCount;
	private final int finishedTaskCount;
	private final double normalizedSlack;
	private final double violationRiskScore;
	private final double readyTaskDensity;
	private final int remainingCriticalPathLength;
	private final List<String> taskIds;

	public WorkflowStateView(int workflowId, String workflowName, int arrivalTime, int makespan, int deadline,
			boolean startedFlag, int finishTime, boolean successfulOrNot, int taskCount, int allocatedTaskCount,
			int finishedTaskCount, double normalizedSlack, double violationRiskScore, double readyTaskDensity,
			int remainingCriticalPathLength, List<String> taskIds)
	{
		this.workflowId = workflowId;
		this.workflowName = workflowName;
		this.arrivalTime = arrivalTime;
		this.makespan = makespan;
		this.deadline = deadline;
		this.startedFlag = startedFlag;
		this.finishTime = finishTime;
		this.successfulOrNot = successfulOrNot;
		this.taskCount = taskCount;
		this.allocatedTaskCount = allocatedTaskCount;
		this.finishedTaskCount = finishedTaskCount;
		this.normalizedSlack = normalizedSlack;
		this.violationRiskScore = violationRiskScore;
		this.readyTaskDensity = readyTaskDensity;
		this.remainingCriticalPathLength = remainingCriticalPathLength;
		this.taskIds = Collections.unmodifiableList(new ArrayList<String>(taskIds));
	}

	public int getWorkflowId()
	{
		return workflowId;
	}

	public String getWorkflowName()
	{
		return workflowName;
	}

	public int getArrivalTime()
	{
		return arrivalTime;
	}

	public int getMakespan()
	{
		return makespan;
	}

	public int getDeadline()
	{
		return deadline;
	}

	public boolean getStartedFlag()
	{
		return startedFlag;
	}

	public int getFinishTime()
	{
		return finishTime;
	}

	public boolean getSuccessfulOrNot()
	{
		return successfulOrNot;
	}

	public int getTaskCount()
	{
		return taskCount;
	}

	public int getAllocatedTaskCount()
	{
		return allocatedTaskCount;
	}

	public int getFinishedTaskCount()
	{
		return finishedTaskCount;
	}

	public double getNormalizedSlack()
	{
		return normalizedSlack;
	}

	public double getViolationRiskScore()
	{
		return violationRiskScore;
	}

	public double getReadyTaskDensity()
	{
		return readyTaskDensity;
	}

	public int getRemainingCriticalPathLength()
	{
		return remainingCriticalPathLength;
	}

	public List<String> getTaskIds()
	{
		return taskIds;
	}
}
