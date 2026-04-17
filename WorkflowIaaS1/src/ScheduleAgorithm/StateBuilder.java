package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import vmInfo.SaaSVm;
import workflow.ConstraintWTask;
import workflow.WTask;
import workflow.Workflow;

public class StateBuilder
{
	public SchedulingState build(WorkflowSchedulingEnv env)
	{
		Set<WTask> globalTaskPoolSet = new HashSet<WTask>(env.getGlobalTaskPool());
		List<WorkflowStateView> workflowStates = buildWorkflowStates(env.getWorkflowList());
		List<TaskStateView> taskStates = buildTaskStates(env.getWorkflowList(), globalTaskPoolSet);
		List<VmStateView> activeVmStates = buildVmStates(env.getActiveVmList());
		List<VmStateView> offVmStates = buildVmStates(env.getOffVmList());
		List<String> globalTaskPoolTaskIds = buildTaskIds(env.getGlobalTaskPool());

		return new SchedulingState(env.getCurrentTime(), workflowStates, taskStates,
				activeVmStates, offVmStates, globalTaskPoolTaskIds);
	}

	private List<WorkflowStateView> buildWorkflowStates(List<Workflow> workflows)
	{
		List<WorkflowStateView> workflowStates = new ArrayList<WorkflowStateView>();

		for(Workflow workflow: workflows)
		{
			List<String> taskIds = new ArrayList<String>();
			int allocatedTaskCount = 0;
			int finishedTaskCount = 0;

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
			}

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
					taskIds));
		}

		return workflowStates;
	}

	private List<TaskStateView> buildTaskStates(List<Workflow> workflows, Set<WTask> globalTaskPoolSet)
	{
		List<TaskStateView> taskStates = new ArrayList<TaskStateView>();

		for(Workflow workflow: workflows)
		{
			for(WTask task: workflow.getTaskList())
			{
				taskStates.add(buildTaskState(task, globalTaskPoolSet));
			}
		}

		return taskStates;
	}

	private TaskStateView buildTaskState(WTask task, Set<WTask> globalTaskPoolSet)
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

	private List<VmStateView> buildVmStates(List<SaaSVm> vmList)
	{
		List<VmStateView> vmStates = new ArrayList<VmStateView>();

		for(SaaSVm vm: vmList)
		{
			vmStates.add(buildVmState(vm));
		}

		return vmStates;
	}

	private VmStateView buildVmState(SaaSVm vm)
	{
		List<String> completedTaskIds = buildTaskIds(vm.getWTaskList());
		String executingTaskId = vm.getExecutingWTask().getTaskId();
		String waitingTaskId = vm.getWaitingWTask().getTaskId();

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
				executingTaskId,
				waitingTaskId,
				completedTaskIds);
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
}
