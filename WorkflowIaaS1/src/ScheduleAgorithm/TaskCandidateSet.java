package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskCandidateSet
{
	private final List<TaskCandidateView> candidates;

	public TaskCandidateSet(List<TaskCandidateView> candidates)
	{
		this.candidates = Collections.unmodifiableList(new ArrayList<TaskCandidateView>(candidates));
	}

	public List<TaskCandidateView> getCandidates()
	{
		return candidates;
	}

	public boolean isEmpty()
	{
		return candidates.isEmpty();
	}

	public int size()
	{
		return candidates.size();
	}

	public TaskCandidateView get(int index)
	{
		return candidates.get(index);
	}
}
