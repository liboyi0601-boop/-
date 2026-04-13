package workflow;

import java.io.Serializable;

/**
 * @author ljg 修改  2017.02.10 ChangSha
 * Constraint类描述由任务ID代码的关联约束
 * */

public class Constraint implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private final String taskId; //连接任务的Id，即可是前驱任务，也可以是后继任务
	private final int dataSize;  //数据传输量(MB)
	
	/**
	 * @param taskId: 任务ID
	 * @param dataSize: 数据传输量
	 * */	
	public Constraint(String taskId, int dataSize)
	{
		this.taskId = taskId;
		this.dataSize = dataSize;
	}
	
	public String getTaskId(){return taskId;}	
	public int getDataSize(){return dataSize;}
}