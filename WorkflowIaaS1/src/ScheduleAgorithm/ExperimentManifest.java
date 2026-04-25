package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExperimentManifest
{
	private final String runId;
	private final String algorithmName;
	private final String gitCommit;
	private final String gitBranch;
	private final boolean dirtyFlag;
	private final String workloadPath;
	private final String workloadFingerprint;
	private final long seed;
	private final boolean traceEnabled;
	private final int decisionSnapshotLimit;
	private final String configHash;
	private final Map<String, Object> staticTagConfig;
	private final String startedAt;
	private final String finishedAt;
	private final Map<String, Object> dataset;

	public ExperimentManifest(String runId, String algorithmName, String gitCommit, String gitBranch,
			boolean dirtyFlag, String workloadPath, String workloadFingerprint, long seed, boolean traceEnabled,
			int decisionSnapshotLimit, String configHash, Map<String, Object> staticTagConfig, String startedAt,
			String finishedAt)
	{
		this(runId, algorithmName, gitCommit, gitBranch, dirtyFlag, workloadPath, workloadFingerprint, seed,
				traceEnabled, decisionSnapshotLimit, configHash, staticTagConfig, startedAt, finishedAt, null);
	}

	public ExperimentManifest(String runId, String algorithmName, String gitCommit, String gitBranch,
			boolean dirtyFlag, String workloadPath, String workloadFingerprint, long seed, boolean traceEnabled,
			int decisionSnapshotLimit, String configHash, Map<String, Object> staticTagConfig, String startedAt,
			String finishedAt, Map<String, Object> dataset)
	{
		this.runId = runId;
		this.algorithmName = algorithmName;
		this.gitCommit = gitCommit;
		this.gitBranch = gitBranch;
		this.dirtyFlag = dirtyFlag;
		this.workloadPath = workloadPath;
		this.workloadFingerprint = workloadFingerprint;
		this.seed = seed;
		this.traceEnabled = traceEnabled;
		this.decisionSnapshotLimit = decisionSnapshotLimit;
		this.configHash = configHash;
		this.staticTagConfig = new LinkedHashMap<String, Object>(staticTagConfig);
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.dataset = dataset == null ? null : new LinkedHashMap<String, Object>(dataset);
	}

	public Map<String, Object> toMap()
	{
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("runId", runId);
		map.put("algorithmName", algorithmName);
		map.put("gitCommit", gitCommit);
		map.put("gitBranch", gitBranch);
		map.put("dirtyFlag", dirtyFlag);
		map.put("workloadPath", workloadPath);
		map.put("workloadFingerprint", workloadFingerprint);
		map.put("seed", seed);
		map.put("traceEnabled", traceEnabled);
		map.put("decisionSnapshotLimit", decisionSnapshotLimit);
		map.put("configHash", configHash);
		map.put("staticTags", new LinkedHashMap<String, Object>(staticTagConfig));
		map.put("startedAt", startedAt);
		map.put("finishedAt", finishedAt);
		if(dataset != null)
		{
			map.put("dataset", new LinkedHashMap<String, Object>(dataset));
		}
		return map;
	}
}
