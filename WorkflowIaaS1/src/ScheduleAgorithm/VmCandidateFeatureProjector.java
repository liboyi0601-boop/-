package ScheduleAgorithm;

import share.StaticfinalTags;
import vmInfo.VmResource.VmParameter;

public final class VmCandidateFeatureProjector
{
	public static final int INPUT_SIZE = 17;

	public double[] extract(VmCandidateView candidate, WorkflowStateView workflowState, SchedulingState state)
	{
		double[] features = new double[INPUT_SIZE];
		double workflowWindow = Math.max(1.0, workflowState.getDeadline() - workflowState.getArrivalTime());
		VmStateView vmState = resolveVmState(candidate, state);
		double unitPrice = extractUnitPrice(candidate);
		double vmFactor = extractVmFactor(candidate);

		features[0] = 1.0;
		features[1] = candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM ? 1.0 : 0.0;
		features[2] = candidate.getCandidateKind() == VmCandidateKind.LEASE_NEW_VM_TYPE ? 1.0 : 0.0;
		features[3] = normalizeTime(resolveDataArrival(candidate), workflowState, workflowWindow);
		features[4] = normalizeTime(candidate.getEstimatedReadyStartTime(), workflowState, workflowWindow);
		features[5] = normalizeTime(candidate.getEstimatedFinishTimeIfAssigned(), workflowState, workflowWindow);
		features[6] = scale(candidate.getEstimatedCostIfAssigned(), 1000.0);
		features[7] = scale(candidate.getIdleGapFitScore(), StaticfinalTags.VmSlot);
		features[8] = candidate.getFeasibleUnderSubDeadline() ? 1.0 : 0.0;
		features[9] = unitPrice / 10.0;
		features[10] = vmFactor / 10.0;
		features[11] = vmState == null ? 0.0 : scale(vmState.getBillingResidual(), 10.0);
		features[12] = vmState == null ? 0.0 : scale(vmState.getSlotRemaining(), StaticfinalTags.VmSlot);
		features[13] = vmState != null && vmState.getIdleNow() ? 1.0 : 0.0;
		features[14] = scale(state.getActiveVmStates().size(), 100.0);
		features[15] = scale(state.getOffVmStates().size(), 100.0);
		features[16] = candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM
				? scale(candidate.getExistingVmId(), 100.0)
				: scale(candidate.getNewVmType(), 10.0);
		return features;
	}

	private VmStateView resolveVmState(VmCandidateView candidate, SchedulingState state)
	{
		if(candidate.getCandidateKind() != VmCandidateKind.EXISTING_VM)
		{
			return null;
		}

		VmStateView vmState = state.findActiveVmState(candidate.getExistingVmId());
		if(vmState == null)
		{
			vmState = state.findOffVmState(candidate.getExistingVmId());
		}
		return vmState;
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
		return (value - workflowState.getArrivalTime()) / workflowWindow;
	}

	private double scale(double value, double denominator)
	{
		return value / Math.max(1.0, denominator);
	}
}
