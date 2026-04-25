package ScheduleAgorithm;

public final class VmDecisionContextExample
{
	private final SchedulingState state;
	private final TaskCandidateView selectedTask;
	private final VmCandidateSet vmSet;
	private final VmActionMask vmMask;
	private final int chosenVmIndex;
	private final ReplayExampleOrigin origin;

	public VmDecisionContextExample(SchedulingState state, TaskCandidateView selectedTask, VmCandidateSet vmSet,
			VmActionMask vmMask, int chosenVmIndex)
	{
		this(state, selectedTask, vmSet, vmMask, chosenVmIndex, null);
	}

	public VmDecisionContextExample(SchedulingState state, TaskCandidateView selectedTask, VmCandidateSet vmSet,
			VmActionMask vmMask, int chosenVmIndex, ReplayExampleOrigin origin)
	{
		this.state = state;
		this.selectedTask = selectedTask;
		this.vmSet = vmSet;
		this.vmMask = vmMask;
		this.chosenVmIndex = chosenVmIndex;
		this.origin = origin;
	}

	public SchedulingState getState()
	{
		return state;
	}

	public TaskCandidateView getSelectedTask()
	{
		return selectedTask;
	}

	public VmCandidateSet getVmSet()
	{
		return vmSet;
	}

	public VmActionMask getVmMask()
	{
		return vmMask;
	}

	public int getChosenVmIndex()
	{
		return chosenVmIndex;
	}

	public ReplayExampleOrigin getOrigin()
	{
		return origin;
	}
}
