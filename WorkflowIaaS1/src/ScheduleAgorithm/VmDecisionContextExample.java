package ScheduleAgorithm;

public final class VmDecisionContextExample
{
	private final SchedulingState state;
	private final TaskCandidateView selectedTask;
	private final VmCandidateSet vmSet;
	private final VmActionMask vmMask;
	private final int chosenVmIndex;

	public VmDecisionContextExample(SchedulingState state, TaskCandidateView selectedTask, VmCandidateSet vmSet,
			VmActionMask vmMask, int chosenVmIndex)
	{
		this.state = state;
		this.selectedTask = selectedTask;
		this.vmSet = vmSet;
		this.vmMask = vmMask;
		this.chosenVmIndex = chosenVmIndex;
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
}
