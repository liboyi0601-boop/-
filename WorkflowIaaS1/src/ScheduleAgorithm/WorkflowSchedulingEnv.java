package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;

import share.StaticfinalTags;
import vmInfo.SaaSVm;
import vmInfo.VmResource;
import workflow.WTask;
import workflow.Workflow;

public class WorkflowSchedulingEnv
{
	private final List<Workflow> workflowList;
	private final VmResource vmRes;
	private final List<WTask> globalTaskPool;

	public WorkflowSchedulingEnv()
	{
		this.workflowList = new ArrayList<Workflow>();
		this.vmRes = new VmResource();
		this.globalTaskPool = new ArrayList<WTask>();
	}

	public void submitWorkflowList(List<Workflow> list)
	{
		workflowList.addAll(list);
	}

	public List<Workflow> getWorkflowList()
	{
		return workflowList;
	}

	public List<SaaSVm> getActiveVmList()
	{
		return vmRes.getActiveVmList();
	}

	public List<SaaSVm> getOffVmList()
	{
		return vmRes.getOffVmList();
	}

	public List<WTask> getGlobalTaskPool()
	{
		return globalTaskPool;
	}

	public void addUnallocatedTasksToGlobalPool(List<Workflow> arrivingWorkflows)
	{
		for(Workflow addWorkflow: arrivingWorkflows)
		{
			for(WTask addTask: addWorkflow.getTaskList())
			{
				if(!addTask.getAllocatedFlag())
				{
					globalTaskPool.add(addTask);
				}
			}
		}
	}

	public void removeAllocatedTasksFromGlobalPool(List<WTask> tasks)
	{
		for(WTask allocatedWtask: tasks)
		{
			if(allocatedWtask.getAllocatedFlag())
			{
				if(!globalTaskPool.remove(allocatedWtask))
				{
					System.out.println("Error: the allocatedWTask cannot be found in GlobalTaskPool");
				}
			}
		}
	}

	public void allocateReadyWTaskToSaaSVm(WTask task, SaaSVm vm, int realDataArrival)
	{
		int vmRealReadyTime = StaticfinalTags.currentTime;
		int vmReadyTimeWC = StaticfinalTags.currentTime;
		int readyStartTime = realDataArrival;
		int readyStartTimeWC = realDataArrival;

		if(!vm.getExecutingWTask().getTaskId().equals("initial"))
		{
			vmRealReadyTime = vm.getExecutingWTask().getRealFinishTime();
			vmReadyTimeWC = vm.getExecutingWTask().getFinishTimeWithConfidency();
		}

		if(vmRealReadyTime > readyStartTime)
		{
			readyStartTime = vmRealReadyTime;
		}
		if(vmReadyTimeWC > readyStartTimeWC)
		{
			readyStartTimeWC = vmReadyTimeWC;
		}

		int realExecutionTime = (int)(task.getRealBaseExecutionTime()*vm.getVmFactor());
		int realFinishTime = readyStartTime + realExecutionTime;

		int executionTimeWithConfidency = (int)(task.getExecutionTimeWithConfidency()*vm.getVmFactor());
		int finishTimeWithConfidency = readyStartTimeWC + executionTimeWithConfidency;

		task.setAllocatedFlag(true);
		task.setAllocateVm(vm);
		task.setRealStartTime(readyStartTime);

		task.setRealExecutionTime(realExecutionTime);
		task.setRealFinishTime(realFinishTime);

		task.setStartTimeWithConfidency(readyStartTimeWC);
		task.setFinishTimeWithConfidency(finishTimeWithConfidency);

		vm.setFinishTime(finishTimeWithConfidency);
		vm.setRealFinishTime(realFinishTime);
		vm.setReadyTime(finishTimeWithConfidency);
		vm.getWTaskList().add(task);

		if(vm.getExecutingWTask().getTaskId().equals("initial"))
		{
			vm.setExecutingWTask(task);
		}
		else
		{
			vm.setWaitingWTask(task);
			if(task.getRealStartTime() < vm.getExecutingWTask().getRealFinishTime())
				System.out.println("等待任务的开始时间 < 执行任务完成时间！in allocateReadyWTaskToSaaSVm()");
		}
	}

	public SaaSVm scaleUpVm(int startTime, int vmType)
	{
		int vmId = getActiveVmList().size();
		SaaSVm newVm = VmResource.scaleUpVm(vmId, startTime, vmType);
		if(newVm != null)
		{
			getActiveVmList().add(newVm);
		}
		return newVm;
	}

	public void allocateReadyWTaskToNewLeasedVm(WTask task, SaaSVm vm, int realDataArrival)
	{
		int vmRealReadyTime = vm.getReadyTime();
		int vmReadyTimeWC = vm.getReadyTime();
		int readyStartTime = realDataArrival;
		int readyStartTimeWC = realDataArrival;

		if(vmRealReadyTime > readyStartTime)
		{
			readyStartTime = vmRealReadyTime;
		}
		if(vmReadyTimeWC > readyStartTimeWC)
		{
			readyStartTimeWC = vmReadyTimeWC;
		}

		int realExecutionTime = (int)(task.getRealBaseExecutionTime()*vm.getVmFactor());
		int realFinishTime = readyStartTime + realExecutionTime;

		int executionTimeWithConfidency = (int)(task.getExecutionTimeWithConfidency()*vm.getVmFactor());
		int finishTimeWithConfidency = readyStartTimeWC + executionTimeWithConfidency;

		task.setAllocatedFlag(true);
		task.setAllocateVm(vm);

		task.setRealStartTime(readyStartTime);
		task.setRealExecutionTime(realExecutionTime);
		task.setRealFinishTime(realFinishTime);

		task.setStartTimeWithConfidency(readyStartTimeWC);
		task.setFinishTimeWithConfidency(finishTimeWithConfidency);

		vm.setFinishTime(finishTimeWithConfidency);
		vm.setRealFinishTime(realFinishTime);
		vm.setReadyTime(finishTimeWithConfidency);
		vm.getWTaskList().add(task);

		if(vm.getExecutingWTask().getTaskId().equals("initial"))
		{
			vm.setExecutingWTask(task);
		}
		else
		{
			vm.setWaitingWTask(task);
			if(task.getRealStartTime() < vm.getExecutingWTask().getRealFinishTime())
				System.out.println("等待任务的开始时间 < 执行任务完成时间！in allocateReadyWTaskToSaaSVm()");
		}
	}

	public List<WTask> completeExecutingTasks(List<SaaSVm> nextFinishVmSet)
	{
		List<WTask> finishedTaskSet = new ArrayList<WTask>();

		for(SaaSVm nextFinishVm: nextFinishVmSet)
		{
			WTask finishedTask = nextFinishVm.getExecutingWTask();

			if(nextFinishVm.getWaitingWTask().getBaseExecutionTime() != -1)
			{
				nextFinishVm.getExecutingWTask().setFinishFlag(true);
				nextFinishVm.setExecutingWTask(nextFinishVm.getWaitingWTask());
				nextFinishVm.setWaitingWTask(new WTask());
			}
			else
			{
				nextFinishVm.getExecutingWTask().setFinishFlag(true);
				nextFinishVm.setExecutingWTask(new WTask());
			}

			finishedTaskSet.add(finishedTask);
		}

		return finishedTaskSet;
	}

	public void turnOffVmSet(List<SaaSVm> turnOffVmSet, int turnOffVmTime)
	{
		for(SaaSVm turnOffVm: turnOffVmSet)
		{
			int workTime = turnOffVmTime - turnOffVm.getVmStartWorkTime();
			double cost = (workTime * turnOffVm.getVmPrice()) / StaticfinalTags.VmSlot;

			turnOffVm.setEndWorkTime(turnOffVmTime);
			turnOffVm.setTotalCost(cost);
			turnOffVm.setVmStatus(false);
			getActiveVmList().remove(turnOffVm);
			getOffVmList().add(turnOffVm);
		}
	}

	public void clearRuntimeState()
	{
		workflowList.clear();
		getOffVmList().clear();
		getActiveVmList().clear();
		globalTaskPool.clear();
	}
}
