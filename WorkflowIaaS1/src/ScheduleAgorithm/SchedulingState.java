package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SchedulingState
{
	private final int currentTime;
	private final List<WorkflowStateView> workflowStates;
	private final List<TaskStateView> taskStates;
	private final List<VmStateView> activeVmStates;
	private final List<VmStateView> offVmStates;
	private final List<String> globalTaskPoolTaskIds;

	public SchedulingState(int currentTime, List<WorkflowStateView> workflowStates, List<TaskStateView> taskStates,
			List<VmStateView> activeVmStates, List<VmStateView> offVmStates, List<String> globalTaskPoolTaskIds)
	{
		this.currentTime = currentTime;
		this.workflowStates = Collections.unmodifiableList(new ArrayList<WorkflowStateView>(workflowStates));
		this.taskStates = Collections.unmodifiableList(new ArrayList<TaskStateView>(taskStates));
		this.activeVmStates = Collections.unmodifiableList(new ArrayList<VmStateView>(activeVmStates));
		this.offVmStates = Collections.unmodifiableList(new ArrayList<VmStateView>(offVmStates));
		this.globalTaskPoolTaskIds = Collections.unmodifiableList(new ArrayList<String>(globalTaskPoolTaskIds));
	}

	public int getCurrentTime()
	{
		return currentTime;
	}

	public List<WorkflowStateView> getWorkflowStates()
	{
		return workflowStates;
	}

	public List<TaskStateView> getTaskStates()
	{
		return taskStates;
	}

	public List<VmStateView> getActiveVmStates()
	{
		return activeVmStates;
	}

	public List<VmStateView> getOffVmStates()
	{
		return offVmStates;
	}

	public List<String> getGlobalTaskPoolTaskIds()
	{
		return globalTaskPoolTaskIds;
	}
}
