package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VmStateView
{
	private final int vmId;
	private final String vmType;
	private final double vmPrice;
	private final double vmFactor;
	private final int vmStartWorkTime;
	private final int endWorkTime;
	private final int realWorkTime;
	private final int idleTime;
	private final int finishTime;
	private final int realFinishTime;
	private final int readyTime;
	private final boolean vmStatus;
	private final double totalCost;
	private final double unitCost;
	private final double billingResidual;
	private final int slotRemaining;
	private final boolean idleNow;
	private final String executingTaskId;
	private final String waitingTaskId;
	private final List<String> completedTaskIds;

	public VmStateView(int vmId, String vmType, double vmPrice, double vmFactor, int vmStartWorkTime, int endWorkTime,
			int realWorkTime, int idleTime, int finishTime, int realFinishTime, int readyTime, boolean vmStatus,
			double totalCost, double unitCost, double billingResidual, int slotRemaining, boolean idleNow,
			String executingTaskId, String waitingTaskId, List<String> completedTaskIds)
	{
		this.vmId = vmId;
		this.vmType = vmType;
		this.vmPrice = vmPrice;
		this.vmFactor = vmFactor;
		this.vmStartWorkTime = vmStartWorkTime;
		this.endWorkTime = endWorkTime;
		this.realWorkTime = realWorkTime;
		this.idleTime = idleTime;
		this.finishTime = finishTime;
		this.realFinishTime = realFinishTime;
		this.readyTime = readyTime;
		this.vmStatus = vmStatus;
		this.totalCost = totalCost;
		this.unitCost = unitCost;
		this.billingResidual = billingResidual;
		this.slotRemaining = slotRemaining;
		this.idleNow = idleNow;
		this.executingTaskId = executingTaskId;
		this.waitingTaskId = waitingTaskId;
		this.completedTaskIds = Collections.unmodifiableList(new ArrayList<String>(completedTaskIds));
	}

	public int getVmId() { return vmId; }
	public String getVmType() { return vmType; }
	public double getVmPrice() { return vmPrice; }
	public double getVmFactor() { return vmFactor; }
	public int getVmStartWorkTime() { return vmStartWorkTime; }
	public int getEndWorkTime() { return endWorkTime; }
	public int getRealWorkTime() { return realWorkTime; }
	public int getIdleTime() { return idleTime; }
	public int getFinishTime() { return finishTime; }
	public int getRealFinishTime() { return realFinishTime; }
	public int getReadyTime() { return readyTime; }
	public boolean getVmStatus() { return vmStatus; }
	public double getTotalCost() { return totalCost; }
	public double getUnitCost() { return unitCost; }
	public double getBillingResidual() { return billingResidual; }
	public int getSlotRemaining() { return slotRemaining; }
	public boolean getIdleNow() { return idleNow; }
	public String getExecutingTaskId() { return executingTaskId; }
	public String getWaitingTaskId() { return waitingTaskId; }
	public List<String> getCompletedTaskIds() { return completedTaskIds; }
}
