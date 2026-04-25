package ScheduleAgorithm;

import java.io.IOException;
import java.util.List;

import vmInfo.SaaSVm;
import workflow.WTask;

public final class NoOpSchedulingTraceRecorder implements SchedulingTraceRecorder
{
	public boolean isEnabled()
	{
		return false;
	}

	public boolean shouldCaptureStateSnapshot(int decisionIndex)
	{
		return false;
	}

	public void recordDecisionCandidate(int currentTime, TaskCandidateSet taskSet, TaskActionMask taskMask,
			List<SaaSVm> candidateVms,
			int workflowCount, int activeVmCount, int offVmCount, int globalTaskPoolSize) throws IOException
	{
	}

	public void recordDecisionChosen(int currentTime, TaskSelection taskSelection, TaskActionMask taskMask,
			TaskCandidateSet taskSet,
			VmCandidateSet vmSet, VmActionMask vmMask, ResourceSelection resourceSelection,
			SchedulingAction action, double estimatedCostIncrement, SchedulingState snapshot) throws IOException
	{
	}

	public void recordActionApplied(int currentTime, SchedulingAction action, SaaSVm appliedVm) throws IOException
	{
	}

	public void recordTaskFinish(int currentTime, List<WTask> finishedTasks, List<WTask> readyTasksAfterFinish)
			throws IOException
	{
	}

	public void recordVmTurnoff(int currentTime, List<SaaSVm> turnOffVmSet, int turnOffVmTime) throws IOException
	{
	}

	public void close() throws IOException
	{
	}
}
