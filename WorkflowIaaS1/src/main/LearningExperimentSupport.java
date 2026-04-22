package main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ScheduleAgorithm.ComparisonSummaryWriter;
import ScheduleAgorithm.ConstraintAwareRewardModel;
import ScheduleAgorithm.ContextualExpertReplayCollector;
import ScheduleAgorithm.ContextualHierarchicalReplayBuffer;
import ScheduleAgorithm.EpochMetricsLogger;
import ScheduleAgorithm.ExperimentMetrics;
import ScheduleAgorithm.HierarchicalSchedulingPolicy;
import ScheduleAgorithm.HierarchicalReplayBuffer;
import ScheduleAgorithm.InMemoryExpertReplayCollector;
import ScheduleAgorithm.JsonSupport;
import ScheduleAgorithm.NOSF_Algorithms;
import ScheduleAgorithm.NoOpSchedulingTraceRecorder;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.SchedulingAction;
import ScheduleAgorithm.SchedulingPolicy;
import ScheduleAgorithm.SchedulingTraceRecorder;
import ScheduleAgorithm.TaskActionMask;
import ScheduleAgorithm.TaskCandidateSet;
import ScheduleAgorithm.TaskSelection;
import ScheduleAgorithm.TrainingTelemetry;
import ScheduleAgorithm.VmActionMask;
import ScheduleAgorithm.VmCandidateSet;
import ScheduleAgorithm.ResourceSelection;
import ScheduleAgorithm.WorkloadFingerprint;
import share.StaticfinalTags;
import vmInfo.SaaSVm;
import workflow.WTask;
import workflow.Workflow;
import workflow.WorkflowDatasetIO;

final class LearningExperimentSupport
{
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private LearningExperimentSupport()
	{
	}

	static Path createRunDirectory(Path outputRoot, RegressionSuite suite, String prefix) throws IOException
	{
		Path runDirectory = outputRoot
				.resolve(suite.getSuiteName())
				.resolve(prefix + "-" + RUN_ID_FORMATTER.format(OffsetDateTime.now()));
		Files.createDirectories(runDirectory);
		return runDirectory;
	}

	static Path requireWorkloadPath(RegressionSuite suite)
	{
		Path workloadPath = suite.getWorkloadPath();
		if(!Files.exists(workloadPath))
		{
			if(suite.isBenchmark())
			{
				throw new IllegalStateException("Benchmark workload file does not exist: " + workloadPath.toString()
						+ ". Generate it with ./scripts/generate_benchmark_workloads.sh " + suite.getSuiteName());
			}
			throw new IllegalStateException("Workload file does not exist: " + workloadPath.toString());
		}
		return workloadPath;
	}

	static ExpertReplayData collectExpertReplay(Path workloadPath, RegressionSuite suite,
			boolean collectFlatReplay, boolean collectContextualReplay) throws Exception
	{
		requireWorkloadPath(suite);
		InMemoryExpertReplayCollector flatCollector = collectFlatReplay
				? new InMemoryExpertReplayCollector(suite.getSuiteName())
				: null;
		ContextualExpertReplayCollector contextualCollector =
				collectContextualReplay ? new ContextualExpertReplayCollector(suite.getSuiteName()) : null;
		SchedulingTraceRecorder recorder = buildRecorder(flatCollector, contextualCollector);

		List<Workflow> expertWorkflows = WorkflowDatasetIO.readWorkflows(workloadPath);
		NOSF_Algorithms expertAlgorithm = new NOSF_Algorithms(recorder);
		expertAlgorithm.submitWorkflowList(expertWorkflows);
		expertAlgorithm.ScheduleWorkflow_By_NOSF();
		recorder.close();

		ExperimentMetrics expertMetrics = ExperimentMetrics.fromWorkflows(expertWorkflows);
		if(suite.isGolden())
		{
			suite.assertMatches(expertMetrics);
		}
		ConstraintAwareRewardModel rewardModel = new ConstraintAwareRewardModel();
		Map<String, Object> expertReward = rewardModel.evaluateEpisode(expertWorkflows, expertMetrics);

		return new ExpertReplayData(expertWorkflows, expertMetrics, expertReward,
				flatCollector == null ? null : flatCollector.getReplayBuffer(),
				contextualCollector == null ? null : contextualCollector.getReplayBuffer());
	}

	static ExpertReplayData collectReferenceExpertReplay(RegressionSuite trainSuite, ExpertReplayData trainExpertReplayData,
			RegressionSuite evalSuite) throws Exception
	{
		if(trainSuite == evalSuite)
		{
			return trainExpertReplayData;
		}
		return collectExpertReplay(evalSuite.getWorkloadPath(), evalSuite, false, false);
	}

	static EvaluationResult evaluate(Path workloadPath, SchedulingPolicy baselinePolicy,
			HierarchicalSchedulingPolicy hierarchicalPolicy) throws Exception
	{
		List<Workflow> workflows = WorkflowDatasetIO.readWorkflows(workloadPath);
		PolicyValidationRecorder validationRecorder = new PolicyValidationRecorder();
		NOSF_Algorithms algorithm = new NOSF_Algorithms(validationRecorder, baselinePolicy, hierarchicalPolicy);
		algorithm.submitWorkflowList(workflows);
		algorithm.ScheduleWorkflow_By_NOSF();

		ExperimentMetrics metrics = ExperimentMetrics.fromWorkflows(workflows);
		ConstraintAwareRewardModel rewardModel = new ConstraintAwareRewardModel();
		Map<String, Object> reward = rewardModel.evaluateEpisode(workflows, metrics);
		return new EvaluationResult(workflows, metrics, reward,
				validationRecorder.getInvalidTaskActionCount(),
				validationRecorder.getInvalidVmActionCount());
	}

	static Map<String, Object> buildManifest(String algorithmName, RegressionSuite trainSuite, RegressionSuite evalSuite,
			String startedAt, String finishedAt, Map<String, Object> hyperParameters,
			ExpertReplayData trainExpertReplayData, ExpertReplayData evalExpertReplayData) throws IOException
	{
		GitMetadata gitMetadata = GitMetadata.read();
		Path trainWorkloadPath = requireWorkloadPath(trainSuite);
		Map<String, Object> trainDataset = buildDatasetMetadata(trainSuite, trainWorkloadPath, trainExpertReplayData);
		Map<String, Object> evalDataset = buildDatasetMetadata(evalSuite, requireWorkloadPath(evalSuite),
				evalExpertReplayData);
		Map<String, Object> staticTags = buildStaticTagConfig();
		Map<String, Object> config = new LinkedHashMap<String, Object>();
		config.put("algorithmName", algorithmName);
		config.put("trainDataset", trainDataset);
		config.put("evalDataset", evalDataset);
		config.put("hyperParameters", hyperParameters);
		config.put("staticTags", staticTags);

		Map<String, Object> manifest = new LinkedHashMap<String, Object>();
		manifest.put("algorithmName", algorithmName);
		manifest.put("suiteName", trainSuite.getSuiteName());
		manifest.put("trainSuiteName", trainSuite.getSuiteName());
		manifest.put("evalSuiteName", evalSuite.getSuiteName());
		manifest.put("workloadPath", trainWorkloadPath.toString());
		manifest.put("workloadFingerprint", WorkloadFingerprint.fromFile(trainWorkloadPath));
		manifest.put("gitCommit", gitMetadata.commit);
		manifest.put("gitBranch", gitMetadata.branch);
		manifest.put("dirtyFlag", gitMetadata.dirty);
		manifest.put("startedAt", startedAt);
		manifest.put("finishedAt", finishedAt);
		manifest.put("staticTags", staticTags);
		manifest.put("hyperParameters", hyperParameters);
		manifest.put("trainDataset", trainDataset);
		manifest.put("evalDataset", evalDataset);
		manifest.put("configHash",
				WorkloadFingerprint.sha256(JsonSupport.toJson(config).getBytes(StandardCharsets.UTF_8)));
		return manifest;
	}

	static void writeArtifacts(Path runDirectory, ExpertReplayData referenceExpertReplayData,
			Map<String, Object> replaySummary, Map<String, Object> trainingSummary, TrainingTelemetry telemetry,
			EvaluationResult learnedResult, Map<String, Object> manifest) throws IOException
	{
		Map<String, Object> comparison = ComparisonSummaryWriter.buildComparison(
				referenceExpertReplayData.getExpertMetrics(), learnedResult.getMetrics(),
				referenceExpertReplayData.getExpertReward(), learnedResult.getReward());

		JsonSupport.writeJson(runDirectory.resolve("expert-metrics.json"),
				referenceExpertReplayData.getExpertMetrics().toMap());
		JsonSupport.writeJson(runDirectory.resolve("expert-reward.json"),
				referenceExpertReplayData.getExpertReward());
		JsonSupport.writeJson(runDirectory.resolve("replay-summary.json"), replaySummary);
		JsonSupport.writeJson(runDirectory.resolve("training-summary.json"), trainingSummary);
		writeEpochMetrics(runDirectory.resolve("epoch-metrics.jsonl"), telemetry);
		JsonSupport.writeJson(runDirectory.resolve("learned-metrics.json"), learnedResult.getMetrics().toMap());
		JsonSupport.writeJson(runDirectory.resolve("learned-reward.json"), learnedResult.getReward());
		JsonSupport.writeJson(runDirectory.resolve("comparison.json"), comparison);
		JsonSupport.writeJson(runDirectory.resolve("manifest.json"), manifest);
	}

	static Map<String, Object> buildReplaySummary(HierarchicalReplayBuffer flatReplay,
			ContextualHierarchicalReplayBuffer contextualReplay)
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		if(flatReplay != null)
		{
			summary.put("flatReplay", flatReplay.toSummary());
		}
		if(contextualReplay != null)
		{
			summary.put("contextualReplay", contextualReplay.toSummary());
		}
		return summary;
	}

	private static Map<String, Object> buildDatasetMetadata(RegressionSuite suite, Path workloadPath,
			ExpertReplayData expertReplayData) throws IOException
	{
		Map<String, Object> metadata = suite.buildDatasetMetadata(
				Integer.valueOf(expertReplayData.getExpertMetrics().getWorkflowCount()),
				Integer.valueOf(expertReplayData.getExpertMetrics().getTotalTaskCount()));
		metadata.put("workloadFingerprint", WorkloadFingerprint.fromFile(workloadPath));
		return metadata;
	}

	static void validateContextualReplay(ContextualHierarchicalReplayBuffer replayBuffer)
	{
		if(replayBuffer == null)
		{
			return;
		}
		if(replayBuffer.getTaskExamples().isEmpty() || replayBuffer.getVmExamples().isEmpty())
		{
			throw new IllegalStateException("Contextual replay buffer is empty");
		}
		if(replayBuffer.computeInvalidTaskActionCount() != 0)
		{
			throw new IllegalStateException("invalid task action count mismatch: "
					+ replayBuffer.computeInvalidTaskActionCount());
		}
		if(replayBuffer.computeInvalidVmActionCount() != 0)
		{
			throw new IllegalStateException("invalid vm action count mismatch: "
					+ replayBuffer.computeInvalidVmActionCount());
		}
	}

	static EpochMetricsLogger newEpochLogger(Path runDirectory) throws IOException
	{
		return new EpochMetricsLogger(runDirectory.resolve("epoch-metrics.jsonl"));
	}

	static void logEpoch(EpochMetricsLogger logger, TrainingTelemetry telemetry, int epoch,
			Map<String, Object> trainingMetrics, EvaluationResult evaluationResult) throws IOException
	{
		telemetry.recordEpoch(epoch, trainingMetrics, evaluationResult.getMetrics(),
				evaluationResult.getReward(), evaluationResult.getInvalidTaskActionCount(),
				evaluationResult.getInvalidVmActionCount());
		logger.append(telemetry.getEpochMetrics().get(telemetry.getEpochMetrics().size() - 1));
	}

	private static void writeEpochMetrics(Path path, TrainingTelemetry telemetry) throws IOException
	{
		try(EpochMetricsLogger logger = new EpochMetricsLogger(path))
		{
			for(Map<String, Object> epochMetric: telemetry.getEpochMetrics())
			{
				logger.append(epochMetric);
			}
		}
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

	private static SchedulingTraceRecorder buildRecorder(InMemoryExpertReplayCollector flatCollector,
			ContextualExpertReplayCollector contextualCollector)
	{
		List<SchedulingTraceRecorder> recorders = new ArrayList<SchedulingTraceRecorder>();
		if(flatCollector != null)
		{
			recorders.add(flatCollector);
		}
		if(contextualCollector != null)
		{
			recorders.add(contextualCollector);
		}
		if(recorders.isEmpty())
		{
			return new NoOpSchedulingTraceRecorder();
		}
		if(recorders.size() == 1)
		{
			return recorders.get(0);
		}
		return new FanOutSchedulingTraceRecorder(recorders);
	}

	static final class ExpertReplayData
	{
		private final List<Workflow> expertWorkflows;
		private final ExperimentMetrics expertMetrics;
		private final Map<String, Object> expertReward;
		private final HierarchicalReplayBuffer flatReplayBuffer;
		private final ContextualHierarchicalReplayBuffer contextualReplayBuffer;

		private ExpertReplayData(List<Workflow> expertWorkflows, ExperimentMetrics expertMetrics,
				Map<String, Object> expertReward, HierarchicalReplayBuffer flatReplayBuffer,
				ContextualHierarchicalReplayBuffer contextualReplayBuffer)
		{
			this.expertWorkflows = expertWorkflows;
			this.expertMetrics = expertMetrics;
			this.expertReward = expertReward;
			this.flatReplayBuffer = flatReplayBuffer;
			this.contextualReplayBuffer = contextualReplayBuffer;
		}

		List<Workflow> getExpertWorkflows()
		{
			return expertWorkflows;
		}

		ExperimentMetrics getExpertMetrics()
		{
			return expertMetrics;
		}

		Map<String, Object> getExpertReward()
		{
			return expertReward;
		}

		HierarchicalReplayBuffer getFlatReplayBuffer()
		{
			return flatReplayBuffer;
		}

		ContextualHierarchicalReplayBuffer getContextualReplayBuffer()
		{
			return contextualReplayBuffer;
		}
	}

	static final class EvaluationResult
	{
		private final List<Workflow> workflows;
		private final ExperimentMetrics metrics;
		private final Map<String, Object> reward;
		private final int invalidTaskActionCount;
		private final int invalidVmActionCount;

		private EvaluationResult(List<Workflow> workflows, ExperimentMetrics metrics, Map<String, Object> reward,
				int invalidTaskActionCount, int invalidVmActionCount)
		{
			this.workflows = workflows;
			this.metrics = metrics;
			this.reward = reward;
			this.invalidTaskActionCount = invalidTaskActionCount;
			this.invalidVmActionCount = invalidVmActionCount;
		}

		List<Workflow> getWorkflows()
		{
			return workflows;
		}

		ExperimentMetrics getMetrics()
		{
			return metrics;
		}

		Map<String, Object> getReward()
		{
			return reward;
		}

		int getInvalidTaskActionCount()
		{
			return invalidTaskActionCount;
		}

		int getInvalidVmActionCount()
		{
			return invalidVmActionCount;
		}
	}

	private static final class PolicyValidationRecorder implements SchedulingTraceRecorder
	{
		private int invalidTaskActionCount;
		private int invalidVmActionCount;

		public boolean isEnabled()
		{
			return true;
		}

		public boolean shouldCaptureStateSnapshot(int decisionIndex)
		{
			return false;
		}

		public void recordDecisionCandidate(int currentTime, TaskCandidateSet taskSet, TaskActionMask taskMask,
				List<SaaSVm> candidateVms, int workflowCount, int activeVmCount, int offVmCount,
				int globalTaskPoolSize) throws IOException
		{
		}

		public void recordDecisionChosen(int currentTime, TaskSelection taskSelection, TaskActionMask taskMask,
				TaskCandidateSet taskSet, VmCandidateSet vmSet, VmActionMask vmMask,
				ResourceSelection resourceSelection, SchedulingAction action,
				double estimatedCostIncrement, ScheduleAgorithm.SchedulingState snapshot) throws IOException
		{
			if(!taskMask.isValid(taskSelection.getSelectedIndex()))
			{
				invalidTaskActionCount++;
			}
			if(!vmMask.isValid(resourceSelection.getSelectedIndex()))
			{
				invalidVmActionCount++;
			}
		}

		public void recordActionApplied(int currentTime, SchedulingAction action, SaaSVm appliedVm) throws IOException
		{
		}

		public void recordTaskFinish(int currentTime, List<WTask> finishedTasks, List<WTask> readyTasksAfterFinish)
				throws IOException
		{
		}

		public void recordVmTurnoff(int currentTime, List<SaaSVm> turnOffVmSet, int turnOffVmTime)
				throws IOException
		{
		}

		public void close() throws IOException
		{
		}

		public int getInvalidTaskActionCount()
		{
			return invalidTaskActionCount;
		}

		public int getInvalidVmActionCount()
		{
			return invalidVmActionCount;
		}
	}

	private static final class FanOutSchedulingTraceRecorder implements SchedulingTraceRecorder
	{
		private final List<SchedulingTraceRecorder> recorders;

		private FanOutSchedulingTraceRecorder(List<SchedulingTraceRecorder> recorders)
		{
			this.recorders = recorders;
		}

		public boolean isEnabled()
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				if(recorder.isEnabled())
				{
					return true;
				}
			}
			return false;
		}

		public boolean shouldCaptureStateSnapshot(int decisionIndex)
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				if(recorder.shouldCaptureStateSnapshot(decisionIndex))
				{
					return true;
				}
			}
			return false;
		}

		public void recordDecisionCandidate(int currentTime, TaskCandidateSet taskSet, TaskActionMask taskMask,
				List<SaaSVm> candidateVms, int workflowCount, int activeVmCount, int offVmCount,
				int globalTaskPoolSize) throws IOException
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				recorder.recordDecisionCandidate(currentTime, taskSet, taskMask, candidateVms,
						workflowCount, activeVmCount, offVmCount, globalTaskPoolSize);
			}
		}

		public void recordDecisionChosen(int currentTime, TaskSelection taskSelection, TaskActionMask taskMask,
				TaskCandidateSet taskSet, VmCandidateSet vmSet, VmActionMask vmMask,
				ResourceSelection resourceSelection, SchedulingAction action,
				double estimatedCostIncrement, ScheduleAgorithm.SchedulingState snapshot) throws IOException
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				recorder.recordDecisionChosen(currentTime, taskSelection, taskMask, taskSet, vmSet, vmMask,
						resourceSelection, action, estimatedCostIncrement, snapshot);
			}
		}

		public void recordActionApplied(int currentTime, SchedulingAction action, SaaSVm appliedVm) throws IOException
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				recorder.recordActionApplied(currentTime, action, appliedVm);
			}
		}

		public void recordTaskFinish(int currentTime, List<WTask> finishedTasks, List<WTask> readyTasksAfterFinish)
				throws IOException
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				recorder.recordTaskFinish(currentTime, finishedTasks, readyTasksAfterFinish);
			}
		}

		public void recordVmTurnoff(int currentTime, List<SaaSVm> turnOffVmSet, int turnOffVmTime)
				throws IOException
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				recorder.recordVmTurnoff(currentTime, turnOffVmSet, turnOffVmTime);
			}
		}

		public void close() throws IOException
		{
			for(SchedulingTraceRecorder recorder: recorders)
			{
				recorder.close();
			}
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
			String branch = runCommand("git", "rev-parse", "--abbrev-ref", "HEAD");
			String status = runCommand("git", "status", "--short");
			return new GitMetadata(commit, branch, status != null && !status.trim().isEmpty());
		}
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
}
