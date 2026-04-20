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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ScheduleAgorithm.BaselineDiffAnalyzer;
import ScheduleAgorithm.ExperimentManifest;
import ScheduleAgorithm.ExperimentMetrics;
import ScheduleAgorithm.JsonSupport;
import ScheduleAgorithm.JsonlSchedulingTraceRecorder;
import ScheduleAgorithm.NOSF_Algorithms;
import ScheduleAgorithm.NoOpSchedulingTraceRecorder;
import ScheduleAgorithm.PostRunAnalyzer;
import ScheduleAgorithm.RegressionSuite;
import ScheduleAgorithm.SchedulingTraceRecorder;
import ScheduleAgorithm.WorkloadFingerprint;
import share.StaticfinalTags;
import workflow.Workflow;
import workflow.WorkflowDatasetIO;

public class RegressionRunner
{
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
	private static final int DEFAULT_SNAPSHOT_LIMIT = 50;

	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		List<Path> runDirectories = new ArrayList<Path>();
		Map<RegressionSuite, Path> runDirectoryBySuite = new LinkedHashMap<RegressionSuite, Path>();

		for(RegressionSuite suite: options.suites)
		{
			Path runDirectory = runSuite(suite, options);
			runDirectories.add(runDirectory);
			runDirectoryBySuite.put(suite, runDirectory);
			validatePhaseSevenTrace(runDirectory, options.traceEnabled);
		}

		if(options.traceEnabled && runDirectoryBySuite.containsKey(RegressionSuite.GOLDEN))
		{
			Path goldenRepeatRun = runSuite(RegressionSuite.GOLDEN, options);
			runDirectories.add(goldenRepeatRun);
			validatePhaseSevenTrace(goldenRepeatRun, true);
			assertNoDivergence(runDirectoryBySuite.get(RegressionSuite.GOLDEN), goldenRepeatRun);
		}

		System.out.println("Regression suites completed: " + runDirectories.size());
		for(Path runDirectory: runDirectories)
		{
			System.out.println("ArtifactDir: " + runDirectory.toString());
		}
	}

	private static Path runSuite(RegressionSuite suite, RunnerOptions options) throws Exception
	{
		Path workloadPath = suite.getWorkloadPath();
		if(!Files.exists(workloadPath))
		{
			throw new IllegalStateException("Workload file does not exist: " + workloadPath);
		}

		List<Workflow> workflows = WorkflowDatasetIO.readWorkflows(workloadPath);
		if(workflows.size() != suite.getExpectedWorkflowCount())
		{
			throw new IllegalStateException("Workload count mismatch for " + suite.getSuiteName()
					+ ": expected " + suite.getExpectedWorkflowCount() + ", actual " + workflows.size());
		}

		String runId = suite.getSuiteName() + "-" + RUN_ID_FORMATTER.format(OffsetDateTime.now());
		Path runDirectory = options.outputRoot.resolve(suite.getSuiteName()).resolve(runId);
		Files.createDirectories(runDirectory);

		Path tracePath = runDirectory.resolve("trace.jsonl");
		SchedulingTraceRecorder recorder = options.traceEnabled
				? new JsonlSchedulingTraceRecorder(tracePath, options.snapshotLimit)
				: new NoOpSchedulingTraceRecorder();

		String startedAt = OffsetDateTime.now().toString();
		NOSF_Algorithms algorithm = new NOSF_Algorithms(recorder);
		try
		{
			algorithm.submitWorkflowList(workflows);
			algorithm.ScheduleWorkflow_By_NOSF();
		}
		finally
		{
			recorder.close();
		}
		String finishedAt = OffsetDateTime.now().toString();

		if(!options.traceEnabled)
		{
			Files.write(tracePath, new byte[0]);
		}

		ExperimentMetrics metrics = ExperimentMetrics.fromWorkflows(workflows);
		if(metrics.getFinishedTaskCount() != metrics.getTotalTaskCount())
		{
			throw new IllegalStateException("FinishedTaskCount mismatch: " + metrics.getFinishedTaskCount()
					+ " != " + metrics.getTotalTaskCount());
		}
		suite.assertMatches(metrics);

		PostRunAnalyzer analyzer = new PostRunAnalyzer();
		Map<String, Object> analysis = analyzer.analyze(workflows, tracePath);

		GitMetadata gitMetadata = GitMetadata.read();
		String workloadFingerprint = WorkloadFingerprint.fromFile(workloadPath);
		Map<String, Object> staticTags = buildStaticTagConfig();
		Map<String, Object> configMap = buildConfigMap(suite, options, staticTags);
		String configHash = WorkloadFingerprint.sha256(JsonSupport.toJson(configMap).getBytes(StandardCharsets.UTF_8));

		ExperimentManifest manifest = new ExperimentManifest(
				runId,
				"NOSF",
				gitMetadata.commit,
				gitMetadata.branch,
				gitMetadata.dirty,
				workloadPath.toString(),
				workloadFingerprint,
				suite.getSeed(),
				options.traceEnabled,
				options.snapshotLimit,
				configHash,
				staticTags,
				startedAt,
				finishedAt);

		JsonSupport.writeJson(runDirectory.resolve("metrics.json"), metrics.toMap());
		JsonSupport.writeJson(runDirectory.resolve("manifest.json"), manifest.toMap());
		JsonSupport.writeJson(runDirectory.resolve("analysis.json"), analysis);

		System.out.println("Suite " + suite.getSuiteName() + " finished at " + runDirectory.toString());
		return runDirectory;
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

	private static Map<String, Object> buildConfigMap(RegressionSuite suite, RunnerOptions options,
			Map<String, Object> staticTags)
	{
		Map<String, Object> config = new LinkedHashMap<String, Object>();
		config.put("suiteName", suite.getSuiteName());
		config.put("seed", suite.getSeed());
		config.put("traceEnabled", options.traceEnabled);
		config.put("decisionSnapshotLimit", options.snapshotLimit);
		config.put("workloadPath", suite.getWorkloadPath().toString());
		config.put("staticTags", staticTags);
		return config;
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

	@SuppressWarnings("unchecked")
	private static void validatePhaseSevenTrace(Path runDirectory, boolean traceEnabled) throws IOException
	{
		if(!traceEnabled)
		{
			return;
		}

		Path tracePath = runDirectory.resolve("trace.jsonl");
		List<String> lines = Files.readAllLines(tracePath, StandardCharsets.UTF_8);
		int invalidTaskActionCount = 0;
		int invalidVmActionCount = 0;

		for(String line: lines)
		{
			if(line.trim().isEmpty())
			{
				continue;
			}

			Object parsed = JsonSupport.parseJson(line);
			if(!(parsed instanceof Map<?, ?>))
			{
				continue;
			}

			Map<String, Object> event = (Map<String, Object>)parsed;
			if(!"decision_chosen".equals(event.get("eventType")))
			{
				continue;
			}

			Map<String, Object> taskSelection = (Map<String, Object>)event.get("taskSelection");
			Map<String, Object> resourceSelection = (Map<String, Object>)event.get("resourceSelection");
			if(taskSelection != null && Boolean.FALSE.equals(taskSelection.get("validSelection")))
			{
				invalidTaskActionCount++;
			}
			if(resourceSelection != null && Boolean.FALSE.equals(resourceSelection.get("validSelection")))
			{
				invalidVmActionCount++;
			}
		}

		if(invalidTaskActionCount != 0)
		{
			throw new IllegalStateException("invalid task action count mismatch: " + invalidTaskActionCount);
		}
		if(invalidVmActionCount != 0)
		{
			throw new IllegalStateException("invalid vm action count mismatch: " + invalidVmActionCount);
		}
	}

	private static void assertNoDivergence(Path leftRunDir, Path rightRunDir) throws IOException
	{
		BaselineDiffAnalyzer analyzer = new BaselineDiffAnalyzer();
		Map<String, Object> diff = analyzer.analyze(leftRunDir, rightRunDir);
		if(!"no_divergence".equals(diff.get("status")))
		{
			throw new IllegalStateException("Golden trace diverged: " + JsonSupport.toJson(diff));
		}
	}

	private static final class RunnerOptions
	{
		private final List<RegressionSuite> suites;
		private final boolean traceEnabled;
		private final int snapshotLimit;
		private final Path outputRoot;

		private RunnerOptions(List<RegressionSuite> suites, boolean traceEnabled, int snapshotLimit, Path outputRoot)
		{
			this.suites = suites;
			this.traceEnabled = traceEnabled;
			this.snapshotLimit = snapshotLimit;
			this.outputRoot = outputRoot;
		}

		private static RunnerOptions parse(String[] args)
		{
			List<RegressionSuite> suites = new ArrayList<RegressionSuite>();
			boolean traceEnabled = true;
			int snapshotLimit = DEFAULT_SNAPSHOT_LIMIT;
			Path outputRoot = Paths.get("regression-artifacts");

			for(int index = 0; index < args.length; index++)
			{
				String arg = args[index];
				if("--suite".equals(arg))
				{
					index++;
					suites.add(RegressionSuite.fromName(args[index]));
				}
				else if("--no-trace".equals(arg))
				{
					traceEnabled = false;
				}
				else if("--snapshot-limit".equals(arg))
				{
					index++;
					snapshotLimit = Integer.parseInt(args[index]);
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

			if(suites.isEmpty())
			{
				suites.addAll(RegressionSuite.defaultSuites());
			}

			return new RunnerOptions(suites, traceEnabled, snapshotLimit, outputRoot);
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
