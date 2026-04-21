package ScheduleAgorithm;

import java.util.Random;

public final class GraphAttentionHierarchicalPolicy implements HierarchicalSchedulingPolicy
{
	private final NosfBaselinePolicy compatibilityPolicy;
	private final GraphAttentionTaskNetwork taskNetwork;
	private final GraphAttentionVmNetwork vmNetwork;
	private final double epsilon;
	private final Random explorationRandom;

	public GraphAttentionHierarchicalPolicy(GraphAttentionTaskNetwork taskNetwork, GraphAttentionVmNetwork vmNetwork,
			double epsilon, long seed)
	{
		this.compatibilityPolicy = new NosfBaselinePolicy();
		this.taskNetwork = taskNetwork;
		this.vmNetwork = vmNetwork;
		this.epsilon = epsilon;
		this.explorationRandom = new Random(seed);
	}

	public TaskSelection selectTask(TaskCandidateSet taskSet, SchedulingState state)
	{
		if(state == null || taskSet.isEmpty())
		{
			return compatibilityPolicy.selectTask(taskSet, state);
		}

		boolean[] validMask = new boolean[taskSet.size()];
		for(int index = 0; index < taskSet.size(); index++)
		{
			TaskCandidateView candidate = taskSet.get(index);
			validMask[index] = !candidate.getTask().getAllocatedFlag() && !candidate.getTask().getFinishFlag();
		}

		int selectedIndex = taskNetwork.selectIndex(taskSet, state, validMask, epsilon, explorationRandom);
		return new TaskSelection(selectedIndex, taskSet.get(selectedIndex));
	}

	public ResourceSelection selectResource(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state)
	{
		if(state == null || vmSet.isEmpty())
		{
			return compatibilityPolicy.selectResource(selectedTask, vmSet, state);
		}

		boolean[] validMask = new boolean[vmSet.size()];
		for(int index = 0; index < vmSet.size(); index++)
		{
			VmCandidateView candidate = vmSet.get(index);
			validMask[index] = candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM
					? candidate.getFeasibleUnderSubDeadline() : true;
		}

		int selectedIndex = vmNetwork.selectIndex(selectedTask, vmSet, state, validMask, epsilon, explorationRandom);
		return new ResourceSelection(selectedIndex, vmSet.get(selectedIndex));
	}
}
