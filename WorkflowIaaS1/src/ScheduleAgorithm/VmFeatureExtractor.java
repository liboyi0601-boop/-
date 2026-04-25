package ScheduleAgorithm;

import share.StaticfinalTags;
import vmInfo.VmResource.VmParameter;

public final class VmFeatureExtractor
{
	public static final int INPUT_SIZE = 19;
	public static final int IDX_ESTIMATED_FINISH_IF_ASSIGNED = 6;
	public static final int IDX_FEASIBLE_UNDER_SUB_DEADLINE = 9;
	public static final int IDX_SELECTED_TASK_SUB_DEADLINE = 11;
	public static final int IDX_CRITICAL_PATH_SLACK = 12;
	public static final int IDX_WORKFLOW_NORMALIZED_SLACK = 13;
	public static final int IDX_VIOLATION_RISK = 14;

	public double[] extract(TaskCandidateView selectedTask, VmCandidateView candidate, SchedulingState state)
	{
		double[] features = new double[INPUT_SIZE];
		TaskStateView taskState = state == null ? null : state.findTaskState(selectedTask.getTaskId());
		WorkflowStateView workflowState = state == null ? null : state.findWorkflowState(selectedTask.getWorkflowId());
		VmStateView vmState = null;
		if(state != null && candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM)
		{
			vmState = state.findActiveVmState(candidate.getExistingVmId());
			if(vmState == null)
			{
				vmState = state.findOffVmState(candidate.getExistingVmId());
			}
		}
		double workflowWindow = workflowState == null
				? 1.0
				: Math.max(1.0, workflowState.getDeadline() - workflowState.getArrivalTime());

		double unitPrice = extractUnitPrice(candidate);
		double vmFactor = extractVmFactor(candidate);

		features[0] = 1.0;
		features[1] = candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM ? 1.0 : 0.0;
		features[2] = candidate.getCandidateKind() == VmCandidateKind.LEASE_NEW_VM_TYPE ? 1.0 : 0.0;
		features[3] = normalizeTime(state == null ? 0 : state.getCurrentTime(), workflowState, workflowWindow);
		features[4] = normalizeTime(resolveDataArrival(candidate), workflowState, workflowWindow);
		features[5] = normalizeTime(candidate.getEstimatedReadyStartTime(), workflowState, workflowWindow);
		features[IDX_ESTIMATED_FINISH_IF_ASSIGNED] = normalizeTime(candidate.getEstimatedFinishTimeIfAssigned(), workflowState, workflowWindow);
		features[7] = scale(candidate.getEstimatedCostIfAssigned(), 1000.0);
		features[8] = scale(candidate.getIdleGapFitScore(), StaticfinalTags.VmSlot);
		features[IDX_FEASIBLE_UNDER_SUB_DEADLINE] = candidate.getFeasibleUnderSubDeadline() ? 1.0 : 0.0;
		features[10] = normalizeTime(selectedTask.getEarliestFinishTime(), workflowState, workflowWindow);
		features[IDX_SELECTED_TASK_SUB_DEADLINE] = normalizeTime(selectedTask.getSubDeadline(), workflowState, workflowWindow);
		features[IDX_CRITICAL_PATH_SLACK] = normalizeTime(taskState == null ? 0 : taskState.getCriticalPathSlack(), workflowState, workflowWindow);
		features[IDX_WORKFLOW_NORMALIZED_SLACK] = workflowState == null ? 0.0 : workflowState.getNormalizedSlack();
		features[IDX_VIOLATION_RISK] = workflowState == null ? 0.0 : cap(workflowState.getViolationRiskScore(), 10.0) / 10.0;
		features[15] = state == null ? 0.0 : scale(state.getActiveVmStates().size(), 100.0);
		features[16] = unitPrice / 10.0;
		features[17] = vmFactor / 10.0;
		features[18] = vmState == null ? 0.0 : scale(vmState.getBillingResidual(), 10.0);
		return features;
	}

	public int getInputSize()
	{
		return INPUT_SIZE;
	}

	private int resolveDataArrival(VmCandidateView candidate)
	{
		if(candidate.getRealDataArrival() != -1)
		{
			return candidate.getRealDataArrival();
		}
		return candidate.getEstimatedReadyStartTime();
	}

	private double extractUnitPrice(VmCandidateView candidate)
	{
		if(candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM && candidate.getTargetVm() != null)
		{
			return candidate.getTargetVm().getVmPrice();
		}
		return VmParameter.valueOf(candidate.getNewVmType()).getPrice();
	}

	private double extractVmFactor(VmCandidateView candidate)
	{
		if(candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM && candidate.getTargetVm() != null)
		{
			return candidate.getTargetVm().getVmFactor();
		}
		return VmParameter.valueOf(candidate.getNewVmType()).getFactor();
	}

	private double normalizeTime(int value, WorkflowStateView workflowState, double workflowWindow)
	{
		if(workflowState == null)
		{
			return scale(value, 10000.0);
		}
		return (value - workflowState.getArrivalTime()) / workflowWindow;
	}

	private double scale(double value, double denominator)
	{
		return value / Math.max(1.0, denominator);
	}

	private double cap(double value, double limit)
	{
		if(value > limit)
		{
			return limit;
		}
		if(value < -limit)
		{
			return -limit;
		}
		return value;
	}
}
