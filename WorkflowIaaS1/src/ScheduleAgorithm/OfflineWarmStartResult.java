package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OfflineWarmStartResult
{
	private final HierarchicalMaskedLearningPolicy policy;
	private final Map<String, Object> summary;

	public OfflineWarmStartResult(HierarchicalMaskedLearningPolicy policy, Map<String, Object> summary)
	{
		this.policy = policy;
		this.summary = new LinkedHashMap<String, Object>(summary);
	}

	public HierarchicalMaskedLearningPolicy getPolicy()
	{
		return policy;
	}

	public Map<String, Object> getSummary()
	{
		return new LinkedHashMap<String, Object>(summary);
	}
}
