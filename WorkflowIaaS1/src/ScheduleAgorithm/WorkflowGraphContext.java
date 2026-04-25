package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowGraphContext
{
	private final int workflowId;
	private final WorkflowStateView workflowState;
	private final List<TaskStateView> taskStates;
	private final Map<String, Integer> taskIndexById;
	private final double[][] nodeInputFeatures;
	private final int[][] parentIndices;
	private final int[][] childIndices;

	public WorkflowGraphContext(int workflowId, WorkflowStateView workflowState, List<TaskStateView> taskStates,
			Map<String, Integer> taskIndexById, double[][] nodeInputFeatures, int[][] parentIndices,
			int[][] childIndices)
	{
		this.workflowId = workflowId;
		this.workflowState = workflowState;
		this.taskStates = Collections.unmodifiableList(new ArrayList<TaskStateView>(taskStates));
		this.taskIndexById = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(taskIndexById));
		this.nodeInputFeatures = copy2d(nodeInputFeatures);
		this.parentIndices = copy2d(parentIndices);
		this.childIndices = copy2d(childIndices);
	}

	public int getWorkflowId()
	{
		return workflowId;
	}

	public WorkflowStateView getWorkflowState()
	{
		return workflowState;
	}

	public int getNodeCount()
	{
		return taskStates.size();
	}

	public TaskStateView getTaskState(int index)
	{
		return taskStates.get(index);
	}

	public int getTaskIndex(String taskId)
	{
		Integer index = taskIndexById.get(taskId);
		if(index == null)
		{
			throw new IllegalArgumentException("Task is not present in workflow graph context: " + taskId);
		}
		return index.intValue();
	}

	public double[] getNodeInputFeatures(int index)
	{
		double[] features = new double[nodeInputFeatures[index].length];
		System.arraycopy(nodeInputFeatures[index], 0, features, 0, features.length);
		return features;
	}

	public int[] getParentIndices(int index)
	{
		int[] copied = new int[parentIndices[index].length];
		System.arraycopy(parentIndices[index], 0, copied, 0, copied.length);
		return copied;
	}

	public int[] getChildIndices(int index)
	{
		int[] copied = new int[childIndices[index].length];
		System.arraycopy(childIndices[index], 0, copied, 0, copied.length);
		return copied;
	}

	private double[][] copy2d(double[][] source)
	{
		double[][] copied = new double[source.length][];
		for(int row = 0; row < source.length; row++)
		{
			copied[row] = new double[source[row].length];
			System.arraycopy(source[row], 0, copied[row], 0, source[row].length);
		}
		return copied;
	}

	private int[][] copy2d(int[][] source)
	{
		int[][] copied = new int[source.length][];
		for(int row = 0; row < source.length; row++)
		{
			copied[row] = new int[source[row].length];
			System.arraycopy(source[row], 0, copied[row], 0, source[row].length);
		}
		return copied;
	}
}
