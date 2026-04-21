package ScheduleAgorithm;

public final class HeuristicRerankPolicy implements HierarchicalSchedulingPolicy
{
	public TaskSelection selectTask(TaskCandidateSet taskSet, SchedulingState state)
	{
		if(taskSet.isEmpty())
		{
			throw new IllegalArgumentException("taskSet is empty");
		}

		int bestIndex = -1;
		for(int index = 0; index < taskSet.size(); index++)
		{
			TaskCandidateView candidate = taskSet.get(index);
			if(candidate.getTask().getAllocatedFlag() || candidate.getTask().getFinishFlag())
			{
				continue;
			}

			if(bestIndex == -1 || compareTasks(candidate, taskSet.get(bestIndex), state) < 0)
			{
				bestIndex = index;
			}
		}

		if(bestIndex == -1)
		{
			bestIndex = 0;
		}
		return new TaskSelection(bestIndex, taskSet.get(bestIndex));
	}

	public ResourceSelection selectResource(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state)
	{
		if(vmSet.isEmpty())
		{
			throw new IllegalArgumentException("vmSet is empty");
		}

		int bestExistingIndex = selectBestExistingVm(vmSet);
		if(bestExistingIndex != -1)
		{
			return new ResourceSelection(bestExistingIndex, vmSet.get(bestExistingIndex));
		}

		int bestNewVmIndex = selectBestNewVm(vmSet);
		if(bestNewVmIndex != -1)
		{
			return new ResourceSelection(bestNewVmIndex, vmSet.get(bestNewVmIndex));
		}

		return new ResourceSelection(0, vmSet.get(0));
	}

	private int selectBestExistingVm(VmCandidateSet vmSet)
	{
		int bestIndex = -1;
		for(int index = 0; index < vmSet.size(); index++)
		{
			VmCandidateView candidate = vmSet.get(index);
			if(candidate.getCandidateKind() != VmCandidateKind.EXISTING_VM
					|| !candidate.getFeasibleUnderSubDeadline())
			{
				continue;
			}

			if(bestIndex == -1 || compareVmCandidates(candidate, vmSet.get(bestIndex)) < 0)
			{
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private int selectBestNewVm(VmCandidateSet vmSet)
	{
		int bestIndex = -1;
		for(int index = 0; index < vmSet.size(); index++)
		{
			VmCandidateView candidate = vmSet.get(index);
			if(candidate.getCandidateKind() != VmCandidateKind.LEASE_NEW_VM_TYPE)
			{
				continue;
			}

			if(bestIndex == -1 || compareVmCandidates(candidate, vmSet.get(bestIndex)) < 0)
			{
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private int compareTasks(TaskCandidateView left, TaskCandidateView right, SchedulingState state)
	{
		int result = Integer.compare(left.getEarliestFinishTime(), right.getEarliestFinishTime());
		if(result != 0)
		{
			return result;
		}

		TaskStateView leftState = state == null ? null : state.findTaskState(left.getTaskId());
		TaskStateView rightState = state == null ? null : state.findTaskState(right.getTaskId());

		result = Integer.compare(resolveCriticalPathSlack(leftState), resolveCriticalPathSlack(rightState));
		if(result != 0)
		{
			return result;
		}

		result = Integer.compare(resolveUpwardRank(rightState), resolveUpwardRank(leftState));
		if(result != 0)
		{
			return result;
		}

		result = Integer.compare(left.getWorkflowId(), right.getWorkflowId());
		if(result != 0)
		{
			return result;
		}

		return left.getTaskId().compareTo(right.getTaskId());
	}

	private int compareVmCandidates(VmCandidateView left, VmCandidateView right)
	{
		int result = Double.compare(left.getEstimatedCostIfAssigned(), right.getEstimatedCostIfAssigned());
		if(result != 0)
		{
			return result;
		}

		result = Integer.compare(left.getEstimatedReadyStartTime(), right.getEstimatedReadyStartTime());
		if(result != 0)
		{
			return result;
		}

		result = Double.compare(right.getIdleGapFitScore(), left.getIdleGapFitScore());
		if(result != 0)
		{
			return result;
		}

		if(left.getCandidateKind() == VmCandidateKind.EXISTING_VM
				&& right.getCandidateKind() == VmCandidateKind.EXISTING_VM)
		{
			return Integer.compare(left.getExistingVmId(), right.getExistingVmId());
		}

		return Integer.compare(left.getNewVmType(), right.getNewVmType());
	}

	private int resolveCriticalPathSlack(TaskStateView taskState)
	{
		return taskState == null ? Integer.MAX_VALUE : taskState.getCriticalPathSlack();
	}

	private int resolveUpwardRank(TaskStateView taskState)
	{
		return taskState == null ? Integer.MIN_VALUE : taskState.getUpwardRank();
	}
}
