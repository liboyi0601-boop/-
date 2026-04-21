package ScheduleAgorithm;

public final class VmAttentionEncoding
{
	private final double[][] candidateEmbeddings;
	private final double[] queryEmbedding;

	public VmAttentionEncoding(double[][] candidateEmbeddings, double[] queryEmbedding)
	{
		this.candidateEmbeddings = copy2d(candidateEmbeddings);
		this.queryEmbedding = new double[queryEmbedding.length];
		System.arraycopy(queryEmbedding, 0, this.queryEmbedding, 0, queryEmbedding.length);
	}

	public double[] getCandidateEmbedding(int index)
	{
		double[] embedding = new double[candidateEmbeddings[index].length];
		System.arraycopy(candidateEmbeddings[index], 0, embedding, 0, embedding.length);
		return embedding;
	}

	public double[] getQueryEmbedding()
	{
		double[] embedding = new double[queryEmbedding.length];
		System.arraycopy(queryEmbedding, 0, embedding, 0, queryEmbedding.length);
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
