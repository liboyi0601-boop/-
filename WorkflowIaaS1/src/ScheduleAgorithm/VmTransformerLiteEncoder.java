package ScheduleAgorithm;

import java.util.Random;

public final class VmTransformerLiteEncoder
{
	public static final int ATTENTION_HEADS = 1;
	public static final int TRANSFORMER_LAYERS = 1;
	public static final String NORMALIZATION_TYPE = "layer_norm";

	private static final double NORM_EPSILON = 1e-6;

	private final int hiddenSize;
	private final int ffnHiddenSize;
	private final double[][] baseProjectionWeights;
	private final double[][] queryProjectionWeights;
	private final double[][] attentionQueryWeights;
	private final double[][] attentionKeyWeights;
	private final double[][] attentionValueWeights;
	private final double[][] ffnUpWeights;
	private final double[][] ffnDownWeights;

	public VmTransformerLiteEncoder(int baseInputSize, int queryInputSize, int hiddenSize, int ffnHiddenSize,
			long seed)
	{
		this.hiddenSize = hiddenSize;
		this.ffnHiddenSize = ffnHiddenSize;
		this.baseProjectionWeights = new double[hiddenSize][baseInputSize];
		this.queryProjectionWeights = new double[hiddenSize][queryInputSize];
		this.attentionQueryWeights = new double[hiddenSize][hiddenSize];
		this.attentionKeyWeights = new double[hiddenSize][hiddenSize];
		this.attentionValueWeights = new double[hiddenSize][hiddenSize];
		this.ffnUpWeights = new double[ffnHiddenSize][hiddenSize];
		this.ffnDownWeights = new double[hiddenSize][ffnHiddenSize];
		initialize(new Random(seed));
	}

	public VmTransformerLiteEncoding encode(VmCandidateSet vmSet, WorkflowStateView workflowState,
			SchedulingState state, double[] selectedTaskEmbedding, double[] pooledEmbedding,
			VmCandidateFeatureProjector projector)
	{
		double[] queryInput = concat(selectedTaskEmbedding, pooledEmbedding);
		double[] baseQueryEmbedding = layerNorm(tanh(matrixVector(queryProjectionWeights, queryInput)));
		double[] attentionQuery = tanh(matrixVector(attentionQueryWeights, baseQueryEmbedding));

		double[][] baseEmbeddings = new double[vmSet.size()][hiddenSize];
		double[][] valueEmbeddings = new double[vmSet.size()][hiddenSize];
		double[] scores = new double[vmSet.size()];
		double maxScore = Double.NEGATIVE_INFINITY;

		for(int index = 0; index < vmSet.size(); index++)
		{
			double[] baseFeatures = projector.extract(vmSet.get(index), workflowState, state);
			baseEmbeddings[index] = layerNorm(tanh(matrixVector(baseProjectionWeights, baseFeatures)));
			double[] keyEmbedding = tanh(matrixVector(attentionKeyWeights, baseEmbeddings[index]));
			valueEmbeddings[index] = tanh(matrixVector(attentionValueWeights, baseEmbeddings[index]));
			scores[index] = dot(attentionQuery, keyEmbedding) / Math.sqrt(Math.max(1.0, hiddenSize));
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

		/*
		 * This is intentionally Transformer-lite rather than a full transformer stack:
		 * one attention block, one FFN block, one head, no deep stacked layers.
		 * The goal is a minimal and numerically stable VM-candidate-set variant that
		 * is easy to compare against the existing vm_attention encoder.
		 */
		double[] contextEmbedding = layerNorm(add(baseQueryEmbedding, globalContext));
		double[][] candidateEmbeddings = new double[vmSet.size()][hiddenSize];
		for(int index = 0; index < vmSet.size(); index++)
		{
			double[] attentionResidual = add(baseEmbeddings[index], contextEmbedding);
			double[] attentionBlock = layerNorm(attentionResidual);
			double[] ffnHidden = tanh(matrixVector(ffnUpWeights, attentionBlock));
			double[] ffnOutput = matrixVector(ffnDownWeights, ffnHidden);
			candidateEmbeddings[index] = layerNorm(add(attentionBlock, ffnOutput));
		}

		return new VmTransformerLiteEncoding(candidateEmbeddings, baseQueryEmbedding, contextEmbedding);
	}

	public int getFfnHiddenSize()
	{
		return ffnHiddenSize;
	}

	private double[] concat(double[] left, double[] right)
	{
		double[] result = new double[left.length + right.length];
		System.arraycopy(left, 0, result, 0, left.length);
		System.arraycopy(right, 0, result, left.length, right.length);
		return result;
	}

	private double[] add(double[] left, double[] right)
	{
		double[] result = new double[left.length];
		for(int index = 0; index < left.length; index++)
		{
			result[index] = left[index] + right[index];
		}
		return result;
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

	private double[] layerNorm(double[] vector)
	{
		double mean = 0.0;
		for(double value: vector)
		{
			mean += value;
		}
		mean = mean / Math.max(1, vector.length);

		double variance = 0.0;
		for(double value: vector)
		{
			double centered = value - mean;
			variance += centered * centered;
		}
		variance = variance / Math.max(1, vector.length);
		double denominator = Math.sqrt(variance + NORM_EPSILON);

		double[] normalized = new double[vector.length];
		for(int index = 0; index < vector.length; index++)
		{
			normalized[index] = (vector[index] - mean) / denominator;
		}
		return normalized;
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
		fillMatrix(attentionQueryWeights, random);
		fillMatrix(attentionKeyWeights, random);
		fillMatrix(attentionValueWeights, random);
		fillMatrix(ffnUpWeights, random);
		fillMatrix(ffnDownWeights, random);
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
