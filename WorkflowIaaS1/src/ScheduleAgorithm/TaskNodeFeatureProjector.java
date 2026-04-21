package ScheduleAgorithm;

public final class TaskNodeFeatureProjector
{
	public static final int INPUT_SIZE = 20;

	public double[] extract(TaskStateView taskState, WorkflowStateView workflowState, int currentTime)
	{
		double[] features = new double[INPUT_SIZE];
		double workflowWindow = Math.max(1.0, workflowState.getDeadline() - workflowState.getArrivalTime());

		features[0] = 1.0;
		features[1] = normalizeTime(currentTime, workflowState, workflowWindow);
		features[2] = normalizeTime(taskState.getEarliestStartTime(), workflowState, workflowWindow);
		features[3] = normalizeTime(taskState.getEarliestFinishTime(), workflowState, workflowWindow);
		features[4] = normalizeTime(taskState.getSubDeadline(), workflowState, workflowWindow);
		features[5] = scale(taskState.getExecutionTimeWithConfidency(), workflowWindow);
		features[6] = scale(taskState.getUpwardRank(), workflowWindow);
		features[7] = scale(taskState.getDownwardRank(), workflowWindow);
		features[8] = scale(taskState.getCriticalPathSlack(), workflowWindow);
		features[9] = scale(taskState.getRemainingDescendantWorkload(), workflowWindow);
		features[10] = scale(taskState.getDataTransferPressure(), 10000.0);
		features[11] = scale(taskState.getReadyDuration(), workflowWindow);
		features[12] = taskState.getPathFirstFlag() ? 1.0 : 0.0;
		features[13] = taskState.getPathLastFlag() ? 1.0 : 0.0;
		features[14] = taskState.getNewStartVm() ? 1.0 : 0.0;
		features[15] = scale(taskState.getPriority(), 100.0);
		features[16] = taskState.getInGlobalTaskPool() ? 1.0 : 0.0;
		features[17] = workflowState.getNormalizedSlack();
		features[18] = cap(workflowState.getViolationRiskScore(), 10.0) / 10.0;
		features[19] = scale(workflowState.getRemainingCriticalPathLength(), workflowWindow);
		return features;
	}

	private double normalizeTime(int value, WorkflowStateView workflowState, double workflowWindow)
	{
		return (value - workflowState.getArrivalTime()) / workflowWindow;
	}

	private double scale(double value, double denominator)
	{
		return value / Math.max(1.0, denominator);
	}

	private double cap(double value, double limit)
	{
		if(value > limit)
		{
			return limit;
		}
		if(value < -limit)
		{
			return -limit;
		}
		return value;
	}
}
