package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;

public final class ActionMaskBuilder
{
	public TaskActionMask buildTaskMask(TaskCandidateSet taskSet)
	{
		List<Boolean> validSelections = new ArrayList<Boolean>();
		for(TaskCandidateView candidate: taskSet.getCandidates())
		{
			validSelections.add(Boolean.valueOf(!candidate.getTask().getAllocatedFlag()
					&& !candidate.getTask().getFinishFlag()));
		}
		return new TaskActionMask(validSelections);
	}

	public VmActionMask buildVmMask(VmCandidateSet vmSet)
	{
		List<Boolean> validSelections = new ArrayList<Boolean>();
		for(VmCandidateView candidate: vmSet.getCandidates())
		{
			if(candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM)
			{
				validSelections.add(Boolean.valueOf(candidate.getFeasibleUnderSubDeadline()));
			}
			else
			{
				validSelections.add(Boolean.TRUE);
			}
		}
		return new VmActionMask(validSelections);
	}
}
