package ScheduleAgorithm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComparisonSummaryWriter
{
	private ComparisonSummaryWriter()
	{
	}

	public static Map<String, Object> buildComparison(ExperimentMetrics expertMetrics, ExperimentMetrics learnedMetrics,
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

	public static Map<String, Object> buildVariantSummary(String variantName, Map<String, Object> trainingSummary,
			Map<String, Object> comparison)
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("variantName", variantName);
		summary.put("bestEpoch", trainingSummary.get("bestEpochByReward"));
		summary.put("bestEpochByViolationThenCost", trainingSummary.get("bestEpochByViolationThenCost"));
		summary.put("finalTaskLoss", trainingSummary.get("finalTaskLoss"));
		summary.put("finalVmLoss", trainingSummary.get("finalVmLoss"));
		summary.put("costDelta", comparison.get("costDelta"));
		summary.put("violationCountDelta", comparison.get("violationCountDelta"));
		summary.put("violationTimeDelta", comparison.get("violationTimeDelta"));
		summary.put("rewardDelta", comparison.get("rewardDelta"));
		summary.put("scheduleTimeDeltaMs", comparison.get("scheduleTimeDeltaMs"));
		return summary;
	}

	public static void writeComparisonSummary(Path path, List<Map<String, Object>> variantSummaries)
			throws IOException
	{
		Map<String, Object> root = new LinkedHashMap<String, Object>();
		root.put("variantCount", variantSummaries.size());
		root.put("variants", variantSummaries);
		JsonSupport.writeJson(path, root);
	}
}
