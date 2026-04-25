package ScheduleAgorithm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import workflow.WTask;
import workflow.Workflow;

public final class PostRunAnalyzer
{
	public Map<String, Object> analyze(List<Workflow> workflows, Path tracePath) throws IOException
	{
		Map<String, Object> analysis = new LinkedHashMap<String, Object>();
		analysis.put("criticalTaskList", buildCriticalTaskList(workflows));
		analysis.put("violationContributors", buildViolationContributors(workflows));
		analysis.put("expensiveVmOccupancy", buildVmTurnoffRanking(tracePath, "totalCost"));
		analysis.put("idleGapWaste", buildVmTurnoffRanking(tracePath, "internalIdleTime"));
		analysis.put("subDeadlineMissChains", buildSubDeadlineMissChains(workflows));
		return analysis;
	}

	private List<Map<String, Object>> buildCriticalTaskList(List<Workflow> workflows)
	{
		List<WTask> candidates = new ArrayList<WTask>();
		for(Workflow workflow: workflows)
		{
			candidates.addAll(workflow.getTaskList());
		}

		Collections.sort(candidates, new Comparator<WTask>()
		{
			public int compare(WTask left, WTask right)
			{
				int leftSlack = left.getSubDeadLine() - left.getRealFinishTime();
				int rightSlack = right.getSubDeadLine() - right.getRealFinishTime();
				if(leftSlack != rightSlack)
				{
					return leftSlack - rightSlack;
				}
				return left.getTaskId().compareTo(right.getTaskId());
			}
		});

		List<Map<String, Object>> criticalTasks = new ArrayList<Map<String, Object>>();
		for(int index = 0; index < candidates.size() && index < 20; index++)
		{
			WTask task = candidates.get(index);
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("taskId", task.getTaskId());
			item.put("workflowId", task.getTaskWorkFlowId());
			item.put("realFinishTime", task.getRealFinishTime());
			item.put("subDeadline", task.getSubDeadLine());
			item.put("slackToSubDeadline", task.getSubDeadLine() - task.getRealFinishTime());
			item.put("pcpNum", task.getPCPNum());
			criticalTasks.add(item);
		}
		return criticalTasks;
	}

	private List<Map<String, Object>> buildViolationContributors(List<Workflow> workflows)
	{
		List<Map<String, Object>> contributors = new ArrayList<Map<String, Object>>();
		for(Workflow workflow: workflows)
		{
			int workflowFinish = 0;
			for(WTask task: workflow.getTaskList())
			{
				if(task.getRealFinishTime() > workflowFinish)
				{
					workflowFinish = task.getRealFinishTime();
				}
			}

			if(workflowFinish <= workflow.getDeadline())
			{
				continue;
			}

			List<Map<String, Object>> lateTasks = new ArrayList<Map<String, Object>>();
			List<WTask> sortedTasks = new ArrayList<WTask>(workflow.getTaskList());
			Collections.sort(sortedTasks, new Comparator<WTask>()
			{
				public int compare(WTask left, WTask right)
				{
					int leftOverrun = (left.getRealFinishTime() - left.getSubDeadLine());
					int rightOverrun = (right.getRealFinishTime() - right.getSubDeadLine());
					if(leftOverrun != rightOverrun)
					{
						return rightOverrun - leftOverrun;
					}
					return left.getTaskId().compareTo(right.getTaskId());
				}
			});

			for(WTask task: sortedTasks)
			{
				if(task.getRealFinishTime() <= task.getSubDeadLine())
				{
					continue;
				}

				Map<String, Object> item = new LinkedHashMap<String, Object>();
				item.put("taskId", task.getTaskId());
				item.put("subDeadline", task.getSubDeadLine());
				item.put("realFinishTime", task.getRealFinishTime());
				item.put("subDeadlineOverrun", task.getRealFinishTime() - task.getSubDeadLine());
				lateTasks.add(item);
			}

			Map<String, Object> contributor = new LinkedHashMap<String, Object>();
			contributor.put("workflowId", workflow.getWorkflowId());
			contributor.put("workflowName", workflow.getWorkflowName());
			contributor.put("workflowDeadline", workflow.getDeadline());
			contributor.put("workflowRealFinishTime", workflowFinish);
			contributor.put("workflowDeadlineOverrun", workflowFinish - workflow.getDeadline());
			contributor.put("lateTasks", lateTasks);
			contributors.add(contributor);
		}
		return contributors;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> buildVmTurnoffRanking(Path tracePath, final String sortKey) throws IOException
	{
		if(tracePath == null || !Files.exists(tracePath))
		{
			return Collections.emptyList();
		}

		List<Map<String, Object>> ranking = new ArrayList<Map<String, Object>>();
		List<String> lines = Files.readAllLines(tracePath, StandardCharsets.UTF_8);
		for(String line: lines)
		{
			if(line.trim().isEmpty())
			{
				continue;
			}

			Object parsed = JsonSupport.parseJson(line);
			if(!(parsed instanceof Map<?, ?>))
			{
				continue;
			}

			Map<String, Object> event = (Map<String, Object>)parsed;
			if(!"vm_turnoff".equals(event.get("eventType")))
			{
				continue;
			}

			List<Object> vmSet = (List<Object>)event.get("vmSet");
			for(Object item: vmSet)
			{
				if(item instanceof Map<?, ?>)
				{
					ranking.add(new LinkedHashMap<String, Object>((Map<String, Object>)item));
				}
			}
		}

		Collections.sort(ranking, new Comparator<Map<String, Object>>()
		{
			public int compare(Map<String, Object> left, Map<String, Object> right)
			{
				double leftValue = ((Number)left.get(sortKey)).doubleValue();
				double rightValue = ((Number)right.get(sortKey)).doubleValue();
				if(leftValue < rightValue)
				{
					return 1;
				}
				if(leftValue > rightValue)
				{
					return -1;
				}
				return ((Number)left.get("vmId")).intValue() - ((Number)right.get("vmId")).intValue();
			}
		});

		if(ranking.size() > 20)
		{
			return new ArrayList<Map<String, Object>>(ranking.subList(0, 20));
		}
		return ranking;
	}

	private List<Map<String, Object>> buildSubDeadlineMissChains(List<Workflow> workflows)
	{
		List<Map<String, Object>> chains = new ArrayList<Map<String, Object>>();
		for(Workflow workflow: workflows)
		{
			List<Map<String, Object>> misses = new ArrayList<Map<String, Object>>();
			for(WTask task: workflow.getTaskList())
			{
				if(task.getRealFinishTime() <= task.getSubDeadLine())
				{
					continue;
				}

				Map<String, Object> miss = new LinkedHashMap<String, Object>();
				miss.put("taskId", task.getTaskId());
				miss.put("realFinishTime", task.getRealFinishTime());
				miss.put("subDeadline", task.getSubDeadLine());
				miss.put("overrun", task.getRealFinishTime() - task.getSubDeadLine());
				misses.add(miss);
			}

			if(misses.isEmpty())
			{
				continue;
			}

			Collections.sort(misses, new Comparator<Map<String, Object>>()
			{
				public int compare(Map<String, Object> left, Map<String, Object> right)
				{
					return ((Number)left.get("realFinishTime")).intValue()
							- ((Number)right.get("realFinishTime")).intValue();
				}
			});

			Map<String, Object> chain = new LinkedHashMap<String, Object>();
			chain.put("workflowId", workflow.getWorkflowId());
			chain.put("workflowName", workflow.getWorkflowName());
			chain.put("misses", misses);
			chains.add(chain);
		}
		return chains;
	}
}
