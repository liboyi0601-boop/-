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
	private final ConstraintAwareWeightingConfig weightingConfig;

	public OfflineWarmStartTrainer(int taskHiddenSize, int vmHiddenSize, int epochs,
			double learningRate, double l2, double epsilon, long seed)
	{
		this(taskHiddenSize, vmHiddenSize, epochs, learningRate, l2, epsilon, seed,
				ConstraintAwareWeightingConfig.disabled());
	}

	public OfflineWarmStartTrainer(int taskHiddenSize, int vmHiddenSize, int epochs,
			double learningRate, double l2, double epsilon, long seed,
			ConstraintAwareWeightingConfig weightingConfig)
	{
		this.taskHiddenSize = taskHiddenSize;
		this.vmHiddenSize = vmHiddenSize;
		this.epochs = epochs;
		this.learningRate = learningRate;
		this.l2 = l2;
		this.epsilon = epsilon;
		this.seed = seed;
		this.weightingConfig = weightingConfig == null
				? ConstraintAwareWeightingConfig.disabled()
				: weightingConfig;
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
		ConstraintAwareSampleWeighter sampleWeighter = new ConstraintAwareSampleWeighter(weightingConfig);

		double lastTaskLoss = 0.0;
		double lastVmLoss = 0.0;
		EpochWeightStats lastWeightStats = null;
		for(int epoch = 0; epoch < epochs; epoch++)
		{
			double taskLossSum = 0.0;
			EpochWeightStats weightStats = weightingConfig.isWeightedLossEnabled()
					? new EpochWeightStats(weightingConfig.getMaxSampleWeight())
					: null;
			for(MaskedDecisionExample example: replayBuffer.getTaskExamples())
			{
				if(weightStats == null)
				{
					taskLossSum += taskScorer.trainOnExample(example, learningRate, l2);
				}
				else
				{
					ConstraintAwareSampleWeighter.WeightResult weightResult = sampleWeighter.weightTask(example);
					double unweightedLoss = taskScorer.computeLoss(example);
					double weightedLoss = taskScorer.trainOnExample(
							example, learningRate, l2, weightResult.getSampleWeight());
					weightStats.recordTask(weightResult, unweightedLoss, weightedLoss);
				}
			}

			double vmLossSum = 0.0;
			for(MaskedDecisionExample example: replayBuffer.getVmExamples())
			{
				if(weightStats == null)
				{
					vmLossSum += vmScorer.trainOnExample(example, learningRate, l2);
				}
				else
				{
					ConstraintAwareSampleWeighter.WeightResult weightResult = sampleWeighter.weightVm(example);
					double unweightedLoss = vmScorer.computeLoss(example);
					double weightedLoss = vmScorer.trainOnExample(
							example, learningRate, l2, weightResult.getSampleWeight());
					weightStats.recordVm(weightResult, unweightedLoss, weightedLoss);
				}
			}

			if(weightStats == null)
			{
				lastTaskLoss = taskLossSum / replayBuffer.getTaskExamples().size();
				lastVmLoss = vmLossSum / replayBuffer.getVmExamples().size();
			}
			else
			{
				lastTaskLoss = weightStats.averageTaskUnweightedLoss();
				lastVmLoss = weightStats.averageVmUnweightedLoss();
				lastWeightStats = weightStats;
			}

			if(epochListener != null)
			{
				Map<String, Object> epochMetrics = new LinkedHashMap<String, Object>();
				epochMetrics.put("taskLoss", lastTaskLoss);
				epochMetrics.put("vmLoss", lastVmLoss);
				epochMetrics.put("taskChosenActionAccuracy", computeAccuracy(taskScorer, replayBuffer.getTaskExamples()));
				epochMetrics.put("vmChosenActionAccuracy", computeAccuracy(vmScorer, replayBuffer.getVmExamples()));
				epochMetrics.put("taskMaskHitRate", computeMaskHitRate(taskScorer, replayBuffer.getTaskExamples()));
				epochMetrics.put("vmMaskHitRate", computeMaskHitRate(vmScorer, replayBuffer.getVmExamples()));
				if(weightStats != null)
				{
					epochMetrics.putAll(weightStats.toEpochMetrics());
				}
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
		if(weightingConfig.isEnabled())
		{
			Map<String, Object> constraintAwareSummary =
					new LinkedHashMap<String, Object>(weightingConfig.toSummary());
			if(lastWeightStats != null)
			{
				constraintAwareSummary.putAll(lastWeightStats.toSummary());
			}
			summary.put("constraintAwareImitation", constraintAwareSummary);
		}

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

	private static final class EpochWeightStats
	{
		private final double highWeightThreshold;
		private int taskCount;
		private int vmCount;
		private double taskUnweightedLossSum;
		private double vmUnweightedLossSum;
		private double taskWeightedLossSum;
		private double vmWeightedLossSum;
		private double taskWeightSum;
		private double vmWeightSum;
		private double taskRiskScoreSum;
		private double vmRiskScoreSum;
		private double maxTaskRiskScore;
		private double maxVmRiskScore;
		private final Map<String, Double> taskComponentSums = new LinkedHashMap<String, Double>();
		private final Map<String, Double> vmComponentSums = new LinkedHashMap<String, Double>();
		private final Map<String, Double> taskComponentMax = new LinkedHashMap<String, Double>();
		private final Map<String, Double> vmComponentMax = new LinkedHashMap<String, Double>();
		private final Map<String, Integer> taskComponentCounts = new LinkedHashMap<String, Integer>();
		private final Map<String, Integer> vmComponentCounts = new LinkedHashMap<String, Integer>();
		private double maxTaskWeight = 1.0;
		private double maxVmWeight = 1.0;
		private double minTaskWeight = Double.POSITIVE_INFINITY;
		private double minVmWeight = Double.POSITIVE_INFINITY;
		private int highTaskWeightCount;
		private int highVmWeightCount;
		private int fallbackCount;
		private final Map<String, Integer> fallbackReasonCounts = new LinkedHashMap<String, Integer>();

		private EpochWeightStats(double maxSampleWeight)
		{
			this.highWeightThreshold = Math.max(1.0, maxSampleWeight * 0.8);
		}

		private void recordTask(ConstraintAwareSampleWeighter.WeightResult weightResult,
				double unweightedLoss, double weightedLoss)
		{
			taskCount++;
			taskUnweightedLossSum += safeValue(unweightedLoss);
			taskWeightedLossSum += safeValue(weightedLoss);
			taskWeightSum += weightResult.getSampleWeight();
			taskRiskScoreSum += weightResult.getRiskScore();
			maxTaskRiskScore = Math.max(maxTaskRiskScore, weightResult.getRiskScore());
			recordComponents(weightResult.getRiskComponents(), taskComponentSums, taskComponentMax, taskComponentCounts);
			maxTaskWeight = Math.max(maxTaskWeight, weightResult.getSampleWeight());
			minTaskWeight = Math.min(minTaskWeight, weightResult.getSampleWeight());
			if(weightResult.getSampleWeight() >= highWeightThreshold)
			{
				highTaskWeightCount++;
			}
			recordFallback(weightResult);
		}

		private void recordVm(ConstraintAwareSampleWeighter.WeightResult weightResult,
				double unweightedLoss, double weightedLoss)
		{
			vmCount++;
			vmUnweightedLossSum += safeValue(unweightedLoss);
			vmWeightedLossSum += safeValue(weightedLoss);
			vmWeightSum += weightResult.getSampleWeight();
			vmRiskScoreSum += weightResult.getRiskScore();
			maxVmRiskScore = Math.max(maxVmRiskScore, weightResult.getRiskScore());
			recordComponents(weightResult.getRiskComponents(), vmComponentSums, vmComponentMax, vmComponentCounts);
			maxVmWeight = Math.max(maxVmWeight, weightResult.getSampleWeight());
			minVmWeight = Math.min(minVmWeight, weightResult.getSampleWeight());
			if(weightResult.getSampleWeight() >= highWeightThreshold)
			{
				highVmWeightCount++;
			}
			recordFallback(weightResult);
		}

		private void recordFallback(ConstraintAwareSampleWeighter.WeightResult weightResult)
		{
			if(!weightResult.isFallback())
			{
				return;
			}
			fallbackCount++;
			String reason = weightResult.getFallbackReason() == null
					? "unknown"
					: weightResult.getFallbackReason();
			Integer current = fallbackReasonCounts.get(reason);
			fallbackReasonCounts.put(reason, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
		}

		private Map<String, Object> toEpochMetrics()
		{
			Map<String, Object> metrics = new LinkedHashMap<String, Object>();
			metrics.put("unweightedTaskLoss", averageTaskUnweightedLoss());
			metrics.put("unweightedVmLoss", averageVmUnweightedLoss());
			metrics.put("weightedTaskLoss", averageTaskWeightedLoss());
			metrics.put("weightedVmLoss", averageVmWeightedLoss());
			metrics.put("averageTaskSampleWeight", averageTaskSampleWeight());
			metrics.put("averageVmSampleWeight", averageVmSampleWeight());
			metrics.put("averageTaskRiskScore", averageTaskRiskScore());
			metrics.put("averageVmRiskScore", averageVmRiskScore());
			metrics.put("maxTaskRiskScore", maxTaskRiskScore);
			metrics.put("maxVmRiskScore", maxVmRiskScore);
			appendTaskComponentEpochMetrics(metrics);
			appendVmComponentEpochMetrics(metrics);
			metrics.put("minTaskSampleWeight", minTaskSampleWeight());
			metrics.put("minVmSampleWeight", minVmSampleWeight());
			metrics.put("maxTaskSampleWeight", maxTaskWeight);
			metrics.put("maxVmSampleWeight", maxVmWeight);
			metrics.put("highTaskWeightRatio", highTaskWeightRatio());
			metrics.put("highVmWeightRatio", highVmWeightRatio());
			metrics.put("taskWeightSaturationWarning", taskWeightSaturationWarning());
			metrics.put("vmWeightSaturationWarning", vmWeightSaturationWarning());
			metrics.put("highSampleWeightThreshold", highWeightThreshold);
			metrics.put("weightingFallbackCount", fallbackCount);
			metrics.put("weightingFallbackReasonCounts", new LinkedHashMap<String, Integer>(fallbackReasonCounts));
			return metrics;
		}

		private Map<String, Object> toSummary()
		{
			Map<String, Object> summary = new LinkedHashMap<String, Object>();
			Map<String, Object> taskWeightStats = new LinkedHashMap<String, Object>();
			taskWeightStats.put("averageSampleWeight", averageTaskSampleWeight());
			taskWeightStats.put("averageRiskScore", averageTaskRiskScore());
			taskWeightStats.put("maxRiskScore", maxTaskRiskScore);
			appendTaskComponentSummary(taskWeightStats);
			taskWeightStats.put("minSampleWeight", minTaskSampleWeight());
			taskWeightStats.put("maxSampleWeight", maxTaskWeight);
			taskWeightStats.put("highSampleWeightThreshold", highWeightThreshold);
			taskWeightStats.put("highWeightRatio", highTaskWeightRatio());
			taskWeightStats.put("saturationWarning", taskWeightSaturationWarning());
			taskWeightStats.put("unweightedLoss", averageTaskUnweightedLoss());
			taskWeightStats.put("weightedLoss", averageTaskWeightedLoss());
			Map<String, Object> vmWeightStats = new LinkedHashMap<String, Object>();
			vmWeightStats.put("averageSampleWeight", averageVmSampleWeight());
			vmWeightStats.put("averageRiskScore", averageVmRiskScore());
			vmWeightStats.put("maxRiskScore", maxVmRiskScore);
			appendVmComponentSummary(vmWeightStats);
			vmWeightStats.put("minSampleWeight", minVmSampleWeight());
			vmWeightStats.put("maxSampleWeight", maxVmWeight);
			vmWeightStats.put("highSampleWeightThreshold", highWeightThreshold);
			vmWeightStats.put("highWeightRatio", highVmWeightRatio());
			vmWeightStats.put("saturationWarning", vmWeightSaturationWarning());
			vmWeightStats.put("unweightedLoss", averageVmUnweightedLoss());
			vmWeightStats.put("weightedLoss", averageVmWeightedLoss());
			summary.put("taskWeightStats", taskWeightStats);
			summary.put("vmWeightStats", vmWeightStats);
			summary.put("fallbackCount", fallbackCount);
			summary.put("fallbackReason", fallbackReasonCounts.isEmpty() ? null : fallbackReasonCounts.keySet().iterator().next());
			summary.put("fallbackReasonCounts", new LinkedHashMap<String, Integer>(fallbackReasonCounts));
			return summary;
		}

		private void recordComponents(Map<String, Double> components, Map<String, Double> sums,
				Map<String, Double> maxValues, Map<String, Integer> counts)
		{
			if(components == null || components.isEmpty())
			{
				return;
			}
			for(Map.Entry<String, Double> entry: components.entrySet())
			{
				if(entry.getValue() == null || !Double.isFinite(entry.getValue().doubleValue()))
				{
					continue;
				}
				String key = entry.getKey();
				double value = entry.getValue().doubleValue();
				Double currentSum = sums.get(key);
				sums.put(key, Double.valueOf((currentSum == null ? 0.0 : currentSum.doubleValue()) + value));
				Double currentMax = maxValues.get(key);
				maxValues.put(key, Double.valueOf(currentMax == null ? value : Math.max(currentMax.doubleValue(), value)));
				Integer currentCount = counts.get(key);
				counts.put(key, Integer.valueOf(currentCount == null ? 1 : currentCount.intValue() + 1));
			}
		}

		private void appendTaskComponentEpochMetrics(Map<String, Object> metrics)
		{
			if(taskComponentCounts.isEmpty())
			{
				return;
			}
			metrics.put("averageTaskSlackRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_SLACK_RISK));
			metrics.put("averageTaskViolationRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_VIOLATION_RISK));
			metrics.put("averageTaskCriticalPathRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_CRITICAL_PATH_RISK));
			metrics.put("averageTaskLatenessRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
			metrics.put("maxTaskLatenessRisk",
					maxComponent(taskComponentMax, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
		}

		private void appendVmComponentEpochMetrics(Map<String, Object> metrics)
		{
			if(vmComponentCounts.isEmpty())
			{
				return;
			}
			metrics.put("averageVmInfeasibleRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_INFEASIBLE_RISK));
			metrics.put("averageVmSlackRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_SLACK_RISK));
			metrics.put("averageVmViolationRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_VIOLATION_RISK));
			metrics.put("averageVmCriticalPathRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_CRITICAL_PATH_RISK));
			metrics.put("averageVmLatenessRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
			metrics.put("maxVmLatenessRisk",
					maxComponent(vmComponentMax, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
		}

		private void appendTaskComponentSummary(Map<String, Object> taskWeightStats)
		{
			if(taskComponentCounts.isEmpty())
			{
				return;
			}
			taskWeightStats.put("averageSlackRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_SLACK_RISK));
			taskWeightStats.put("averageViolationRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_VIOLATION_RISK));
			taskWeightStats.put("averageCriticalPathRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_CRITICAL_PATH_RISK));
			taskWeightStats.put("averageLatenessRisk",
					averageComponent(taskComponentSums, taskComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
			taskWeightStats.put("maxLatenessRisk",
					maxComponent(taskComponentMax, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
		}

		private void appendVmComponentSummary(Map<String, Object> vmWeightStats)
		{
			if(vmComponentCounts.isEmpty())
			{
				return;
			}
			vmWeightStats.put("averageInfeasibleRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_INFEASIBLE_RISK));
			vmWeightStats.put("averageSlackRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_SLACK_RISK));
			vmWeightStats.put("averageViolationRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_VIOLATION_RISK));
			vmWeightStats.put("averageCriticalPathRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_CRITICAL_PATH_RISK));
			vmWeightStats.put("averageLatenessRisk",
					averageComponent(vmComponentSums, vmComponentCounts, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
			vmWeightStats.put("maxLatenessRisk",
					maxComponent(vmComponentMax, ConstraintAwareSampleWeighter.COMPONENT_LATENESS_RISK));
		}

		private double averageComponent(Map<String, Double> sums, Map<String, Integer> counts, String key)
		{
			Integer count = counts.get(key);
			Double sum = sums.get(key);
			if(count == null || count.intValue() == 0 || sum == null)
			{
				return 0.0;
			}
			return sum.doubleValue() / count.intValue();
		}

		private double maxComponent(Map<String, Double> maxValues, String key)
		{
			Double value = maxValues.get(key);
			return value == null ? 0.0 : value.doubleValue();
		}

		private double averageTaskUnweightedLoss()
		{
			return taskCount == 0 ? 0.0 : taskUnweightedLossSum / taskCount;
		}

		private double averageVmUnweightedLoss()
		{
			return vmCount == 0 ? 0.0 : vmUnweightedLossSum / vmCount;
		}

		private double averageTaskWeightedLoss()
		{
			return taskCount == 0 ? 0.0 : taskWeightedLossSum / taskCount;
		}

		private double averageVmWeightedLoss()
		{
			return vmCount == 0 ? 0.0 : vmWeightedLossSum / vmCount;
		}

		private double averageTaskSampleWeight()
		{
			return taskCount == 0 ? 1.0 : taskWeightSum / taskCount;
		}

		private double averageVmSampleWeight()
		{
			return vmCount == 0 ? 1.0 : vmWeightSum / vmCount;
		}

		private double averageTaskRiskScore()
		{
			return taskCount == 0 ? 0.0 : taskRiskScoreSum / taskCount;
		}

		private double averageVmRiskScore()
		{
			return vmCount == 0 ? 0.0 : vmRiskScoreSum / vmCount;
		}

		private double minTaskSampleWeight()
		{
			return taskCount == 0 ? 1.0 : minTaskWeight;
		}

		private double minVmSampleWeight()
		{
			return vmCount == 0 ? 1.0 : minVmWeight;
		}

		private double highTaskWeightRatio()
		{
			return taskCount == 0 ? 0.0 : (double)highTaskWeightCount / taskCount;
		}

		private double highVmWeightRatio()
		{
			return vmCount == 0 ? 0.0 : (double)highVmWeightCount / vmCount;
		}

		private boolean taskWeightSaturationWarning()
		{
			return highTaskWeightRatio() > 0.5;
		}

		private boolean vmWeightSaturationWarning()
		{
			return highVmWeightRatio() > 0.5;
		}

		private static double safeValue(double value)
		{
			return Double.isFinite(value) ? value : 0.0;
		}
	}
}
