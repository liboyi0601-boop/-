package workflow;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorkflowDatasetIO
{
	private WorkflowDatasetIO()
	{
	}

	public static List<Workflow> readWorkflows(Path path) throws IOException, ClassNotFoundException
	{
		List<Workflow> workflows = new ArrayList<Workflow>();

		try(ObjectInputStream input = new ObjectInputStream(new FileInputStream(path.toFile())))
		{
			while(true)
			{
				try
				{
					workflows.add((Workflow)input.readObject());
				}
				catch(EOFException endOfFile)
				{
					break;
				}
			}
		}

		return workflows;
	}

	public static void writeWorkflows(Path path, List<Workflow> workflows) throws IOException
	{
		Path parent = path.getParent();
		if(parent != null)
		{
			Files.createDirectories(parent);
		}

		try(ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(path.toFile())))
		{
			for(Workflow workflow: workflows)
			{
				output.writeObject(workflow);
			}
		}
	}
}
