package ScheduleAgorithm;

import vmInfo.SaaSVm;

public final class VmCandidateView
{
	private final int candidateIndex;
	private final VmCandidateKind candidateKind;
	private final SaaSVm targetVm;
	private final int existingVmId;
	private final String existingVmType;
	private final int newVmType;
	private final int realDataArrival;
	private final int estimatedReadyStartTime;
	private final int estimatedFinishTimeIfAssigned;
	private final double estimatedCostIfAssigned;
	private final double idleGapFitScore;
	private final boolean feasibleUnderSubDeadline;

	private VmCandidateView(int candidateIndex, VmCandidateKind candidateKind, SaaSVm targetVm,
			int existingVmId, String existingVmType, int newVmType, int realDataArrival,
			int estimatedReadyStartTime, int estimatedFinishTimeIfAssigned, double estimatedCostIfAssigned,
			double idleGapFitScore, boolean feasibleUnderSubDeadline)
	{
		this.candidateIndex = candidateIndex;
		this.candidateKind = candidateKind;
		this.targetVm = targetVm;
		this.existingVmId = existingVmId;
		this.existingVmType = existingVmType;
		this.newVmType = newVmType;
		this.realDataArrival = realDataArrival;
		this.estimatedReadyStartTime = estimatedReadyStartTime;
		this.estimatedFinishTimeIfAssigned = estimatedFinishTimeIfAssigned;
		this.estimatedCostIfAssigned = estimatedCostIfAssigned;
		this.idleGapFitScore = idleGapFitScore;
		this.feasibleUnderSubDeadline = feasibleUnderSubDeadline;
	}

	public static VmCandidateView forExistingVm(int candidateIndex, SaaSVm targetVm, int realDataArrival,
			int estimatedReadyStartTime, int estimatedFinishTimeIfAssigned, double estimatedCostIfAssigned,
			double idleGapFitScore, boolean feasibleUnderSubDeadline)
	{
		return new VmCandidateView(candidateIndex, VmCandidateKind.EXISTING_VM, targetVm,
				targetVm.getVmID(), targetVm.getVmType(), -1, realDataArrival, estimatedReadyStartTime,
				estimatedFinishTimeIfAssigned, estimatedCostIfAssigned, idleGapFitScore, feasibleUnderSubDeadline);
	}

	public static VmCandidateView forNewVmType(int candidateIndex, int newVmType, int estimatedReadyStartTime,
			int estimatedFinishTimeIfAssigned, double estimatedCostIfAssigned, double idleGapFitScore,
			boolean feasibleUnderSubDeadline)
	{
		return new VmCandidateView(candidateIndex, VmCandidateKind.LEASE_NEW_VM_TYPE, null,
				-1, null, newVmType, -1, estimatedReadyStartTime, estimatedFinishTimeIfAssigned,
				estimatedCostIfAssigned, idleGapFitScore, feasibleUnderSubDeadline);
	}

	public int getCandidateIndex()
	{
		return candidateIndex;
	}

	public VmCandidateKind getCandidateKind()
	{
		return candidateKind;
	}

	public SaaSVm getTargetVm()
	{
		return targetVm;
	}

	public int getExistingVmId()
	{
		return existingVmId;
	}

	public String getExistingVmType()
	{
		return existingVmType;
	}

	public int getNewVmType()
	{
		return newVmType;
	}

	public int getRealDataArrival()
	{
		return realDataArrival;
	}

	public int getEstimatedReadyStartTime()
	{
		return estimatedReadyStartTime;
	}

	public int getEstimatedFinishTimeIfAssigned()
	{
		return estimatedFinishTimeIfAssigned;
	}

	public double getEstimatedCostIfAssigned()
	{
		return estimatedCostIfAssigned;
	}

	public double getIdleGapFitScore()
	{
		return idleGapFitScore;
	}

	public boolean getFeasibleUnderSubDeadline()
	{
		return feasibleUnderSubDeadline;
	}
}
