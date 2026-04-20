package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import vmInfo.SaaSVm;
import workflow.WTask;

public final class HierarchicalMaskedLearningPolicy implements SchedulingPolicy, HierarchicalSchedulingPolicy
{
	private final NosfBaselinePolicy compatibilityPolicy;
	private final TaskFeatureExtractor taskFeatureExtractor;
	private final VmFeatureExtractor vmFeatureExtractor;
	private final SimpleTwoLayerScorer taskScorer;
	private final SimpleTwoLayerScorer vmScorer;
	private final Random explorationRandom;
	private final double epsilon;

	public HierarchicalMaskedLearningPolicy(SimpleTwoLayerScorer taskScorer, SimpleTwoLayerScorer vmScorer,
			double epsilon, long seed)
	{
		this.compatibilityPolicy = new NosfBaselinePolicy();
		this.taskFeatureExtractor = new TaskFeatureExtractor();
		this.vmFeatureExtractor = new VmFeatureExtractor();
		this.taskScorer = taskScorer;
		this.vmScorer = vmScorer;
		this.epsilon = epsilon;
		this.explorationRandom = new Random(seed);
	}

	public SchedulingAction selectAction(WTask task, List<SaaSVm> vmList)
	{
		return compatibilityPolicy.selectAction(task, vmList);
	}

	public TaskSelection selectTask(TaskCandidateSet taskSet, SchedulingState state)
	{
		if(state == null || taskSet.isEmpty())
		{
			return compatibilityPolicy.selectTask(taskSet, state);
		}

		boolean[] validMask = buildTaskMask(taskSet);
		int selectedIndex = selectIndex(buildTaskFeatures(taskSet, state), validMask, taskScorer);
		return new TaskSelection(selectedIndex, taskSet.get(selectedIndex));
	}

	public ResourceSelection selectResource(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state)
	{
		if(state == null || vmSet.isEmpty())
		{
			return compatibilityPolicy.selectResource(selectedTask, vmSet, state);
		}

		boolean[] validMask = buildVmMask(vmSet);
		int selectedIndex = selectIndex(buildVmFeatures(selectedTask, vmSet, state), validMask, vmScorer);
		return new ResourceSelection(selectedIndex, vmSet.get(selectedIndex));
	}

	private List<double[]> buildTaskFeatures(TaskCandidateSet taskSet, SchedulingState state)
	{
		List<double[]> features = new ArrayList<double[]>();
		for(TaskCandidateView candidate: taskSet.getCandidates())
		{
			features.add(taskFeatureExtractor.extract(candidate, state));
		}
		return features;
	}

	private List<double[]> buildVmFeatures(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state)
	{
		List<double[]> features = new ArrayList<double[]>();
		for(VmCandidateView candidate: vmSet.getCandidates())
		{
			features.add(vmFeatureExtractor.extract(selectedTask, candidate, state));
		}
		return features;
	}

	private boolean[] buildTaskMask(TaskCandidateSet taskSet)
	{
		boolean[] validMask = new boolean[taskSet.size()];
		for(int index = 0; index < taskSet.size(); index++)
		{
			TaskCandidateView candidate = taskSet.get(index);
			validMask[index] = !candidate.getTask().getAllocatedFlag() && !candidate.getTask().getFinishFlag();
		}
		return validMask;
	}

	private boolean[] buildVmMask(VmCandidateSet vmSet)
	{
		boolean[] validMask = new boolean[vmSet.size()];
		for(int index = 0; index < vmSet.size(); index++)
		{
			VmCandidateView candidate = vmSet.get(index);
			if(candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM)
			{
				validMask[index] = candidate.getFeasibleUnderSubDeadline();
			}
			else
			{
				validMask[index] = true;
			}
		}
		return validMask;
	}

	private int selectIndex(List<double[]> features, boolean[] validMask, SimpleTwoLayerScorer scorer)
	{
		List<Integer> validIndices = new ArrayList<Integer>();
		for(int index = 0; index < validMask.length; index++)
		{
			if(validMask[index])
			{
				validIndices.add(Integer.valueOf(index));
			}
		}

		if(validIndices.isEmpty())
		{
			return 0;
		}

		if(epsilon > 0.0 && explorationRandom.nextDouble() < epsilon)
		{
			return validIndices.get(explorationRandom.nextInt(validIndices.size())).intValue();
		}

		int bestIndex = validIndices.get(0).intValue();
		double bestScore = Double.NEGATIVE_INFINITY;
		for(Integer validIndex: validIndices)
		{
			double score = scorer.score(features.get(validIndex.intValue()));
			if(score > bestScore)
			{
				bestScore = score;
				bestIndex = validIndex.intValue();
			}
		}
		return bestIndex;
	}
}
