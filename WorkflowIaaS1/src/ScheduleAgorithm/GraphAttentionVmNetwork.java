package ScheduleAgorithm;

import java.util.Random;

public final class GraphAttentionVmNetwork
{
	private final WorkflowGraphContextBuilder contextBuilder;
	private final WorkflowGraphEncoder graphEncoder;
	private final VmCandidateFeatureProjector candidateProjector;
	private final VmAttentionEncoder attentionEncoder;
	private final VmAttentionPolicyHead policyHead;

	public GraphAttentionVmNetwork(int graphHiddenSize, int vmHiddenSize, int graphLayers, long seed)
	{
		TaskNodeFeatureProjector taskProjector = new TaskNodeFeatureProjector();
		this.contextBuilder = new WorkflowGraphContextBuilder(taskProjector);
		this.graphEncoder = new WorkflowGraphEncoder(TaskNodeFeatureProjector.INPUT_SIZE, graphHiddenSize,
				graphLayers, seed);
		this.candidateProjector = new VmCandidateFeatureProjector();
		this.attentionEncoder = new VmAttentionEncoder(VmCandidateFeatureProjector.INPUT_SIZE,
				graphHiddenSize * 2, vmHiddenSize, seed + 1L);
		this.policyHead = new VmAttentionPolicyHead(vmHiddenSize, seed + 2L);
	}

	public double trainOnExample(VmDecisionContextExample example, double learningRate, double l2)
	{
		VmForwardPass forwardPass = forward(example.getSelectedTask(), example.getVmSet(), example.getState());
		return policyHead.train(forwardPass.candidateEmbeddings, forwardPass.queryEmbedding,
				example.getVmMask().getValidSelections(), example.getChosenVmIndex(), learningRate, l2);
	}

	public int selectIndex(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state,
			boolean[] validMask, double epsilon, Random random)
	{
		VmForwardPass forwardPass = forward(selectedTask, vmSet, state);
		if(hasNoValidIndex(validMask))
		{
			return 0;
		}

		if(epsilon > 0.0 && random.nextDouble() < epsilon)
		{
			return selectRandom(validMask, random);
		}

		int bestIndex = 0;
		double bestLogit = Double.NEGATIVE_INFINITY;
		for(int index = 0; index < forwardPass.logits.length; index++)
		{
			if(!validMask[index])
			{
				continue;
			}
			if(forwardPass.logits[index] > bestLogit)
			{
				bestLogit = forwardPass.logits[index];
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private VmForwardPass forward(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state)
	{
		WorkflowGraphEncoding graphEncoding = graphEncoder.encode(
				contextBuilder.build(state, selectedTask.getWorkflowId()));
		double[] selectedTaskEmbedding = graphEncoding.getTaskEmbedding(selectedTask.getTaskId());
		double[] pooledEmbedding = graphEncoding.getPooledEmbedding();
		VmAttentionEncoding attentionEncoding = attentionEncoder.encode(vmSet,
				graphEncoding.getContext().getWorkflowState(), state,
				selectedTaskEmbedding, pooledEmbedding, candidateProjector);

		double[][] candidateEmbeddings = new double[vmSet.size()][];
		double[] logits = new double[vmSet.size()];
		for(int index = 0; index < vmSet.size(); index++)
		{
			candidateEmbeddings[index] = attentionEncoding.getCandidateEmbedding(index);
			logits[index] = policyHead.score(candidateEmbeddings[index], attentionEncoding.getQueryEmbedding());
		}

		return new VmForwardPass(candidateEmbeddings, attentionEncoding.getQueryEmbedding(), logits);
	}

	private boolean hasNoValidIndex(boolean[] validMask)
	{
		for(boolean valid: validMask)
		{
			if(valid)
			{
				return false;
			}
		}
		return true;
	}

	private int selectRandom(boolean[] validMask, Random random)
	{
		int validCount = 0;
		for(boolean valid: validMask)
		{
			if(valid)
			{
				validCount++;
			}
		}

		int selected = random.nextInt(validCount);
		for(int index = 0; index < validMask.length; index++)
		{
			if(!validMask[index])
			{
				continue;
			}
			if(selected == 0)
			{
				return index;
			}
			selected--;
		}
		return 0;
	}

	private static final class VmForwardPass
	{
		private final double[][] candidateEmbeddings;
		private final double[] queryEmbedding;
		private final double[] logits;

		private VmForwardPass(double[][] candidateEmbeddings, double[] queryEmbedding, double[] logits)
		{
			this.candidateEmbeddings = candidateEmbeddings;
			this.queryEmbedding = queryEmbedding;
			this.logits = logits;
		}
	}
}
