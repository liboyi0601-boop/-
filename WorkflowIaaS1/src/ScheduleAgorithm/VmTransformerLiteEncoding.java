package ScheduleAgorithm;

public final class VmTransformerLiteEncoding
{
	private final double[][] candidateEmbeddings;
	private final double[] queryEmbedding;
	private final double[] contextEmbedding;

	public VmTransformerLiteEncoding(double[][] candidateEmbeddings, double[] queryEmbedding, double[] contextEmbedding)
	{
		this.candidateEmbeddings = copy2d(candidateEmbeddings);
		this.queryEmbedding = copy1d(queryEmbedding);
		this.contextEmbedding = copy1d(contextEmbedding);
	}

	public double[] getCandidateEmbedding(int index)
	{
		return copy1d(candidateEmbeddings[index]);
	}

	public double[] getQueryEmbedding()
	{
		return copy1d(queryEmbedding);
	}

	public double[] getContextEmbedding()
	{
		return copy1d(contextEmbedding);
	}

	private double[] copy1d(double[] source)
	{
		double[] copied = new double[source.length];
		System.arraycopy(source, 0, copied, 0, source.length);
		return copied;
	}

	private double[][] copy2d(double[][] source)
	{
		double[][] copied = new double[source.length][];
		for(int row = 0; row < source.length; row++)
		{
			copied[row] = copy1d(source[row]);
		}
		return copied;
	}
}
