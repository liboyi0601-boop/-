package main;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ScheduleAgorithm.ConstraintFirstCheckpointSelector;
import ScheduleAgorithm.EpochMetricsLogger;
import ScheduleAgorithm.ExperimentMetrics;
import ScheduleAgorithm.HierarchicalMaskedLearningPolicy;
import ScheduleAgorithm.JsonSupport;
import ScheduleAgorithm.OfflineWarmStartResult;
import ScheduleAgorithm.OfflineWarmStartTrainer;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.TrainingTelemetry;

public class LearningRunner
{
	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		Path trainWorkloadPath = LearningExperimentSupport.requireWorkloadPath(options.trainSuite);
		Path evalWorkloadPath = LearningExperimentSupport.requireWorkloadPath(options.evalSuite);
		Path runDirectory = LearningExperimentSupport.createRunDirectory(options.outputRoot, options.trainSuite, "phase8");
		String startedAt = OffsetDateTime.now().toString();

		LearningExperimentSupport.ExpertReplayData trainExpertReplayData =
				LearningExperimentSupport.collectExpertReplay(trainWorkloadPath, options.trainSuite, true, false);
		LearningExperimentSupport.ExpertReplayData evalReferenceExpertReplayData =
				LearningExperimentSupport.collectReferenceExpertReplay(
						options.trainSuite, trainExpertReplayData, options.evalSuite);
		LearningExperimentSupport.ReplayPreparation replayPreparation =
				LearningExperimentSupport.prepareReplayForTraining(
						options.trainSuite,
						trainExpertReplayData.getFlatReplayBuffer(),
						null,
						options.balancedFamilies,
						options.balanceStrategy,
						options.seed);

		OfflineWarmStartTrainer trainer = new OfflineWarmStartTrainer(
				options.taskHiddenSize,
				options.vmHiddenSize,
				options.epochs,
				options.learningRate,
				options.l2,
				options.epsilon,
				options.seed);

		TrainingTelemetry telemetry = new TrainingTelemetry("imitation");
		final ConstraintFirstCheckpointSelector checkpointSelector = options.isCheckpointSelectionEnabled()
				? new ConstraintFirstCheckpointSelector()
				: null;
		OfflineWarmStartResult trainingResult;
		try(EpochMetricsLogger epochLogger = LearningExperimentSupport.newEpochLogger(runDirectory))
		{
			trainingResult = trainer.train(replayPreparation.getFlatReplayBuffer(),
					(epoch, trainingMetrics, baselinePolicy, hierarchicalPolicy) ->
					{
						try
						{
							LearningExperimentSupport.EvaluationResult epochEvaluation =
									LearningExperimentSupport.evaluate(evalWorkloadPath, baselinePolicy, hierarchicalPolicy);
							LearningExperimentSupport.logEpoch(epochLogger, telemetry, epoch,
									trainingMetrics, epochEvaluation);
							if(checkpointSelector != null)
							{
								if(!(hierarchicalPolicy instanceof HierarchicalMaskedLearningPolicy))
								{
									throw new IllegalStateException("Phase 8 checkpoint selection requires "
											+ "HierarchicalMaskedLearningPolicy, actual="
											+ hierarchicalPolicy.getClass().getName());
								}
								checkpointSelector.consider(epoch, (HierarchicalMaskedLearningPolicy)hierarchicalPolicy,
										epochEvaluation.getMetrics(), epochEvaluation.getReward(),
										epochEvaluation.getInvalidTaskActionCount(),
										epochEvaluation.getInvalidVmActionCount());
							}
						}
						catch(Exception exception)
						{
							throw new IllegalStateException("Failed to evaluate Phase 8 policy at epoch " + epoch,
									exception);
						}
					});
		}

		LearningExperimentSupport.EvaluationResult learnedResult =
				LearningExperimentSupport.evaluate(
						evalWorkloadPath,
						trainingResult.getPolicy(),
						trainingResult.getPolicy());
		ExperimentMetrics learnedMetrics = learnedResult.getMetrics();

		Map<String, Object> comparison = ScheduleAgorithm.ComparisonSummaryWriter.buildComparison(
				evalReferenceExpertReplayData.getExpertMetrics(),
				learnedResult.getMetrics(),
				evalReferenceExpertReplayData.getExpertReward(),
				learnedResult.getReward(),
				options.normalizedComparison,
				options.normalizationEpsilon);
		LearningExperimentSupport.EvaluationResult bestCheckpointResult = null;
		Map<String, Object> checkpointSummary = null;
		if(checkpointSelector != null)
		{
			if(!checkpointSelector.hasBestCheckpoint())
			{
				throw new IllegalStateException("Checkpoint selection was enabled but no checkpoint was selected");
			}
			bestCheckpointResult = LearningExperimentSupport.evaluate(
					evalWorkloadPath,
					checkpointSelector.getBestPolicy(),
					checkpointSelector.getBestPolicy());
			Map<String, Object> bestCheckpointComparison = ScheduleAgorithm.ComparisonSummaryWriter.buildComparison(
					evalReferenceExpertReplayData.getExpertMetrics(),
					bestCheckpointResult.getMetrics(),
					evalReferenceExpertReplayData.getExpertReward(),
					bestCheckpointResult.getReward(),
					options.normalizedComparison,
					options.normalizationEpsilon);
			comparison.put("bestCheckpointComparison", bestCheckpointComparison);
			checkpointSummary = checkpointSelector.buildSummary(
					copyFinalEpochMetrics(telemetry),
					bestCheckpointResult.getMetrics(),
					bestCheckpointResult.getReward(),
					bestCheckpointResult.getInvalidTaskActionCount(),
					bestCheckpointResult.getInvalidVmActionCount(),
					bestCheckpointComparison);
		}
		Map<String, Object> trainingSummary = LearningExperimentSupport.enrichTrainingSummary(
				telemetry.enrichSummary(trainingResult.getSummary()),
				replayPreparation.getReplayBalancingSummary(),
				ScheduleAgorithm.ComparisonSummaryWriter.buildNormalizedComparisonSummary(comparison));
		if(checkpointSummary != null)
		{
			enrichTrainingSummaryWithCheckpoint(trainingSummary, checkpointSummary);
		}
		Map<String, Object> manifest = LearningExperimentSupport.buildManifest(
				"PHASE8_HIERARCHICAL_WARM_START",
				options.trainSuite,
				options.evalSuite,
				startedAt,
				OffsetDateTime.now().toString(),
				options.toHyperParameters(),
				trainExpertReplayData,
				evalReferenceExpertReplayData,
				replayPreparation.getReplayBalancingSummary(),
				LearningExperimentSupport.buildAnalysisOptions(
						options.normalizedComparison, options.normalizationEpsilon));
		if(options.isCheckpointSelectionEnabled())
		{
			manifest.put("checkpointSelectionEnabled", Boolean.TRUE);
			manifest.put("checkpointSelectionRule", options.checkpointSelection);
			if(checkpointSummary != null)
			{
				manifest.put("bestCheckpointEpoch", checkpointSummary.get("bestCheckpointEpoch"));
			}
		}

		LearningExperimentSupport.writeArtifacts(
				runDirectory,
				evalReferenceExpertReplayData,
				LearningExperimentSupport.buildReplaySummary(
						trainExpertReplayData.getFlatReplayBuffer(), null, replayPreparation.getReplayBalancingSummary()),
				trainingSummary,
				telemetry,
				learnedResult,
				comparison,
				manifest);
		if(checkpointSummary != null && bestCheckpointResult != null)
		{
			JsonSupport.writeJson(runDirectory.resolve("checkpoint-summary.json"), checkpointSummary);
			JsonSupport.writeJson(runDirectory.resolve("best-learned-metrics.json"),
					bestCheckpointResult.getMetrics().toMap());
			JsonSupport.writeJson(runDirectory.resolve("best-learned-reward.json"),
					bestCheckpointResult.getReward());
		}

		System.out.println("Phase 8 learning run completed: " + runDirectory.toString());
		System.out.println("Expert totalCost=" + evalReferenceExpertReplayData.getExpertMetrics().getTotalCost()
				+ " learned totalCost=" + learnedMetrics.getTotalCost());
		System.out.println("Expert reward=" + evalReferenceExpertReplayData.getExpertReward().get("totalReward")
				+ " learned reward=" + learnedResult.getReward().get("totalReward"));
	}

	private static Map<String, Object> copyFinalEpochMetrics(TrainingTelemetry telemetry)
	{
		List<Map<String, Object>> epochMetrics = telemetry.getEpochMetrics();
		if(epochMetrics.isEmpty())
		{
			return null;
		}
		return new LinkedHashMap<String, Object>(epochMetrics.get(epochMetrics.size() - 1));
	}

	private static void enrichTrainingSummaryWithCheckpoint(Map<String, Object> trainingSummary,
			Map<String, Object> checkpointSummary)
	{
		trainingSummary.put("checkpointSelectionEnabled", Boolean.TRUE);
		trainingSummary.put("selectionRule", checkpointSummary.get("selectionRule"));
		trainingSummary.put("bestCheckpointEpoch", checkpointSummary.get("bestCheckpointEpoch"));
		trainingSummary.put("bestCheckpointReason", checkpointSummary.get("bestCheckpointReason"));
		trainingSummary.put("bestCheckpointMetrics", checkpointSummary.get("bestCheckpointMetrics"));
		trainingSummary.put("finalEpochMetrics", checkpointSummary.get("finalEpochMetrics"));
	}

	private static final class RunnerOptions
	{
		private static final String CHECKPOINT_SELECTION_NONE = "none";

		private final RegressionSuite trainSuite;
		private final RegressionSuite evalSuite;
		private final int epochs;
		private final int taskHiddenSize;
		private final int vmHiddenSize;
		private final double learningRate;
		private final double l2;
		private final double epsilon;
		private final long seed;
		private final boolean balancedFamilies;
		private final String balanceStrategy;
		private final boolean normalizedComparison;
		private final double normalizationEpsilon;
		private final String checkpointSelection;
		private final Path outputRoot;

		private RunnerOptions(RegressionSuite trainSuite, RegressionSuite evalSuite, int epochs, int taskHiddenSize,
				int vmHiddenSize,
				double learningRate, double l2, double epsilon, long seed, boolean balancedFamilies,
				String balanceStrategy, boolean normalizedComparison, double normalizationEpsilon,
				String checkpointSelection, Path outputRoot)
		{
			this.trainSuite = trainSuite;
			this.evalSuite = evalSuite;
			this.epochs = epochs;
			this.taskHiddenSize = taskHiddenSize;
			this.vmHiddenSize = vmHiddenSize;
			this.learningRate = learningRate;
			this.l2 = l2;
			this.epsilon = epsilon;
			this.seed = seed;
			this.balancedFamilies = balancedFamilies;
			this.balanceStrategy = balanceStrategy;
			this.normalizedComparison = normalizedComparison;
			this.normalizationEpsilon = normalizationEpsilon;
			this.checkpointSelection = checkpointSelection;
			this.outputRoot = outputRoot;
		}

		private static RunnerOptions parse(String[] args)
		{
			RegressionSuite suite = RegressionSuite.AUX_SMALL;
			RegressionSuite trainSuite = null;
			RegressionSuite evalSuite = null;
			int epochs = 8;
			int taskHiddenSize = 24;
			int vmHiddenSize = 24;
			double learningRate = 0.01;
			double l2 = 0.0001;
			double epsilon = 0.0;
			long seed = 20260420L;
			boolean balancedFamilies = false;
			String balanceStrategy = ScheduleAgorithm.ReplayBalancingSupport.STRATEGY_MIN_QUOTA;
			boolean normalizedComparison = false;
			double normalizationEpsilon = 1e-9;
			String checkpointSelection = CHECKPOINT_SELECTION_NONE;
			Path outputRoot = Paths.get("learning-artifacts");

			for(int index = 0; index < args.length; index++)
			{
				String arg = args[index];
				if("--suite".equals(arg))
				{
					index++;
					suite = RegressionSuite.fromName(args[index]);
				}
				else if("--train-suite".equals(arg))
				{
					index++;
					trainSuite = RegressionSuite.fromName(args[index]);
				}
				else if("--eval-suite".equals(arg))
				{
					index++;
					evalSuite = RegressionSuite.fromName(args[index]);
				}
				else if("--epochs".equals(arg))
				{
					index++;
					epochs = Integer.parseInt(args[index]);
				}
				else if("--task-hidden".equals(arg))
				{
					index++;
					taskHiddenSize = Integer.parseInt(args[index]);
				}
				else if("--vm-hidden".equals(arg))
				{
					index++;
					vmHiddenSize = Integer.parseInt(args[index]);
				}
				else if("--learning-rate".equals(arg))
				{
					index++;
					learningRate = Double.parseDouble(args[index]);
				}
				else if("--l2".equals(arg))
				{
					index++;
					l2 = Double.parseDouble(args[index]);
				}
				else if("--epsilon".equals(arg))
				{
					index++;
					epsilon = Double.parseDouble(args[index]);
				}
				else if("--seed".equals(arg))
				{
					index++;
					seed = Long.parseLong(args[index]);
				}
				else if("--balanced-families".equals(arg))
				{
					balancedFamilies = true;
				}
				else if("--balance-strategy".equals(arg))
				{
					index++;
					balanceStrategy = args[index];
				}
				else if("--normalized-comparison".equals(arg))
				{
					normalizedComparison = true;
				}
				else if("--normalization-epsilon".equals(arg))
				{
					index++;
					normalizationEpsilon = Double.parseDouble(args[index]);
				}
				else if("--checkpoint-selection".equals(arg))
				{
					index++;
					checkpointSelection = args[index];
				}
				else if("--out".equals(arg))
				{
					index++;
					outputRoot = Paths.get(args[index]);
				}
				else
				{
					throw new IllegalArgumentException("Unknown argument: " + arg);
				}
			}

			if(!ScheduleAgorithm.ReplayBalancingSupport.STRATEGY_MIN_QUOTA.equals(balanceStrategy))
			{
				throw new IllegalArgumentException("Unsupported balance strategy: " + balanceStrategy);
			}
			if(!CHECKPOINT_SELECTION_NONE.equals(checkpointSelection)
					&& !ConstraintFirstCheckpointSelector.RULE_NAME.equals(checkpointSelection))
			{
				throw new IllegalArgumentException("Unsupported checkpoint selection: " + checkpointSelection);
			}

			RegressionSuite resolvedTrainSuite = trainSuite == null ? suite : trainSuite;
			RegressionSuite resolvedEvalSuite = evalSuite == null ? resolvedTrainSuite : evalSuite;
			return new RunnerOptions(resolvedTrainSuite, resolvedEvalSuite, epochs, taskHiddenSize, vmHiddenSize,
					learningRate, l2, epsilon, seed, balancedFamilies, balanceStrategy,
					normalizedComparison, normalizationEpsilon, checkpointSelection, outputRoot);
		}

		private boolean isCheckpointSelectionEnabled()
		{
			return ConstraintFirstCheckpointSelector.RULE_NAME.equals(checkpointSelection);
		}

		private Map<String, Object> toHyperParameters()
		{
			Map<String, Object> hyperParameters = new LinkedHashMap<String, Object>();
			hyperParameters.put("variantName", "phase8_mlp");
			hyperParameters.put("epochs", epochs);
			hyperParameters.put("taskHiddenSize", taskHiddenSize);
			hyperParameters.put("vmHiddenSize", vmHiddenSize);
			hyperParameters.put("learningRate", learningRate);
			hyperParameters.put("l2", l2);
			hyperParameters.put("epsilon", epsilon);
			hyperParameters.put("seed", seed);
			hyperParameters.put("trainSuiteName", trainSuite.getSuiteName());
			hyperParameters.put("evalSuiteName", evalSuite.getSuiteName());
			if(balancedFamilies)
			{
				hyperParameters.put("balancedFamilies", Boolean.TRUE);
				hyperParameters.put("balanceStrategy", balanceStrategy);
			}
			if(normalizedComparison)
			{
				hyperParameters.put("normalizedComparison", Boolean.TRUE);
				hyperParameters.put("normalizationEpsilon", normalizationEpsilon);
			}
			if(isCheckpointSelectionEnabled())
			{
				hyperParameters.put("checkpointSelection", checkpointSelection);
			}
			return hyperParameters;
		}
	}
}
