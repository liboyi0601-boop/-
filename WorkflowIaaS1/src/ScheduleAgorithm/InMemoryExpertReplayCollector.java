package ScheduleAgorithm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import vmInfo.SaaSVm;
import workflow.WTask;
import workflow.BenchmarkFamily;

public final class InMemoryExpertReplayCollector implements SchedulingTraceRecorder
{
	private final HierarchicalReplayBuffer replayBuffer;
	private final TaskFeatureExtractor taskFeatureExtractor;
	private final VmFeatureExtractor vmFeatureExtractor;
	private final String sourceSuiteName;

	public InMemoryExpertReplayCollector()
	{
		this(null);
	}

	public InMemoryExpertReplayCollector(String sourceSuiteName)
	{
		this.replayBuffer = new HierarchicalReplayBuffer();
		this.taskFeatureExtractor = new TaskFeatureExtractor();
		this.vmFeatureExtractor = new VmFeatureExtractor();
		this.sourceSuiteName = sourceSuiteName;
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
		ReplayExampleOrigin origin = buildOrigin(snapshot, taskSelection.getSelectedCandidate());
		replayBuffer.addTaskExample(new MaskedDecisionExample(taskFeatures, taskMask.getValidSelections(),
				taskSelection.getSelectedIndex(), origin));

		List<double[]> vmFeatures = new ArrayList<double[]>();
		for(VmCandidateView candidate: vmSet.getCandidates())
		{
			vmFeatures.add(vmFeatureExtractor.extract(taskSelection.getSelectedCandidate(), candidate, snapshot));
		}
		replayBuffer.addVmExample(new MaskedDecisionExample(vmFeatures, vmMask.getValidSelections(),
				resourceSelection.getSelectedIndex(), origin));
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

	private ReplayExampleOrigin buildOrigin(SchedulingState snapshot, TaskCandidateView selectedTask)
	{
		String workflowName = null;
		WorkflowStateView workflowState = snapshot.findWorkflowState(selectedTask.getWorkflowId());
		if(workflowState != null)
		{
			workflowName = workflowState.getWorkflowName();
		}

		BenchmarkFamily benchmarkFamily = BenchmarkFamily.fromWorkflowName(workflowName);
		return new ReplayExampleOrigin(sourceSuiteName,
				benchmarkFamily == null ? null : benchmarkFamily.getFamilyName(),
				workflowName);
	}
}
