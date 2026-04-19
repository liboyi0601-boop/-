package ScheduleAgorithm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vmInfo.SaaSVm;
import workflow.WTask;

public final class JsonlSchedulingTraceRecorder implements SchedulingTraceRecorder
{
	private final BufferedWriter writer;
	private final int snapshotLimit;
	private int eventSequence;

	public JsonlSchedulingTraceRecorder(Path tracePath, int snapshotLimit) throws IOException
	{
		this.writer = JsonSupport.newJsonlWriter(tracePath);
		this.snapshotLimit = snapshotLimit;
		this.eventSequence = 0;
	}

	public boolean isEnabled()
	{
		return true;
	}

	public boolean shouldCaptureStateSnapshot(int decisionIndex)
	{
		return decisionIndex < snapshotLimit;
	}

	public void recordDecisionCandidate(int currentTime, List<WTask> readyTasks, List<SaaSVm> candidateVms,
			int workflowCount, int activeVmCount, int offVmCount, int globalTaskPoolSize) throws IOException
	{
		Map<String, Object> event = baseEvent("decision_candidate", currentTime);
		event.put("readyTaskSet", buildReadyTaskSet(readyTasks));
		event.put("candidateVmSet", buildCandidateVmSet(candidateVms));
		event.put("summary", buildSummary(workflowCount, activeVmCount, offVmCount, globalTaskPoolSize));
		JsonSupport.appendJsonLine(writer, event);
	}

	public void recordDecisionChosen(int currentTime, WTask task, SchedulingAction action, double estimatedCostIncrement,
			SchedulingState snapshot) throws IOException
	{
		Map<String, Object> event = baseEvent("decision_chosen", currentTime);
		event.put("task", buildTaskPointer(task));
		event.put("action", buildAction(action));
		event.put("estimatedCostIncrement", estimatedCostIncrement);
		if(snapshot != null)
		{
			event.put("stateSnapshot", buildSchedulingState(snapshot));
		}
		JsonSupport.appendJsonLine(writer, event);
	}

	public void recordActionApplied(int currentTime, SchedulingAction action, SaaSVm appliedVm) throws IOException
	{
		Map<String, Object> event = baseEvent("action_applied", currentTime);
		event.put("action", buildAction(action));
		event.put("appliedVm", buildAppliedVm(appliedVm));
		JsonSupport.appendJsonLine(writer, event);
	}

	public void recordTaskFinish(int currentTime, List<WTask> finishedTasks, List<WTask> readyTasksAfterFinish)
			throws IOException
	{
		Map<String, Object> event = baseEvent("task_finish", currentTime);
		event.put("finishedTasks", buildFinishedTasks(finishedTasks));
		event.put("readyTasksAfterFinish", buildReadyTaskSet(readyTasksAfterFinish));
		JsonSupport.appendJsonLine(writer, event);
	}

	public void recordVmTurnoff(int currentTime, List<SaaSVm> turnOffVmSet, int turnOffVmTime) throws IOException
	{
		Map<String, Object> event = baseEvent("vm_turnoff", currentTime);
		event.put("turnOffVmTime", turnOffVmTime);
		event.put("vmSet", buildTurnOffVmSet(turnOffVmSet, turnOffVmTime));
		JsonSupport.appendJsonLine(writer, event);
	}

	public void close() throws IOException
	{
		writer.close();
	}

	private Map<String, Object> baseEvent(String eventType, int currentTime)
	{
		Map<String, Object> event = new LinkedHashMap<String, Object>();
		event.put("sequence", eventSequence++);
		event.put("eventType", eventType);
		event.put("currentTime", currentTime);
		return event;
	}

	private Map<String, Object> buildSummary(int workflowCount, int activeVmCount, int offVmCount, int globalTaskPoolSize)
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("workflowCount", workflowCount);
		summary.put("activeVmCount", activeVmCount);
		summary.put("offVmCount", offVmCount);
		summary.put("globalTaskPoolSize", globalTaskPoolSize);
		return summary;
	}

	private List<Map<String, Object>> buildReadyTaskSet(List<WTask> readyTasks)
	{
		List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
		for(WTask task: readyTasks)
		{
			if(task.getAllocatedFlag())
			{
				continue;
			}

			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("taskId", task.getTaskId());
			item.put("workflowId", task.getTaskWorkFlowId());
			item.put("earliestStartTime", task.getEarliestStartTime());
			item.put("earliestFinishTime", task.getEarliestFinishTime());
			item.put("subDeadline", task.getSubDeadLine());
			item.put("priority", task.getPriority());
			tasks.add(item);
		}
		return tasks;
	}

	private List<Map<String, Object>> buildCandidateVmSet(List<SaaSVm> candidateVms)
	{
		List<Map<String, Object>> vms = new ArrayList<Map<String, Object>>();
		for(SaaSVm vm: candidateVms)
		{
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("vmId", vm.getVmID());
			item.put("vmType", vm.getVmType());
			item.put("vmPrice", vm.getVmPrice());
			item.put("vmFactor", vm.getVmFactor());
			item.put("readyTime", vm.getReadyTime());
			item.put("finishTime", vm.getFinishTime());
			item.put("realFinishTime", vm.getRealFinishTime());
			item.put("executingTaskId", vm.getExecutingWTask().getTaskId());
			item.put("waitingTaskId", vm.getWaitingWTask().getTaskId());
			item.put("completedTaskCount", vm.getWTaskList().size());
			vms.add(item);
		}
		return vms;
	}

	private Map<String, Object> buildTaskPointer(WTask task)
	{
		Map<String, Object> pointer = new LinkedHashMap<String, Object>();
		pointer.put("taskId", task.getTaskId());
		pointer.put("workflowId", task.getTaskWorkFlowId());
		pointer.put("subDeadline", task.getSubDeadLine());
		pointer.put("earliestStartTime", task.getEarliestStartTime());
		pointer.put("earliestFinishTime", task.getEarliestFinishTime());
		return pointer;
	}

	private Map<String, Object> buildAction(SchedulingAction action)
	{
		Map<String, Object> item = new LinkedHashMap<String, Object>();
		item.put("actionType", action.getActionType().name());
		item.put("taskId", action.getTask().getTaskId());
		item.put("workflowId", action.getTask().getTaskWorkFlowId());
		item.put("realDataArrival", action.getRealDataArrival());
		item.put("readyStartTime", action.getReadyStartTime());
		item.put("newVmType", action.getNewVmType());
		if(action.getTargetVm() != null)
		{
			item.put("targetVmId", action.getTargetVm().getVmID());
			item.put("targetVmType", action.getTargetVm().getVmType());
		}
		else
		{
			item.put("targetVmId", null);
			item.put("targetVmType", null);
		}
		return item;
	}

	private Map<String, Object> buildAppliedVm(SaaSVm vm)
	{
		Map<String, Object> item = new LinkedHashMap<String, Object>();
		if(vm == null)
		{
			item.put("vmId", null);
			item.put("vmType", null);
			return item;
		}

		item.put("vmId", vm.getVmID());
		item.put("vmType", vm.getVmType());
		item.put("vmPrice", vm.getVmPrice());
		item.put("vmFactor", vm.getVmFactor());
		item.put("readyTime", vm.getReadyTime());
		item.put("executingTaskId", vm.getExecutingWTask().getTaskId());
		item.put("waitingTaskId", vm.getWaitingWTask().getTaskId());
		return item;
	}

	private List<Map<String, Object>> buildFinishedTasks(List<WTask> finishedTasks)
	{
		List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
		for(WTask task: finishedTasks)
		{
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("taskId", task.getTaskId());
			item.put("workflowId", task.getTaskWorkFlowId());
			item.put("realFinishTime", task.getRealFinishTime());
			item.put("finishTimeWithConfidency", task.getFinishTimeWithConfidency());
			item.put("subDeadline", task.getSubDeadLine());
			tasks.add(item);
		}
		return tasks;
	}

	private List<Map<String, Object>> buildTurnOffVmSet(List<SaaSVm> turnOffVmSet, int turnOffVmTime)
	{
		List<Map<String, Object>> vms = new ArrayList<Map<String, Object>>();
		for(SaaSVm vm: turnOffVmSet)
		{
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			int internalIdleTime = 0;
			int terminalIdleTime = 0;
			int lastFinishTime = -1;

			if(!vm.getWTaskList().isEmpty())
			{
				lastFinishTime = vm.getWTaskList().get(vm.getWTaskList().size()-1).getRealFinishTime();
				terminalIdleTime = turnOffVmTime - lastFinishTime;
				for(int index = 1; index < vm.getWTaskList().size(); index++)
				{
					internalIdleTime += vm.getWTaskList().get(index).getRealStartTime()
							- vm.getWTaskList().get(index-1).getRealFinishTime();
				}
			}

			int workTime = turnOffVmTime - vm.getVmStartWorkTime();
			item.put("vmId", vm.getVmID());
			item.put("vmType", vm.getVmType());
			item.put("vmPrice", vm.getVmPrice());
			item.put("workTime", workTime);
			item.put("totalCost", (workTime * vm.getVmPrice()) / share.StaticfinalTags.VmSlot);
			item.put("completedTaskCount", vm.getWTaskList().size());
			item.put("internalIdleTime", internalIdleTime);
			item.put("terminalIdleTime", terminalIdleTime);
			item.put("lastTaskRealFinishTime", lastFinishTime);
			vms.add(item);
		}
		return vms;
	}

	private Map<String, Object> buildSchedulingState(SchedulingState state)
	{
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("currentTime", state.getCurrentTime());
		map.put("workflowStates", buildWorkflowStates(state));
		map.put("taskStates", buildTaskStates(state));
		map.put("activeVmStates", buildVmStates(state.getActiveVmStates()));
		map.put("offVmStates", buildVmStates(state.getOffVmStates()));
		map.put("globalTaskPoolTaskIds", new ArrayList<String>(state.getGlobalTaskPoolTaskIds()));
		return map;
	}

	private List<Map<String, Object>> buildWorkflowStates(SchedulingState state)
	{
		List<Map<String, Object>> workflows = new ArrayList<Map<String, Object>>();
		for(WorkflowStateView workflow: state.getWorkflowStates())
		{
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("workflowId", workflow.getWorkflowId());
			item.put("workflowName", workflow.getWorkflowName());
			item.put("arrivalTime", workflow.getArrivalTime());
			item.put("makespan", workflow.getMakespan());
			item.put("deadline", workflow.getDeadline());
			item.put("startedFlag", workflow.getStartedFlag());
			item.put("finishTime", workflow.getFinishTime());
			item.put("successfulOrNot", workflow.getSuccessfulOrNot());
			item.put("taskCount", workflow.getTaskCount());
			item.put("allocatedTaskCount", workflow.getAllocatedTaskCount());
			item.put("finishedTaskCount", workflow.getFinishedTaskCount());
			item.put("taskIds", new ArrayList<String>(workflow.getTaskIds()));
			workflows.add(item);
		}
		return workflows;
	}

	private List<Map<String, Object>> buildTaskStates(SchedulingState state)
	{
		List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
		for(TaskStateView task: state.getTaskStates())
		{
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("taskId", task.getTaskId());
			item.put("workflowId", task.getWorkflowId());
			item.put("workflowArrival", task.getWorkflowArrival());
			item.put("workflowDeadline", task.getWorkflowDeadline());
			item.put("baseExecutionTime", task.getBaseExecutionTime());
			item.put("baseStartTime", task.getBaseStartTime());
			item.put("baseFinishTime", task.getBaseFinishTime());
			item.put("realBaseExecutionTime", task.getRealBaseExecutionTime());
			item.put("realExecutionTime", task.getRealExecutionTime());
			item.put("executionTimeWithConfidency", task.getExecutionTimeWithConfidency());
			item.put("realStartTime", task.getRealStartTime());
			item.put("startTimeWithConfidency", task.getStartTimeWithConfidency());
			item.put("earliestStartTime", task.getEarliestStartTime());
			item.put("leastStartTime", task.getLeastStartTime());
			item.put("realFinishTime", task.getRealFinishTime());
			item.put("finishTimeWithConfidency", task.getFinishTimeWithConfidency());
			item.put("earliestFinishTime", task.getEarliestFinishTime());
			item.put("leastFinishTime", task.getLeastFinishTime());
			item.put("subDeadline", task.getSubDeadline());
			item.put("subSpan", task.getSubSpan());
			item.put("pathLastFlag", task.getPathLastFlag());
			item.put("pathFirstFlag", task.getPathFirstFlag());
			item.put("pathFirstTaskId", task.getPathFirstTaskId());
			item.put("pathLastTaskId", task.getPathLastTaskId());
			item.put("allocatedFlag", task.getAllocatedFlag());
			item.put("allocatedVmId", task.getAllocatedVmId());
			item.put("finishFlag", task.getFinishFlag());
			item.put("newStartVm", task.getNewStartVm());
			item.put("priority", task.getPriority());
			item.put("pcpNum", task.getPcpNum());
			item.put("inGlobalTaskPool", task.getInGlobalTaskPool());
			item.put("parentTaskIds", new ArrayList<String>(task.getParentTaskIds()));
			item.put("parentDataSizes", new ArrayList<Integer>(task.getParentDataSizes()));
			item.put("successorTaskIds", new ArrayList<String>(task.getSuccessorTaskIds()));
			item.put("successorDataSizes", new ArrayList<Integer>(task.getSuccessorDataSizes()));
			tasks.add(item);
		}
		return tasks;
	}

	private List<Map<String, Object>> buildVmStates(List<VmStateView> vmStates)
	{
		List<Map<String, Object>> vms = new ArrayList<Map<String, Object>>();
		for(VmStateView vm: vmStates)
		{
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("vmId", vm.getVmId());
			item.put("vmType", vm.getVmType());
			item.put("vmPrice", vm.getVmPrice());
			item.put("vmFactor", vm.getVmFactor());
			item.put("vmStartWorkTime", vm.getVmStartWorkTime());
			item.put("endWorkTime", vm.getEndWorkTime());
			item.put("realWorkTime", vm.getRealWorkTime());
			item.put("idleTime", vm.getIdleTime());
			item.put("finishTime", vm.getFinishTime());
			item.put("realFinishTime", vm.getRealFinishTime());
			item.put("readyTime", vm.getReadyTime());
			item.put("vmStatus", vm.getVmStatus());
			item.put("totalCost", vm.getTotalCost());
			item.put("executingTaskId", vm.getExecutingTaskId());
			item.put("waitingTaskId", vm.getWaitingTaskId());
			item.put("completedTaskIds", new ArrayList<String>(vm.getCompletedTaskIds()));
			vms.add(item);
		}
		return vms;
	}
}
