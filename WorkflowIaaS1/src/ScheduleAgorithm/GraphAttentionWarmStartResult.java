package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphAttentionWarmStartResult
{
	private final GraphAttentionHierarchicalPolicy policy;
	private final Map<String, Object> summary;

	public GraphAttentionWarmStartResult(GraphAttentionHierarchicalPolicy policy, Map<String, Object> summary)
	{
		this.policy = policy;
		this.summary = new LinkedHashMap<String, Object>(summary);
	}

	public GraphAttentionHierarchicalPolicy getPolicy()
	{
		return policy;
	}

	public Map<String, Object> getSummary()
	{
		return new LinkedHashMap<String, Object>(summary);
	}
}
