package ScheduleAgorithm;

import java.util.Random;

public final class WorkflowGraphEncoder
{
	private final int inputSize;
	private final int hiddenSize;
	private final int layers;
	private final double[][] inputWeights;
	private final double[][][] selfWeights;
	private final double[][][] parentWeights;
	private final double[][][] childWeights;
	private final double[][] layerBiases;
	private final double[][] parentSelfAttention;
	private final double[][] parentNeighborAttention;
	private final double[][] childSelfAttention;
	private final double[][] childNeighborAttention;

	public WorkflowGraphEncoder(int inputSize, int hiddenSize, int layers, long seed)
	{
		this.inputSize = inputSize;
		this.hiddenSize = hiddenSize;
		this.layers = layers;
		this.inputWeights = new double[hiddenSize][inputSize];
		this.selfWeights = new double[layers][hiddenSize][hiddenSize];
		this.parentWeights = new double[layers][hiddenSize][hiddenSize];
		this.childWeights = new double[layers][hiddenSize][hiddenSize];
		this.layerBiases = new double[layers][hiddenSize];
		this.parentSelfAttention = new double[layers][hiddenSize];
		this.parentNeighborAttention = new double[layers][hiddenSize];
		this.childSelfAttention = new double[layers][hiddenSize];
		this.childNeighborAttention = new double[layers][hiddenSize];
		initialize(new Random(seed));
	}

	public WorkflowGraphEncoding encode(WorkflowGraphContext context)
	{
		int nodeCount = context.getNodeCount();
		double[][] hidden = new double[nodeCount][hiddenSize];
		for(int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++)
		{
			hidden[nodeIndex] = tanh(matrixVector(inputWeights, context.getNodeInputFeatures(nodeIndex)));
		}

		for(int layerIndex = 0; layerIndex < layers; layerIndex++)
		{
			double[][] nextHidden = new double[nodeCount][hiddenSize];
			for(int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++)
			{
				double[] selfPart = matrixVector(selfWeights[layerIndex], hidden[nodeIndex]);
				double[] parentAggregate = aggregateDirectional(hidden, nodeIndex, context.getParentIndices(nodeIndex),
						parentSelfAttention[layerIndex], parentNeighborAttention[layerIndex]);
				double[] childAggregate = aggregateDirectional(hidden, nodeIndex, context.getChildIndices(nodeIndex),
						childSelfAttention[layerIndex], childNeighborAttention[layerIndex]);
				double[] parentPart = matrixVector(parentWeights[layerIndex], parentAggregate);
				double[] childPart = matrixVector(childWeights[layerIndex], childAggregate);
				nextHidden[nodeIndex] = tanh(add(selfPart, parentPart, childPart, layerBiases[layerIndex]));
			}
			hidden = nextHidden;
		}

		double[] pooled = new double[hiddenSize];
		for(int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++)
		{
			for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
			{
				pooled[hiddenIndex] += hidden[nodeIndex][hiddenIndex];
			}
		}
		for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
		{
			pooled[hiddenIndex] /= Math.max(1.0, nodeCount);
		}

		return new WorkflowGraphEncoding(context, hidden, pooled);
	}

	private double[] aggregateDirectional(double[][] nodeEmbeddings, int nodeIndex, int[] neighbors,
			double[] selfAttentionVector, double[] neighborAttentionVector)
	{
		double[] aggregate = new double[hiddenSize];
		if(neighbors.length == 0)
		{
			return aggregate;
		}

		double[] scores = new double[neighbors.length];
		double maxScore = Double.NEGATIVE_INFINITY;
		for(int i = 0; i < neighbors.length; i++)
		{
			scores[i] = dot(selfAttentionVector, nodeEmbeddings[nodeIndex])
					+ dot(neighborAttentionVector, nodeEmbeddings[neighbors[i]]);
			if(scores[i] > maxScore)
			{
				maxScore = scores[i];
			}
		}

		double denominator = 0.0;
		double[] weights = new double[neighbors.length];
		for(int i = 0; i < neighbors.length; i++)
		{
			weights[i] = Math.exp(scores[i] - maxScore);
			denominator += weights[i];
		}

		for(int i = 0; i < neighbors.length; i++)
		{
			double normalizedWeight = weights[i] / Math.max(1e-12, denominator);
			double[] neighborEmbedding = nodeEmbeddings[neighbors[i]];
			for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
			{
				aggregate[hiddenIndex] += normalizedWeight * neighborEmbedding[hiddenIndex];
			}
		}
		return aggregate;
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

	private double[] add(double[] first, double[] second, double[] third, double[] fourth)
	{
		double[] result = new double[first.length];
		for(int index = 0; index < result.length; index++)
		{
			result[index] = first[index] + second[index] + third[index] + fourth[index];
		}
		return result;
	}

	private double[] tanh(double[] values)
	{
		double[] activated = new double[values.length];
		for(int index = 0; index < values.length; index++)
		{
			activated[index] = Math.tanh(values[index]);
		}
		return activated;
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
		fillMatrix(inputWeights, random);
		for(int layerIndex = 0; layerIndex < layers; layerIndex++)
		{
			fillMatrix(selfWeights[layerIndex], random);
			fillMatrix(parentWeights[layerIndex], random);
			fillMatrix(childWeights[layerIndex], random);
			fillVector(parentSelfAttention[layerIndex], random);
			fillVector(parentNeighborAttention[layerIndex], random);
			fillVector(childSelfAttention[layerIndex], random);
			fillVector(childNeighborAttention[layerIndex], random);
		}
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

	private void fillVector(double[] vector, Random random)
	{
		for(int index = 0; index < vector.length; index++)
		{
			vector[index] = (random.nextDouble() - 0.5) * 0.1;
		}
	}
}
