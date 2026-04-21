package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RandomSchedulingPolicy implements HierarchicalSchedulingPolicy
{
	private final Random random;

	public RandomSchedulingPolicy(long seed)
	{
		this.random = new Random(seed);
	}

	public TaskSelection selectTask(TaskCandidateSet taskSet, SchedulingState state)
	{
		List<Integer> validIndices = new ArrayList<Integer>();
		for(int index = 0; index < taskSet.size(); index++)
		{
			TaskCandidateView candidate = taskSet.get(index);
			if(!candidate.getTask().getAllocatedFlag() && !candidate.getTask().getFinishFlag())
			{
				validIndices.add(Integer.valueOf(index));
			}
		}

		if(validIndices.isEmpty())
		{
			return new TaskSelection(0, taskSet.get(0));
		}

		int selectedIndex = validIndices.get(random.nextInt(validIndices.size())).intValue();
		return new TaskSelection(selectedIndex, taskSet.get(selectedIndex));
	}

	public ResourceSelection selectResource(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state)
	{
		List<Integer> validIndices = new ArrayList<Integer>();
		for(int index = 0; index < vmSet.size(); index++)
		{
			VmCandidateView candidate = vmSet.get(index);
			if(candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM)
			{
				if(candidate.getFeasibleUnderSubDeadline())
				{
					validIndices.add(Integer.valueOf(index));
				}
			}
			else
			{
				validIndices.add(Integer.valueOf(index));
			}
		}

		if(validIndices.isEmpty())
		{
			return new ResourceSelection(0, vmSet.get(0));
		}

		int selectedIndex = validIndices.get(random.nextInt(validIndices.size())).intValue();
		return new ResourceSelection(selectedIndex, vmSet.get(selectedIndex));
	}
}
