package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class AblationPolicyFactory
{
	public static final String PHASE8_MLP = "phase8_mlp";
	public static final String PHASE9_GRAPH_PLUS_VM_ATTENTION = "phase9_graph_plus_vm_attention";
	public static final String PHASE9_GRAPH_PLUS_MLP_VM = "phase9_graph_plus_mlp_vm";
	public static final String PHASE9_MLP_TASK_PLUS_VM_ATTENTION = "phase9_mlp_task_plus_vm_attention";
	public static final String RANDOM_POLICY = "random_policy";
	public static final String HEURISTIC_RERANK = "heuristic_rerank";

	private AblationPolicyFactory()
	{
	}

	public static List<String> defaultVariantNames()
	{
		return Arrays.asList(
				PHASE8_MLP,
				PHASE9_GRAPH_PLUS_VM_ATTENTION,
				PHASE9_GRAPH_PLUS_MLP_VM,
				PHASE9_MLP_TASK_PLUS_VM_ATTENTION,
				RANDOM_POLICY);
	}

	public static List<String> supportedVariantNames()
	{
		return Arrays.asList(
				PHASE8_MLP,
				PHASE9_GRAPH_PLUS_VM_ATTENTION,
				PHASE9_GRAPH_PLUS_MLP_VM,
				PHASE9_MLP_TASK_PLUS_VM_ATTENTION,
				RANDOM_POLICY,
				HEURISTIC_RERANK);
	}

	public static VariantTrainingResult trainVariant(String variantName, HierarchicalReplayBuffer flatReplay,
			ContextualHierarchicalReplayBuffer contextualReplay, TrainingOptions options,
			EpochTrainingListener epochListener)
	{
		if(PHASE8_MLP.equals(variantName))
		{
			OfflineWarmStartTrainer trainer = new OfflineWarmStartTrainer(
					options.taskHiddenSize,
					options.vmHiddenSize,
					options.epochs,
					options.learningRate,
					options.l2,
					options.epsilon,
					options.seed);
			OfflineWarmStartResult result = trainer.train(flatReplay, epochListener);
			return new VariantTrainingResult(variantName, "PHASE8_HIERARCHICAL_WARM_START",
					result.getSummary(), result.getPolicy(), result.getPolicy());
		}

		if(PHASE9_GRAPH_PLUS_VM_ATTENTION.equals(variantName))
		{
			GraphAttentionWarmStartTrainer trainer = new GraphAttentionWarmStartTrainer(
					options.graphHiddenSize,
					options.vmHiddenSize,
					options.graphLayers,
					options.epochs,
					options.learningRate,
					options.l2,
					options.epsilon,
					options.seed);
			GraphAttentionWarmStartResult result = trainer.train(contextualReplay, epochListener);
			return new VariantTrainingResult(variantName, "PHASE9_GRAPH_ATTENTION_WARM_START",
					result.getSummary(), null, result.getPolicy());
		}

		if(PHASE9_GRAPH_PLUS_MLP_VM.equals(variantName))
		{
			HybridWarmStartTrainer trainer = new HybridWarmStartTrainer(
					true, false, options.graphHiddenSize, options.taskHiddenSize, options.vmHiddenSize,
					options.graphLayers, options.epochs, options.learningRate, options.l2,
					options.epsilon, options.seed);
			return trainer.train(variantName, flatReplay, contextualReplay, epochListener);
		}

		if(PHASE9_MLP_TASK_PLUS_VM_ATTENTION.equals(variantName))
		{
			HybridWarmStartTrainer trainer = new HybridWarmStartTrainer(
					false, true, options.graphHiddenSize, options.taskHiddenSize, options.vmHiddenSize,
					options.graphLayers, options.epochs, options.learningRate, options.l2,
					options.epsilon, options.seed);
			return trainer.train(variantName, flatReplay, contextualReplay, epochListener);
		}

		if(RANDOM_POLICY.equals(variantName))
		{
			Map<String, Object> summary = new LinkedHashMap<String, Object>();
			summary.put("epochs", 0);
			summary.put("seed", options.seed);
			summary.put("finalTaskLoss", null);
			summary.put("finalVmLoss", null);
			return new VariantTrainingResult(variantName, "PHASE10A_RANDOM_POLICY",
					summary, null, new RandomSchedulingPolicy(options.seed));
		}

		if(HEURISTIC_RERANK.equals(variantName))
		{
			Map<String, Object> summary = new LinkedHashMap<String, Object>();
			summary.put("epochs", 0);
			summary.put("seed", options.seed);
			summary.put("finalTaskLoss", null);
			summary.put("finalVmLoss", null);
			return new VariantTrainingResult(variantName, "PHASE10A_HEURISTIC_RERANK",
					summary, null, new HeuristicRerankPolicy());
		}

		throw new IllegalArgumentException("Unsupported variant: " + variantName);
	}

	public static final class TrainingOptions
	{
		private final int epochs;
		private final int taskHiddenSize;
		private final int graphHiddenSize;
		private final int vmHiddenSize;
		private final int graphLayers;
		private final double learningRate;
		private final double l2;
		private final double epsilon;
		private final long seed;

		public TrainingOptions(int epochs, int taskHiddenSize, int graphHiddenSize, int vmHiddenSize,
				int graphLayers, double learningRate, double l2, double epsilon, long seed)
		{
			this.epochs = epochs;
			this.taskHiddenSize = taskHiddenSize;
			this.graphHiddenSize = graphHiddenSize;
			this.vmHiddenSize = vmHiddenSize;
			this.graphLayers = graphLayers;
			this.learningRate = learningRate;
			this.l2 = l2;
			this.epsilon = epsilon;
			this.seed = seed;
		}
	}

	public static final class VariantTrainingResult
	{
		private final String variantName;
		private final String algorithmName;
		private final Map<String, Object> summary;
		private final SchedulingPolicy baselinePolicy;
		private final HierarchicalSchedulingPolicy hierarchicalPolicy;

		private VariantTrainingResult(String variantName, String algorithmName, Map<String, Object> summary,
				SchedulingPolicy baselinePolicy, HierarchicalSchedulingPolicy hierarchicalPolicy)
		{
			this.variantName = variantName;
			this.algorithmName = algorithmName;
			this.summary = new LinkedHashMap<String, Object>(summary);
			this.baselinePolicy = baselinePolicy;
			this.hierarchicalPolicy = hierarchicalPolicy;
		}

		public String getVariantName()
		{
			return variantName;
		}

		public String getAlgorithmName()
		{
			return algorithmName;
		}

		public Map<String, Object> getSummary()
		{
			return new LinkedHashMap<String, Object>(summary);
		}

		public SchedulingPolicy getBaselinePolicy()
		{
			return baselinePolicy;
		}

		public HierarchicalSchedulingPolicy getHierarchicalPolicy()
		{
			return hierarchicalPolicy;
		}
	}

	private static final class HybridWarmStartTrainer
	{
		private final boolean useGraphTask;
		private final boolean useVmAttention;
		private final int graphHiddenSize;
		private final int taskHiddenSize;
		private final int vmHiddenSize;
		private final int graphLayers;
		private final int epochs;
		private final double learningRate;
		private final double l2;
		private final double epsilon;
		private final long seed;

		private HybridWarmStartTrainer(boolean useGraphTask, boolean useVmAttention, int graphHiddenSize,
				int taskHiddenSize, int vmHiddenSize, int graphLayers, int epochs, double learningRate, double l2,
				double epsilon, long seed)
		{
			this.useGraphTask = useGraphTask;
			this.useVmAttention = useVmAttention;
			this.graphHiddenSize = graphHiddenSize;
			this.taskHiddenSize = taskHiddenSize;
			this.vmHiddenSize = vmHiddenSize;
			this.graphLayers = graphLayers;
			this.epochs = epochs;
			this.learningRate = learningRate;
			this.l2 = l2;
			this.epsilon = epsilon;
			this.seed = seed;
		}

		private VariantTrainingResult train(String variantName, HierarchicalReplayBuffer flatReplay,
				ContextualHierarchicalReplayBuffer contextualReplay, EpochTrainingListener epochListener)
		{
			GraphAttentionTaskNetwork taskNetwork = useGraphTask
					? new GraphAttentionTaskNetwork(graphHiddenSize, graphLayers, seed)
					: null;
			GraphAttentionVmNetwork vmNetwork = useVmAttention
					? new GraphAttentionVmNetwork(graphHiddenSize, vmHiddenSize, graphLayers, seed + 1000L)
					: null;
			SimpleTwoLayerScorer taskScorer = useGraphTask
					? null
					: new SimpleTwoLayerScorer(flatReplay.getTaskExamples().get(0).getFeatureSize(),
							taskHiddenSize, new Random(seed));
			SimpleTwoLayerScorer vmScorer = useVmAttention
					? null
					: new SimpleTwoLayerScorer(flatReplay.getVmExamples().get(0).getFeatureSize(),
							vmHiddenSize, new Random(seed + 1L));

			double lastTaskLoss = 0.0;
			double lastVmLoss = 0.0;
			for(int epoch = 0; epoch < epochs; epoch++)
			{
				lastTaskLoss = trainTask(taskNetwork, taskScorer, flatReplay, contextualReplay);
				lastVmLoss = trainVm(vmNetwork, vmScorer, flatReplay, contextualReplay);

				if(epochListener != null)
				{
					Map<String, Object> epochMetrics = new LinkedHashMap<String, Object>();
					epochMetrics.put("taskLoss", lastTaskLoss);
					epochMetrics.put("vmLoss", lastVmLoss);
					epochMetrics.put("taskChosenActionAccuracy",
							computeTaskAccuracy(taskNetwork, taskScorer, flatReplay, contextualReplay));
					epochMetrics.put("vmChosenActionAccuracy",
							computeVmAccuracy(vmNetwork, vmScorer, flatReplay, contextualReplay));
					epochMetrics.put("taskMaskHitRate",
							computeTaskMaskHitRate(taskNetwork, taskScorer, flatReplay, contextualReplay));
					epochMetrics.put("vmMaskHitRate",
							computeVmMaskHitRate(vmNetwork, vmScorer, flatReplay, contextualReplay));
					epochListener.onEpoch(epoch, epochMetrics, null,
							new HybridHierarchicalPolicy(taskNetwork, taskScorer, vmNetwork, vmScorer,
									epsilon, seed + 2000L));
				}
			}

			Map<String, Object> summary = new LinkedHashMap<String, Object>();
			summary.putAll(flatReplay.toSummary());
			summary.put("epochs", epochs);
			summary.put("learningRate", learningRate);
			summary.put("l2", l2);
			summary.put("taskHiddenSize", taskHiddenSize);
			summary.put("graphHiddenSize", graphHiddenSize);
			summary.put("vmHiddenSize", vmHiddenSize);
			summary.put("graphLayers", graphLayers);
			summary.put("epsilon", epsilon);
			summary.put("seed", seed);
			summary.put("taskModel", useGraphTask ? "graph" : "mlp");
			summary.put("vmModel", useVmAttention ? "vm_attention" : "mlp");
			summary.put("finalTaskLoss", lastTaskLoss);
			summary.put("finalVmLoss", lastVmLoss);

			return new VariantTrainingResult(variantName,
					useGraphTask ? "PHASE10A_GRAPH_TASK_HYBRID" : "PHASE10A_MLP_TASK_HYBRID",
					summary,
					null,
					new HybridHierarchicalPolicy(taskNetwork, taskScorer, vmNetwork, vmScorer,
							epsilon, seed + 2000L));
		}

		private double trainTask(GraphAttentionTaskNetwork taskNetwork, SimpleTwoLayerScorer taskScorer,
				HierarchicalReplayBuffer flatReplay, ContextualHierarchicalReplayBuffer contextualReplay)
		{
			double lossSum = 0.0;
			if(useGraphTask)
			{
				for(TaskDecisionContextExample example: contextualReplay.getTaskExamples())
				{
					lossSum += taskNetwork.trainOnExample(example, learningRate, l2);
				}
				return lossSum / contextualReplay.getTaskExamples().size();
			}

			for(MaskedDecisionExample example: flatReplay.getTaskExamples())
			{
				lossSum += taskScorer.trainOnExample(example, learningRate, l2);
			}
			return lossSum / flatReplay.getTaskExamples().size();
		}

		private double trainVm(GraphAttentionVmNetwork vmNetwork, SimpleTwoLayerScorer vmScorer,
				HierarchicalReplayBuffer flatReplay, ContextualHierarchicalReplayBuffer contextualReplay)
		{
			double lossSum = 0.0;
			if(useVmAttention)
			{
				for(VmDecisionContextExample example: contextualReplay.getVmExamples())
				{
					lossSum += vmNetwork.trainOnExample(example, learningRate, l2);
				}
				return lossSum / contextualReplay.getVmExamples().size();
			}

			for(MaskedDecisionExample example: flatReplay.getVmExamples())
			{
				lossSum += vmScorer.trainOnExample(example, learningRate, l2);
			}
			return lossSum / flatReplay.getVmExamples().size();
		}

		private double computeTaskAccuracy(GraphAttentionTaskNetwork taskNetwork, SimpleTwoLayerScorer taskScorer,
				HierarchicalReplayBuffer flatReplay, ContextualHierarchicalReplayBuffer contextualReplay)
		{
			int correctCount = 0;
			int total = useGraphTask ? contextualReplay.getTaskExamples().size() : flatReplay.getTaskExamples().size();
			if(useGraphTask)
			{
				for(TaskDecisionContextExample example: contextualReplay.getTaskExamples())
				{
					if(taskNetwork.selectIndex(example) == example.getChosenTaskIndex())
					{
						correctCount++;
					}
				}
			}
			else
			{
				for(MaskedDecisionExample example: flatReplay.getTaskExamples())
				{
					if(taskScorer.selectIndex(example) == example.getChosenIndex())
					{
						correctCount++;
					}
				}
			}
			return (double)correctCount / total;
		}

		private double computeVmAccuracy(GraphAttentionVmNetwork vmNetwork, SimpleTwoLayerScorer vmScorer,
				HierarchicalReplayBuffer flatReplay, ContextualHierarchicalReplayBuffer contextualReplay)
		{
			int correctCount = 0;
			int total = useVmAttention ? contextualReplay.getVmExamples().size() : flatReplay.getVmExamples().size();
			if(useVmAttention)
			{
				for(VmDecisionContextExample example: contextualReplay.getVmExamples())
				{
					if(vmNetwork.selectIndex(example) == example.getChosenVmIndex())
					{
						correctCount++;
					}
				}
			}
			else
			{
				for(MaskedDecisionExample example: flatReplay.getVmExamples())
				{
					if(vmScorer.selectIndex(example) == example.getChosenIndex())
					{
						correctCount++;
					}
				}
			}
			return (double)correctCount / total;
		}

		private double computeTaskMaskHitRate(GraphAttentionTaskNetwork taskNetwork, SimpleTwoLayerScorer taskScorer,
				HierarchicalReplayBuffer flatReplay, ContextualHierarchicalReplayBuffer contextualReplay)
		{
			int validCount = 0;
			int total = useGraphTask ? contextualReplay.getTaskExamples().size() : flatReplay.getTaskExamples().size();
			if(useGraphTask)
			{
				for(TaskDecisionContextExample example: contextualReplay.getTaskExamples())
				{
					if(example.getTaskMask().isValid(taskNetwork.selectIndex(example)))
					{
						validCount++;
					}
				}
			}
			else
			{
				for(MaskedDecisionExample example: flatReplay.getTaskExamples())
				{
					if(example.isValid(taskScorer.selectIndex(example)))
					{
						validCount++;
					}
				}
			}
			return (double)validCount / total;
		}

		private double computeVmMaskHitRate(GraphAttentionVmNetwork vmNetwork, SimpleTwoLayerScorer vmScorer,
				HierarchicalReplayBuffer flatReplay, ContextualHierarchicalReplayBuffer contextualReplay)
		{
			int validCount = 0;
			int total = useVmAttention ? contextualReplay.getVmExamples().size() : flatReplay.getVmExamples().size();
			if(useVmAttention)
			{
				for(VmDecisionContextExample example: contextualReplay.getVmExamples())
				{
					if(example.getVmMask().isValid(vmNetwork.selectIndex(example)))
					{
						validCount++;
					}
				}
			}
			else
			{
				for(MaskedDecisionExample example: flatReplay.getVmExamples())
				{
					if(example.isValid(vmScorer.selectIndex(example)))
					{
						validCount++;
					}
				}
			}
			return (double)validCount / total;
		}
	}

	private static final class HybridHierarchicalPolicy implements HierarchicalSchedulingPolicy
	{
		private final NosfBaselinePolicy compatibilityPolicy;
		private final TaskFeatureExtractor taskFeatureExtractor;
		private final VmFeatureExtractor vmFeatureExtractor;
		private final GraphAttentionTaskNetwork graphTaskNetwork;
		private final SimpleTwoLayerScorer taskScorer;
		private final GraphAttentionVmNetwork vmNetwork;
		private final SimpleTwoLayerScorer vmScorer;
		private final double epsilon;
		private final Random random;

		private HybridHierarchicalPolicy(GraphAttentionTaskNetwork graphTaskNetwork, SimpleTwoLayerScorer taskScorer,
				GraphAttentionVmNetwork vmNetwork, SimpleTwoLayerScorer vmScorer, double epsilon, long seed)
		{
			this.compatibilityPolicy = new NosfBaselinePolicy();
			this.taskFeatureExtractor = new TaskFeatureExtractor();
			this.vmFeatureExtractor = new VmFeatureExtractor();
			this.graphTaskNetwork = graphTaskNetwork;
			this.taskScorer = taskScorer;
			this.vmNetwork = vmNetwork;
			this.vmScorer = vmScorer;
			this.epsilon = epsilon;
			this.random = new Random(seed);
		}

		public TaskSelection selectTask(TaskCandidateSet taskSet, SchedulingState state)
		{
			if(state == null || taskSet.isEmpty())
			{
				return compatibilityPolicy.selectTask(taskSet, state);
			}

			boolean[] validMask = buildTaskMask(taskSet);
			int selectedIndex;
			if(graphTaskNetwork != null)
			{
				selectedIndex = graphTaskNetwork.selectIndex(taskSet, state, validMask, epsilon, random);
			}
			else
			{
				selectedIndex = shouldExplore(validMask)
						? selectRandom(validMask)
						: taskScorer.selectIndex(buildTaskExample(taskSet, state, validMask));
			}
			return new TaskSelection(selectedIndex, taskSet.get(selectedIndex));
		}

		public ResourceSelection selectResource(TaskCandidateView selectedTask, VmCandidateSet vmSet,
				SchedulingState state)
		{
			if(state == null || vmSet.isEmpty())
			{
				return compatibilityPolicy.selectResource(selectedTask, vmSet, state);
			}

			boolean[] validMask = buildVmMask(vmSet);
			int selectedIndex;
			if(vmNetwork != null)
			{
				selectedIndex = vmNetwork.selectIndex(selectedTask, vmSet, state, validMask, epsilon, random);
			}
			else
			{
				selectedIndex = shouldExplore(validMask)
						? selectRandom(validMask)
						: vmScorer.selectIndex(buildVmExample(selectedTask, vmSet, state, validMask));
			}
			return new ResourceSelection(selectedIndex, vmSet.get(selectedIndex));
		}

		private MaskedDecisionExample buildTaskExample(TaskCandidateSet taskSet, SchedulingState state, boolean[] validMask)
		{
			List<double[]> features = new ArrayList<double[]>();
			List<Boolean> validSelections = new ArrayList<Boolean>();
			for(int index = 0; index < taskSet.size(); index++)
			{
				features.add(taskFeatureExtractor.extract(taskSet.get(index), state));
				validSelections.add(Boolean.valueOf(validMask[index]));
			}
			return new MaskedDecisionExample(features, validSelections, selectFallbackIndex(validMask));
		}

		private MaskedDecisionExample buildVmExample(TaskCandidateView selectedTask, VmCandidateSet vmSet,
				SchedulingState state, boolean[] validMask)
		{
			List<double[]> features = new ArrayList<double[]>();
			List<Boolean> validSelections = new ArrayList<Boolean>();
			for(int index = 0; index < vmSet.size(); index++)
			{
				features.add(vmFeatureExtractor.extract(selectedTask, vmSet.get(index), state));
				validSelections.add(Boolean.valueOf(validMask[index]));
			}
			return new MaskedDecisionExample(features, validSelections, selectFallbackIndex(validMask));
		}

		private boolean[] buildTaskMask(TaskCandidateSet taskSet)
		{
			boolean[] validMask = new boolean[taskSet.size()];
			for(int index = 0; index < taskSet.size(); index++)
			{
				TaskCandidateView candidate = taskSet.get(index);
				validMask[index] = !candidate.getTask().getAllocatedFlag() && !candidate.getTask().getFinishFlag();
			}
			return validMask;
		}

		private boolean[] buildVmMask(VmCandidateSet vmSet)
		{
			boolean[] validMask = new boolean[vmSet.size()];
			for(int index = 0; index < vmSet.size(); index++)
			{
				VmCandidateView candidate = vmSet.get(index);
				validMask[index] = candidate.getCandidateKind() == VmCandidateKind.EXISTING_VM
						? candidate.getFeasibleUnderSubDeadline() : true;
			}
			return validMask;
		}

		private int selectFallbackIndex(boolean[] validMask)
		{
			for(int index = 0; index < validMask.length; index++)
			{
				if(validMask[index])
				{
					return index;
				}
			}
			return 0;
		}

		private boolean shouldExplore(boolean[] validMask)
		{
			return epsilon > 0.0 && hasValidIndex(validMask) && random.nextDouble() < epsilon;
		}

		private int selectRandom(boolean[] validMask)
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

		private boolean hasValidIndex(boolean[] validMask)
		{
			for(boolean valid: validMask)
			{
				if(valid)
				{
					return true;
				}
			}
			return false;
		}
	}
}
