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
		Path workloadPath = options.suite.getWorkloadPath();
		Path runDirectory = LearningExperimentSupport.createRunDirectory(options.outputRoot, options.suite, "phase10a");
		String startedAt = OffsetDateTime.now().toString();

		LearningExperimentSupport.ExpertReplayData expertReplayData =
				LearningExperimentSupport.collectExpertReplay(workloadPath, options.suite, true, true);
		LearningExperimentSupport.validateContextualReplay(expertReplayData.getContextualReplayBuffer());
		Map<String, Object> replaySummary = LearningExperimentSupport.buildReplaySummary(
				expertReplayData.getFlatReplayBuffer(),
				expertReplayData.getContextualReplayBuffer());

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

		for(String variantName: options.resolveVariants())
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
						expertReplayData.getFlatReplayBuffer(),
						expertReplayData.getContextualReplayBuffer(),
						trainingOptions,
						(epoch, trainingMetrics, baselinePolicy, hierarchicalPolicy) ->
						{
							try
							{
								LearningExperimentSupport.EvaluationResult epochEvaluation =
										LearningExperimentSupport.evaluate(workloadPath, baselinePolicy, hierarchicalPolicy);
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
						workloadPath,
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

			Map<String, Object> trainingSummary = telemetry.enrichSummary(trainingResult.getSummary());
			Map<String, Object> manifest = LearningExperimentSupport.buildManifest(
					trainingResult.getAlgorithmName(),
					options.suite,
					workloadPath,
					startedAt,
					OffsetDateTime.now().toString(),
					options.toHyperParameters(variantName));

			LearningExperimentSupport.writeArtifacts(
					variantDirectory,
					expertReplayData,
					replaySummary,
					trainingSummary,
					telemetry,
					learnedResult,
					manifest);

			Map<String, Object> comparison = ComparisonSummaryWriter.buildComparison(
					expertReplayData.getExpertMetrics(),
					learnedResult.getMetrics(),
					expertReplayData.getExpertReward(),
					learnedResult.getReward());
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

	private static final class RunnerOptions
	{
		private final RegressionSuite suite;
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
		private final Path outputRoot;

		private RunnerOptions(RegressionSuite suite, List<String> variants, boolean includeHeuristic, int epochs,
				int taskHiddenSize, int graphHiddenSize, int vmHiddenSize, int graphLayers, double learningRate,
				double l2, double epsilon, long seed, Path outputRoot)
		{
			this.suite = suite;
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
			return hyperParameters;
		}

		private static RunnerOptions parse(String[] args)
		{
			RegressionSuite suite = RegressionSuite.AUX_SMALL;
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
			Path outputRoot = Paths.get("learning-artifacts");

			for(int index = 0; index < args.length; index++)
			{
				String arg = args[index];
				if("--suite".equals(arg))
				{
					index++;
					suite = RegressionSuite.fromName(args[index]);
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

			return new RunnerOptions(suite, variants, includeHeuristic, epochs, taskHiddenSize,
					graphHiddenSize, vmHiddenSize, graphLayers, learningRate, l2, epsilon, seed, outputRoot);
		}
	}
}
