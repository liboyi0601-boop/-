package ScheduleAgorithm;

import workflow.WTask;

public final class TaskCandidateView
{
	private final int candidateIndex;
	private final WTask task;
	private final String taskId;
	private final int workflowId;
	private final int earliestStartTime;
	private final int earliestFinishTime;
	private final int subDeadline;
	private final int priority;

	public TaskCandidateView(int candidateIndex, WTask task)
	{
		this.candidateIndex = candidateIndex;
		this.task = task;
		this.taskId = task.getTaskId();
		this.workflowId = task.getTaskWorkFlowId();
		this.earliestStartTime = task.getEarliestStartTime();
		this.earliestFinishTime = task.getEarliestFinishTime();
		this.subDeadline = task.getSubDeadLine();
		this.priority = task.getPriority();
	}

	public int getCandidateIndex()
	{
		return candidateIndex;
	}

	public WTask getTask()
	{
		return task;
	}

	public String getTaskId()
	{
		return taskId;
	}

	public int getWorkflowId()
	{
		return workflowId;
	}

	public int getEarliestStartTime()
	{
		return earliestStartTime;
	}

	public int getEarliestFinishTime()
	{
		return earliestFinishTime;
	}

	public int getSubDeadline()
	{
		return subDeadline;
	}

	public int getPriority()
	{
		return priority;
	}
}
