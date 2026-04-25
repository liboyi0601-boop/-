package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchedulingState
{
	private final int currentTime;
	private final List<WorkflowStateView> workflowStates;
	private final List<TaskStateView> taskStates;
	private final List<VmStateView> activeVmStates;
	private final List<VmStateView> offVmStates;
	private final List<String> globalTaskPoolTaskIds;
	private final Map<Integer, WorkflowStateView> workflowStateById;
	private final Map<String, TaskStateView> taskStateById;
	private final Map<Integer, VmStateView> activeVmStateById;
	private final Map<Integer, VmStateView> offVmStateById;

	public SchedulingState(int currentTime, List<WorkflowStateView> workflowStates, List<TaskStateView> taskStates,
			List<VmStateView> activeVmStates, List<VmStateView> offVmStates, List<String> globalTaskPoolTaskIds)
	{
		this.currentTime = currentTime;
		this.workflowStates = Collections.unmodifiableList(new ArrayList<WorkflowStateView>(workflowStates));
		this.taskStates = Collections.unmodifiableList(new ArrayList<TaskStateView>(taskStates));
		this.activeVmStates = Collections.unmodifiableList(new ArrayList<VmStateView>(activeVmStates));
		this.offVmStates = Collections.unmodifiableList(new ArrayList<VmStateView>(offVmStates));
		this.globalTaskPoolTaskIds = Collections.unmodifiableList(new ArrayList<String>(globalTaskPoolTaskIds));
		this.workflowStateById = indexWorkflowStates(this.workflowStates);
		this.taskStateById = indexTaskStates(this.taskStates);
		this.activeVmStateById = indexVmStates(this.activeVmStates);
		this.offVmStateById = indexVmStates(this.offVmStates);
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

	public WorkflowStateView findWorkflowState(int workflowId)
	{
		return workflowStateById.get(Integer.valueOf(workflowId));
	}

	public TaskStateView findTaskState(String taskId)
	{
		return taskStateById.get(taskId);
	}

	public VmStateView findActiveVmState(int vmId)
	{
		return activeVmStateById.get(Integer.valueOf(vmId));
	}

	public VmStateView findOffVmState(int vmId)
	{
		return offVmStateById.get(Integer.valueOf(vmId));
	}

	private Map<Integer, WorkflowStateView> indexWorkflowStates(List<WorkflowStateView> states)
	{
		Map<Integer, WorkflowStateView> indexedStates = new LinkedHashMap<Integer, WorkflowStateView>();
		for(WorkflowStateView state: states)
		{
			indexedStates.put(Integer.valueOf(state.getWorkflowId()), state);
		}
		return Collections.unmodifiableMap(indexedStates);
	}

	private Map<String, TaskStateView> indexTaskStates(List<TaskStateView> states)
	{
		Map<String, TaskStateView> indexedStates = new LinkedHashMap<String, TaskStateView>();
		for(TaskStateView state: states)
		{
			indexedStates.put(state.getTaskId(), state);
		}
		return Collections.unmodifiableMap(indexedStates);
	}

	private Map<Integer, VmStateView> indexVmStates(List<VmStateView> states)
	{
		Map<Integer, VmStateView> indexedStates = new LinkedHashMap<Integer, VmStateView>();
		for(VmStateView state: states)
		{
			indexedStates.put(Integer.valueOf(state.getVmId()), state);
		}
		return Collections.unmodifiableMap(indexedStates);
	}
}
