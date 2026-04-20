package ScheduleAgorithm;

import workflow.WTask;

public final class ResourceSelection
{
	private final int selectedIndex;
	private final VmCandidateView selectedCandidate;

	public ResourceSelection(int selectedIndex, VmCandidateView selectedCandidate)
	{
		this.selectedIndex = selectedIndex;
		this.selectedCandidate = selectedCandidate;
	}

	public int getSelectedIndex()
	{
		return selectedIndex;
	}

	public VmCandidateView getSelectedCandidate()
	{
		return selectedCandidate;
	}

	public SchedulingAction toSchedulingAction(WTask task)
	{
		if(selectedCandidate.getCandidateKind() == VmCandidateKind.EXISTING_VM)
		{
			return SchedulingAction.allocateToExistingVm(task, selectedCandidate.getTargetVm(),
					selectedCandidate.getRealDataArrival());
		}

		return SchedulingAction.leaseNewVmAndAllocate(task, selectedCandidate.getEstimatedReadyStartTime(),
				selectedCandidate.getNewVmType());
	}
}
