package main;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import ScheduleAgorithm.EpochMetricsLogger;
import ScheduleAgorithm.ExperimentMetrics;
import ScheduleAgorithm.OfflineWarmStartResult;
import ScheduleAgorithm.OfflineWarmStartTrainer;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.TrainingTelemetry;

public class LearningRunner
{
	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		Path workloadPath = options.suite.getWorkloadPath();
		Path runDirectory = LearningExperimentSupport.createRunDirectory(options.outputRoot, options.suite, "phase8");
		String startedAt = OffsetDateTime.now().toString();

		LearningExperimentSupport.ExpertReplayData expertReplayData =
				LearningExperimentSupport.collectExpertReplay(workloadPath, options.suite, true, false);

		OfflineWarmStartTrainer trainer = new OfflineWarmStartTrainer(
				options.taskHiddenSize,
				options.vmHiddenSize,
				options.epochs,
				options.learningRate,
				options.l2,
				options.epsilon,
				options.seed);

		TrainingTelemetry telemetry = new TrainingTelemetry("imitation");
		OfflineWarmStartResult trainingResult;
		try(EpochMetricsLogger epochLogger = LearningExperimentSupport.newEpochLogger(runDirectory))
		{
			trainingResult = trainer.train(expertReplayData.getFlatReplayBuffer(),
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
							throw new IllegalStateException("Failed to evaluate Phase 8 policy at epoch " + epoch,
									exception);
						}
					});
		}

		LearningExperimentSupport.EvaluationResult learnedResult =
				LearningExperimentSupport.evaluate(workloadPath, trainingResult.getPolicy(), trainingResult.getPolicy());
		ExperimentMetrics learnedMetrics = learnedResult.getMetrics();

		Map<String, Object> trainingSummary = telemetry.enrichSummary(trainingResult.getSummary());
		Map<String, Object> manifest = LearningExperimentSupport.buildManifest(
				"PHASE8_HIERARCHICAL_WARM_START",
				options.suite,
				workloadPath,
				startedAt,
				OffsetDateTime.now().toString(),
				options.toHyperParameters());

		LearningExperimentSupport.writeArtifacts(
				runDirectory,
				expertReplayData,
				LearningExperimentSupport.buildReplaySummary(expertReplayData.getFlatReplayBuffer(), null),
				trainingSummary,
				telemetry,
				learnedResult,
				manifest);

		System.out.println("Phase 8 learning run completed: " + runDirectory.toString());
		System.out.println("Expert totalCost=" + expertReplayData.getExpertMetrics().getTotalCost()
				+ " learned totalCost=" + learnedMetrics.getTotalCost());
		System.out.println("Expert reward=" + expertReplayData.getExpertReward().get("totalReward")
				+ " learned reward=" + learnedResult.getReward().get("totalReward"));
	}

	private static final class RunnerOptions
	{
		private final RegressionSuite suite;
		private final int epochs;
		private final int taskHiddenSize;
		private final int vmHiddenSize;
		private final double learningRate;
		private final double l2;
		private final double epsilon;
		private final long seed;
		private final Path outputRoot;

		private RunnerOptions(RegressionSuite suite, int epochs, int taskHiddenSize, int vmHiddenSize,
				double learningRate, double l2, double epsilon, long seed, Path outputRoot)
		{
			this.suite = suite;
			this.epochs = epochs;
			this.taskHiddenSize = taskHiddenSize;
			this.vmHiddenSize = vmHiddenSize;
			this.learningRate = learningRate;
			this.l2 = l2;
			this.epsilon = epsilon;
			this.seed = seed;
			this.outputRoot = outputRoot;
		}

		private static RunnerOptions parse(String[] args)
		{
			RegressionSuite suite = RegressionSuite.AUX_SMALL;
			int epochs = 8;
			int taskHiddenSize = 24;
			int vmHiddenSize = 24;
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

			return new RunnerOptions(suite, epochs, taskHiddenSize, vmHiddenSize,
					learningRate, l2, epsilon, seed, outputRoot);
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
			return hyperParameters;
		}
	}
}
