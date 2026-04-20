package ScheduleAgorithm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import vmInfo.SaaSVm;
import workflow.WTask;

public final class InMemoryExpertReplayCollector implements SchedulingTraceRecorder
{
	private final HierarchicalReplayBuffer replayBuffer;
	private final TaskFeatureExtractor taskFeatureExtractor;
	private final VmFeatureExtractor vmFeatureExtractor;

	public InMemoryExpertReplayCollector()
	{
		this.replayBuffer = new HierarchicalReplayBuffer();
		this.taskFeatureExtractor = new TaskFeatureExtractor();
		this.vmFeatureExtractor = new VmFeatureExtractor();
	}

	public HierarchicalReplayBuffer getReplayBuffer()
	{
		return replayBuffer;
	}

	public boolean isEnabled()
	{
		return true;
	}

	public boolean shouldCaptureStateSnapshot(int decisionIndex)
	{
		return true;
	}

	public void recordDecisionCandidate(int currentTime, TaskCandidateSet taskSet, TaskActionMask taskMask,
			List<SaaSVm> candidateVms, int workflowCount, int activeVmCount, int offVmCount, int globalTaskPoolSize)
			throws IOException
	{
	}

	public void recordDecisionChosen(int currentTime, TaskSelection taskSelection, TaskActionMask taskMask,
			TaskCandidateSet taskSet,
			VmCandidateSet vmSet, VmActionMask vmMask, ResourceSelection resourceSelection,
			SchedulingAction action, double estimatedCostIncrement, SchedulingState snapshot) throws IOException
	{
		if(snapshot == null)
		{
			throw new IllegalStateException("Expert replay collection requires state snapshots");
		}

		List<double[]> taskFeatures = new ArrayList<double[]>();
		for(TaskCandidateView candidate: taskSet.getCandidates())
		{
			taskFeatures.add(taskFeatureExtractor.extract(candidate, snapshot));
		}
		replayBuffer.addTaskExample(new MaskedDecisionExample(taskFeatures, taskMask.getValidSelections(),
				taskSelection.getSelectedIndex()));

		List<double[]> vmFeatures = new ArrayList<double[]>();
		for(VmCandidateView candidate: vmSet.getCandidates())
		{
			vmFeatures.add(vmFeatureExtractor.extract(taskSelection.getSelectedCandidate(), candidate, snapshot));
		}
		replayBuffer.addVmExample(new MaskedDecisionExample(vmFeatures, vmMask.getValidSelections(),
				resourceSelection.getSelectedIndex()));
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
