package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import share.StaticfinalTags;
import vmInfo.SaaSVm;
import workflow.ConstraintWTask;
import workflow.WTask;
import workflow.Workflow;

public class StateBuilder
{
	public SchedulingState build(WorkflowSchedulingEnv env)
	{
		Set<WTask> globalTaskPoolSet = new HashSet<WTask>(env.getGlobalTaskPool());
		Map<WTask, Integer> upwardRankMap = buildUpwardRankMap(env.getWorkflowList());
		Map<WTask, Integer> downwardRankMap = buildDownwardRankMap(env.getWorkflowList());
		Map<WTask, Integer> descendantWorkloadMap = buildDescendantWorkloadMap(env.getWorkflowList());
		List<WorkflowStateView> workflowStates = buildWorkflowStates(env.getWorkflowList(), globalTaskPoolSet,
				upwardRankMap, env.getCurrentTime());
		List<TaskStateView> taskStates = buildTaskStates(env.getWorkflowList(), globalTaskPoolSet,
				upwardRankMap, downwardRankMap, descendantWorkloadMap, env.getCurrentTime());
		List<VmStateView> activeVmStates = buildVmStates(env.getActiveVmList(), env.getCurrentTime());
		List<VmStateView> offVmStates = buildVmStates(env.getOffVmList(), env.getCurrentTime());
		List<String> globalTaskPoolTaskIds = buildTaskIds(env.getGlobalTaskPool());

		return new SchedulingState(env.getCurrentTime(), workflowStates, taskStates,
				activeVmStates, offVmStates, globalTaskPoolTaskIds);
	}

	private List<WorkflowStateView> buildWorkflowStates(List<Workflow> workflows, Set<WTask> globalTaskPoolSet,
			Map<WTask, Integer> upwardRankMap, int currentTime)
	{
		List<WorkflowStateView> workflowStates = new ArrayList<WorkflowStateView>();

		for(Workflow workflow: workflows)
		{
			List<String> taskIds = new ArrayList<String>();
			int allocatedTaskCount = 0;
			int finishedTaskCount = 0;
			int readyTaskCount = 0;
			int remainingCriticalPathLength = 0;

			for(WTask task: workflow.getTaskList())
			{
				taskIds.add(task.getTaskId());
				if(task.getAllocatedFlag())
				{
					allocatedTaskCount++;
				}
				if(task.getFinishFlag())
				{
					finishedTaskCount++;
				}
				if(globalTaskPoolSet.contains(task) && !task.getAllocatedFlag() && !task.getFinishFlag())
				{
					readyTaskCount++;
				}
				if(!task.getFinishFlag() && upwardRankMap.get(task).intValue() > remainingCriticalPathLength)
				{
					remainingCriticalPathLength = upwardRankMap.get(task).intValue();
				}
			}

			int unfinishedTaskCount = workflow.getTaskList().size() - finishedTaskCount;
			int remainingWindow = workflow.getDeadline() - currentTime;
			double normalizedSlack = ((double)remainingWindow - remainingCriticalPathLength)
					/ Math.max(1, workflow.getDeadline() - workflow.getArrivalTime());
			double violationRiskScore;
			if(remainingWindow <= 0)
			{
				violationRiskScore = remainingCriticalPathLength > 0 ? remainingCriticalPathLength : 0.0;
			}
			else
			{
				violationRiskScore = (double)remainingCriticalPathLength / remainingWindow;
			}
			double readyTaskDensity = (double)readyTaskCount / Math.max(1, unfinishedTaskCount);

			workflowStates.add(new WorkflowStateView(
					workflow.getWorkflowId(),
					workflow.getWorkflowName(),
					workflow.getArrivalTime(),
					workflow.getMakespan(),
					workflow.getDeadline(),
					workflow.getStartedFlag(),
					workflow.getFinishTime(),
					workflow.getSuccessfulOrNot(),
					workflow.getTaskList().size(),
					allocatedTaskCount,
					finishedTaskCount,
					normalizedSlack,
					violationRiskScore,
					readyTaskDensity,
					remainingCriticalPathLength,
					taskIds));
		}

		return workflowStates;
	}

	private List<TaskStateView> buildTaskStates(List<Workflow> workflows, Set<WTask> globalTaskPoolSet,
			Map<WTask, Integer> upwardRankMap, Map<WTask, Integer> downwardRankMap,
			Map<WTask, Integer> descendantWorkloadMap, int currentTime)
	{
		List<TaskStateView> taskStates = new ArrayList<TaskStateView>();

		for(Workflow workflow: workflows)
		{
			for(WTask task: workflow.getTaskList())
			{
				taskStates.add(buildTaskState(task, globalTaskPoolSet, upwardRankMap, downwardRankMap,
						descendantWorkloadMap, currentTime));
			}
		}

		return taskStates;
	}

	private TaskStateView buildTaskState(WTask task, Set<WTask> globalTaskPoolSet, Map<WTask, Integer> upwardRankMap,
			Map<WTask, Integer> downwardRankMap, Map<WTask, Integer> descendantWorkloadMap, int currentTime)
	{
		List<String> parentTaskIds = new ArrayList<String>();
		List<Integer> parentDataSizes = new ArrayList<Integer>();
		for(ConstraintWTask parentTask: task.getParentTaskList())
		{
			parentTaskIds.add(parentTask.getWTask().getTaskId());
			parentDataSizes.add(parentTask.getDataSize());
		}

		List<String> successorTaskIds = new ArrayList<String>();
		List<Integer> successorDataSizes = new ArrayList<Integer>();
		for(ConstraintWTask successorTask: task.getSuccessorTaskList())
		{
			successorTaskIds.add(successorTask.getWTask().getTaskId());
			successorDataSizes.add(successorTask.getDataSize());
		}

		String pathFirstTaskId = null;
		if(task.getPathFirstTask() != null)
		{
			pathFirstTaskId = task.getPathFirstTask().getTaskId();
		}

		String pathLastTaskId = null;
		if(task.getPathLastTask() != null)
		{
			pathLastTaskId = task.getPathLastTask().getTaskId();
		}

		int allocatedVmId = -1;
		if(task.getAllocateVm() != null)
		{
			allocatedVmId = task.getAllocateVm().getVmID();
		}

		int dataTransferPressure = sum(parentDataSizes) + sum(successorDataSizes);
		int readyDuration = 0;
		if(task.getEarliestStartTime() != -1 && currentTime > task.getEarliestStartTime())
		{
			readyDuration = currentTime - task.getEarliestStartTime();
		}

		return new TaskStateView(
				task.getTaskId(),
				task.getTaskWorkFlowId(),
				task.getWorkFlowArrival(),
				task.getWorkFlowDeadline(),
				task.getBaseExecutionTime(),
				task.getBaseStartTime(),
				task.getBaseFinishTime(),
				task.getRealBaseExecutionTime(),
				task.getRealExecutionTime(),
				task.getExecutionTimeWithConfidency(),
				task.getRealStartTime(),
				task.getStartTimeWithConfidency(),
				task.getEarliestStartTime(),
				task.getLeastStartTime(),
				task.getRealFinishTime(),
				task.getFinishTimeWithConfidency(),
				task.getEarliestFinishTime(),
				task.getLeastFinishTime(),
				task.getSubDeadLine(),
				task.getSubSpan(),
				upwardRankMap.get(task).intValue(),
				downwardRankMap.get(task).intValue(),
				task.getSubDeadLine() - task.getEarliestFinishTime(),
				descendantWorkloadMap.get(task).intValue(),
				dataTransferPressure,
				readyDuration,
				task.getPathLastFlag(),
				task.getPathFirstFlag(),
				pathFirstTaskId,
				pathLastTaskId,
				task.getAllocatedFlag(),
				allocatedVmId,
				task.getFinishFlag(),
				task.getNewStartVm(),
				task.getPriority(),
				task.getPCPNum(),
				globalTaskPoolSet.contains(task),
				parentTaskIds,
				parentDataSizes,
				successorTaskIds,
				successorDataSizes);
	}

	private List<VmStateView> buildVmStates(List<SaaSVm> vmList, int currentTime)
	{
		List<VmStateView> vmStates = new ArrayList<VmStateView>();

		for(SaaSVm vm: vmList)
		{
			vmStates.add(buildVmState(vm, currentTime));
		}

		return vmStates;
	}

	private VmStateView buildVmState(SaaSVm vm, int currentTime)
	{
		List<String> completedTaskIds = buildTaskIds(vm.getWTaskList());
		String executingTaskId = vm.getExecutingWTask().getTaskId();
		String waitingTaskId = vm.getWaitingWTask().getTaskId();
		int slotRemaining = calculateSlotRemaining(vm, currentTime);
		double billingResidual = (slotRemaining * vm.getVmPrice()) / StaticfinalTags.VmSlot;
		boolean idleNow = "initial".equals(executingTaskId) && "initial".equals(waitingTaskId);

		return new VmStateView(
				vm.getVmID(),
				vm.getVmType(),
				vm.getVmPrice(),
				vm.getVmFactor(),
				vm.getVmStartWorkTime(),
				vm.getEndWorkTime(),
				vm.getRealWorkTime(),
				vm.getIdleTime(),
				vm.getFinishTime(),
				vm.getRealFinishTime(),
				vm.getReadyTime(),
				vm.getVmStatus(),
				vm.getTotalCost(),
				vm.getVmPrice(),
				billingResidual,
				slotRemaining,
				idleNow,
				executingTaskId,
				waitingTaskId,
				completedTaskIds);
	}

	private int calculateSlotRemaining(SaaSVm vm, int currentTime)
	{
		if(currentTime <= vm.getVmStartWorkTime())
		{
			return StaticfinalTags.VmSlot;
		}

		int elapsed = currentTime - vm.getVmStartWorkTime();
		int remainder = elapsed % StaticfinalTags.VmSlot;
		if(remainder == 0)
		{
			return 0;
		}
		return StaticfinalTags.VmSlot - remainder;
	}

	private List<String> buildTaskIds(List<WTask> tasks)
	{
		List<String> taskIds = new ArrayList<String>();

		for(WTask task: tasks)
		{
			taskIds.add(task.getTaskId());
		}

		return taskIds;
	}

	private Map<WTask, Integer> buildUpwardRankMap(List<Workflow> workflows)
	{
		Map<WTask, Integer> rankMap = new HashMap<WTask, Integer>();
		for(Workflow workflow: workflows)
		{
			for(WTask task: workflow.getTaskList())
			{
				computeUpwardRank(task, rankMap);
			}
		}
		return rankMap;
	}

	private int computeUpwardRank(WTask task, Map<WTask, Integer> rankMap)
	{
		if(rankMap.containsKey(task))
		{
			return rankMap.get(task).intValue();
		}

		int maxSuccessorContribution = 0;
		for(ConstraintWTask successor: task.getSuccessorTaskList())
		{
			int contribution = successor.getDataSize() / StaticfinalTags.bandwidth
					+ computeUpwardRank(successor.getWTask(), rankMap);
			if(contribution > maxSuccessorContribution)
			{
				maxSuccessorContribution = contribution;
			}
		}

		int rank = task.getExecutionTimeWithConfidency() + maxSuccessorContribution;
		rankMap.put(task, Integer.valueOf(rank));
		return rank;
	}

	private Map<WTask, Integer> buildDownwardRankMap(List<Workflow> workflows)
	{
		Map<WTask, Integer> rankMap = new HashMap<WTask, Integer>();
		for(Workflow workflow: workflows)
		{
			for(WTask task: workflow.getTaskList())
			{
				computeDownwardRank(task, rankMap);
			}
		}
		return rankMap;
	}

	private int computeDownwardRank(WTask task, Map<WTask, Integer> rankMap)
	{
		if(rankMap.containsKey(task))
		{
			return rankMap.get(task).intValue();
		}

		int maxParentContribution = 0;
		for(ConstraintWTask parent: task.getParentTaskList())
		{
			int contribution = computeDownwardRank(parent.getWTask(), rankMap)
					+ parent.getWTask().getExecutionTimeWithConfidency()
					+ parent.getDataSize() / StaticfinalTags.bandwidth;
			if(contribution > maxParentContribution)
			{
				maxParentContribution = contribution;
			}
		}

		rankMap.put(task, Integer.valueOf(maxParentContribution));
		return maxParentContribution;
	}

	private Map<WTask, Integer> buildDescendantWorkloadMap(List<Workflow> workflows)
	{
		Map<WTask, Integer> workloadMap = new HashMap<WTask, Integer>();
		for(Workflow workflow: workflows)
		{
			for(WTask task: workflow.getTaskList())
			{
				computeDescendantWorkload(task, workloadMap);
			}
		}
		return workloadMap;
	}

	private int computeDescendantWorkload(WTask task, Map<WTask, Integer> workloadMap)
	{
		if(workloadMap.containsKey(task))
		{
			return workloadMap.get(task).intValue();
		}

		int workload = 0;
		for(ConstraintWTask successor: task.getSuccessorTaskList())
		{
			workload += successor.getWTask().getExecutionTimeWithConfidency()
					+ computeDescendantWorkload(successor.getWTask(), workloadMap);
		}

		workloadMap.put(task, Integer.valueOf(workload));
		return workload;
	}

	private int sum(List<Integer> values)
	{
		int total = 0;
		for(Integer value: values)
		{
			total += value.intValue();
		}
		return total;
	}
}
