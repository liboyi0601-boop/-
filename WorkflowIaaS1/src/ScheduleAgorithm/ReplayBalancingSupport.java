package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class ReplayBalancingSupport
{
	public static final String STRATEGY_MIN_QUOTA = "min-quota";
	private static final String MODE_FAMILY_BALANCED = "family-balanced";
	private static final String QUOTA_RULE = "minimum-positive-family-count";

	private ReplayBalancingSupport()
	{
	}

	public static BalanceResult<HierarchicalReplayBuffer> balanceFlatReplay(HierarchicalReplayBuffer source,
			BalanceOptions options)
	{
		if(source == null)
		{
			return new BalanceResult<HierarchicalReplayBuffer>(null,
					buildUnavailableSummary(options, "flat", "replay-buffer-unavailable"));
		}

		Map<String, Integer> taskCountsBefore = countFlatFamilies(source.getTaskExamples(), options.getExpectedFamilies());
		Map<String, Integer> vmCountsBefore = countFlatFamilies(source.getVmExamples(), options.getExpectedFamilies());
		int untrackedTaskExamples = countFlatUntracked(source.getTaskExamples());
		int untrackedVmExamples = countFlatUntracked(source.getVmExamples());
		BalancePlan plan = buildPlan(options, taskCountsBefore, vmCountsBefore);
		if(!plan.isApplied())
		{
			return new BalanceResult<HierarchicalReplayBuffer>(source,
					buildSummary(options, "flat", plan, taskCountsBefore, taskCountsBefore, vmCountsBefore, vmCountsBefore,
							untrackedTaskExamples, untrackedVmExamples));
		}

		HierarchicalReplayBuffer balancedReplay = new HierarchicalReplayBuffer();
		List<IndexedMaskedDecisionExample> selectedTaskExamples =
				selectFlatExamples(source.getTaskExamples(), options, plan, "flat-task");
		for(IndexedMaskedDecisionExample example: selectedTaskExamples)
		{
			balancedReplay.addTaskExample(example.example);
		}

		List<IndexedMaskedDecisionExample> selectedVmExamples =
				selectFlatExamples(source.getVmExamples(), options, plan, "flat-vm");
		for(IndexedMaskedDecisionExample example: selectedVmExamples)
		{
			balancedReplay.addVmExample(example.example);
		}

		Map<String, Integer> taskCountsAfter =
				countFlatFamilies(balancedReplay.getTaskExamples(), options.getExpectedFamilies());
		Map<String, Integer> vmCountsAfter =
				countFlatFamilies(balancedReplay.getVmExamples(), options.getExpectedFamilies());
		return new BalanceResult<HierarchicalReplayBuffer>(balancedReplay,
				buildSummary(options, "flat", plan, taskCountsBefore, taskCountsAfter, vmCountsBefore, vmCountsAfter,
						untrackedTaskExamples, untrackedVmExamples));
	}

	public static BalanceResult<ContextualHierarchicalReplayBuffer> balanceContextualReplay(
			ContextualHierarchicalReplayBuffer source, BalanceOptions options)
	{
		if(source == null)
		{
			return new BalanceResult<ContextualHierarchicalReplayBuffer>(null,
					buildUnavailableSummary(options, "contextual", "replay-buffer-unavailable"));
		}

		Map<String, Integer> taskCountsBefore =
				countContextualTaskFamilies(source.getTaskExamples(), options.getExpectedFamilies());
		Map<String, Integer> vmCountsBefore =
				countContextualVmFamilies(source.getVmExamples(), options.getExpectedFamilies());
		int untrackedTaskExamples = countContextualTaskUntracked(source.getTaskExamples());
		int untrackedVmExamples = countContextualVmUntracked(source.getVmExamples());
		BalancePlan plan = buildPlan(options, taskCountsBefore, vmCountsBefore);
		if(!plan.isApplied())
		{
			return new BalanceResult<ContextualHierarchicalReplayBuffer>(source,
					buildSummary(options, "contextual", plan, taskCountsBefore, taskCountsBefore, vmCountsBefore,
							vmCountsBefore, untrackedTaskExamples, untrackedVmExamples));
		}

		ContextualHierarchicalReplayBuffer balancedReplay = new ContextualHierarchicalReplayBuffer();
		List<IndexedTaskDecisionContextExample> selectedTaskExamples =
				selectTaskExamples(source.getTaskExamples(), options, plan, "contextual-task");
		for(IndexedTaskDecisionContextExample example: selectedTaskExamples)
		{
			balancedReplay.addTaskExample(example.example);
		}

		List<IndexedVmDecisionContextExample> selectedVmExamples =
				selectVmExamples(source.getVmExamples(), options, plan, "contextual-vm");
		for(IndexedVmDecisionContextExample example: selectedVmExamples)
		{
			balancedReplay.addVmExample(example.example);
		}

		Map<String, Integer> taskCountsAfter =
				countContextualTaskFamilies(balancedReplay.getTaskExamples(), options.getExpectedFamilies());
		Map<String, Integer> vmCountsAfter =
				countContextualVmFamilies(balancedReplay.getVmExamples(), options.getExpectedFamilies());
		return new BalanceResult<ContextualHierarchicalReplayBuffer>(balancedReplay,
				buildSummary(options, "contextual", plan, taskCountsBefore, taskCountsAfter, vmCountsBefore,
						vmCountsAfter, untrackedTaskExamples, untrackedVmExamples));
	}

	public static Map<String, Object> buildNotApplicableSummary(BalanceOptions options, String reason)
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("enabled", Boolean.valueOf(options.isEnabled()));
		summary.put("applied", Boolean.FALSE);
		summary.put("requestedButNotApplicable", Boolean.TRUE);
		summary.put("mode", MODE_FAMILY_BALANCED);
		summary.put("strategy", options.getStrategy());
		summary.put("seed", Long.valueOf(options.getSeed()));
		summary.put("reason", reason);
		return summary;
	}

	private static Map<String, Object> buildUnavailableSummary(BalanceOptions options, String replayType, String reason)
	{
		Map<String, Object> summary = buildNotApplicableSummary(options, reason);
		summary.put("replayType", replayType);
		summary.put("eligibleFamilies", Collections.<String>emptyList());
		summary.put("missingFamilies", options.getExpectedFamilies());
		summary.put("targetCountPerFamily", null);
		summary.put("taskFamilyCountsBefore", Collections.<String, Integer>emptyMap());
		summary.put("taskFamilyCountsAfter", Collections.<String, Integer>emptyMap());
		summary.put("vmFamilyCountsBefore", Collections.<String, Integer>emptyMap());
		summary.put("vmFamilyCountsAfter", Collections.<String, Integer>emptyMap());
		summary.put("untrackedTaskExampleCount", Integer.valueOf(0));
		summary.put("untrackedVmExampleCount", Integer.valueOf(0));
		return summary;
	}

	private static Map<String, Object> buildSummary(BalanceOptions options, String replayType, BalancePlan plan,
			Map<String, Integer> taskCountsBefore, Map<String, Integer> taskCountsAfter,
			Map<String, Integer> vmCountsBefore, Map<String, Integer> vmCountsAfter,
			int untrackedTaskExamples, int untrackedVmExamples)
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("enabled", Boolean.valueOf(options.isEnabled()));
		summary.put("applied", Boolean.valueOf(plan.isApplied()));
		summary.put("mode", MODE_FAMILY_BALANCED);
		summary.put("strategy", options.getStrategy());
		summary.put("seed", Long.valueOf(options.getSeed()));
		summary.put("replayType", replayType);
		summary.put("quotaRule", QUOTA_RULE);
		summary.put("eligibleFamilies", plan.getEligibleFamilies());
		summary.put("missingFamilies", plan.getMissingFamilies());
		summary.put("targetCountPerFamily", plan.getTargetCountPerFamily());
		summary.put("taskFamilyCountsBefore", taskCountsBefore);
		summary.put("taskFamilyCountsAfter", taskCountsAfter);
		summary.put("vmFamilyCountsBefore", vmCountsBefore);
		summary.put("vmFamilyCountsAfter", vmCountsAfter);
		summary.put("untrackedTaskExampleCount", Integer.valueOf(untrackedTaskExamples));
		summary.put("untrackedVmExampleCount", Integer.valueOf(untrackedVmExamples));
		if(plan.getReason() != null)
		{
			summary.put("reason", plan.getReason());
		}
		return summary;
	}

	private static BalancePlan buildPlan(BalanceOptions options, Map<String, Integer> taskCounts,
			Map<String, Integer> vmCounts)
	{
		List<String> expectedFamilies = resolveFamilyUniverse(options.getExpectedFamilies(), taskCounts, vmCounts);
		List<String> eligibleFamilies = new ArrayList<String>();
		List<String> missingFamilies = new ArrayList<String>();
		int targetCount = Integer.MAX_VALUE;
		for(String familyName: expectedFamilies)
		{
			int taskCount = countForFamily(taskCounts, familyName);
			int vmCount = countForFamily(vmCounts, familyName);
			if(taskCount > 0 && vmCount > 0)
			{
				eligibleFamilies.add(familyName);
				targetCount = Math.min(targetCount, Math.min(taskCount, vmCount));
			}
			else
			{
				missingFamilies.add(familyName);
			}
		}

		if(!options.isEnabled())
		{
			return new BalancePlan(false, null, eligibleFamilies, missingFamilies, Integer.valueOf(0), "disabled");
		}
		if(!STRATEGY_MIN_QUOTA.equals(options.getStrategy()))
		{
			return new BalancePlan(false, null, eligibleFamilies, missingFamilies, Integer.valueOf(0),
					"unsupported-strategy");
		}
		if(eligibleFamilies.size() < 2)
		{
			return new BalancePlan(false, null, eligibleFamilies, missingFamilies, Integer.valueOf(0),
					"insufficient-eligible-families");
		}
		if(targetCount <= 0 || targetCount == Integer.MAX_VALUE)
		{
			return new BalancePlan(false, null, eligibleFamilies, missingFamilies, Integer.valueOf(0),
					"no-positive-family-quota");
		}
		return new BalancePlan(true, Integer.valueOf(targetCount), eligibleFamilies, missingFamilies,
				Integer.valueOf(targetCount), null);
	}

	private static List<String> resolveFamilyUniverse(List<String> expectedFamilies, Map<String, Integer> taskCounts,
			Map<String, Integer> vmCounts)
	{
		if(!expectedFamilies.isEmpty())
		{
			return expectedFamilies;
		}

		Set<String> familyNames = new LinkedHashSet<String>();
		familyNames.addAll(taskCounts.keySet());
		familyNames.addAll(vmCounts.keySet());
		return new ArrayList<String>(familyNames);
	}

	private static int countForFamily(Map<String, Integer> counts, String familyName)
	{
		Integer count = counts.get(familyName);
		return count == null ? 0 : count.intValue();
	}

	private static List<IndexedMaskedDecisionExample> selectFlatExamples(List<MaskedDecisionExample> source,
			BalanceOptions options, BalancePlan plan, String channel)
	{
		Map<String, List<IndexedMaskedDecisionExample>> buckets = bucketFlatExamples(source);
		List<IndexedMaskedDecisionExample> selected = new ArrayList<IndexedMaskedDecisionExample>();
		for(String familyName: plan.getEligibleFamilies())
		{
			List<IndexedMaskedDecisionExample> bucket = buckets.get(familyName);
			if(bucket == null || bucket.size() < plan.getTargetCountPerFamily().intValue())
			{
				continue;
			}
			List<IndexedMaskedDecisionExample> shuffled = new ArrayList<IndexedMaskedDecisionExample>(bucket);
			Collections.shuffle(shuffled, new Random(options.getSeed() ^ familyName.hashCode() ^ channel.hashCode()));
			selected.addAll(shuffled.subList(0, plan.getTargetCountPerFamily().intValue()));
		}
		Collections.sort(selected);
		return selected;
	}

	private static List<IndexedTaskDecisionContextExample> selectTaskExamples(List<TaskDecisionContextExample> source,
			BalanceOptions options, BalancePlan plan, String channel)
	{
		Map<String, List<IndexedTaskDecisionContextExample>> buckets = bucketTaskExamples(source);
		List<IndexedTaskDecisionContextExample> selected = new ArrayList<IndexedTaskDecisionContextExample>();
		for(String familyName: plan.getEligibleFamilies())
		{
			List<IndexedTaskDecisionContextExample> bucket = buckets.get(familyName);
			if(bucket == null || bucket.size() < plan.getTargetCountPerFamily().intValue())
			{
				continue;
			}
			List<IndexedTaskDecisionContextExample> shuffled = new ArrayList<IndexedTaskDecisionContextExample>(bucket);
			Collections.shuffle(shuffled, new Random(options.getSeed() ^ familyName.hashCode() ^ channel.hashCode()));
			selected.addAll(shuffled.subList(0, plan.getTargetCountPerFamily().intValue()));
		}
		Collections.sort(selected);
		return selected;
	}

	private static List<IndexedVmDecisionContextExample> selectVmExamples(List<VmDecisionContextExample> source,
			BalanceOptions options, BalancePlan plan, String channel)
	{
		Map<String, List<IndexedVmDecisionContextExample>> buckets = bucketVmExamples(source);
		List<IndexedVmDecisionContextExample> selected = new ArrayList<IndexedVmDecisionContextExample>();
		for(String familyName: plan.getEligibleFamilies())
		{
			List<IndexedVmDecisionContextExample> bucket = buckets.get(familyName);
			if(bucket == null || bucket.size() < plan.getTargetCountPerFamily().intValue())
			{
				continue;
			}
			List<IndexedVmDecisionContextExample> shuffled = new ArrayList<IndexedVmDecisionContextExample>(bucket);
			Collections.shuffle(shuffled, new Random(options.getSeed() ^ familyName.hashCode() ^ channel.hashCode()));
			selected.addAll(shuffled.subList(0, plan.getTargetCountPerFamily().intValue()));
		}
		Collections.sort(selected);
		return selected;
	}

	private static Map<String, List<IndexedMaskedDecisionExample>> bucketFlatExamples(List<MaskedDecisionExample> source)
	{
		Map<String, List<IndexedMaskedDecisionExample>> buckets =
				new LinkedHashMap<String, List<IndexedMaskedDecisionExample>>();
		for(int index = 0; index < source.size(); index++)
		{
			MaskedDecisionExample example = source.get(index);
			String familyName = familyName(example.getOrigin());
			if(familyName == null)
			{
				continue;
			}
			List<IndexedMaskedDecisionExample> bucket = buckets.get(familyName);
			if(bucket == null)
			{
				bucket = new ArrayList<IndexedMaskedDecisionExample>();
				buckets.put(familyName, bucket);
			}
			bucket.add(new IndexedMaskedDecisionExample(index, example));
		}
		return buckets;
	}

	private static Map<String, List<IndexedTaskDecisionContextExample>> bucketTaskExamples(
			List<TaskDecisionContextExample> source)
	{
		Map<String, List<IndexedTaskDecisionContextExample>> buckets =
				new LinkedHashMap<String, List<IndexedTaskDecisionContextExample>>();
		for(int index = 0; index < source.size(); index++)
		{
			TaskDecisionContextExample example = source.get(index);
			String familyName = familyName(example.getOrigin());
			if(familyName == null)
			{
				continue;
			}
			List<IndexedTaskDecisionContextExample> bucket = buckets.get(familyName);
			if(bucket == null)
			{
				bucket = new ArrayList<IndexedTaskDecisionContextExample>();
				buckets.put(familyName, bucket);
			}
			bucket.add(new IndexedTaskDecisionContextExample(index, example));
		}
		return buckets;
	}

	private static Map<String, List<IndexedVmDecisionContextExample>> bucketVmExamples(
			List<VmDecisionContextExample> source)
	{
		Map<String, List<IndexedVmDecisionContextExample>> buckets =
				new LinkedHashMap<String, List<IndexedVmDecisionContextExample>>();
		for(int index = 0; index < source.size(); index++)
		{
			VmDecisionContextExample example = source.get(index);
			String familyName = familyName(example.getOrigin());
			if(familyName == null)
			{
				continue;
			}
			List<IndexedVmDecisionContextExample> bucket = buckets.get(familyName);
			if(bucket == null)
			{
				bucket = new ArrayList<IndexedVmDecisionContextExample>();
				buckets.put(familyName, bucket);
			}
			bucket.add(new IndexedVmDecisionContextExample(index, example));
		}
		return buckets;
	}

	private static Map<String, Integer> countFlatFamilies(List<MaskedDecisionExample> examples, List<String> expectedFamilies)
	{
		Map<String, Integer> counts = initializeCounts(expectedFamilies);
		for(MaskedDecisionExample example: examples)
		{
			String familyName = familyName(example.getOrigin());
			if(familyName == null)
			{
				continue;
			}
			incrementCount(counts, familyName);
		}
		return counts;
	}

	private static Map<String, Integer> countContextualTaskFamilies(List<TaskDecisionContextExample> examples,
			List<String> expectedFamilies)
	{
		Map<String, Integer> counts = initializeCounts(expectedFamilies);
		for(TaskDecisionContextExample example: examples)
		{
			String familyName = familyName(example.getOrigin());
			if(familyName == null)
			{
				continue;
			}
			incrementCount(counts, familyName);
		}
		return counts;
	}

	private static Map<String, Integer> countContextualVmFamilies(List<VmDecisionContextExample> examples,
			List<String> expectedFamilies)
	{
		Map<String, Integer> counts = initializeCounts(expectedFamilies);
		for(VmDecisionContextExample example: examples)
		{
			String familyName = familyName(example.getOrigin());
			if(familyName == null)
			{
				continue;
			}
			incrementCount(counts, familyName);
		}
		return counts;
	}

	private static int countFlatUntracked(List<MaskedDecisionExample> examples)
	{
		int count = 0;
		for(MaskedDecisionExample example: examples)
		{
			if(familyName(example.getOrigin()) == null)
			{
				count++;
			}
		}
		return count;
	}

	private static int countContextualTaskUntracked(List<TaskDecisionContextExample> examples)
	{
		int count = 0;
		for(TaskDecisionContextExample example: examples)
		{
			if(familyName(example.getOrigin()) == null)
			{
				count++;
			}
		}
		return count;
	}

	private static int countContextualVmUntracked(List<VmDecisionContextExample> examples)
	{
		int count = 0;
		for(VmDecisionContextExample example: examples)
		{
			if(familyName(example.getOrigin()) == null)
			{
				count++;
			}
		}
		return count;
	}

	private static Map<String, Integer> initializeCounts(List<String> expectedFamilies)
	{
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for(String familyName: expectedFamilies)
		{
			counts.put(familyName, Integer.valueOf(0));
		}
		return counts;
	}

	private static void incrementCount(Map<String, Integer> counts, String familyName)
	{
		Integer count = counts.get(familyName);
		counts.put(familyName, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
	}

	private static String familyName(ReplayExampleOrigin origin)
	{
		if(origin == null)
		{
			return null;
		}
		return origin.getBenchmarkFamily();
	}

	public static final class BalanceOptions
	{
		private final boolean enabled;
		private final String strategy;
		private final long seed;
		private final List<String> expectedFamilies;

		public BalanceOptions(boolean enabled, String strategy, long seed, List<String> expectedFamilies)
		{
			this.enabled = enabled;
			this.strategy = strategy;
			this.seed = seed;
			this.expectedFamilies = expectedFamilies == null
					? Collections.<String>emptyList()
					: Collections.unmodifiableList(new ArrayList<String>(expectedFamilies));
		}

		public boolean isEnabled()
		{
			return enabled;
		}

		public String getStrategy()
		{
			return strategy;
		}

		public long getSeed()
		{
			return seed;
		}

		public List<String> getExpectedFamilies()
		{
			return expectedFamilies;
		}
	}

	public static final class BalanceResult<T>
	{
		private final T replayBuffer;
		private final Map<String, Object> summary;

		private BalanceResult(T replayBuffer, Map<String, Object> summary)
		{
			this.replayBuffer = replayBuffer;
			this.summary = summary;
		}

		public T getReplayBuffer()
		{
			return replayBuffer;
		}

		public Map<String, Object> getSummary()
		{
			return summary;
		}
	}

	private static final class BalancePlan
	{
		private final boolean applied;
		private final Integer targetCountPerFamily;
		private final List<String> eligibleFamilies;
		private final List<String> missingFamilies;
		private final Integer minimumPositiveFamilyCount;
		private final String reason;

		private BalancePlan(boolean applied, Integer targetCountPerFamily, List<String> eligibleFamilies,
				List<String> missingFamilies, Integer minimumPositiveFamilyCount, String reason)
		{
			this.applied = applied;
			this.targetCountPerFamily = targetCountPerFamily;
			this.eligibleFamilies = Collections.unmodifiableList(new ArrayList<String>(eligibleFamilies));
			this.missingFamilies = Collections.unmodifiableList(new ArrayList<String>(missingFamilies));
			this.minimumPositiveFamilyCount = minimumPositiveFamilyCount;
			this.reason = reason;
		}

		public boolean isApplied()
		{
			return applied;
		}

		public Integer getTargetCountPerFamily()
		{
			return targetCountPerFamily;
		}

		public List<String> getEligibleFamilies()
		{
			return eligibleFamilies;
		}

		public List<String> getMissingFamilies()
		{
			return missingFamilies;
		}

		public Integer getMinimumPositiveFamilyCount()
		{
			return minimumPositiveFamilyCount;
		}

		public String getReason()
		{
			return reason;
		}
	}

	private static final class IndexedMaskedDecisionExample implements Comparable<IndexedMaskedDecisionExample>
	{
		private final int index;
		private final MaskedDecisionExample example;

		private IndexedMaskedDecisionExample(int index, MaskedDecisionExample example)
		{
			this.index = index;
			this.example = example;
		}

		public int compareTo(IndexedMaskedDecisionExample other)
		{
			return Integer.compare(index, other.index);
		}
	}

	private static final class IndexedTaskDecisionContextExample
			implements Comparable<IndexedTaskDecisionContextExample>
	{
		private final int index;
		private final TaskDecisionContextExample example;

		private IndexedTaskDecisionContextExample(int index, TaskDecisionContextExample example)
		{
			this.index = index;
			this.example = example;
		}

		public int compareTo(IndexedTaskDecisionContextExample other)
		{
			return Integer.compare(index, other.index);
		}
	}

	private static final class IndexedVmDecisionContextExample
			implements Comparable<IndexedVmDecisionContextExample>
	{
		private final int index;
		private final VmDecisionContextExample example;

		private IndexedVmDecisionContextExample(int index, VmDecisionContextExample example)
		{
			this.index = index;
			this.example = example;
		}

		public int compareTo(IndexedVmDecisionContextExample other)
		{
			return Integer.compare(index, other.index);
		}
	}
}
