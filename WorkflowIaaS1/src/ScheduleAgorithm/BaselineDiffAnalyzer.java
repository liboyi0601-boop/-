package ScheduleAgorithm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BaselineDiffAnalyzer
{
	@SuppressWarnings("unchecked")
	public Map<String, Object> analyze(Path leftRunDir, Path rightRunDir) throws IOException
	{
		Map<String, Object> analysis = new LinkedHashMap<String, Object>();

		Map<String, Object> leftManifest = (Map<String, Object>)JsonSupport.parseJson(
				JsonSupport.readString(leftRunDir.resolve("manifest.json")));
		Map<String, Object> rightManifest = (Map<String, Object>)JsonSupport.parseJson(
				JsonSupport.readString(rightRunDir.resolve("manifest.json")));

		Map<String, Object> manifestComparison = new LinkedHashMap<String, Object>();
		manifestComparison.put("workloadFingerprintMatch",
				safeEquals(leftManifest.get("workloadFingerprint"), rightManifest.get("workloadFingerprint")));
		manifestComparison.put("configHashMatch",
				safeEquals(leftManifest.get("configHash"), rightManifest.get("configHash")));
		manifestComparison.put("leftWorkloadFingerprint", leftManifest.get("workloadFingerprint"));
		manifestComparison.put("rightWorkloadFingerprint", rightManifest.get("workloadFingerprint"));
		manifestComparison.put("leftConfigHash", leftManifest.get("configHash"));
		manifestComparison.put("rightConfigHash", rightManifest.get("configHash"));
		analysis.put("manifestComparison", manifestComparison);

		Path leftTrace = leftRunDir.resolve("trace.jsonl");
		Path rightTrace = rightRunDir.resolve("trace.jsonl");
		List<String> leftLines = Files.exists(leftTrace)
				? Files.readAllLines(leftTrace, StandardCharsets.UTF_8) : new ArrayList<String>();
		List<String> rightLines = Files.exists(rightTrace)
				? Files.readAllLines(rightTrace, StandardCharsets.UTF_8) : new ArrayList<String>();

		int minSize = Math.min(leftLines.size(), rightLines.size());
		for(int index = 0; index < minSize; index++)
		{
			if(!leftLines.get(index).equals(rightLines.get(index)))
			{
				appendDifference(analysis, index, leftLines.get(index), rightLines.get(index));
				return analysis;
			}
		}

		if(leftLines.size() != rightLines.size())
		{
			appendDifference(analysis, minSize,
					minSize < leftLines.size() ? leftLines.get(minSize) : null,
					minSize < rightLines.size() ? rightLines.get(minSize) : null);
			return analysis;
		}

		analysis.put("status", "no_divergence");
		analysis.put("firstDifferentEventIndex", -1);
		analysis.put("eventType", null);
		analysis.put("differentFields", new ArrayList<String>());
		analysis.put("leftSummary", null);
		analysis.put("rightSummary", null);
		return analysis;
	}

	@SuppressWarnings("unchecked")
	private void appendDifference(Map<String, Object> analysis, int index, String leftLine, String rightLine)
	{
		Map<String, Object> leftEvent = leftLine == null ? null
				: (Map<String, Object>)JsonSupport.parseJson(leftLine);
		Map<String, Object> rightEvent = rightLine == null ? null
				: (Map<String, Object>)JsonSupport.parseJson(rightLine);

		List<String> differentFields = new ArrayList<String>();
		collectDifferences("", leftEvent, rightEvent, differentFields);

		analysis.put("status", "diverged");
		analysis.put("firstDifferentEventIndex", index);
		analysis.put("eventType", extractEventType(leftEvent, rightEvent));
		analysis.put("differentFields", differentFields);
		analysis.put("leftSummary", summarizeEvent(leftEvent));
		analysis.put("rightSummary", summarizeEvent(rightEvent));
	}

	@SuppressWarnings("unchecked")
	private void collectDifferences(String prefix, Object left, Object right, List<String> differences)
	{
		if(left == null || right == null)
		{
			if(!safeEquals(left, right))
			{
				differences.add(prefix);
			}
			return;
		}

		if(left instanceof Map<?, ?> && right instanceof Map<?, ?>)
		{
			Map<String, Object> leftMap = (Map<String, Object>)left;
			Map<String, Object> rightMap = (Map<String, Object>)right;
			for(String key: leftMap.keySet())
			{
				String childPrefix = prefix.length() == 0 ? key : prefix + "." + key;
				if(!rightMap.containsKey(key))
				{
					differences.add(childPrefix);
					continue;
				}
				collectDifferences(childPrefix, leftMap.get(key), rightMap.get(key), differences);
			}
			for(String key: rightMap.keySet())
			{
				if(!leftMap.containsKey(key))
				{
					String childPrefix = prefix.length() == 0 ? key : prefix + "." + key;
					differences.add(childPrefix);
				}
			}
			return;
		}

		if(left instanceof List<?> && right instanceof List<?>)
		{
			List<Object> leftList = (List<Object>)left;
			List<Object> rightList = (List<Object>)right;
			int minSize = Math.min(leftList.size(), rightList.size());
			for(int index = 0; index < minSize; index++)
			{
				String childPrefix = prefix + "[" + index + "]";
				collectDifferences(childPrefix, leftList.get(index), rightList.get(index), differences);
			}
			if(leftList.size() != rightList.size())
			{
				differences.add(prefix + ".size");
			}
			return;
		}

		if(!safeEquals(left, right))
		{
			differences.add(prefix);
		}
	}

	private String extractEventType(Map<String, Object> leftEvent, Map<String, Object> rightEvent)
	{
		if(leftEvent != null && leftEvent.get("eventType") != null)
		{
			return String.valueOf(leftEvent.get("eventType"));
		}
		if(rightEvent != null && rightEvent.get("eventType") != null)
		{
			return String.valueOf(rightEvent.get("eventType"));
		}
		return null;
	}

	private Map<String, Object> summarizeEvent(Map<String, Object> event)
	{
		if(event == null)
		{
			return null;
		}

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("eventType", event.get("eventType"));
		summary.put("currentTime", event.get("currentTime"));
		summary.put("sequence", event.get("sequence"));
		return summary;
	}

	private boolean safeEquals(Object left, Object right)
	{
		if(left == null)
		{
			return right == null;
		}
		return left.equals(right);
	}

	public static void main(String[] args) throws Exception
	{
		if(args.length != 2)
		{
			throw new IllegalArgumentException(
					"Usage: java ScheduleAgorithm.BaselineDiffAnalyzer <left-run-dir> <right-run-dir>");
		}

		BaselineDiffAnalyzer analyzer = new BaselineDiffAnalyzer();
		Map<String, Object> result = analyzer.analyze(Paths.get(args[0]), Paths.get(args[1]));
		System.out.println(JsonSupport.toJson(result));
	}
}
