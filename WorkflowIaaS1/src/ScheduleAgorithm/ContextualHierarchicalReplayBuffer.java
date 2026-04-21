package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContextualHierarchicalReplayBuffer
{
	private final List<TaskDecisionContextExample> taskExamples;
	private final List<VmDecisionContextExample> vmExamples;

	public ContextualHierarchicalReplayBuffer()
	{
		this.taskExamples = new ArrayList<TaskDecisionContextExample>();
		this.vmExamples = new ArrayList<VmDecisionContextExample>();
	}

	public void addTaskExample(TaskDecisionContextExample example)
	{
		taskExamples.add(example);
	}

	public void addVmExample(VmDecisionContextExample example)
	{
		vmExamples.add(example);
	}

	public List<TaskDecisionContextExample> getTaskExamples()
	{
		return Collections.unmodifiableList(taskExamples);
	}

	public List<VmDecisionContextExample> getVmExamples()
	{
		return Collections.unmodifiableList(vmExamples);
	}

	public Map<String, Object> toSummary()
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("taskExampleCount", taskExamples.size());
		summary.put("vmExampleCount", vmExamples.size());
		summary.put("invalidTaskActionCount", computeInvalidTaskActionCount());
		summary.put("invalidVmActionCount", computeInvalidVmActionCount());
		summary.put("meanTaskCandidateCount", computeMeanTaskCandidateCount());
		summary.put("meanVmCandidateCount", computeMeanVmCandidateCount());
		summary.put("maxTaskCandidateCount", computeMaxTaskCandidateCount());
		summary.put("maxVmCandidateCount", computeMaxVmCandidateCount());
		return summary;
	}

	private double computeMeanTaskCandidateCount()
	{
		if(taskExamples.isEmpty())
		{
			return 0.0;
		}

		double total = 0.0;
		for(TaskDecisionContextExample example: taskExamples)
		{
			total += example.getTaskSet().size();
		}
		return total / taskExamples.size();
	}

	private double computeMeanVmCandidateCount()
	{
		if(vmExamples.isEmpty())
		{
			return 0.0;
		}

		double total = 0.0;
		for(VmDecisionContextExample example: vmExamples)
		{
			total += example.getVmSet().size();
		}
		return total / vmExamples.size();
	}

	private int computeMaxTaskCandidateCount()
	{
		int max = 0;
		for(TaskDecisionContextExample example: taskExamples)
		{
			if(example.getTaskSet().size() > max)
			{
				max = example.getTaskSet().size();
			}
		}
		return max;
	}

	private int computeMaxVmCandidateCount()
	{
		int max = 0;
		for(VmDecisionContextExample example: vmExamples)
		{
			if(example.getVmSet().size() > max)
			{
				max = example.getVmSet().size();
			}
		}
		return max;
	}

	public int computeInvalidTaskActionCount()
	{
		int invalidCount = 0;
		for(TaskDecisionContextExample example: taskExamples)
		{
			if(!example.getTaskMask().isValid(example.getChosenTaskIndex()))
			{
				invalidCount++;
			}
		}
		return invalidCount;
	}

	public int computeInvalidVmActionCount()
	{
		int invalidCount = 0;
		for(VmDecisionContextExample example: vmExamples)
		{
			if(!example.getVmMask().isValid(example.getChosenVmIndex()))
			{
				invalidCount++;
			}
		}
		return invalidCount;
	}
}
