package workflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public final class BenchmarkTemplateBuilder
{
	private static final Path TEMPLATE_OUTPUT_DIR = Paths.get("workloads/benchmarks/templates");

	private BenchmarkTemplateBuilder()
	{
	}

	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		if("all".equals(options.familyName))
		{
			for(BenchmarkFamily family: BenchmarkFamily.values())
			{
				writeFamilyTemplateSet(family, options.overwrite);
			}
			writeMixedTemplateSet(options.overwrite);
			return;
		}

		if("mixed".equals(options.familyName))
		{
			writeMixedTemplateSet(options.overwrite);
			return;
		}

		writeFamilyTemplateSet(BenchmarkFamily.fromName(options.familyName), options.overwrite);
	}

	private static void writeFamilyTemplateSet(BenchmarkFamily family, boolean overwrite)
			throws IOException, DocumentException
	{
		Path outputPath = familyTemplatePath(family);
		ensureWritable(outputPath, overwrite);
		List<TempWorkflow> templates = readFamilyTemplates(family);
		TempWorkflowDatasetIO.writeWorkflows(outputPath, templates);
		System.out.println("Benchmark template set generated: " + outputPath.toString());
	}

	private static void writeMixedTemplateSet(boolean overwrite) throws IOException, DocumentException
	{
		Path outputPath = mixedTemplatePath();
		ensureWritable(outputPath, overwrite);

		List<List<TempWorkflow>> templatesByFamily = new ArrayList<List<TempWorkflow>>();
		for(BenchmarkFamily family: BenchmarkFamily.values())
		{
			templatesByFamily.add(readFamilyTemplates(family));
		}

		List<TempWorkflow> mixedTemplates = interleaveRoundRobin(templatesByFamily);
		TempWorkflowDatasetIO.writeWorkflows(outputPath, mixedTemplates);
		System.out.println("Benchmark mixed template set generated: " + outputPath.toString());
	}

	private static List<TempWorkflow> readFamilyTemplates(BenchmarkFamily family) throws DocumentException, IOException
	{
		List<TempWorkflow> templates = new ArrayList<TempWorkflow>();
		for(Path sourceXmlPath: family.getSourceXmlPaths())
		{
			ensureSourceXmlExists(sourceXmlPath);
			templates.add(readWorkflowTemplate(sourceXmlPath));
		}
		return templates;
	}

	private static TempWorkflow readWorkflowTemplate(Path xmlPath) throws DocumentException
	{
		SAXReader reader = new SAXReader();
		Document document = reader.read(xmlPath.toFile());
		Element root = document.getRootElement();
		List<Element> taskElements = root.elements("job");

		List<TempWTask> tempTaskList = new ArrayList<TempWTask>();
		ResourceUti resourceUtilizationByTaskType = new ResourceUti();

		for(Element taskElement: taskElements)
		{
			String taskId = taskElement.attributeValue("id");
			double runTime = Double.parseDouble(taskElement.attributeValue("runtime"));
			String taskType = taskElement.attributeValue("name");
			double utilization = resourceUtilizationByTaskType.getResourceUtilization(taskType);
			tempTaskList.add(new TempWTask(taskId, -1, runTime, utilization));
		}

		List<Element> childElements = root.elements("child");
		for(Element childElement: childElements)
		{
			String childTaskId = childElement.attributeValue("ref");
			List<String> inputFiles = collectInputFiles(taskElements, childTaskId);

			for(Iterator<Element> iterator = childElement.elementIterator(); iterator.hasNext();)
			{
				Element parentElement = iterator.next();
				String parentTaskId = parentElement.attributeValue("ref");
				int dataSize = resolveDataSize(taskElements, parentTaskId, inputFiles);
				if(dataSize == 0)
				{
					throw new IllegalArgumentException("Data size is zero for " + xmlPath.getFileName().toString());
				}

				Constraint parentConstraint = new Constraint(parentTaskId, dataSize);
				for(TempWTask childTask: tempTaskList)
				{
					if(childTask.getTaskId().equals(childTaskId))
					{
						childTask.getParentTaskList().add(parentConstraint);
						break;
					}
				}
			}
		}

		for(TempWTask childTask: tempTaskList)
		{
			for(Constraint parentConstraint: childTask.getParentTaskList())
			{
				for(TempWTask parentTask: tempTaskList)
				{
					if(parentTask.getTaskId().equals(parentConstraint.getTaskId()))
					{
						parentTask.getSuccessorTaskList().add(
								new Constraint(childTask.getTaskId(), parentConstraint.getDataSize()));
						break;
					}
				}
			}
		}

		TempWorkflow workflow = new TempWorkflow(xmlPath.getFileName().toString());
		workflow.setTaskList(tempTaskList);
		return workflow;
	}

	private static List<String> collectInputFiles(List<Element> taskElements, String taskId)
	{
		List<String> inputFiles = new ArrayList<String>();
		for(Element taskElement: taskElements)
		{
			if(!taskId.equals(taskElement.attributeValue("id")))
			{
				continue;
			}

			for(Iterator<Element> iterator = taskElement.elementIterator(); iterator.hasNext();)
			{
				Element dataElement = iterator.next();
				if("input".equals(dataElement.attributeValue("link")))
				{
					inputFiles.add(dataElement.attributeValue("file"));
				}
			}
			break;
		}
		return inputFiles;
	}

	private static int resolveDataSize(List<Element> taskElements, String parentTaskId, List<String> inputFiles)
	{
		int dataSize = 0;
		for(Element taskElement: taskElements)
		{
			if(!parentTaskId.equals(taskElement.attributeValue("id")))
			{
				continue;
			}

			for(Iterator<Element> iterator = taskElement.elementIterator(); iterator.hasNext();)
			{
				Element dataElement = iterator.next();
				if(!"output".equals(dataElement.attributeValue("link")))
				{
					continue;
				}

				String outputFile = dataElement.attributeValue("file");
				for(String inputFile: inputFiles)
				{
					if(inputFile.equals(outputFile))
					{
						dataSize += (int)Double.parseDouble(dataElement.attributeValue("size"));
					}
				}
			}
			break;
		}
		return dataSize;
	}

	private static List<TempWorkflow> interleaveRoundRobin(List<List<TempWorkflow>> templatesByFamily)
	{
		List<TempWorkflow> combined = new ArrayList<TempWorkflow>();
		int maxSize = 0;
		for(List<TempWorkflow> familyTemplates: templatesByFamily)
		{
			if(familyTemplates.size() > maxSize)
			{
				maxSize = familyTemplates.size();
			}
		}

		for(int index = 0; index < maxSize; index++)
		{
			for(List<TempWorkflow> familyTemplates: templatesByFamily)
			{
				if(index < familyTemplates.size())
				{
					combined.add(familyTemplates.get(index));
				}
			}
		}
		return combined;
	}

	private static Path familyTemplatePath(BenchmarkFamily family)
	{
		return TEMPLATE_OUTPUT_DIR.resolve(family.getFamilyName() + "-template-set.dat");
	}

	private static Path mixedTemplatePath()
	{
		return TEMPLATE_OUTPUT_DIR.resolve("bench-mixed-template-set.dat");
	}

	private static void ensureWritable(Path outputPath, boolean overwrite) throws IOException
	{
		if(Files.exists(outputPath) && !overwrite)
		{
			throw new IllegalStateException("Refusing to overwrite existing benchmark template set: "
					+ outputPath.toString() + ". Pass --overwrite to replace it.");
		}
		Files.createDirectories(outputPath.getParent());
	}

	private static void ensureSourceXmlExists(Path xmlPath)
	{
		if(!Files.exists(xmlPath))
		{
			throw new IllegalStateException("Benchmark source XML is missing: " + xmlPath.toString()
					+ ". Place the file under XML Example/ before generating benchmark templates.");
		}
	}

	private static final class RunnerOptions
	{
		private final String familyName;
		private final boolean overwrite;

		private RunnerOptions(String familyName, boolean overwrite)
		{
			this.familyName = familyName;
			this.overwrite = overwrite;
		}

		private static RunnerOptions parse(String[] args)
		{
			String familyName = null;
			boolean overwrite = false;

			for(int index = 0; index < args.length; index++)
			{
				String arg = args[index];
				if("--family".equals(arg))
				{
					index++;
					familyName = args[index].toLowerCase();
				}
				else if("--overwrite".equals(arg))
				{
					overwrite = true;
				}
				else
				{
					throw new IllegalArgumentException("Unknown argument: " + arg);
				}
			}

			if(familyName == null)
			{
				throw new IllegalArgumentException("Missing required argument: --family");
			}

			return new RunnerOptions(familyName, overwrite);
		}
	}
}
