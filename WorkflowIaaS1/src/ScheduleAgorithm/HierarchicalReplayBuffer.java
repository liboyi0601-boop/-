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
		return summary;
	}
}
