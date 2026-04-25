package ScheduleAgorithm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import vmInfo.SaaSVm;
import workflow.WTask;
import workflow.BenchmarkFamily;

public final class ContextualExpertReplayCollector implements SchedulingTraceRecorder
{
	private final ContextualHierarchicalReplayBuffer replayBuffer;
	private final String sourceSuiteName;

	public ContextualExpertReplayCollector()
	{
		this(null);
	}

	public ContextualExpertReplayCollector(String sourceSuiteName)
	{
		this.replayBuffer = new ContextualHierarchicalReplayBuffer();
		this.sourceSuiteName = sourceSuiteName;
	}

	public ContextualHierarchicalReplayBuffer getReplayBuffer()
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
			TaskCandidateSet taskSet, VmCandidateSet vmSet, VmActionMask vmMask, ResourceSelection resourceSelection,
			SchedulingAction action, double estimatedCostIncrement, SchedulingState snapshot) throws IOException
	{
		if(snapshot == null)
		{
			throw new IllegalStateException("Contextual replay collection requires state snapshots");
		}

		SchedulingState compactState = compactSnapshot(snapshot, taskSet);
		ReplayExampleOrigin origin = buildOrigin(snapshot, taskSelection.getSelectedCandidate());
		replayBuffer.addTaskExample(new TaskDecisionContextExample(compactState, taskSet, taskMask,
				taskSelection.getSelectedIndex(), origin));
		replayBuffer.addVmExample(new VmDecisionContextExample(compactState, taskSelection.getSelectedCandidate(),
				vmSet, vmMask, resourceSelection.getSelectedIndex(), origin));
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

	private SchedulingState compactSnapshot(SchedulingState snapshot, TaskCandidateSet taskSet)
	{
		Set<Integer> relevantWorkflowIds = new LinkedHashSet<Integer>();
		Set<String> relevantTaskIds = new LinkedHashSet<String>();
		for(TaskCandidateView candidate: taskSet.getCandidates())
		{
			relevantWorkflowIds.add(Integer.valueOf(candidate.getWorkflowId()));
			relevantTaskIds.add(candidate.getTaskId());
		}

		List<WorkflowStateView> workflowStates = new ArrayList<WorkflowStateView>();
		for(WorkflowStateView workflowState: snapshot.getWorkflowStates())
		{
			if(relevantWorkflowIds.contains(Integer.valueOf(workflowState.getWorkflowId())))
			{
				workflowStates.add(workflowState);
				relevantTaskIds.addAll(workflowState.getTaskIds());
			}
		}

		List<TaskStateView> taskStates = new ArrayList<TaskStateView>();
		for(TaskStateView taskState: snapshot.getTaskStates())
		{
			if(relevantWorkflowIds.contains(Integer.valueOf(taskState.getWorkflowId())))
			{
				taskStates.add(taskState);
				relevantTaskIds.add(taskState.getTaskId());
			}
		}

		List<String> globalTaskPoolTaskIds = new ArrayList<String>();
		for(String taskId: snapshot.getGlobalTaskPoolTaskIds())
		{
			if(relevantTaskIds.contains(taskId))
			{
				globalTaskPoolTaskIds.add(taskId);
			}
		}

		return new SchedulingState(snapshot.getCurrentTime(), workflowStates, taskStates,
				snapshot.getActiveVmStates(), snapshot.getOffVmStates(), globalTaskPoolTaskIds);
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
