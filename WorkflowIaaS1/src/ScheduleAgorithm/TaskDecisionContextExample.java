package ScheduleAgorithm;

public final class TaskDecisionContextExample
{
	private final SchedulingState state;
	private final TaskCandidateSet taskSet;
	private final TaskActionMask taskMask;
	private final int chosenTaskIndex;

	public TaskDecisionContextExample(SchedulingState state, TaskCandidateSet taskSet, TaskActionMask taskMask,
			int chosenTaskIndex)
	{
		this.state = state;
		this.taskSet = taskSet;
		this.taskMask = taskMask;
		this.chosenTaskIndex = chosenTaskIndex;
	}

	public SchedulingState getState()
	{
		return state;
	}

	public TaskCandidateSet getTaskSet()
	{
		return taskSet;
	}

	public TaskActionMask getTaskMask()
	{
		return taskMask;
	}

	public int getChosenTaskIndex()
	{
		return chosenTaskIndex;
	}
}
