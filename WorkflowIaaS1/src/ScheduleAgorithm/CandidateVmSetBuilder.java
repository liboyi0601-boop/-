package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;

import share.StaticfinalTags;
import vmInfo.SaaSVm;
import vmInfo.VmResource.VmParameter;
import workflow.ConstraintWTask;

public final class CandidateVmSetBuilder
{
	public VmCandidateSet build(TaskCandidateView selectedTask, List<SaaSVm> vmList, SchedulingState state)
	{
		List<VmCandidateView> candidates = new ArrayList<VmCandidateView>();
		int candidateIndex = 0;
		int[] realDataArrivalList = computeRealDataArrivalList(selectedTask.getTask(), vmList);

		for(int index = 0; index < vmList.size(); index++)
		{
			SaaSVm vm = vmList.get(index);
			if(!vm.getWaitingWTask().getTaskId().equals("initial"))
			{
				continue;
			}

			int readyStartTime = realDataArrivalList[index];
			int vmReadyTime = StaticfinalTags.currentTime;
			if(!vm.getExecutingWTask().getTaskId().equals("initial"))
			{
				vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency();
			}
			if(vmReadyTime > readyStartTime)
			{
				readyStartTime = vmReadyTime;
			}

			int executionTimeWithConfidency = (int)(selectedTask.getTask().getExecutionTimeWithConfidency()
					* vm.getVmFactor());
			int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency;
			double estimatedCost = executionTimeWithConfidency * vm.getVmPrice();
			int idleGap = readyStartTime - vmReadyTime;
			boolean feasible = finishTimeWithConfidency <= selectedTask.getTask().getSubDeadLine();

			if(feasible)
			{
				candidates.add(VmCandidateView.forExistingVm(candidateIndex, vm, realDataArrivalList[index],
						readyStartTime, finishTimeWithConfidency, estimatedCost, -idleGap, feasible));
				candidateIndex++;
			}
		}

		int baseReadyStartTime = selectedTask.getTask().getEarliestStartTime();
		if(StaticfinalTags.currentTime > baseReadyStartTime)
		{
			baseReadyStartTime = StaticfinalTags.currentTime;
		}

		for(int level = 0; level <= 6; level++)
		{
			VmParameter vmParameter = VmParameter.valueOf(level);
			int finishTimeWithConfidency = baseReadyStartTime
					+ (int)(selectedTask.getTask().getExecutionTimeWithConfidency() * vmParameter.getFactor());
			double estimatedCost = (selectedTask.getTask().getExecutionTimeWithConfidency() * vmParameter.getFactor())
					* vmParameter.getPrice();
			boolean feasible = finishTimeWithConfidency <= selectedTask.getTask().getSubDeadLine();
			candidates.add(VmCandidateView.forNewVmType(candidateIndex, level, baseReadyStartTime,
					finishTimeWithConfidency, estimatedCost, 0.0, feasible));
			candidateIndex++;
		}

		return new VmCandidateSet(candidates);
	}

	private int[] computeRealDataArrivalList(workflow.WTask task, List<SaaSVm> vmList)
	{
		int[] realDataArrivalList = new int[vmList.size()];

		for(int index = 0; index < vmList.size(); index++)
		{
			SaaSVm vm = vmList.get(index);
			int realDataArrival = Integer.MIN_VALUE;
			for(ConstraintWTask parentCon: task.getParentTaskList())
			{
				int dataArrival;

				if(vm.getWTaskList().contains(parentCon.getWTask()))
				{
					dataArrival = parentCon.getWTask().getRealFinishTime();
				}
				else
				{
					dataArrival = parentCon.getWTask().getRealFinishTime()
							+ parentCon.getDataSize()/StaticfinalTags.bandwidth;
				}

				if(dataArrival > realDataArrival)
				{
					realDataArrival = dataArrival;
				}
			}
			if(realDataArrival != Integer.MIN_VALUE)
			{
				realDataArrivalList[index] = realDataArrival;
			}
		}

		return realDataArrivalList;
	}
}
