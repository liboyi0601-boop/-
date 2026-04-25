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

public final class TempWorkflowDatasetIO
{
	private TempWorkflowDatasetIO()
	{
	}

	public static List<TempWorkflow> readWorkflows(Path path) throws IOException, ClassNotFoundException
	{
		List<TempWorkflow> workflows = new ArrayList<TempWorkflow>();

		try(ObjectInputStream input = new ObjectInputStream(new FileInputStream(path.toFile())))
		{
			while(true)
			{
				try
				{
					workflows.add((TempWorkflow)input.readObject());
				}
				catch(EOFException endOfFile)
				{
					break;
				}
			}
		}

		return workflows;
	}

	public static void writeWorkflows(Path path, List<TempWorkflow> workflows) throws IOException
	{
		Path parent = path.getParent();
		if(parent != null)
		{
			Files.createDirectories(parent);
		}

		try(ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(path.toFile())))
		{
			for(TempWorkflow workflow: workflows)
			{
				output.writeObject(workflow);
			}
		}
	}
}
