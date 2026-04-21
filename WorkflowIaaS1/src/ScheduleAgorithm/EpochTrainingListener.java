package ScheduleAgorithm;

import java.util.Map;

public interface EpochTrainingListener
{
	void onEpoch(int epoch, Map<String, Object> trainingMetrics,
			SchedulingPolicy baselinePolicy,
			HierarchicalSchedulingPolicy hierarchicalPolicy);
}
