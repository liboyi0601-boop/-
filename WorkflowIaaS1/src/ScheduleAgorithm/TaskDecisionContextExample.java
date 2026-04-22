package ScheduleAgorithm;

public final class TaskDecisionContextExample
{
	private final SchedulingState state;
	private final TaskCandidateSet taskSet;
	private final TaskActionMask taskMask;
	private final int chosenTaskIndex;
	private final ReplayExampleOrigin origin;

	public TaskDecisionContextExample(SchedulingState state, TaskCandidateSet taskSet, TaskActionMask taskMask,
			int chosenTaskIndex)
	{
		this(state, taskSet, taskMask, chosenTaskIndex, null);
	}

	public TaskDecisionContextExample(SchedulingState state, TaskCandidateSet taskSet, TaskActionMask taskMask,
			int chosenTaskIndex, ReplayExampleOrigin origin)
	{
		this.state = state;
		this.taskSet = taskSet;
		this.taskMask = taskMask;
		this.chosenTaskIndex = chosenTaskIndex;
		this.origin = origin;
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

	public ReplayExampleOrigin getOrigin()
	{
		return origin;
	}
}
