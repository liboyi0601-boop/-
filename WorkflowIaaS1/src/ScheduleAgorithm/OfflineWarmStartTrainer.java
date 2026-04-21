package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class OfflineWarmStartTrainer
{
	private final int taskHiddenSize;
	private final int vmHiddenSize;
	private final int epochs;
	private final double learningRate;
	private final double l2;
	private final double epsilon;
	private final long seed;

	public OfflineWarmStartTrainer(int taskHiddenSize, int vmHiddenSize, int epochs,
			double learningRate, double l2, double epsilon, long seed)
	{
		this.taskHiddenSize = taskHiddenSize;
		this.vmHiddenSize = vmHiddenSize;
		this.epochs = epochs;
		this.learningRate = learningRate;
		this.l2 = l2;
		this.epsilon = epsilon;
		this.seed = seed;
	}

	public OfflineWarmStartResult train(HierarchicalReplayBuffer replayBuffer)
	{
		return train(replayBuffer, null);
	}

	public OfflineWarmStartResult train(HierarchicalReplayBuffer replayBuffer, EpochTrainingListener epochListener)
	{
		if(replayBuffer.getTaskExamples().isEmpty() || replayBuffer.getVmExamples().isEmpty())
		{
			throw new IllegalStateException("Replay buffer is empty");
		}

		SimpleTwoLayerScorer taskScorer = new SimpleTwoLayerScorer(
				replayBuffer.getTaskExamples().get(0).getFeatureSize(), taskHiddenSize, new Random(seed));
		SimpleTwoLayerScorer vmScorer = new SimpleTwoLayerScorer(
				replayBuffer.getVmExamples().get(0).getFeatureSize(), vmHiddenSize, new Random(seed + 1L));

		double lastTaskLoss = 0.0;
		double lastVmLoss = 0.0;
		for(int epoch = 0; epoch < epochs; epoch++)
		{
			double taskLossSum = 0.0;
			for(MaskedDecisionExample example: replayBuffer.getTaskExamples())
			{
				taskLossSum += taskScorer.trainOnExample(example, learningRate, l2);
			}

			double vmLossSum = 0.0;
			for(MaskedDecisionExample example: replayBuffer.getVmExamples())
			{
				vmLossSum += vmScorer.trainOnExample(example, learningRate, l2);
			}

			lastTaskLoss = taskLossSum / replayBuffer.getTaskExamples().size();
			lastVmLoss = vmLossSum / replayBuffer.getVmExamples().size();

			if(epochListener != null)
			{
				Map<String, Object> epochMetrics = new LinkedHashMap<String, Object>();
				epochMetrics.put("taskLoss", lastTaskLoss);
				epochMetrics.put("vmLoss", lastVmLoss);
				epochMetrics.put("taskChosenActionAccuracy", computeAccuracy(taskScorer, replayBuffer.getTaskExamples()));
				epochMetrics.put("vmChosenActionAccuracy", computeAccuracy(vmScorer, replayBuffer.getVmExamples()));
				epochMetrics.put("taskMaskHitRate", computeMaskHitRate(taskScorer, replayBuffer.getTaskExamples()));
				epochMetrics.put("vmMaskHitRate", computeMaskHitRate(vmScorer, replayBuffer.getVmExamples()));
				HierarchicalMaskedLearningPolicy currentPolicy = new HierarchicalMaskedLearningPolicy(
						taskScorer, vmScorer, epsilon, seed + 2L);
				epochListener.onEpoch(epoch, epochMetrics, currentPolicy, currentPolicy);
			}
		}

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.putAll(replayBuffer.toSummary());
		summary.put("epochs", epochs);
		summary.put("learningRate", learningRate);
		summary.put("l2", l2);
		summary.put("taskHiddenSize", taskHiddenSize);
		summary.put("vmHiddenSize", vmHiddenSize);
		summary.put("epsilon", epsilon);
		summary.put("seed", seed);
		summary.put("finalTaskLoss", lastTaskLoss);
		summary.put("finalVmLoss", lastVmLoss);

		return new OfflineWarmStartResult(
				new HierarchicalMaskedLearningPolicy(taskScorer, vmScorer, epsilon, seed + 2L),
				summary);
	}

	private double computeAccuracy(SimpleTwoLayerScorer scorer, java.util.List<MaskedDecisionExample> examples)
	{
		if(examples.isEmpty())
		{
			return 0.0;
		}

		int correctCount = 0;
		for(MaskedDecisionExample example: examples)
		{
			if(scorer.selectIndex(example) == example.getChosenIndex())
			{
				correctCount++;
			}
		}
		return (double)correctCount / examples.size();
	}

	private double computeMaskHitRate(SimpleTwoLayerScorer scorer, java.util.List<MaskedDecisionExample> examples)
	{
		if(examples.isEmpty())
		{
			return 0.0;
		}

		int validCount = 0;
		for(MaskedDecisionExample example: examples)
		{
			if(example.isValid(scorer.selectIndex(example)))
			{
				validCount++;
			}
		}
		return (double)validCount / examples.size();
	}
}
