package main;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import ScheduleAgorithm.ContextualHierarchicalReplayBuffer;
import ScheduleAgorithm.EpochMetricsLogger;
import ScheduleAgorithm.ExperimentMetrics;
import ScheduleAgorithm.GraphAttentionWarmStartResult;
import ScheduleAgorithm.GraphAttentionWarmStartTrainer;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.TrainingTelemetry;

public class GraphAttentionLearningRunner
{
	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		Path trainWorkloadPath = LearningExperimentSupport.requireWorkloadPath(options.trainSuite);
		Path evalWorkloadPath = LearningExperimentSupport.requireWorkloadPath(options.evalSuite);
		Path runDirectory = LearningExperimentSupport.createRunDirectory(options.outputRoot, options.trainSuite, "phase9");
		String startedAt = OffsetDateTime.now().toString();

		LearningExperimentSupport.ExpertReplayData trainExpertReplayData =
				LearningExperimentSupport.collectExpertReplay(trainWorkloadPath, options.trainSuite, false, true);
		LearningExperimentSupport.ExpertReplayData evalReferenceExpertReplayData =
				LearningExperimentSupport.collectReferenceExpertReplay(
						options.trainSuite, trainExpertReplayData, options.evalSuite);
		LearningExperimentSupport.ReplayPreparation replayPreparation =
				LearningExperimentSupport.prepareReplayForTraining(
						options.trainSuite,
						null,
						trainExpertReplayData.getContextualReplayBuffer(),
						options.balancedFamilies,
						options.balanceStrategy,
						options.seed);
		ContextualHierarchicalReplayBuffer replayBuffer = replayPreparation.getContextualReplayBuffer();
		LearningExperimentSupport.validateContextualReplay(replayBuffer);

		GraphAttentionWarmStartTrainer trainer = new GraphAttentionWarmStartTrainer(
				options.graphHiddenSize,
				options.vmHiddenSize,
				options.graphLayers,
				options.epochs,
				options.learningRate,
				options.l2,
				options.epsilon,
				options.seed);

		TrainingTelemetry telemetry = new TrainingTelemetry("imitation");
		GraphAttentionWarmStartResult trainingResult;
		try(EpochMetricsLogger epochLogger = LearningExperimentSupport.newEpochLogger(runDirectory))
		{
			trainingResult = trainer.train(replayBuffer,
					(epoch, trainingMetrics, baselinePolicy, hierarchicalPolicy) ->
					{
						try
						{
							LearningExperimentSupport.EvaluationResult epochEvaluation =
									LearningExperimentSupport.evaluate(evalWorkloadPath, baselinePolicy, hierarchicalPolicy);
							LearningExperimentSupport.logEpoch(epochLogger, telemetry, epoch,
									trainingMetrics, epochEvaluation);
						}
						catch(Exception exception)
						{
							throw new IllegalStateException("Failed to evaluate Phase 9 policy at epoch " + epoch,
									exception);
						}
					});
		}

		LearningExperimentSupport.EvaluationResult learnedResult =
				LearningExperimentSupport.evaluate(evalWorkloadPath, null, trainingResult.getPolicy());
		ExperimentMetrics learnedMetrics = learnedResult.getMetrics();

		Map<String, Object> comparison = ScheduleAgorithm.ComparisonSummaryWriter.buildComparison(
				evalReferenceExpertReplayData.getExpertMetrics(),
				learnedResult.getMetrics(),
				evalReferenceExpertReplayData.getExpertReward(),
				learnedResult.getReward(),
				options.normalizedComparison,
				options.normalizationEpsilon);
		Map<String, Object> trainingSummary = LearningExperimentSupport.enrichTrainingSummary(
				telemetry.enrichSummary(trainingResult.getSummary()),
				replayPreparation.getReplayBalancingSummary(),
				ScheduleAgorithm.ComparisonSummaryWriter.buildNormalizedComparisonSummary(comparison));
		Map<String, Object> manifest = LearningExperimentSupport.buildManifest(
				"PHASE9_GRAPH_ATTENTION_WARM_START",
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

		LearningExperimentSupport.writeArtifacts(
				runDirectory,
				evalReferenceExpertReplayData,
				LearningExperimentSupport.buildReplaySummary(
						null, trainExpertReplayData.getContextualReplayBuffer(),
						replayPreparation.getReplayBalancingSummary()),
				trainingSummary,
				telemetry,
				learnedResult,
				comparison,
				manifest);

		System.out.println("Phase 9 graph-attention run completed: " + runDirectory.toString());
		System.out.println("Expert totalCost=" + evalReferenceExpertReplayData.getExpertMetrics().getTotalCost()
				+ " learned totalCost=" + learnedMetrics.getTotalCost());
		System.out.println("Expert reward=" + evalReferenceExpertReplayData.getExpertReward().get("totalReward")
				+ " learned reward=" + learnedResult.getReward().get("totalReward"));
	}

	private static final class RunnerOptions
	{
		private final RegressionSuite trainSuite;
		private final RegressionSuite evalSuite;
		private final int epochs;
		private final int graphHiddenSize;
		private final int vmHiddenSize;
		private final int graphLayers;
		private final double learningRate;
		private final double l2;
		private final double epsilon;
		private final long seed;
		private final boolean balancedFamilies;
		private final String balanceStrategy;
		private final boolean normalizedComparison;
		private final double normalizationEpsilon;
		private final Path outputRoot;

		private RunnerOptions(RegressionSuite trainSuite, RegressionSuite evalSuite, int epochs, int graphHiddenSize,
				int vmHiddenSize,
				int graphLayers, double learningRate, double l2, double epsilon, long seed,
				boolean balancedFamilies, String balanceStrategy, boolean normalizedComparison,
				double normalizationEpsilon, Path outputRoot)
		{
			this.trainSuite = trainSuite;
			this.evalSuite = evalSuite;
			this.epochs = epochs;
			this.graphHiddenSize = graphHiddenSize;
			this.vmHiddenSize = vmHiddenSize;
			this.graphLayers = graphLayers;
			this.learningRate = learningRate;
			this.l2 = l2;
			this.epsilon = epsilon;
			this.seed = seed;
			this.balancedFamilies = balancedFamilies;
			this.balanceStrategy = balanceStrategy;
			this.normalizedComparison = normalizedComparison;
			this.normalizationEpsilon = normalizationEpsilon;
			this.outputRoot = outputRoot;
		}

		private static RunnerOptions parse(String[] args)
		{
			RegressionSuite suite = RegressionSuite.AUX_SMALL;
			RegressionSuite trainSuite = null;
			RegressionSuite evalSuite = null;
			int epochs = 8;
			int graphHiddenSize = 24;
			int vmHiddenSize = 24;
			int graphLayers = 2;
			double learningRate = 0.01;
			double l2 = 0.0001;
			double epsilon = 0.0;
			long seed = 20260420L;
			boolean balancedFamilies = false;
			String balanceStrategy = ScheduleAgorithm.ReplayBalancingSupport.STRATEGY_MIN_QUOTA;
			boolean normalizedComparison = false;
			double normalizationEpsilon = 1e-9;
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
				else if("--graph-hidden".equals(arg))
				{
					index++;
					graphHiddenSize = Integer.parseInt(args[index]);
				}
				else if("--vm-hidden".equals(arg))
				{
					index++;
					vmHiddenSize = Integer.parseInt(args[index]);
				}
				else if("--graph-layers".equals(arg))
				{
					index++;
					graphLayers = Integer.parseInt(args[index]);
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

			RegressionSuite resolvedTrainSuite = trainSuite == null ? suite : trainSuite;
			RegressionSuite resolvedEvalSuite = evalSuite == null ? resolvedTrainSuite : evalSuite;
			return new RunnerOptions(resolvedTrainSuite, resolvedEvalSuite, epochs, graphHiddenSize, vmHiddenSize,
					graphLayers,
					learningRate, l2, epsilon, seed, balancedFamilies, balanceStrategy,
					normalizedComparison, normalizationEpsilon, outputRoot);
		}

		private Map<String, Object> toHyperParameters()
		{
			Map<String, Object> hyperParameters = new LinkedHashMap<String, Object>();
			hyperParameters.put("variantName", "phase9_graph_plus_vm_attention");
			hyperParameters.put("epochs", epochs);
			hyperParameters.put("graphHiddenSize", graphHiddenSize);
			hyperParameters.put("vmHiddenSize", vmHiddenSize);
			hyperParameters.put("graphLayers", graphLayers);
			hyperParameters.put("attentionHeads", 1);
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
			return hyperParameters;
		}
	}
}
