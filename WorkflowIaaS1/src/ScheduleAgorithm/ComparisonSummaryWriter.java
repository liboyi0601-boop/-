package ScheduleAgorithm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComparisonSummaryWriter
{
	private static final double DEFAULT_NORMALIZATION_EPSILON = 1e-9;

	private ComparisonSummaryWriter()
	{
	}

	public static Map<String, Object> buildComparison(ExperimentMetrics expertMetrics, ExperimentMetrics learnedMetrics,
			Map<String, Object> expertReward, Map<String, Object> learnedReward)
	{
		return buildComparison(expertMetrics, learnedMetrics, expertReward, learnedReward, false,
				DEFAULT_NORMALIZATION_EPSILON);
	}

	public static Map<String, Object> buildComparison(ExperimentMetrics expertMetrics, ExperimentMetrics learnedMetrics,
			Map<String, Object> expertReward, Map<String, Object> learnedReward,
			boolean normalizedComparisonEnabled, double normalizationEpsilon)
	{
		return buildComparison(expertMetrics, learnedMetrics, null, null, expertReward, learnedReward,
				normalizedComparisonEnabled, normalizationEpsilon);
	}

	public static Map<String, Object> buildComparison(ExperimentMetrics expertMetrics, ExperimentMetrics learnedMetrics,
			DeadlineViolationMetrics expertDeadlineMetrics, DeadlineViolationMetrics learnedDeadlineMetrics,
			Map<String, Object> expertReward, Map<String, Object> learnedReward,
			boolean normalizedComparisonEnabled, double normalizationEpsilon)
	{
		Map<String, Object> comparison = new LinkedHashMap<String, Object>();
		double costDelta = learnedMetrics.getTotalCost() - expertMetrics.getTotalCost();
		double violationCountDelta = learnedMetrics.getViolationCount() - expertMetrics.getViolationCount();
		double violationTimeDelta = learnedMetrics.getViolationTime() - expertMetrics.getViolationTime();
		double rewardDelta = ((Number)learnedReward.get("totalReward")).doubleValue()
				- ((Number)expertReward.get("totalReward")).doubleValue();
		comparison.put("costDelta", costDelta);
		comparison.put("violationCountDelta", violationCountDelta);
		comparison.put("violationTimeDelta", violationTimeDelta);
		comparison.put("violationTimeSemantics", DeadlineViolationMetrics.RAW_VIOLATION_TIME_SEMANTICS);
		comparison.put("resourceUtilizationDelta",
				learnedMetrics.getResourceUtilization() - expertMetrics.getResourceUtilization());
		comparison.put("scheduleTimeDeltaMs", learnedMetrics.getScheduleTimeMs() - expertMetrics.getScheduleTimeMs());
		comparison.put("rewardDelta", rewardDelta);
		if(expertDeadlineMetrics != null && learnedDeadlineMetrics != null)
		{
			double positiveViolationTimeDelta = learnedDeadlineMetrics.getPositiveViolationTime()
					- expertDeadlineMetrics.getPositiveViolationTime();
			double positiveViolationTimeRatioDelta = learnedDeadlineMetrics.getPositiveViolationTimeRatio()
					- expertDeadlineMetrics.getPositiveViolationTimeRatio();
			double violationCountRatioCorrectedDelta = learnedDeadlineMetrics.getViolationCountRatioCorrected()
					- expertDeadlineMetrics.getViolationCountRatioCorrected();
			comparison.put("positiveViolationTimeDelta", positiveViolationTimeDelta);
			comparison.put("positiveViolationTimeRatioDelta", positiveViolationTimeRatioDelta);
			comparison.put("violationCountRatioCorrectedDelta", violationCountRatioCorrectedDelta);
			comparison.put("correctedDeadlineMetricSource", DeadlineViolationMetrics.FINISH_TIME_SOURCE);
		}
		if(normalizedComparisonEnabled)
		{
			double safeEpsilon = normalizationEpsilon > 0.0 ? normalizationEpsilon : DEFAULT_NORMALIZATION_EPSILON;
			double expertMakespan = numberValue(expertReward.get("meanWorkflowMakespan"));
			double learnedMakespan = numberValue(learnedReward.get("meanWorkflowMakespan"));
			double meanWorkflowMakespanDelta = learnedMakespan - expertMakespan;
			comparison.put("meanWorkflowMakespanDelta", meanWorkflowMakespanDelta);
			comparison.put("normalizedCostDelta",
					normalizeDelta(costDelta, expertMetrics.getTotalCost(), safeEpsilon));
			comparison.put("normalizedViolationCountDelta",
					normalizeDelta(violationCountDelta, expertMetrics.getViolationCount(), safeEpsilon));
			comparison.put("normalizedViolationTimeDelta",
					normalizeDelta(violationTimeDelta, expertMetrics.getViolationTime(), safeEpsilon));
			comparison.put("normalizedMakespanDelta",
					normalizeDelta(meanWorkflowMakespanDelta, expertMakespan, safeEpsilon));
			if(expertDeadlineMetrics != null && learnedDeadlineMetrics != null)
			{
				comparison.put("normalizedPositiveViolationTimeDelta",
						normalizeDelta(numberValue(comparison.get("positiveViolationTimeDelta")),
								expertDeadlineMetrics.getPositiveViolationTime(), safeEpsilon));
				comparison.put("normalizedPositiveViolationTimeRatioDelta",
						normalizeDelta(numberValue(comparison.get("positiveViolationTimeRatioDelta")),
								expertDeadlineMetrics.getPositiveViolationTimeRatio(), safeEpsilon));
				comparison.put("normalizedViolationCountRatioCorrectedDelta",
						normalizeDelta(numberValue(comparison.get("violationCountRatioCorrectedDelta")),
								expertDeadlineMetrics.getViolationCountRatioCorrected(), safeEpsilon));
			}
			comparison.put("normalizedComparisonReference", "expert");
			comparison.put("normalizedComparisonEpsilon", safeEpsilon);
			comparison.put("normalizedDenominatorRule", "max(abs(reference), epsilon)");
			comparison.put("zeroReferenceHandling", "epsilon-floor");
		}
		return comparison;
	}

	public static Map<String, Object> buildNormalizedComparisonSummary(Map<String, Object> comparison)
	{
		if(!comparison.containsKey("normalizedCostDelta"))
		{
			return null;
		}

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("enabled", Boolean.TRUE);
		summary.put("reference", comparison.get("normalizedComparisonReference"));
		summary.put("epsilon", comparison.get("normalizedComparisonEpsilon"));
		summary.put("denominatorRule", comparison.get("normalizedDenominatorRule"));
		summary.put("zeroReferenceHandling", comparison.get("zeroReferenceHandling"));
		summary.put("meanWorkflowMakespanDelta", comparison.get("meanWorkflowMakespanDelta"));
		summary.put("normalizedCostDelta", comparison.get("normalizedCostDelta"));
		summary.put("normalizedViolationCountDelta", comparison.get("normalizedViolationCountDelta"));
		summary.put("normalizedViolationTimeDelta", comparison.get("normalizedViolationTimeDelta"));
		summary.put("normalizedMakespanDelta", comparison.get("normalizedMakespanDelta"));
		copyIfPresent(summary, comparison, "positiveViolationTimeDelta");
		copyIfPresent(summary, comparison, "positiveViolationTimeRatioDelta");
		copyIfPresent(summary, comparison, "violationCountRatioCorrectedDelta");
		copyIfPresent(summary, comparison, "normalizedPositiveViolationTimeDelta");
		copyIfPresent(summary, comparison, "normalizedPositiveViolationTimeRatioDelta");
		copyIfPresent(summary, comparison, "normalizedViolationCountRatioCorrectedDelta");
		copyIfPresent(summary, comparison, "violationTimeSemantics");
		return summary;
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
		copyIfPresent(summary, comparison, "violationTimeSemantics");
		copyIfPresent(summary, comparison, "positiveViolationTimeDelta");
		copyIfPresent(summary, comparison, "positiveViolationTimeRatioDelta");
		copyIfPresent(summary, comparison, "violationCountRatioCorrectedDelta");
		summary.put("rewardDelta", comparison.get("rewardDelta"));
		summary.put("scheduleTimeDeltaMs", comparison.get("scheduleTimeDeltaMs"));
		copyIfPresent(summary, comparison, "meanWorkflowMakespanDelta");
		copyIfPresent(summary, comparison, "normalizedCostDelta");
		copyIfPresent(summary, comparison, "normalizedViolationCountDelta");
		copyIfPresent(summary, comparison, "normalizedViolationTimeDelta");
		copyIfPresent(summary, comparison, "normalizedMakespanDelta");
		copyIfPresent(summary, comparison, "normalizedPositiveViolationTimeDelta");
		copyIfPresent(summary, comparison, "normalizedPositiveViolationTimeRatioDelta");
		copyIfPresent(summary, comparison, "normalizedViolationCountRatioCorrectedDelta");
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

	private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key)
	{
		if(source.containsKey(key))
		{
			target.put(key, source.get(key));
		}
	}

	private static double numberValue(Object value)
	{
		return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
	}

	private static double normalizeDelta(double delta, double reference, double epsilon)
	{
		// If the expert reference is zero or near zero, floor the denominator at epsilon to avoid NaN/Infinity.
		double denominator = Math.max(Math.abs(reference), epsilon);
		return delta / denominator;
	}
}
