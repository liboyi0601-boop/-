package ScheduleAgorithm;

public final class WorkflowGraphEncoding
{
	private final WorkflowGraphContext context;
	private final double[][] nodeEmbeddings;
	private final double[] pooledEmbedding;

	public WorkflowGraphEncoding(WorkflowGraphContext context, double[][] nodeEmbeddings, double[] pooledEmbedding)
	{
		this.context = context;
		this.nodeEmbeddings = copy2d(nodeEmbeddings);
		this.pooledEmbedding = new double[pooledEmbedding.length];
		System.arraycopy(pooledEmbedding, 0, this.pooledEmbedding, 0, pooledEmbedding.length);
	}

	public WorkflowGraphContext getContext()
	{
		return context;
	}

	public double[] getTaskEmbedding(String taskId)
	{
		int taskIndex = context.getTaskIndex(taskId);
		double[] embedding = new double[nodeEmbeddings[taskIndex].length];
		System.arraycopy(nodeEmbeddings[taskIndex], 0, embedding, 0, embedding.length);
		return embedding;
	}

	public double[] getPooledEmbedding()
	{
		double[] embedding = new double[pooledEmbedding.length];
		System.arraycopy(pooledEmbedding, 0, embedding, 0, embedding.length);
		return embedding;
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
}
