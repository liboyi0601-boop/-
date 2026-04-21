package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class GraphAttentionTaskNetwork
{
	private final WorkflowGraphContextBuilder contextBuilder;
	private final WorkflowGraphEncoder encoder;
	private final TaskGraphPolicyHead policyHead;

	public GraphAttentionTaskNetwork(int hiddenSize, int graphLayers, long seed)
	{
		TaskNodeFeatureProjector projector = new TaskNodeFeatureProjector();
		this.contextBuilder = new WorkflowGraphContextBuilder(projector);
		this.encoder = new WorkflowGraphEncoder(TaskNodeFeatureProjector.INPUT_SIZE, hiddenSize, graphLayers, seed);
		this.policyHead = new TaskGraphPolicyHead(hiddenSize, seed + 1L);
	}

	public double trainOnExample(TaskDecisionContextExample example, double learningRate, double l2)
	{
		TaskForwardPass forwardPass = forward(example.getTaskSet(), example.getState());
		return policyHead.train(forwardPass.candidateEmbeddings, forwardPass.pooledEmbeddings,
				example.getTaskMask().getValidSelections(), example.getChosenTaskIndex(), learningRate, l2);
	}

	public int selectIndex(TaskDecisionContextExample example)
	{
		return selectIndex(example.getTaskSet(), example.getState(), buildValidMask(example),
				0.0, new Random(0L));
	}

	public int selectIndex(TaskCandidateSet taskSet, SchedulingState state, boolean[] validMask, double epsilon,
			Random random)
	{
		TaskForwardPass forwardPass = forward(taskSet, state);
		if(hasNoValidIndex(validMask))
		{
			return 0;
		}

		if(epsilon > 0.0 && random.nextDouble() < epsilon)
		{
			return selectRandom(validMask, random);
		}

		int bestIndex = 0;
		double bestLogit = Double.NEGATIVE_INFINITY;
		for(int index = 0; index < forwardPass.logits.length; index++)
		{
			if(!validMask[index])
			{
				continue;
			}
			if(forwardPass.logits[index] > bestLogit)
			{
				bestLogit = forwardPass.logits[index];
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private TaskForwardPass forward(TaskCandidateSet taskSet, SchedulingState state)
	{
		Map<Integer, WorkflowGraphEncoding> encodings = new LinkedHashMap<Integer, WorkflowGraphEncoding>();
		double[][] candidateEmbeddings = new double[taskSet.size()][];
		double[][] pooledEmbeddings = new double[taskSet.size()][];
		double[] logits = new double[taskSet.size()];

		for(int index = 0; index < taskSet.size(); index++)
		{
			TaskCandidateView candidate = taskSet.get(index);
			WorkflowGraphEncoding encoding = getEncoding(encodings, state, candidate.getWorkflowId());
			candidateEmbeddings[index] = encoding.getTaskEmbedding(candidate.getTaskId());
			pooledEmbeddings[index] = encoding.getPooledEmbedding();
			logits[index] = policyHead.score(candidateEmbeddings[index], pooledEmbeddings[index]);
		}

		return new TaskForwardPass(candidateEmbeddings, pooledEmbeddings, logits);
	}

	private boolean[] buildValidMask(TaskDecisionContextExample example)
	{
		boolean[] validMask = new boolean[example.getTaskSet().size()];
		for(int index = 0; index < validMask.length; index++)
		{
			validMask[index] = example.getTaskMask().isValid(index);
		}
		return validMask;
	}

	private WorkflowGraphEncoding getEncoding(Map<Integer, WorkflowGraphEncoding> encodings, SchedulingState state,
			int workflowId)
	{
		Integer key = Integer.valueOf(workflowId);
		WorkflowGraphEncoding encoding = encodings.get(key);
		if(encoding == null)
		{
			encoding = encoder.encode(contextBuilder.build(state, workflowId));
			encodings.put(key, encoding);
		}
		return encoding;
	}

	private boolean hasNoValidIndex(boolean[] validMask)
	{
		for(boolean valid: validMask)
		{
			if(valid)
			{
				return false;
			}
		}
		return true;
	}

	private int selectRandom(boolean[] validMask, Random random)
	{
		int validCount = 0;
		for(boolean valid: validMask)
		{
			if(valid)
			{
				validCount++;
			}
		}

		int selected = random.nextInt(validCount);
		for(int index = 0; index < validMask.length; index++)
		{
			if(!validMask[index])
			{
				continue;
			}
			if(selected == 0)
			{
				return index;
			}
			selected--;
		}
		return 0;
	}

	private static final class TaskForwardPass
	{
		private final double[][] candidateEmbeddings;
		private final double[][] pooledEmbeddings;
		private final double[] logits;

		private TaskForwardPass(double[][] candidateEmbeddings, double[][] pooledEmbeddings, double[] logits)
		{
			this.candidateEmbeddings = candidateEmbeddings;
			this.pooledEmbeddings = pooledEmbeddings;
			this.logits = logits;
		}
	}
}
