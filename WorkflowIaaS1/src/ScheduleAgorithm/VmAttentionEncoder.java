package ScheduleAgorithm;

import java.util.Random;

public final class VmAttentionEncoder
{
	private final int hiddenSize;
	private final double[][] baseProjectionWeights;
	private final double[][] queryProjectionWeights;
	private final double[][] keyProjectionWeights;
	private final double[][] valueProjectionWeights;

	public VmAttentionEncoder(int baseInputSize, int queryInputSize, int hiddenSize, long seed)
	{
		this.hiddenSize = hiddenSize;
		this.baseProjectionWeights = new double[hiddenSize][baseInputSize];
		this.queryProjectionWeights = new double[hiddenSize][queryInputSize];
		this.keyProjectionWeights = new double[hiddenSize][hiddenSize];
		this.valueProjectionWeights = new double[hiddenSize][hiddenSize];
		initialize(new Random(seed));
	}

	public VmAttentionEncoding encode(VmCandidateSet vmSet, WorkflowStateView workflowState, SchedulingState state,
			double[] selectedTaskEmbedding, double[] pooledEmbedding, VmCandidateFeatureProjector projector)
	{
		double[] queryInput = new double[selectedTaskEmbedding.length + pooledEmbedding.length];
		System.arraycopy(selectedTaskEmbedding, 0, queryInput, 0, selectedTaskEmbedding.length);
		System.arraycopy(pooledEmbedding, 0, queryInput, selectedTaskEmbedding.length, pooledEmbedding.length);
		double[] queryEmbedding = tanh(matrixVector(queryProjectionWeights, queryInput));

		double[][] baseEmbeddings = new double[vmSet.size()][hiddenSize];
		double[][] valueEmbeddings = new double[vmSet.size()][hiddenSize];
		double[] scores = new double[vmSet.size()];
		double maxScore = Double.NEGATIVE_INFINITY;

		for(int index = 0; index < vmSet.size(); index++)
		{
			double[] baseFeatures = projector.extract(vmSet.get(index), workflowState, state);
			baseEmbeddings[index] = tanh(matrixVector(baseProjectionWeights, baseFeatures));
			double[] keyEmbedding = tanh(matrixVector(keyProjectionWeights, baseEmbeddings[index]));
			valueEmbeddings[index] = tanh(matrixVector(valueProjectionWeights, baseEmbeddings[index]));
			scores[index] = dot(queryEmbedding, keyEmbedding) / Math.sqrt(Math.max(1.0, hiddenSize));
			if(scores[index] > maxScore)
			{
				maxScore = scores[index];
			}
		}

		double[] attentionWeights = new double[vmSet.size()];
		double denominator = 0.0;
		for(int index = 0; index < attentionWeights.length; index++)
		{
			attentionWeights[index] = Math.exp(scores[index] - maxScore);
			denominator += attentionWeights[index];
		}

		double[] globalContext = new double[hiddenSize];
		for(int index = 0; index < attentionWeights.length; index++)
		{
			double normalizedWeight = attentionWeights[index] / Math.max(1e-12, denominator);
			for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
			{
				globalContext[hiddenIndex] += normalizedWeight * valueEmbeddings[index][hiddenIndex];
			}
		}

		double[][] candidateEmbeddings = new double[vmSet.size()][hiddenSize];
		for(int index = 0; index < vmSet.size(); index++)
		{
			double[] contextual = new double[hiddenSize];
			for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
			{
				contextual[hiddenIndex] = Math.tanh(baseEmbeddings[index][hiddenIndex]
						+ globalContext[hiddenIndex] + queryEmbedding[hiddenIndex]);
			}
			candidateEmbeddings[index] = contextual;
		}

		return new VmAttentionEncoding(candidateEmbeddings, queryEmbedding);
	}

	private double[] matrixVector(double[][] matrix, double[] vector)
	{
		double[] result = new double[matrix.length];
		for(int row = 0; row < matrix.length; row++)
		{
			for(int column = 0; column < vector.length; column++)
			{
				result[row] += matrix[row][column] * vector[column];
			}
		}
		return result;
	}

	private double[] tanh(double[] vector)
	{
		double[] result = new double[vector.length];
		for(int index = 0; index < vector.length; index++)
		{
			result[index] = Math.tanh(vector[index]);
		}
		return result;
	}

	private double dot(double[] left, double[] right)
	{
		double value = 0.0;
		for(int index = 0; index < left.length; index++)
		{
			value += left[index] * right[index];
		}
		return value;
	}

	private void initialize(Random random)
	{
		fillMatrix(baseProjectionWeights, random);
		fillMatrix(queryProjectionWeights, random);
		fillMatrix(keyProjectionWeights, random);
		fillMatrix(valueProjectionWeights, random);
	}

	private void fillMatrix(double[][] matrix, Random random)
	{
		for(int row = 0; row < matrix.length; row++)
		{
			for(int column = 0; column < matrix[row].length; column++)
			{
				matrix[row][column] = (random.nextDouble() - 0.5) * 0.1;
			}
		}
	}
}
