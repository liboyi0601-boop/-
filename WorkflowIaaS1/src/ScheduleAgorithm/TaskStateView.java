package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskStateView
{
	private final String taskId;
	private final int workflowId;
	private final int workflowArrival;
	private final int workflowDeadline;
	private final int baseExecutionTime;
	private final int baseStartTime;
	private final int baseFinishTime;
	private final int realBaseExecutionTime;
	private final int realExecutionTime;
	private final int executionTimeWithConfidency;
	private final int realStartTime;
	private final int startTimeWithConfidency;
	private final int earliestStartTime;
	private final int leastStartTime;
	private final int realFinishTime;
	private final int finishTimeWithConfidency;
	private final int earliestFinishTime;
	private final int leastFinishTime;
	private final int subDeadline;
	private final int subSpan;
	private final int upwardRank;
	private final int downwardRank;
	private final int criticalPathSlack;
	private final int remainingDescendantWorkload;
	private final int dataTransferPressure;
	private final int readyDuration;
	private final boolean pathLastFlag;
	private final boolean pathFirstFlag;
	private final String pathFirstTaskId;
	private final String pathLastTaskId;
	private final boolean allocatedFlag;
	private final int allocatedVmId;
	private final boolean finishFlag;
	private final boolean newStartVm;
	private final int priority;
	private final int pcpNum;
	private final boolean inGlobalTaskPool;
	private final List<String> parentTaskIds;
	private final List<Integer> parentDataSizes;
	private final List<String> successorTaskIds;
	private final List<Integer> successorDataSizes;

	public TaskStateView(String taskId, int workflowId, int workflowArrival, int workflowDeadline, int baseExecutionTime,
			int baseStartTime, int baseFinishTime, int realBaseExecutionTime, int realExecutionTime,
			int executionTimeWithConfidency, int realStartTime, int startTimeWithConfidency, int earliestStartTime,
			int leastStartTime, int realFinishTime, int finishTimeWithConfidency, int earliestFinishTime,
			int leastFinishTime, int subDeadline, int subSpan, int upwardRank, int downwardRank,
			int criticalPathSlack, int remainingDescendantWorkload, int dataTransferPressure, int readyDuration,
			boolean pathLastFlag, boolean pathFirstFlag, String pathFirstTaskId, String pathLastTaskId,
			boolean allocatedFlag, int allocatedVmId, boolean finishFlag, boolean newStartVm, int priority,
			int pcpNum, boolean inGlobalTaskPool, List<String> parentTaskIds, List<Integer> parentDataSizes,
			List<String> successorTaskIds, List<Integer> successorDataSizes)
	{
		this.taskId = taskId;
		this.workflowId = workflowId;
		this.workflowArrival = workflowArrival;
		this.workflowDeadline = workflowDeadline;
		this.baseExecutionTime = baseExecutionTime;
		this.baseStartTime = baseStartTime;
		this.baseFinishTime = baseFinishTime;
		this.realBaseExecutionTime = realBaseExecutionTime;
		this.realExecutionTime = realExecutionTime;
		this.executionTimeWithConfidency = executionTimeWithConfidency;
		this.realStartTime = realStartTime;
		this.startTimeWithConfidency = startTimeWithConfidency;
		this.earliestStartTime = earliestStartTime;
		this.leastStartTime = leastStartTime;
		this.realFinishTime = realFinishTime;
		this.finishTimeWithConfidency = finishTimeWithConfidency;
		this.earliestFinishTime = earliestFinishTime;
		this.leastFinishTime = leastFinishTime;
		this.subDeadline = subDeadline;
		this.subSpan = subSpan;
		this.upwardRank = upwardRank;
		this.downwardRank = downwardRank;
		this.criticalPathSlack = criticalPathSlack;
		this.remainingDescendantWorkload = remainingDescendantWorkload;
		this.dataTransferPressure = dataTransferPressure;
		this.readyDuration = readyDuration;
		this.pathLastFlag = pathLastFlag;
		this.pathFirstFlag = pathFirstFlag;
		this.pathFirstTaskId = pathFirstTaskId;
		this.pathLastTaskId = pathLastTaskId;
		this.allocatedFlag = allocatedFlag;
		this.allocatedVmId = allocatedVmId;
		this.finishFlag = finishFlag;
		this.newStartVm = newStartVm;
		this.priority = priority;
		this.pcpNum = pcpNum;
		this.inGlobalTaskPool = inGlobalTaskPool;
		this.parentTaskIds = Collections.unmodifiableList(new ArrayList<String>(parentTaskIds));
		this.parentDataSizes = Collections.unmodifiableList(new ArrayList<Integer>(parentDataSizes));
		this.successorTaskIds = Collections.unmodifiableList(new ArrayList<String>(successorTaskIds));
		this.successorDataSizes = Collections.unmodifiableList(new ArrayList<Integer>(successorDataSizes));
	}

	public String getTaskId() { return taskId; }
	public int getWorkflowId() { return workflowId; }
	public int getWorkflowArrival() { return workflowArrival; }
	public int getWorkflowDeadline() { return workflowDeadline; }
	public int getBaseExecutionTime() { return baseExecutionTime; }
	public int getBaseStartTime() { return baseStartTime; }
	public int getBaseFinishTime() { return baseFinishTime; }
	public int getRealBaseExecutionTime() { return realBaseExecutionTime; }
	public int getRealExecutionTime() { return realExecutionTime; }
	public int getExecutionTimeWithConfidency() { return executionTimeWithConfidency; }
	public int getRealStartTime() { return realStartTime; }
	public int getStartTimeWithConfidency() { return startTimeWithConfidency; }
	public int getEarliestStartTime() { return earliestStartTime; }
	public int getLeastStartTime() { return leastStartTime; }
	public int getRealFinishTime() { return realFinishTime; }
	public int getFinishTimeWithConfidency() { return finishTimeWithConfidency; }
	public int getEarliestFinishTime() { return earliestFinishTime; }
	public int getLeastFinishTime() { return leastFinishTime; }
	public int getSubDeadline() { return subDeadline; }
	public int getSubSpan() { return subSpan; }
	public int getUpwardRank() { return upwardRank; }
	public int getDownwardRank() { return downwardRank; }
	public int getCriticalPathSlack() { return criticalPathSlack; }
	public int getRemainingDescendantWorkload() { return remainingDescendantWorkload; }
	public int getDataTransferPressure() { return dataTransferPressure; }
	public int getReadyDuration() { return readyDuration; }
	public boolean getPathLastFlag() { return pathLastFlag; }
	public boolean getPathFirstFlag() { return pathFirstFlag; }
	public String getPathFirstTaskId() { return pathFirstTaskId; }
	public String getPathLastTaskId() { return pathLastTaskId; }
	public boolean getAllocatedFlag() { return allocatedFlag; }
	public int getAllocatedVmId() { return allocatedVmId; }
	public boolean getFinishFlag() { return finishFlag; }
	public boolean getNewStartVm() { return newStartVm; }
	public int getPriority() { return priority; }
	public int getPcpNum() { return pcpNum; }
	public boolean getInGlobalTaskPool() { return inGlobalTaskPool; }
	public List<String> getParentTaskIds() { return parentTaskIds; }
	public List<Integer> getParentDataSizes() { return parentDataSizes; }
	public List<String> getSuccessorTaskIds() { return successorTaskIds; }
	public List<Integer> getSuccessorDataSizes() { return successorDataSizes; }
}
