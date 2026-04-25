package ScheduleAgorithm;

public final class TaskFeatureExtractor
{
	public static final int INPUT_SIZE = 19;
	public static final int IDX_CRITICAL_PATH_SLACK = 8;
	public static final int IDX_WORKFLOW_NORMALIZED_SLACK = 12;
	public static final int IDX_VIOLATION_RISK = 13;
	public static final int IDX_REMAINING_CRITICAL_PATH = 15;

	public double[] extract(TaskCandidateView candidate, SchedulingState state)
	{
		double[] features = new double[INPUT_SIZE];
		TaskStateView taskState = state == null ? null : state.findTaskState(candidate.getTaskId());
		WorkflowStateView workflowState = state == null ? null : state.findWorkflowState(candidate.getWorkflowId());
		double workflowWindow = workflowState == null
				? 1.0
				: Math.max(1.0, workflowState.getDeadline() - workflowState.getArrivalTime());

		features[0] = 1.0;
		features[1] = normalizeTime(state == null ? 0 : state.getCurrentTime(), workflowState, workflowWindow);
		features[2] = normalizeTime(candidate.getEarliestStartTime(), workflowState, workflowWindow);
		features[3] = normalizeTime(candidate.getEarliestFinishTime(), workflowState, workflowWindow);
		features[4] = normalizeTime(candidate.getSubDeadline(), workflowState, workflowWindow);
		features[5] = candidate.getPriority() / 100.0;
		features[6] = normalizeTime(taskState == null ? 0 : taskState.getUpwardRank(), workflowState, workflowWindow);
		features[7] = normalizeTime(taskState == null ? 0 : taskState.getDownwardRank(), workflowState, workflowWindow);
		features[IDX_CRITICAL_PATH_SLACK] = normalizeTime(taskState == null ? 0 : taskState.getCriticalPathSlack(), workflowState, workflowWindow);
		features[9] = normalizeTime(taskState == null ? 0 : taskState.getRemainingDescendantWorkload(),
				workflowState, workflowWindow);
		features[10] = scale(taskState == null ? 0 : taskState.getDataTransferPressure(), 10000.0);
		features[11] = normalizeTime(taskState == null ? 0 : taskState.getReadyDuration(), workflowState, workflowWindow);
		features[IDX_WORKFLOW_NORMALIZED_SLACK] = workflowState == null ? 0.0 : workflowState.getNormalizedSlack();
		features[IDX_VIOLATION_RISK] = workflowState == null ? 0.0 : cap(workflowState.getViolationRiskScore(), 10.0) / 10.0;
		features[14] = workflowState == null ? 0.0 : workflowState.getReadyTaskDensity();
		features[IDX_REMAINING_CRITICAL_PATH] = normalizeTime(workflowState == null ? 0 : workflowState.getRemainingCriticalPathLength(),
				workflowState, workflowWindow);
		features[16] = state == null ? 0.0 : scale(state.getActiveVmStates().size(), 100.0);
		features[17] = state == null ? 0.0 : scale(state.getGlobalTaskPoolTaskIds().size(), 1000.0);
		features[18] = workflowState == null
				? 0.0
				: (double)workflowState.getFinishedTaskCount() / Math.max(1.0, workflowState.getTaskCount());
		return features;
	}

	public int getInputSize()
	{
		return INPUT_SIZE;
	}

	private double normalizeTime(int value, WorkflowStateView workflowState, double workflowWindow)
	{
		if(workflowState == null)
		{
			return scale(value, 10000.0);
		}
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
