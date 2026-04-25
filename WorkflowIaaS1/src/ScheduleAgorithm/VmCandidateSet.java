package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VmCandidateSet
{
	private final List<VmCandidateView> candidates;

	public VmCandidateSet(List<VmCandidateView> candidates)
	{
		this.candidates = Collections.unmodifiableList(new ArrayList<VmCandidateView>(candidates));
	}

	public List<VmCandidateView> getCandidates()
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

	public VmCandidateView get(int index)
	{
		return candidates.get(index);
	}
}
