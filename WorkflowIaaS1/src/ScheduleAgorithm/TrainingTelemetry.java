package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrainingTelemetry
{
	private final String trainingMode;
	private final List<Map<String, Object>> epochMetrics;

	public TrainingTelemetry(String trainingMode)
	{
		this.trainingMode = trainingMode;
		this.epochMetrics = new ArrayList<Map<String, Object>>();
	}

	public void recordEpoch(int epoch, Map<String, Object> trainingMetrics,
			ExperimentMetrics experimentMetrics, DeadlineViolationMetrics deadlineMetrics, Map<String, Object> rewardMetrics,
			int invalidTaskActionCount, int invalidVmActionCount)
	{
		Map<String, Object> record = new LinkedHashMap<String, Object>();
		record.put("epoch", epoch);
		record.put("taskLoss", trainingMetrics.get("taskLoss"));
		record.put("vmLoss", trainingMetrics.get("vmLoss"));
		record.put("taskChosenActionAccuracy", trainingMetrics.get("taskChosenActionAccuracy"));
		record.put("vmChosenActionAccuracy", trainingMetrics.get("vmChosenActionAccuracy"));
		record.put("taskMaskHitRate", trainingMetrics.get("taskMaskHitRate"));
		record.put("vmMaskHitRate", trainingMetrics.get("vmMaskHitRate"));
		copyOptional(trainingMetrics, record, "unweightedTaskLoss");
		copyOptional(trainingMetrics, record, "unweightedVmLoss");
		copyOptional(trainingMetrics, record, "weightedTaskLoss");
		copyOptional(trainingMetrics, record, "weightedVmLoss");
		copyOptional(trainingMetrics, record, "averageTaskSampleWeight");
		copyOptional(trainingMetrics, record, "averageVmSampleWeight");
		copyOptional(trainingMetrics, record, "averageTaskRiskScore");
		copyOptional(trainingMetrics, record, "averageVmRiskScore");
		copyOptional(trainingMetrics, record, "maxTaskRiskScore");
		copyOptional(trainingMetrics, record, "maxVmRiskScore");
		copyOptional(trainingMetrics, record, "averageTaskSlackRisk");
		copyOptional(trainingMetrics, record, "averageTaskViolationRisk");
		copyOptional(trainingMetrics, record, "averageTaskCriticalPathRisk");
		copyOptional(trainingMetrics, record, "averageTaskLatenessRisk");
		copyOptional(trainingMetrics, record, "maxTaskLatenessRisk");
		copyOptional(trainingMetrics, record, "averageVmInfeasibleRisk");
		copyOptional(trainingMetrics, record, "averageVmSlackRisk");
		copyOptional(trainingMetrics, record, "averageVmViolationRisk");
		copyOptional(trainingMetrics, record, "averageVmCriticalPathRisk");
		copyOptional(trainingMetrics, record, "averageVmLatenessRisk");
		copyOptional(trainingMetrics, record, "maxVmLatenessRisk");
		copyOptional(trainingMetrics, record, "minTaskSampleWeight");
		copyOptional(trainingMetrics, record, "minVmSampleWeight");
		copyOptional(trainingMetrics, record, "maxTaskSampleWeight");
		copyOptional(trainingMetrics, record, "maxVmSampleWeight");
		copyOptional(trainingMetrics, record, "highTaskWeightRatio");
		copyOptional(trainingMetrics, record, "highVmWeightRatio");
		copyOptional(trainingMetrics, record, "taskWeightSaturationWarning");
		copyOptional(trainingMetrics, record, "vmWeightSaturationWarning");
		copyOptional(trainingMetrics, record, "highSampleWeightThreshold");
		copyOptional(trainingMetrics, record, "weightingFallbackCount");
		copyOptional(trainingMetrics, record, "weightingFallbackReasonCounts");
		record.put("totalCost", experimentMetrics.getTotalCost());
		record.put("violationCount", experimentMetrics.getViolationCount());
		record.put("violationTime", experimentMetrics.getViolationTime());
		if(deadlineMetrics != null)
		{
			record.put(DeadlineViolationMetrics.MAP_KEY, deadlineMetrics.toMap());
			record.put("positiveViolationTimeRatio", deadlineMetrics.getPositiveViolationTimeRatio());
			record.put("violationCountRatioCorrected", deadlineMetrics.getViolationCountRatioCorrected());
		}
		record.put("resourceUtilization", experimentMetrics.getResourceUtilization());
		record.put("scheduleTimeMs", experimentMetrics.getScheduleTimeMs());
		record.put("totalReward", rewardMetrics.get("totalReward"));
		record.put("invalidTaskActionCount", invalidTaskActionCount);
		record.put("invalidVmActionCount", invalidVmActionCount);
		epochMetrics.add(Collections.unmodifiableMap(record));
	}

	public List<Map<String, Object>> getEpochMetrics()
	{
		return Collections.unmodifiableList(epochMetrics);
	}

	public Map<String, Object> enrichSummary(Map<String, Object> baseSummary)
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>(baseSummary);
		summary.put("trainingMode", trainingMode);
		summary.put("epochCount", epochMetrics.size());
		summary.put("bestEpochByReward", findBestEpochByReward());
		summary.put("bestEpochByViolationThenCost", findBestEpochByViolationThenCost());
		summary.put("bestEpochByCorrectedViolationThenCost", findBestEpochByCorrectedViolationThenCost());
		Map<String, Object> lastEpoch = epochMetrics.isEmpty() ? null : epochMetrics.get(epochMetrics.size() - 1);
		summary.put("finalTaskAccuracy", valueOrNull(lastEpoch, "taskChosenActionAccuracy"));
		summary.put("finalVmAccuracy", valueOrNull(lastEpoch, "vmChosenActionAccuracy"));
		summary.put("finalTaskMaskHitRate", valueOrNull(lastEpoch, "taskMaskHitRate"));
		summary.put("finalVmMaskHitRate", valueOrNull(lastEpoch, "vmMaskHitRate"));
		return summary;
	}

	private void copyOptional(Map<String, Object> source, Map<String, Object> target, String key)
	{
		if(source.containsKey(key))
		{
			target.put(key, source.get(key));
		}
	}

	private Object valueOrNull(Map<String, Object> metrics, String key)
	{
		if(metrics == null)
		{
			return null;
		}
		return metrics.get(key);
	}

	private Integer findBestEpochByReward()
	{
		if(epochMetrics.isEmpty())
		{
			return null;
		}

		Map<String, Object> best = epochMetrics.get(0);
		for(int index = 1; index < epochMetrics.size(); index++)
		{
			Map<String, Object> candidate = epochMetrics.get(index);
			if(compareByReward(candidate, best) > 0)
			{
				best = candidate;
			}
		}
		return Integer.valueOf(((Number)best.get("epoch")).intValue());
	}

	private Integer findBestEpochByViolationThenCost()
	{
		if(epochMetrics.isEmpty())
		{
			return null;
		}

		Map<String, Object> best = epochMetrics.get(0);
		for(int index = 1; index < epochMetrics.size(); index++)
		{
			Map<String, Object> candidate = epochMetrics.get(index);
			if(compareByViolationThenCost(candidate, best) < 0)
			{
				best = candidate;
			}
		}
		return Integer.valueOf(((Number)best.get("epoch")).intValue());
	}

	private Integer findBestEpochByCorrectedViolationThenCost()
	{
		if(epochMetrics.isEmpty())
		{
			return null;
		}

		Map<String, Object> best = epochMetrics.get(0);
		for(int index = 1; index < epochMetrics.size(); index++)
		{
			Map<String, Object> candidate = epochMetrics.get(index);
			if(compareByCorrectedViolationThenCost(candidate, best) < 0)
			{
				best = candidate;
			}
		}
		return Integer.valueOf(((Number)best.get("epoch")).intValue());
	}

	private int compareByReward(Map<String, Object> left, Map<String, Object> right)
	{
		double leftReward = numberValue(left.get("totalReward"));
		double rightReward = numberValue(right.get("totalReward"));
		if(leftReward > rightReward)
		{
			return 1;
		}
		if(leftReward < rightReward)
		{
			return -1;
		}
		return Integer.compare(((Number)right.get("epoch")).intValue(), ((Number)left.get("epoch")).intValue());
	}

	private int compareByViolationThenCost(Map<String, Object> left, Map<String, Object> right)
	{
		int result = Double.compare(numberValue(left.get("violationCount")), numberValue(right.get("violationCount")));
		if(result != 0)
		{
			return result;
		}
		result = Double.compare(numberValue(left.get("violationTime")), numberValue(right.get("violationTime")));
		if(result != 0)
		{
			return result;
		}
		result = Double.compare(numberValue(left.get("totalCost")), numberValue(right.get("totalCost")));
		if(result != 0)
		{
			return result;
		}
		return Integer.compare(((Number)left.get("epoch")).intValue(), ((Number)right.get("epoch")).intValue());
	}

	private int compareByCorrectedViolationThenCost(Map<String, Object> left, Map<String, Object> right)
	{
		int result = Integer.compare(invalidActionCount(left), invalidActionCount(right));
		if(result != 0)
		{
			return result;
		}
		result = Double.compare(numberValue(left.get("violationCount")), numberValue(right.get("violationCount")));
		if(result != 0)
		{
			return result;
		}
		result = Double.compare(correctedPositiveViolationTimeRatio(left), correctedPositiveViolationTimeRatio(right));
		if(result != 0)
		{
			return result;
		}
		result = Double.compare(numberValue(left.get("totalCost")), numberValue(right.get("totalCost")));
		if(result != 0)
		{
			return result;
		}
		return Integer.compare(((Number)left.get("epoch")).intValue(), ((Number)right.get("epoch")).intValue());
	}

	private int invalidActionCount(Map<String, Object> metrics)
	{
		return intValue(metrics.get("invalidTaskActionCount")) + intValue(metrics.get("invalidVmActionCount"));
	}

	private double correctedPositiveViolationTimeRatio(Map<String, Object> metrics)
	{
		Object value = metrics.get("positiveViolationTimeRatio");
		return value instanceof Number ? ((Number)value).doubleValue() : Double.POSITIVE_INFINITY;
	}

	private int intValue(Object value)
	{
		return value instanceof Number ? ((Number)value).intValue() : Integer.MAX_VALUE / 2;
	}

	private double numberValue(Object value)
	{
		return value instanceof Number ? ((Number)value).doubleValue() : Double.NEGATIVE_INFINITY;
	}
}
