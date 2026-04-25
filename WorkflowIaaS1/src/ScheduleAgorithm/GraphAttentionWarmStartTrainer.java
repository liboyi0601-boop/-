package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphAttentionWarmStartTrainer
{
	private final int graphHiddenSize;
	private final int vmAttentionHiddenSize;
	private final int graphLayers;
	private final int epochs;
	private final double learningRate;
	private final double l2;
	private final double epsilon;
	private final long seed;

	public GraphAttentionWarmStartTrainer(int graphHiddenSize, int vmAttentionHiddenSize, int graphLayers,
			int epochs, double learningRate, double l2, double epsilon, long seed)
	{
		this.graphHiddenSize = graphHiddenSize;
		this.vmAttentionHiddenSize = vmAttentionHiddenSize;
		this.graphLayers = graphLayers;
		this.epochs = epochs;
		this.learningRate = learningRate;
		this.l2 = l2;
		this.epsilon = epsilon;
		this.seed = seed;
	}

	public GraphAttentionWarmStartResult train(ContextualHierarchicalReplayBuffer replayBuffer)
	{
		return train(replayBuffer, null);
	}

	public GraphAttentionWarmStartResult train(ContextualHierarchicalReplayBuffer replayBuffer,
			EpochTrainingListener epochListener)
	{
		if(replayBuffer.getTaskExamples().isEmpty() || replayBuffer.getVmExamples().isEmpty())
		{
			throw new IllegalStateException("Contextual replay buffer is empty");
		}

		GraphAttentionTaskNetwork taskNetwork = new GraphAttentionTaskNetwork(graphHiddenSize, graphLayers, seed);
		GraphAttentionVmNetwork vmNetwork = new GraphAttentionVmNetwork(graphHiddenSize, vmAttentionHiddenSize,
				graphLayers, seed + 1000L);

		double lastTaskLoss = 0.0;
		double lastVmLoss = 0.0;
		for(int epoch = 0; epoch < epochs; epoch++)
		{
			double taskLossSum = 0.0;
			for(TaskDecisionContextExample example: replayBuffer.getTaskExamples())
			{
				taskLossSum += taskNetwork.trainOnExample(example, learningRate, l2);
			}

			double vmLossSum = 0.0;
			for(VmDecisionContextExample example: replayBuffer.getVmExamples())
			{
				vmLossSum += vmNetwork.trainOnExample(example, learningRate, l2);
			}

			lastTaskLoss = taskLossSum / replayBuffer.getTaskExamples().size();
			lastVmLoss = vmLossSum / replayBuffer.getVmExamples().size();

			if(epochListener != null)
			{
				Map<String, Object> epochMetrics = new LinkedHashMap<String, Object>();
				epochMetrics.put("taskLoss", lastTaskLoss);
				epochMetrics.put("vmLoss", lastVmLoss);
				epochMetrics.put("taskChosenActionAccuracy",
						computeTaskAccuracy(taskNetwork, replayBuffer));
				epochMetrics.put("vmChosenActionAccuracy",
						computeVmAccuracy(vmNetwork, replayBuffer));
				epochMetrics.put("taskMaskHitRate",
						computeTaskMaskHitRate(taskNetwork, replayBuffer));
				epochMetrics.put("vmMaskHitRate",
						computeVmMaskHitRate(vmNetwork, replayBuffer));
				epochListener.onEpoch(epoch, epochMetrics, null,
						new GraphAttentionHierarchicalPolicy(taskNetwork, vmNetwork, epsilon, seed + 2000L));
			}
		}

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.putAll(replayBuffer.toSummary());
		summary.put("epochs", epochs);
		summary.put("learningRate", learningRate);
		summary.put("l2", l2);
		summary.put("graphHiddenSize", graphHiddenSize);
		summary.put("vmAttentionHiddenSize", vmAttentionHiddenSize);
		summary.put("graphLayers", graphLayers);
		summary.put("attentionHeads", 1);
		summary.put("epsilon", epsilon);
		summary.put("seed", seed);
		summary.put("finalTaskLoss", lastTaskLoss);
		summary.put("finalVmLoss", lastVmLoss);

		GraphAttentionHierarchicalPolicy policy = new GraphAttentionHierarchicalPolicy(
				taskNetwork, vmNetwork, epsilon, seed + 2000L);
		return new GraphAttentionWarmStartResult(policy, summary);
	}

	private double computeTaskAccuracy(GraphAttentionTaskNetwork taskNetwork,
			ContextualHierarchicalReplayBuffer replayBuffer)
	{
		int correctCount = 0;
		for(TaskDecisionContextExample example: replayBuffer.getTaskExamples())
		{
			if(taskNetwork.selectIndex(example) == example.getChosenTaskIndex())
			{
				correctCount++;
			}
		}
		return (double)correctCount / replayBuffer.getTaskExamples().size();
	}

	private double computeVmAccuracy(GraphAttentionVmNetwork vmNetwork,
			ContextualHierarchicalReplayBuffer replayBuffer)
	{
		int correctCount = 0;
		for(VmDecisionContextExample example: replayBuffer.getVmExamples())
		{
			if(vmNetwork.selectIndex(example) == example.getChosenVmIndex())
			{
				correctCount++;
			}
		}
		return (double)correctCount / replayBuffer.getVmExamples().size();
	}

	private double computeTaskMaskHitRate(GraphAttentionTaskNetwork taskNetwork,
			ContextualHierarchicalReplayBuffer replayBuffer)
	{
		int validCount = 0;
		for(TaskDecisionContextExample example: replayBuffer.getTaskExamples())
		{
			if(example.getTaskMask().isValid(taskNetwork.selectIndex(example)))
			{
				validCount++;
			}
		}
		return (double)validCount / replayBuffer.getTaskExamples().size();
	}

	private double computeVmMaskHitRate(GraphAttentionVmNetwork vmNetwork,
			ContextualHierarchicalReplayBuffer replayBuffer)
	{
		int validCount = 0;
		for(VmDecisionContextExample example: replayBuffer.getVmExamples())
		{
			if(example.getVmMask().isValid(vmNetwork.selectIndex(example)))
			{
				validCount++;
			}
		}
		return (double)validCount / replayBuffer.getVmExamples().size();
	}
}
