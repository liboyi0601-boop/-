package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowGraphContextBuilder
{
	private final TaskNodeFeatureProjector projector;

	public WorkflowGraphContextBuilder(TaskNodeFeatureProjector projector)
	{
		this.projector = projector;
	}

	public WorkflowGraphContext build(SchedulingState state, int workflowId)
	{
		WorkflowStateView workflowState = state.findWorkflowState(workflowId);
		if(workflowState == null)
		{
			throw new IllegalArgumentException("Workflow state is not available: " + workflowId);
		}

		List<TaskStateView> taskStates = new ArrayList<TaskStateView>();
		for(TaskStateView taskState: state.getTaskStates())
		{
			if(taskState.getWorkflowId() == workflowId)
			{
				taskStates.add(taskState);
			}
		}

		Map<String, Integer> taskIndexById = new LinkedHashMap<String, Integer>();
		for(int index = 0; index < taskStates.size(); index++)
		{
			taskIndexById.put(taskStates.get(index).getTaskId(), Integer.valueOf(index));
		}

		double[][] nodeInputFeatures = new double[taskStates.size()][];
		int[][] parentIndices = new int[taskStates.size()][];
		int[][] childIndices = new int[taskStates.size()][];
		for(int index = 0; index < taskStates.size(); index++)
		{
			TaskStateView taskState = taskStates.get(index);
			nodeInputFeatures[index] = projector.extract(taskState, workflowState, state.getCurrentTime());
			parentIndices[index] = translate(taskState.getParentTaskIds(), taskIndexById);
			childIndices[index] = translate(taskState.getSuccessorTaskIds(), taskIndexById);
		}

		return new WorkflowGraphContext(workflowId, workflowState, taskStates, taskIndexById,
				nodeInputFeatures, parentIndices, childIndices);
	}

	private int[] translate(List<String> taskIds, Map<String, Integer> taskIndexById)
	{
		List<Integer> translated = new ArrayList<Integer>();
		for(String taskId: taskIds)
		{
			Integer index = taskIndexById.get(taskId);
			if(index != null)
			{
				translated.add(index);
			}
		}

		int[] indices = new int[translated.size()];
		for(int i = 0; i < translated.size(); i++)
		{
			indices[i] = translated.get(i).intValue();
		}
		return indices;
	}
}
