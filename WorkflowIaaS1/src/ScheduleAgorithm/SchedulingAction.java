package ScheduleAgorithm;

import vmInfo.SaaSVm;
import workflow.WTask;

public final class SchedulingAction
{
	private final SchedulingActionType actionType;
	private final WTask task;
	private final SaaSVm targetVm;
	private final int realDataArrival;
	private final int readyStartTime;
	private final int newVmType;

	private SchedulingAction(SchedulingActionType actionType, WTask task, SaaSVm targetVm,
			int realDataArrival, int readyStartTime, int newVmType)
	{
		this.actionType = actionType;
		this.task = task;
		this.targetVm = targetVm;
		this.realDataArrival = realDataArrival;
		this.readyStartTime = readyStartTime;
		this.newVmType = newVmType;
	}

	public static SchedulingAction allocateToExistingVm(WTask task, SaaSVm targetVm, int realDataArrival)
	{
		return new SchedulingAction(SchedulingActionType.ALLOCATE_TO_EXISTING_VM, task, targetVm,
				realDataArrival, -1, -1);
	}

	public static SchedulingAction leaseNewVmAndAllocate(WTask task, int readyStartTime, int newVmType)
	{
		return new SchedulingAction(SchedulingActionType.LEASE_NEW_VM_AND_ALLOCATE, task, null,
				-1, readyStartTime, newVmType);
	}

	public SchedulingActionType getActionType()
	{
		return actionType;
	}

	public WTask getTask()
	{
		return task;
	}

	public SaaSVm getTargetVm()
	{
		return targetVm;
	}

	public int getRealDataArrival()
	{
		return realDataArrival;
	}

	public int getReadyStartTime()
	{
		return readyStartTime;
	}

	public int getNewVmType()
	{
		return newVmType;
	}
}
