package ScheduleAgorithm;

import java.util.List;
import java.util.Random;

public final class TaskGraphPolicyHead
{
	private final double[] weights;
	private double bias;

	public TaskGraphPolicyHead(int hiddenSize, long seed)
	{
		this.weights = new double[hiddenSize * 2];
		this.bias = 0.0;
		initialize(new Random(seed));
	}

	public double score(double[] candidateEmbedding, double[] pooledEmbedding)
	{
		return score(buildInput(candidateEmbedding, pooledEmbedding));
	}

	public double train(double[][] candidateEmbeddings, double[][] pooledEmbeddings, List<Boolean> validSelections,
			int chosenIndex, double learningRate, double l2)
	{
		double[][] inputs = new double[candidateEmbeddings.length][];
		for(int index = 0; index < candidateEmbeddings.length; index++)
		{
			inputs[index] = buildInput(candidateEmbeddings[index], pooledEmbeddings[index]);
		}

		double[] logits = new double[inputs.length];
		double maxLogit = Double.NEGATIVE_INFINITY;
		for(int index = 0; index < inputs.length; index++)
		{
			if(!validSelections.get(index).booleanValue())
			{
				logits[index] = Double.NEGATIVE_INFINITY;
				continue;
			}
			logits[index] = score(inputs[index]);
			if(logits[index] > maxLogit)
			{
				maxLogit = logits[index];
			}
		}

		double[] probabilities = new double[inputs.length];
		double denominator = 0.0;
		for(int index = 0; index < logits.length; index++)
		{
			if(!validSelections.get(index).booleanValue())
			{
				continue;
			}
			probabilities[index] = Math.exp(logits[index] - maxLogit);
			denominator += probabilities[index];
		}

		for(int index = 0; index < probabilities.length; index++)
		{
			if(validSelections.get(index).booleanValue())
			{
				probabilities[index] = probabilities[index] / Math.max(1e-12, denominator);
			}
		}

		double[] gradient = new double[weights.length];
		double biasGradient = 0.0;
		for(int index = 0; index < inputs.length; index++)
		{
			if(!validSelections.get(index).booleanValue())
			{
				continue;
			}
			double delta = probabilities[index] - (index == chosenIndex ? 1.0 : 0.0);
			biasGradient += delta;
			for(int weightIndex = 0; weightIndex < weights.length; weightIndex++)
			{
				gradient[weightIndex] += delta * inputs[index][weightIndex];
			}
		}

		for(int weightIndex = 0; weightIndex < weights.length; weightIndex++)
		{
			weights[weightIndex] -= learningRate * (gradient[weightIndex] + l2 * weights[weightIndex]);
		}
		bias -= learningRate * biasGradient;

		return -Math.log(Math.max(1e-12, probabilities[chosenIndex]));
	}

	private double[] buildInput(double[] candidateEmbedding, double[] pooledEmbedding)
	{
		double[] input = new double[candidateEmbedding.length + pooledEmbedding.length];
		System.arraycopy(candidateEmbedding, 0, input, 0, candidateEmbedding.length);
		System.arraycopy(pooledEmbedding, 0, input, candidateEmbedding.length, pooledEmbedding.length);
		return input;
	}

	private double score(double[] input)
	{
		double value = bias;
		for(int index = 0; index < weights.length; index++)
		{
			value += weights[index] * input[index];
		}
		return value;
	}

	private void initialize(Random random)
	{
		for(int index = 0; index < weights.length; index++)
		{
			weights[index] = (random.nextDouble() - 0.5) * 0.1;
		}
	}
}
