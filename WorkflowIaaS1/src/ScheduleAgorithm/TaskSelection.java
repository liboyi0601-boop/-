package ScheduleAgorithm;

public final class TaskSelection
{
	private final int selectedIndex;
	private final TaskCandidateView selectedCandidate;

	public TaskSelection(int selectedIndex, TaskCandidateView selectedCandidate)
	{
		this.selectedIndex = selectedIndex;
		this.selectedCandidate = selectedCandidate;
	}

	public int getSelectedIndex()
	{
		return selectedIndex;
	}

	public TaskCandidateView getSelectedCandidate()
	{
		return selectedCandidate;
	}
}
