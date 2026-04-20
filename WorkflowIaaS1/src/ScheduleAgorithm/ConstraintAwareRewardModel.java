package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import workflow.WTask;
import workflow.Workflow;

public final class ConstraintAwareRewardModel
{
	private final double violationCountWeight;
	private final double violationTimeWeight;
	private final double costWeight;
	private final double makespanWeight;

	public ConstraintAwareRewardModel()
	{
		this(2000.0, 1000.0, 1.0, 0.05);
	}

	public ConstraintAwareRewardModel(double violationCountWeight, double violationTimeWeight,
			double costWeight, double makespanWeight)
	{
		this.violationCountWeight = violationCountWeight;
		this.violationTimeWeight = violationTimeWeight;
		this.costWeight = costWeight;
		this.makespanWeight = makespanWeight;
	}

	public Map<String, Object> evaluateEpisode(List<Workflow> workflows, ExperimentMetrics metrics)
	{
		double meanWorkflowMakespan = computeMeanWorkflowMakespan(workflows);
		double violationPenalty = metrics.getViolationCount() * violationCountWeight
				+ Math.max(0.0, metrics.getViolationTime()) * violationTimeWeight;
		double costPenalty = metrics.getTotalCost() * costWeight;
		double makespanPenalty = meanWorkflowMakespan * makespanWeight;
		double totalReward = -(violationPenalty + costPenalty + makespanPenalty);

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("violationCountWeight", violationCountWeight);
		summary.put("violationTimeWeight", violationTimeWeight);
		summary.put("costWeight", costWeight);
		summary.put("makespanWeight", makespanWeight);
		summary.put("meanWorkflowMakespan", meanWorkflowMakespan);
		summary.put("violationPenalty", violationPenalty);
		summary.put("costPenalty", costPenalty);
		summary.put("makespanPenalty", makespanPenalty);
		summary.put("totalReward", totalReward);
		return summary;
	}

	private double computeMeanWorkflowMakespan(List<Workflow> workflows)
	{
		if(workflows.isEmpty())
		{
			return 0.0;
		}

		double totalMakespan = 0.0;
		for(Workflow workflow: workflows)
		{
			int maxRealFinishTime = workflow.getArrivalTime();
			for(WTask task: workflow.getTaskList())
			{
				if(task.getRealFinishTime() > maxRealFinishTime)
				{
					maxRealFinishTime = task.getRealFinishTime();
				}
			}
			totalMakespan += (maxRealFinishTime - workflow.getArrivalTime());
		}
		return totalMakespan / workflows.size();
	}
}
