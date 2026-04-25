package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HierarchicalReplayBuffer
{
	private final List<MaskedDecisionExample> taskExamples;
	private final List<MaskedDecisionExample> vmExamples;

	public HierarchicalReplayBuffer()
	{
		this.taskExamples = new ArrayList<MaskedDecisionExample>();
		this.vmExamples = new ArrayList<MaskedDecisionExample>();
	}

	public void addTaskExample(MaskedDecisionExample example)
	{
		taskExamples.add(example);
	}

	public void addVmExample(MaskedDecisionExample example)
	{
		vmExamples.add(example);
	}

	public List<MaskedDecisionExample> getTaskExamples()
	{
		return Collections.unmodifiableList(taskExamples);
	}

	public List<MaskedDecisionExample> getVmExamples()
	{
		return Collections.unmodifiableList(vmExamples);
	}

	public Map<String, Object> toSummary()
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("taskExampleCount", taskExamples.size());
		summary.put("vmExampleCount", vmExamples.size());
		summary.put("taskFeatureSize", taskExamples.isEmpty() ? 0 : taskExamples.get(0).getFeatureSize());
		summary.put("vmFeatureSize", vmExamples.isEmpty() ? 0 : vmExamples.get(0).getFeatureSize());
		summary.put("taskExampleBenchmarkFamilyCounts", countBenchmarkFamilies(taskExamples));
		summary.put("vmExampleBenchmarkFamilyCounts", countBenchmarkFamilies(vmExamples));
		summary.put("taskExampleSuiteCounts", countSuiteNames(taskExamples));
		summary.put("vmExampleSuiteCounts", countSuiteNames(vmExamples));
		return summary;
	}

	private Map<String, Integer> countBenchmarkFamilies(List<MaskedDecisionExample> examples)
	{
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for(MaskedDecisionExample example: examples)
		{
			if(example.getOrigin() == null || example.getOrigin().getBenchmarkFamily() == null)
			{
				continue;
			}
			incrementCount(counts, example.getOrigin().getBenchmarkFamily());
		}
		return counts;
	}

	private Map<String, Integer> countSuiteNames(List<MaskedDecisionExample> examples)
	{
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for(MaskedDecisionExample example: examples)
		{
			if(example.getOrigin() == null || example.getOrigin().getSuiteName() == null)
			{
				continue;
			}
			incrementCount(counts, example.getOrigin().getSuiteName());
		}
		return counts;
	}

	private void incrementCount(Map<String, Integer> counts, String key)
	{
		Integer current = counts.get(key);
		counts.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
	}
}
