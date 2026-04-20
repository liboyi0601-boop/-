package main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ScheduleAgorithm.ConstraintAwareRewardModel;
import ScheduleAgorithm.ExperimentMetrics;
import ScheduleAgorithm.HierarchicalReplayBuffer;
import ScheduleAgorithm.InMemoryExpertReplayCollector;
import ScheduleAgorithm.JsonSupport;
import ScheduleAgorithm.NOSF_Algorithms;
import ScheduleAgorithm.NoOpSchedulingTraceRecorder;
import ScheduleAgorithm.OfflineWarmStartResult;
import ScheduleAgorithm.OfflineWarmStartTrainer;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.WorkloadFingerprint;
import share.StaticfinalTags;
import workflow.Workflow;
import workflow.WorkflowDatasetIO;

public class LearningRunner
{
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		Path runDirectory = options.outputRoot
				.resolve(options.suite.getSuiteName())
				.resolve("phase8-" + RUN_ID_FORMATTER.format(OffsetDateTime.now()));
		Files.createDirectories(runDirectory);

		Path workloadPath = options.suite.getWorkloadPath();
		String startedAt = OffsetDateTime.now().toString();

		InMemoryExpertReplayCollector collector = new InMemoryExpertReplayCollector();
		List<Workflow> expertWorkflows = WorkflowDatasetIO.readWorkflows(workloadPath);
		NOSF_Algorithms expertAlgorithm = new NOSF_Algorithms(collector);
		expertAlgorithm.submitWorkflowList(expertWorkflows);
		expertAlgorithm.ScheduleWorkflow_By_NOSF();
		collector.close();
		ExperimentMetrics expertMetrics = ExperimentMetrics.fromWorkflows(expertWorkflows);
		if(options.suite.isGolden())
		{
			options.suite.assertMatches(expertMetrics);
		}

		HierarchicalReplayBuffer replayBuffer = collector.getReplayBuffer();
		OfflineWarmStartTrainer trainer = new OfflineWarmStartTrainer(
				options.taskHiddenSize,
				options.vmHiddenSize,
				options.epochs,
				options.learningRate,
				options.l2,
				options.epsilon,
				options.seed);
		OfflineWarmStartResult trainingResult = trainer.train(replayBuffer);

		List<Workflow> evaluationWorkflows = WorkflowDatasetIO.readWorkflows(workloadPath);
		NOSF_Algorithms learnedAlgorithm = new NOSF_Algorithms(
				new NoOpSchedulingTraceRecorder(),
				trainingResult.getPolicy(),
				trainingResult.getPolicy());
		learnedAlgorithm.submitWorkflowList(evaluationWorkflows);
		learnedAlgorithm.ScheduleWorkflow_By_NOSF();
		ExperimentMetrics learnedMetrics = ExperimentMetrics.fromWorkflows(evaluationWorkflows);

		ConstraintAwareRewardModel rewardModel = new ConstraintAwareRewardModel();
		Map<String, Object> expertReward = rewardModel.evaluateEpisode(expertWorkflows, expertMetrics);
		Map<String, Object> learnedReward = rewardModel.evaluateEpisode(evaluationWorkflows, learnedMetrics);
		Map<String, Object> comparison = buildComparison(expertMetrics, learnedMetrics, expertReward, learnedReward);
		Map<String, Object> manifest = buildManifest(options, workloadPath, startedAt, OffsetDateTime.now().toString());

		JsonSupport.writeJson(runDirectory.resolve("expert-metrics.json"), expertMetrics.toMap());
		JsonSupport.writeJson(runDirectory.resolve("expert-reward.json"), expertReward);
		JsonSupport.writeJson(runDirectory.resolve("replay-summary.json"), replayBuffer.toSummary());
		JsonSupport.writeJson(runDirectory.resolve("training-summary.json"), trainingResult.getSummary());
		JsonSupport.writeJson(runDirectory.resolve("learned-metrics.json"), learnedMetrics.toMap());
		JsonSupport.writeJson(runDirectory.resolve("learned-reward.json"), learnedReward);
		JsonSupport.writeJson(runDirectory.resolve("comparison.json"), comparison);
		JsonSupport.writeJson(runDirectory.resolve("manifest.json"), manifest);

		System.out.println("Phase 8 learning run completed: " + runDirectory.toString());
		System.out.println("Expert totalCost=" + expertMetrics.getTotalCost()
				+ " learned totalCost=" + learnedMetrics.getTotalCost());
		System.out.println("Expert reward=" + expertReward.get("totalReward")
				+ " learned reward=" + learnedReward.get("totalReward"));
	}

	private static Map<String, Object> buildComparison(ExperimentMetrics expertMetrics, ExperimentMetrics learnedMetrics,
			Map<String, Object> expertReward, Map<String, Object> learnedReward)
	{
		Map<String, Object> comparison = new LinkedHashMap<String, Object>();
		comparison.put("costDelta", learnedMetrics.getTotalCost() - expertMetrics.getTotalCost());
		comparison.put("violationCountDelta", learnedMetrics.getViolationCount() - expertMetrics.getViolationCount());
		comparison.put("violationTimeDelta", learnedMetrics.getViolationTime() - expertMetrics.getViolationTime());
		comparison.put("resourceUtilizationDelta",
				learnedMetrics.getResourceUtilization() - expertMetrics.getResourceUtilization());
		comparison.put("scheduleTimeDeltaMs", learnedMetrics.getScheduleTimeMs() - expertMetrics.getScheduleTimeMs());
		comparison.put("rewardDelta",
				((Number)learnedReward.get("totalReward")).doubleValue()
						- ((Number)expertReward.get("totalReward")).doubleValue());
		return comparison;
	}

	private static Map<String, Object> buildManifest(RunnerOptions options, Path workloadPath, String startedAt,
			String finishedAt) throws IOException
	{
		GitMetadata gitMetadata = GitMetadata.read();
		Map<String, Object> staticTags = buildStaticTagConfig();
		Map<String, Object> hyperParameters = new LinkedHashMap<String, Object>();
		hyperParameters.put("epochs", options.epochs);
		hyperParameters.put("taskHiddenSize", options.taskHiddenSize);
		hyperParameters.put("vmHiddenSize", options.vmHiddenSize);
		hyperParameters.put("learningRate", options.learningRate);
		hyperParameters.put("l2", options.l2);
		hyperParameters.put("epsilon", options.epsilon);
		hyperParameters.put("seed", options.seed);

		Map<String, Object> manifest = new LinkedHashMap<String, Object>();
		manifest.put("algorithmName", "PHASE8_HIERARCHICAL_WARM_START");
		manifest.put("suiteName", options.suite.getSuiteName());
		manifest.put("workloadPath", workloadPath.toString());
		manifest.put("workloadFingerprint", WorkloadFingerprint.fromFile(workloadPath));
		manifest.put("gitCommit", gitMetadata.commit);
		manifest.put("gitBranch", gitMetadata.branch);
		manifest.put("dirtyFlag", gitMetadata.dirty);
		manifest.put("startedAt", startedAt);
		manifest.put("finishedAt", finishedAt);
		manifest.put("staticTags", staticTags);
		manifest.put("hyperParameters", hyperParameters);
		manifest.put("configHash", WorkloadFingerprint.sha256(
				JsonSupport.toJson(hyperParameters).getBytes(StandardCharsets.UTF_8)));
		return manifest;
	}

	private static Map<String, Object> buildStaticTagConfig()
	{
		Map<String, Object> staticTags = new LinkedHashMap<String, Object>();
		staticTags.put("choose", StaticfinalTags.choose);
		staticTags.put("workflowNum", StaticfinalTags.workflowNum);
		staticTags.put("workflowTemplateNum", StaticfinalTags.workflowTemplateNum);
		staticTags.put("arrivalLamda", StaticfinalTags.arrivalLamda);
		staticTags.put("deadlineBase", StaticfinalTags.deadlineBase);
		staticTags.put("bandwidth", StaticfinalTags.bandwidth);
		staticTags.put("standardDeviation", StaticfinalTags.standardDeviation);
		staticTags.put("varDeviation", StaticfinalTags.VarDeviation);
		staticTags.put("createVmTime", StaticfinalTags.createVmTime);
		staticTags.put("partVmSlot", StaticfinalTags.PartVmSlot);
		staticTags.put("vmSlot", StaticfinalTags.VmSlot);
		staticTags.put("selectedNum", StaticfinalTags.selectedNum);
		staticTags.put("operationStyle", StaticfinalTags.OperationStyle.name());
		staticTags.put("rosaConfidency", StaticfinalTags.ROSAConfidency);
		return staticTags;
	}

	private static String runCommand(String... command)
	{
		Process process = null;
		try
		{
			process = new ProcessBuilder(command).start();
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			copy(process.getInputStream(), output);
			copy(process.getErrorStream(), new ByteArrayOutputStream());
			if(process.waitFor() != 0)
			{
				return "unknown";
			}
			return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
		}
		catch(Exception exception)
		{
			return "unknown";
		}
		finally
		{
			if(process != null)
			{
				process.destroy();
			}
		}
	}

	private static void copy(InputStream input, ByteArrayOutputStream output) throws IOException
	{
		byte[] buffer = new byte[4096];
		int count;
		while((count = input.read(buffer)) != -1)
		{
			output.write(buffer, 0, count);
		}
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
	}

	private static final class GitMetadata
	{
		private final String commit;
		private final String branch;
		private final boolean dirty;

		private GitMetadata(String commit, String branch, boolean dirty)
		{
			this.commit = commit;
			this.branch = branch;
			this.dirty = dirty;
		}

		private static GitMetadata read()
		{
			String commit = runCommand("git", "rev-parse", "HEAD");
			String branch = runCommand("git", "branch", "--show-current");
			String status = runCommand("git", "status", "--porcelain");
			return new GitMetadata(commit, branch, status.length() != 0 && !"unknown".equals(status));
		}
	}
}
