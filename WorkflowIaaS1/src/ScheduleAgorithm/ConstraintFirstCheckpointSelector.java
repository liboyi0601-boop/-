package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConstraintFirstCheckpointSelector
{
	public static final String RULE_NAME = "constraint-first";
	private static final String REASON =
			"lowest invalid action count, then violationCount, then corrected positiveViolationTimeRatio, "
					+ "then totalCost, then highest totalReward, then earliest epoch";

	private PolicyCheckpoint bestCheckpoint;

	public void consider(int epoch, HierarchicalMaskedLearningPolicy policy, ExperimentMetrics metrics,
			DeadlineViolationMetrics deadlineMetrics, Map<String, Object> reward,
			int invalidTaskActionCount, int invalidVmActionCount)
	{
		PolicyCheckpoint candidate = new PolicyCheckpoint(epoch, policy.copy(), metrics, deadlineMetrics, reward,
				invalidTaskActionCount, invalidVmActionCount);
		if(bestCheckpoint == null || isBetter(candidate, bestCheckpoint))
		{
			bestCheckpoint = candidate;
		}
	}

	public boolean hasBestCheckpoint()
	{
		return bestCheckpoint != null;
	}

	public int getBestEpoch()
	{
		requireBestCheckpoint();
		return bestCheckpoint.epoch;
	}

	public HierarchicalMaskedLearningPolicy getBestPolicy()
	{
		requireBestCheckpoint();
		return bestCheckpoint.policy.copy();
	}

	public Map<String, Object> buildSummary(Map<String, Object> finalEpochMetrics,
			ExperimentMetrics bestMetrics, DeadlineViolationMetrics bestDeadlineMetrics, Map<String, Object> bestReward,
			int bestInvalidTaskActionCount, int bestInvalidVmActionCount,
			Map<String, Object> bestCheckpointComparison)
	{
		requireBestCheckpoint();
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("enabled", Boolean.TRUE);
		summary.put("selectionRule", RULE_NAME);
		summary.put("bestCheckpointEpoch", Integer.valueOf(bestCheckpoint.epoch));
		summary.put("bestCheckpointReason", REASON);
		summary.put("bestCheckpointMetrics", metricsMap(bestMetrics, bestDeadlineMetrics));
		summary.put("bestCheckpointReward", new LinkedHashMap<String, Object>(bestReward));
		summary.put("bestInvalidTaskActionCount", Integer.valueOf(bestInvalidTaskActionCount));
		summary.put("bestInvalidVmActionCount", Integer.valueOf(bestInvalidVmActionCount));
		if(finalEpochMetrics != null)
		{
			summary.put("finalEpochMetrics", new LinkedHashMap<String, Object>(finalEpochMetrics));
		}
		if(bestCheckpointComparison != null)
		{
			summary.put("bestCheckpointComparison", bestCheckpointComparison);
		}
		return summary;
	}

	public Map<String, Object> getSelectionMetrics()
	{
		requireBestCheckpoint();
		return bestCheckpoint.metrics.toMap();
	}

	public Map<String, Object> getSelectionReward()
	{
		requireBestCheckpoint();
		return new LinkedHashMap<String, Object>(bestCheckpoint.reward);
	}

	public DeadlineViolationMetrics getSelectionDeadlineMetrics()
	{
		requireBestCheckpoint();
		return bestCheckpoint.deadlineMetrics;
	}

	public int getSelectionInvalidTaskActionCount()
	{
		requireBestCheckpoint();
		return bestCheckpoint.invalidTaskActionCount;
	}

	public int getSelectionInvalidVmActionCount()
	{
		requireBestCheckpoint();
		return bestCheckpoint.invalidVmActionCount;
	}

	public static String getSelectionReason()
	{
		return REASON;
	}

	private boolean isBetter(PolicyCheckpoint candidate, PolicyCheckpoint currentBest)
	{
		int result = Integer.compare(candidate.invalidActionCount(), currentBest.invalidActionCount());
		if(result != 0)
		{
			return result < 0;
		}
		result = Double.compare(candidate.metrics.getViolationCount(), currentBest.metrics.getViolationCount());
		if(result != 0)
		{
			return result < 0;
		}
		result = Double.compare(positiveViolationTimeRatio(candidate), positiveViolationTimeRatio(currentBest));
		if(result != 0)
		{
			return result < 0;
		}
		result = Double.compare(candidate.metrics.getTotalCost(), currentBest.metrics.getTotalCost());
		if(result != 0)
		{
			return result < 0;
		}
		result = Double.compare(totalReward(candidate.reward), totalReward(currentBest.reward));
		if(result != 0)
		{
			return result > 0;
		}
		return candidate.epoch < currentBest.epoch;
	}

	private double positiveViolationTimeRatio(PolicyCheckpoint checkpoint)
	{
		if(checkpoint.deadlineMetrics != null)
		{
			return checkpoint.deadlineMetrics.getPositiveViolationTimeRatio();
		}
		return Math.max(0.0, checkpoint.metrics.getViolationTime());
	}

	private double totalReward(Map<String, Object> reward)
	{
		Object value = reward.get("totalReward");
		return value instanceof Number ? ((Number)value).doubleValue() : Double.NEGATIVE_INFINITY;
	}

	private void requireBestCheckpoint()
	{
		if(bestCheckpoint == null)
		{
			throw new IllegalStateException("No checkpoint has been selected");
		}
	}

	private Map<String, Object> metricsMap(ExperimentMetrics metrics, DeadlineViolationMetrics deadlineMetrics)
	{
		Map<String, Object> map = metrics.toMap();
		if(deadlineMetrics != null)
		{
			map.put(DeadlineViolationMetrics.MAP_KEY, deadlineMetrics.toMap());
		}
		return map;
	}

	private static final class PolicyCheckpoint
	{
		private final int epoch;
		private final HierarchicalMaskedLearningPolicy policy;
		private final ExperimentMetrics metrics;
		private final DeadlineViolationMetrics deadlineMetrics;
		private final Map<String, Object> reward;
		private final int invalidTaskActionCount;
		private final int invalidVmActionCount;

		private PolicyCheckpoint(int epoch, HierarchicalMaskedLearningPolicy policy, ExperimentMetrics metrics,
				DeadlineViolationMetrics deadlineMetrics, Map<String, Object> reward,
				int invalidTaskActionCount, int invalidVmActionCount)
		{
			this.epoch = epoch;
			this.policy = policy;
			this.metrics = metrics;
			this.deadlineMetrics = deadlineMetrics;
			this.reward = new LinkedHashMap<String, Object>(reward);
			this.invalidTaskActionCount = invalidTaskActionCount;
			this.invalidVmActionCount = invalidVmActionCount;
		}

		private int invalidActionCount()
		{
			return invalidTaskActionCount + invalidVmActionCount;
		}
	}
}
