package main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ScheduleAgorithm.AblationPolicyFactory;
import ScheduleAgorithm.ComparisonSummaryWriter;
import ScheduleAgorithm.EpochMetricsLogger;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.TrainingTelemetry;

public class AblationRunner
{
	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		Path trainWorkloadPath = LearningExperimentSupport.requireWorkloadPath(options.trainSuite);
		Path evalWorkloadPath = LearningExperimentSupport.requireWorkloadPath(options.evalSuite);
		Path runDirectory = LearningExperimentSupport.createRunDirectory(options.outputRoot, options.trainSuite, "phase10a");
		String startedAt = OffsetDateTime.now().toString();
		List<String> resolvedVariants = options.resolveVariants();
		boolean requiresFlatReplay = requiresFlatReplay(resolvedVariants);
		boolean requiresContextualReplay = requiresContextualReplay(resolvedVariants);

		LearningExperimentSupport.ExpertReplayData trainExpertReplayData =
				LearningExperimentSupport.collectExpertReplay(
						trainWorkloadPath,
						options.trainSuite,
						requiresFlatReplay,
						requiresContextualReplay);
		LearningExperimentSupport.ReplayPreparation replayPreparation =
				LearningExperimentSupport.prepareReplayForTraining(
						options.trainSuite,
						trainExpertReplayData.getFlatReplayBuffer(),
						trainExpertReplayData.getContextualReplayBuffer(),
						options.balancedFamilies,
						options.balanceStrategy,
						options.seed);
		LearningExperimentSupport.ExpertReplayData evalReferenceExpertReplayData =
				LearningExperimentSupport.collectReferenceExpertReplay(
						options.trainSuite, trainExpertReplayData, options.evalSuite);
		if(requiresContextualReplay)
		{
			LearningExperimentSupport.validateContextualReplay(replayPreparation.getContextualReplayBuffer());
		}
		Map<String, Object> replaySummary = LearningExperimentSupport.buildReplaySummary(
				trainExpertReplayData.getFlatReplayBuffer(),
				trainExpertReplayData.getContextualReplayBuffer(),
				replayPreparation.getReplayBalancingSummary());

		List<Map<String, Object>> variantSummaries = new ArrayList<Map<String, Object>>();
		AblationPolicyFactory.TrainingOptions trainingOptions = new AblationPolicyFactory.TrainingOptions(
				options.epochs,
				options.taskHiddenSize,
				options.graphHiddenSize,
				options.vmHiddenSize,
				options.graphLayers,
				options.learningRate,
				options.l2,
				options.epsilon,
				options.seed);

		for(String variantName: resolvedVariants)
		{
			Path variantDirectory = runDirectory.resolve(variantName);
			Files.createDirectories(variantDirectory);
			TrainingTelemetry telemetry = new TrainingTelemetry(isNonLearningVariant(variantName) ? "none" : "imitation");

			AblationPolicyFactory.VariantTrainingResult trainingResult;
			LearningExperimentSupport.EvaluationResult learnedResult;
			try(EpochMetricsLogger epochLogger = LearningExperimentSupport.newEpochLogger(variantDirectory))
			{
				trainingResult = AblationPolicyFactory.trainVariant(
						variantName,
						replayPreparation.getFlatReplayBuffer(),
						replayPreparation.getContextualReplayBuffer(),
						trainingOptions,
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
								throw new IllegalStateException("Failed to evaluate variant " + variantName
										+ " at epoch " + epoch, exception);
							}
						});

				learnedResult = LearningExperimentSupport.evaluate(
						evalWorkloadPath,
						trainingResult.getBaselinePolicy(),
						trainingResult.getHierarchicalPolicy());

				if(telemetry.getEpochMetrics().isEmpty())
				{
					Map<String, Object> epochMetrics = new LinkedHashMap<String, Object>();
					epochMetrics.put("taskLoss", null);
					epochMetrics.put("vmLoss", null);
					epochMetrics.put("taskChosenActionAccuracy", null);
					epochMetrics.put("vmChosenActionAccuracy", null);
					epochMetrics.put("taskMaskHitRate",
							learnedResult.getInvalidTaskActionCount() == 0 ? Double.valueOf(1.0) : Double.valueOf(0.0));
					epochMetrics.put("vmMaskHitRate",
							learnedResult.getInvalidVmActionCount() == 0 ? Double.valueOf(1.0) : Double.valueOf(0.0));
					LearningExperimentSupport.logEpoch(epochLogger, telemetry, 0, epochMetrics, learnedResult);
				}
			}

			if(learnedResult.getInvalidTaskActionCount() != 0 || learnedResult.getInvalidVmActionCount() != 0)
			{
				throw new IllegalStateException("Invalid action counts for " + variantName + ": task="
						+ learnedResult.getInvalidTaskActionCount() + ", vm="
						+ learnedResult.getInvalidVmActionCount());
			}

			Map<String, Object> comparison = ComparisonSummaryWriter.buildComparison(
					evalReferenceExpertReplayData.getExpertMetrics(),
					learnedResult.getMetrics(),
					evalReferenceExpertReplayData.getExpertDeadlineMetrics(),
					learnedResult.getDeadlineViolationMetrics(),
					evalReferenceExpertReplayData.getExpertReward(),
					learnedResult.getReward(),
					options.normalizedComparison,
					options.normalizationEpsilon);
			Map<String, Object> variantReplayBalancing = resolveVariantReplayBalancingSummary(
					variantName,
					replayPreparation.getReplayBalancingSummary(),
					options);
			Map<String, Object> trainingSummary = LearningExperimentSupport.enrichTrainingSummary(
					telemetry.enrichSummary(trainingResult.getSummary()),
					variantReplayBalancing,
					ComparisonSummaryWriter.buildNormalizedComparisonSummary(comparison));
			Map<String, Object> manifest = LearningExperimentSupport.buildManifest(
					trainingResult.getAlgorithmName(),
					options.trainSuite,
					options.evalSuite,
					startedAt,
					OffsetDateTime.now().toString(),
					options.toHyperParameters(variantName),
					trainExpertReplayData,
					evalReferenceExpertReplayData,
					variantReplayBalancing,
					LearningExperimentSupport.buildAnalysisOptions(
							options.normalizedComparison, options.normalizationEpsilon));

			LearningExperimentSupport.writeArtifacts(
					variantDirectory,
					evalReferenceExpertReplayData,
					replaySummary,
					trainingSummary,
					telemetry,
					learnedResult,
					comparison,
					manifest);
			variantSummaries.add(ComparisonSummaryWriter.buildVariantSummary(
					variantName, trainingSummary, comparison));
		}

		ComparisonSummaryWriter.writeComparisonSummary(runDirectory.resolve("comparison-summary.json"), variantSummaries);
		System.out.println("Phase 10A ablation run completed: " + runDirectory.toString());
	}

	private static boolean isNonLearningVariant(String variantName)
	{
		return AblationPolicyFactory.RANDOM_POLICY.equals(variantName)
				|| AblationPolicyFactory.HEURISTIC_RERANK.equals(variantName);
	}

	private static Map<String, Object> resolveVariantReplayBalancingSummary(String variantName,
			Map<String, Object> globalReplayBalancing, RunnerOptions options)
	{
		if(!options.balancedFamilies)
		{
			return null;
		}
		if(usesReplay(variantName))
		{
			if(globalReplayBalancing == null)
			{
				return null;
			}
			Map<String, Object> filtered = new LinkedHashMap<String, Object>();
			copyIfPresent(filtered, globalReplayBalancing, "enabled");
			copyIfPresent(filtered, globalReplayBalancing, "mode");
			copyIfPresent(filtered, globalReplayBalancing, "strategy");
			copyIfPresent(filtered, globalReplayBalancing, "seed");
			if(usesFlatReplay(variantName))
			{
				copyIfPresent(filtered, globalReplayBalancing, "flat");
			}
			if(usesContextualReplay(variantName))
			{
				copyIfPresent(filtered, globalReplayBalancing, "contextual");
			}
			return filtered;
		}
		return LearningExperimentSupport.buildNotApplicableReplayBalancing(
				options.balanceStrategy,
				options.seed,
				"selected-variant-does-not-use-replay");
	}

	private static boolean usesReplay(String variantName)
	{
		return usesFlatReplay(variantName) || usesContextualReplay(variantName);
	}

	private static boolean usesFlatReplay(String variantName)
	{
		return AblationPolicyFactory.PHASE8_MLP.equals(variantName)
				|| AblationPolicyFactory.PHASE9_GRAPH_PLUS_MLP_VM.equals(variantName)
				|| AblationPolicyFactory.PHASE9_MLP_TASK_PLUS_VM_ATTENTION.equals(variantName)
				|| AblationPolicyFactory.PHASE10B_GRAPH_PLUS_VM_TRANSFORMER.equals(variantName)
				|| AblationPolicyFactory.PHASE10B_MLP_TASK_PLUS_VM_TRANSFORMER.equals(variantName);
	}

	private static boolean usesContextualReplay(String variantName)
	{
		return AblationPolicyFactory.PHASE9_GRAPH_PLUS_VM_ATTENTION.equals(variantName)
				|| AblationPolicyFactory.PHASE9_GRAPH_PLUS_MLP_VM.equals(variantName)
				|| AblationPolicyFactory.PHASE9_MLP_TASK_PLUS_VM_ATTENTION.equals(variantName)
				|| AblationPolicyFactory.PHASE10B_GRAPH_PLUS_VM_TRANSFORMER.equals(variantName)
				|| AblationPolicyFactory.PHASE10B_MLP_TASK_PLUS_VM_TRANSFORMER.equals(variantName);
	}

	private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key)
	{
		if(source.containsKey(key))
		{
			target.put(key, source.get(key));
		}
	}

	private static boolean requiresFlatReplay(List<String> variantNames)
	{
		for(String variantName: variantNames)
		{
			if(usesFlatReplay(variantName))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean requiresContextualReplay(List<String> variantNames)
	{
		for(String variantName: variantNames)
		{
			if(usesContextualReplay(variantName))
			{
				return true;
			}
		}
		return false;
	}

	private static final class RunnerOptions
	{
		private final RegressionSuite trainSuite;
		private final RegressionSuite evalSuite;
		private final List<String> variants;
		private final boolean includeHeuristic;
		private final int epochs;
		private final int taskHiddenSize;
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

		private RunnerOptions(RegressionSuite trainSuite, RegressionSuite evalSuite, List<String> variants,
				boolean includeHeuristic, int epochs,
				int taskHiddenSize, int graphHiddenSize, int vmHiddenSize, int graphLayers, double learningRate,
				double l2, double epsilon, long seed, boolean balancedFamilies, String balanceStrategy,
				boolean normalizedComparison, double normalizationEpsilon, Path outputRoot)
		{
			this.trainSuite = trainSuite;
			this.evalSuite = evalSuite;
			this.variants = variants;
			this.includeHeuristic = includeHeuristic;
			this.epochs = epochs;
			this.taskHiddenSize = taskHiddenSize;
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

		private List<String> resolveVariants()
		{
			List<String> resolved = new ArrayList<String>(variants);
			if(includeHeuristic && !resolved.contains(AblationPolicyFactory.HEURISTIC_RERANK))
			{
				resolved.add(AblationPolicyFactory.HEURISTIC_RERANK);
			}
			return resolved;
		}

		private Map<String, Object> toHyperParameters(String variantName)
		{
			Map<String, Object> hyperParameters = new LinkedHashMap<String, Object>();
			hyperParameters.put("variantName", variantName);
			hyperParameters.put("epochs", epochs);
			hyperParameters.put("taskHiddenSize", taskHiddenSize);
			hyperParameters.put("graphHiddenSize", graphHiddenSize);
			hyperParameters.put("vmHiddenSize", vmHiddenSize);
			hyperParameters.put("graphLayers", graphLayers);
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

		private static RunnerOptions parse(String[] args)
		{
			RegressionSuite suite = RegressionSuite.AUX_SMALL;
			RegressionSuite trainSuite = null;
			RegressionSuite evalSuite = null;
			List<String> variants = new ArrayList<String>(AblationPolicyFactory.defaultVariantNames());
			boolean includeHeuristic = false;
			int epochs = 8;
			int taskHiddenSize = 24;
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
				else if("--variants".equals(arg))
				{
					index++;
					variants = new ArrayList<String>();
					String[] split = args[index].split(",");
					for(String variant: split)
					{
						String normalized = variant.trim();
						if(!normalized.isEmpty())
						{
							variants.add(normalized);
						}
					}
				}
				else if("--include-heuristic".equals(arg))
				{
					includeHeuristic = true;
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

			for(String variant: variants)
			{
				if(!AblationPolicyFactory.supportedVariantNames().contains(variant))
				{
					throw new IllegalArgumentException("Unsupported ablation variant: " + variant);
				}
			}
			if(!ScheduleAgorithm.ReplayBalancingSupport.STRATEGY_MIN_QUOTA.equals(balanceStrategy))
			{
				throw new IllegalArgumentException("Unsupported balance strategy: " + balanceStrategy);
			}

			RegressionSuite resolvedTrainSuite = trainSuite == null ? suite : trainSuite;
			RegressionSuite resolvedEvalSuite = evalSuite == null ? resolvedTrainSuite : evalSuite;
			return new RunnerOptions(resolvedTrainSuite, resolvedEvalSuite, variants, includeHeuristic, epochs, taskHiddenSize,
					graphHiddenSize, vmHiddenSize, graphLayers, learningRate, l2, epsilon, seed,
					balancedFamilies, balanceStrategy, normalizedComparison, normalizationEpsilon, outputRoot);
		}
	}
}
