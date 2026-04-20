package ScheduleAgorithm;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import vmInfo.SaaSVm;
import workflow.WTask;

public interface SchedulingTraceRecorder extends Closeable
{
	boolean isEnabled();

	boolean shouldCaptureStateSnapshot(int decisionIndex);

	void recordDecisionCandidate(int currentTime, TaskCandidateSet taskSet, TaskActionMask taskMask,
			List<SaaSVm> candidateVms,
			int workflowCount, int activeVmCount, int offVmCount, int globalTaskPoolSize) throws IOException;

	void recordDecisionChosen(int currentTime, TaskSelection taskSelection, TaskActionMask taskMask,
			TaskCandidateSet taskSet,
			VmCandidateSet vmSet, VmActionMask vmMask, ResourceSelection resourceSelection,
			SchedulingAction action, double estimatedCostIncrement, SchedulingState snapshot) throws IOException;

	void recordActionApplied(int currentTime, SchedulingAction action, SaaSVm appliedVm) throws IOException;

	void recordTaskFinish(int currentTime, List<WTask> finishedTasks, List<WTask> readyTasksAfterFinish)
			throws IOException;

	void recordVmTurnoff(int currentTime, List<SaaSVm> turnOffVmSet, int turnOffVmTime) throws IOException;

	void close() throws IOException;
}
