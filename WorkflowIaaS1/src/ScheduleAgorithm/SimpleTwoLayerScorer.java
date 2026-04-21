package ScheduleAgorithm;

import java.util.Random;

public final class SimpleTwoLayerScorer
{
	private final int inputSize;
	private final int hiddenSize;
	private final double[][] hiddenWeights;
	private final double[] hiddenBias;
	private final double[] outputWeights;
	private double outputBias;

	public SimpleTwoLayerScorer(int inputSize, int hiddenSize, Random random)
	{
		this.inputSize = inputSize;
		this.hiddenSize = hiddenSize;
		this.hiddenWeights = new double[hiddenSize][inputSize];
		this.hiddenBias = new double[hiddenSize];
		this.outputWeights = new double[hiddenSize];
		this.outputBias = 0.0;
		initialize(random);
	}

	public double score(double[] input)
	{
		double score = outputBias;
		for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
		{
			double hiddenActivation = activateHidden(input, hiddenIndex);
			score += outputWeights[hiddenIndex] * hiddenActivation;
		}
		return score;
	}

	public int selectIndex(MaskedDecisionExample example)
	{
		if(example.size() == 0)
		{
			return 0;
		}

		int bestIndex = -1;
		double bestScore = Double.NEGATIVE_INFINITY;
		for(int candidateIndex = 0; candidateIndex < example.size(); candidateIndex++)
		{
			if(!example.isValid(candidateIndex))
			{
				continue;
			}

			double score = score(example.getCandidateFeatures(candidateIndex));
			if(bestIndex == -1 || score > bestScore)
			{
				bestIndex = candidateIndex;
				bestScore = score;
			}
		}

		return bestIndex == -1 ? 0 : bestIndex;
	}

	public double trainOnExample(MaskedDecisionExample example, double learningRate, double l2)
	{
		if(example.size() == 0)
		{
			return 0.0;
		}
		if(example.getFeatureSize() != inputSize)
		{
			throw new IllegalArgumentException("Feature size mismatch: expected " + inputSize
					+ ", actual " + example.getFeatureSize());
		}
		if(!example.isValid(example.getChosenIndex()))
		{
			throw new IllegalArgumentException("Chosen action is not valid: " + example.getChosenIndex());
		}

		double[][] hiddenActivations = new double[example.size()][hiddenSize];
		double[] scores = new double[example.size()];
		double maxScore = Double.NEGATIVE_INFINITY;

		for(int candidateIndex = 0; candidateIndex < example.size(); candidateIndex++)
		{
			if(!example.isValid(candidateIndex))
			{
				scores[candidateIndex] = Double.NEGATIVE_INFINITY;
				continue;
			}

			double score = outputBias;
			double[] input = example.getCandidateFeatures(candidateIndex);
			for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
			{
				double activation = activateHidden(input, hiddenIndex);
				hiddenActivations[candidateIndex][hiddenIndex] = activation;
				score += outputWeights[hiddenIndex] * activation;
			}
			scores[candidateIndex] = score;
			if(score > maxScore)
			{
				maxScore = score;
			}
		}

		double[] probabilities = new double[example.size()];
		double denominator = 0.0;
		for(int candidateIndex = 0; candidateIndex < example.size(); candidateIndex++)
		{
			if(!example.isValid(candidateIndex))
			{
				continue;
			}
			double weight = Math.exp(scores[candidateIndex] - maxScore);
			probabilities[candidateIndex] = weight;
			denominator += weight;
		}

		for(int candidateIndex = 0; candidateIndex < probabilities.length; candidateIndex++)
		{
			if(example.isValid(candidateIndex))
			{
				probabilities[candidateIndex] = probabilities[candidateIndex] / Math.max(1e-12, denominator);
			}
		}

		double loss = -Math.log(Math.max(1e-12, probabilities[example.getChosenIndex()]));
		double[][] hiddenWeightGradient = new double[hiddenSize][inputSize];
		double[] hiddenBiasGradient = new double[hiddenSize];
		double[] outputWeightGradient = new double[hiddenSize];
		double outputBiasGradient = 0.0;

		for(int candidateIndex = 0; candidateIndex < example.size(); candidateIndex++)
		{
			if(!example.isValid(candidateIndex))
			{
				continue;
			}

			double delta = probabilities[candidateIndex] - (candidateIndex == example.getChosenIndex() ? 1.0 : 0.0);
			double[] input = example.getCandidateFeatures(candidateIndex);
			outputBiasGradient += delta;

			for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
			{
				double hiddenActivation = hiddenActivations[candidateIndex][hiddenIndex];
				outputWeightGradient[hiddenIndex] += delta * hiddenActivation;

				double backPropagated = delta * outputWeights[hiddenIndex]
						* (1.0 - hiddenActivation * hiddenActivation);
				hiddenBiasGradient[hiddenIndex] += backPropagated;
				for(int inputIndex = 0; inputIndex < inputSize; inputIndex++)
				{
					hiddenWeightGradient[hiddenIndex][inputIndex] += backPropagated * input[inputIndex];
				}
			}
		}

		for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
		{
			for(int inputIndex = 0; inputIndex < inputSize; inputIndex++)
			{
				hiddenWeights[hiddenIndex][inputIndex] -= learningRate
						* (hiddenWeightGradient[hiddenIndex][inputIndex] + l2 * hiddenWeights[hiddenIndex][inputIndex]);
			}
			hiddenBias[hiddenIndex] -= learningRate * hiddenBiasGradient[hiddenIndex];
			outputWeights[hiddenIndex] -= learningRate
					* (outputWeightGradient[hiddenIndex] + l2 * outputWeights[hiddenIndex]);
		}
		outputBias -= learningRate * outputBiasGradient;

		return loss;
	}

	private void initialize(Random random)
	{
		for(int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++)
		{
			for(int inputIndex = 0; inputIndex < inputSize; inputIndex++)
			{
				hiddenWeights[hiddenIndex][inputIndex] = (random.nextDouble() - 0.5) * 0.1;
			}
			outputWeights[hiddenIndex] = (random.nextDouble() - 0.5) * 0.1;
			hiddenBias[hiddenIndex] = 0.0;
		}
		outputBias = 0.0;
	}

	private double activateHidden(double[] input, int hiddenIndex)
	{
		double hidden = hiddenBias[hiddenIndex];
		for(int inputIndex = 0; inputIndex < inputSize; inputIndex++)
		{
			hidden += hiddenWeights[hiddenIndex][inputIndex] * input[inputIndex];
		}
		return Math.tanh(hidden);
	}
}
