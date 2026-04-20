package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;

import workflow.WTask;

public final class CandidateTaskSetBuilder
{
	public TaskCandidateSet build(List<WTask> readyTasks, SchedulingState state)
	{
		List<TaskCandidateView> candidates = new ArrayList<TaskCandidateView>();
		int candidateIndex = 0;

		for(WTask task: readyTasks)
		{
			if(task.getAllocatedFlag() || task.getFinishFlag())
			{
				continue;
			}
			candidates.add(new TaskCandidateView(candidateIndex, task));
			candidateIndex++;
		}

		return new TaskCandidateSet(candidates);
	}
}
