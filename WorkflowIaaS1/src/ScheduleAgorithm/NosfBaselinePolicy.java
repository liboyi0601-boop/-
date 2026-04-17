package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;

import share.StaticfinalTags;
import vmInfo.SaaSVm;
import vmInfo.VmResource.VmParameter;
import workflow.ConstraintWTask;
import workflow.WTask;

public class NosfBaselinePolicy
{
	public SchedulingDecision choosePlacement(WTask task, List<SaaSVm> vmList)
	{
		int realDataArrivalList[] = computeRealDataArrivalList(task, vmList);
		double minCost = Double.MAX_VALUE;

		for(SaaSVm vm: vmList)
		{
			if(vm.getWaitingWTask().getTaskId().equals("initial"))
			{
				int readyStartTime = realDataArrivalList[vmList.indexOf(vm)];
				int vmReadyTime = StaticfinalTags.currentTime;

				if(!vm.getExecutingWTask().getTaskId().equals("initial"))
				{
					vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency();
				}

				if(vmReadyTime > readyStartTime)
				{
					readyStartTime = vmReadyTime;
				}

				int executionTimeWithConfidency = (int)(task.getExecutionTimeWithConfidency()*vm.getVmFactor());
				double cost = executionTimeWithConfidency*vm.getVmPrice();
				int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency;

				if(finishTimeWithConfidency <= task.getSubDeadLine())
				{
					if(cost < minCost)
					{
						minCost = cost;
					}
				}
			}
		}

		List<SaaSVm> mapTargetVmSet = collectMinCostVmSet(task, vmList, realDataArrivalList, minCost);
		SaaSVm mapTargetVm = selectMinIdleVm(task, vmList, realDataArrivalList, mapTargetVmSet);

		if(mapTargetVm != null)
		{
			return SchedulingDecision.forExistingVm(mapTargetVm, realDataArrivalList[vmList.indexOf(mapTargetVm)]);
		}

		int readyStartTime = task.getEarliestStartTime();
		if(StaticfinalTags.currentTime > readyStartTime)
		{
			readyStartTime = StaticfinalTags.currentTime;
		}

		return SchedulingDecision.forNewVm(readyStartTime, determineSaaSVmType(task, readyStartTime));
	}

	private int[] computeRealDataArrivalList(WTask task, List<SaaSVm> vmList)
	{
		int realDataArrivalList[] = new int [vmList.size()];

		for(SaaSVm vm: vmList)
		{
			int realDataArrival = Integer.MIN_VALUE;
			for(ConstraintWTask parentCon: task.getParentTaskList())
			{
				int DataArrival = 0;

				if(vm.getWTaskList().contains(parentCon.getWTask()))
				{
					DataArrival = parentCon.getWTask().getRealFinishTime();
				}
				else
				{
					DataArrival = parentCon.getWTask().getRealFinishTime()
							+ parentCon.getDataSize()/StaticfinalTags.bandwidth;
				}

				if(DataArrival > realDataArrival)
				{
					realDataArrival = DataArrival;
					realDataArrivalList[vmList.indexOf(vm)] = realDataArrival;
				}
			}
		}

		return realDataArrivalList;
	}

	private List<SaaSVm> collectMinCostVmSet(WTask task, List<SaaSVm> vmList, int[] realDataArrivalList, double minCost)
	{
		List<SaaSVm> mapTargetVmSet = new ArrayList<SaaSVm>();

		for(SaaSVm vm: vmList)
		{
			if(vm.getWaitingWTask().getTaskId().equals("initial"))
			{
				int readyStartTime = realDataArrivalList[vmList.indexOf(vm)];
				int vmReadyTime = StaticfinalTags.currentTime;

				if(!vm.getExecutingWTask().getTaskId().equals("initial"))
				{
					vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency();
				}

				if(vmReadyTime > readyStartTime)
				{
					readyStartTime = vmReadyTime;
				}

				int executionTimeWithConfidency = (int)(task.getExecutionTimeWithConfidency()*vm.getVmFactor());
				double cost = executionTimeWithConfidency*vm.getVmPrice();
				int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency;

				if(finishTimeWithConfidency <= task.getSubDeadLine())
				{
					if(cost == minCost)
					{
						mapTargetVmSet.add(vm);
					}
				}
			}
		}

		return mapTargetVmSet;
	}

	private SaaSVm selectMinIdleVm(WTask task, List<SaaSVm> vmList, int[] realDataArrivalList, List<SaaSVm> mapTargetVmSet)
	{
		int minIdleTime = Integer.MAX_VALUE;
		SaaSVm mapTargetVm = null;

		for(SaaSVm vm: mapTargetVmSet)
		{
			int vmReadyTime = StaticfinalTags.currentTime;

			if(!vm.getExecutingWTask().getTaskId().equals("initial"))
			{
				vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency();
			}

			int readyStartTime = realDataArrivalList[vmList.indexOf(vm)];
			if(vmReadyTime > readyStartTime)
			{
				readyStartTime = vmReadyTime;
			}

			int IdleTime = readyStartTime - vmReadyTime;
			if(IdleTime < minIdleTime)
			{
				minIdleTime = IdleTime;
				mapTargetVm = vm;
			}
		}

		return mapTargetVm;
	}

	private int determineSaaSVmType(WTask task, int startTime)
	{
		int level = 0;

		int grade6 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_7.getFactor());
		int grade5 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_6.getFactor());
		int grade4 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_5.getFactor());
		int grade3 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_4.getFactor());
		int grade2 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_3.getFactor());
		int grade1 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_2.getFactor());
		int grade0 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_1.getFactor());

		if(grade6 <= task.getSubDeadLine())
		{
			level = 6;
		}
		else if(grade5 <= task.getSubDeadLine())
		{
			level = 5;
		}
		else if(grade4 <= task.getSubDeadLine())
		{
			level = 4;
		}
		else if(grade3 <= task.getSubDeadLine())
		{
			level = 3;
		}
		else if(grade2 <= task.getSubDeadLine())
		{
			level = 2;
		}
		else if(grade1 <= task.getSubDeadLine())
		{
			level = 1;
		}
		else if(grade0 <= task.getSubDeadLine())
		{
			level = 0;
		}
		else
		{
			level = 0;
		}

		return level;
	}

	public static final class SchedulingDecision
	{
		public final boolean useExistingVm;
		public final SaaSVm targetVm;
		public final int realDataArrival;
		public final int readyStartTime;
		public final int newVmType;

		private SchedulingDecision(boolean useExistingVm, SaaSVm targetVm, int realDataArrival,
				int readyStartTime, int newVmType)
		{
			this.useExistingVm = useExistingVm;
			this.targetVm = targetVm;
			this.realDataArrival = realDataArrival;
			this.readyStartTime = readyStartTime;
			this.newVmType = newVmType;
		}

		public static SchedulingDecision forExistingVm(SaaSVm targetVm, int realDataArrival)
		{
			return new SchedulingDecision(true, targetVm, realDataArrival, -1, -1);
		}

		public static SchedulingDecision forNewVm(int readyStartTime, int newVmType)
		{
			return new SchedulingDecision(false, null, -1, readyStartTime, newVmType);
		}
	}
}
