package workflow;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import share.StaticfinalTags;
import share.StochasticDistribution;

/**
 * @author ljg修改补充  2017.2.12
 * WorkflowProducer类实现如下功能：
 * 1、设置实验工作流的数目
 * 2、从工作流模板产生泊松分布的工作流到达集合
 * 3、给每个工作流集合中的每个工作流设置正态分布的基准执行时间
 * 4、将工作流集合存入文件
 * */


public class WorkflowProducer {
	private static List<Workflow> workflowList; //工作流集合
	
	public static void main(String[] args) throws ClassNotFoundException, IOException 
	{
		workflowList = new ArrayList<Workflow>(); //初始化工作流列表
		int workflowNum = StaticfinalTags.workflowNum; //设置工作流数目为静态标签数目的值
		
		/**从工作流模板产生泊松分布的工作流到达集合*/
		workflowList = produceWorkflow(workflowNum); //产生工作流测试集		
		//System.out.println("Num: "+workflowList.size());
		
		//给任务设正态分布的基准执行时间
		calculateRealTaskBaseExecutionTime(workflowList); //给所有工作流中每个任务的赋值随机参数
		
		System.out.println("产生"+workflowNum+"个工作流集合！");
		
		//将制作完成的实验工作流写入文件中,以供对比试验使用
		FileOutputStream fos = new FileOutputStream("ExperimentalWorkflow.dat"); 
		ObjectOutputStream os = new ObjectOutputStream(fos);
		try
		{
			for(int i=0; i<workflowList.size(); i++)
			{
//				System.out.println("WriteNum: "+i);
				os.writeObject(workflowList.get(i));
			}
			os.close();
		}catch(IOException e){System.out.println(e.getMessage());}
		workflowList.clear();
		System.out.println("End Write!");

	}// end:  main(String[] args)

		
/*=====================================================================================================================*/

	/**
	 * 该方法完成以下功能：
	 * 1、从给定的工作流模板中分别读出各个模板的工作流参数
	 * 2、计算每个模板工作流的makespan
	 * 3、使用泊松分布来制作同时到达的工作流列表
	 * 4、返回制作好的工作流列表 
	 * */
	private static List<Workflow> produceWorkflow(int workflowNum) throws IOException, ClassNotFoundException
	{
		//System.out.println("Start produce workflow!");
		//制作样本工作流
		List<Workflow> templateWList = new ArrayList<Workflow>(); //用来存放从模板中读出工作流列表
		FileInputStream fi = new FileInputStream("WorkflowTemplateSet.dat");
		ObjectInputStream si = new ObjectInputStream(fi);
		try
		{
			
			for(int i=0; i<StaticfinalTags.workflowTemplateNum; i++)//总共n个工作流模板
			{
				
				TempWorkflow readWorkflow = (TempWorkflow)si.readObject(); //从文件中读入工作流模板，一次读一个工作流
				List<WTask> taskList = new ArrayList<WTask>(); //工作流的任务集合，存放从模板中复制出的工作流中的任务
				List<TempWTask> tempWTaskList = readWorkflow.getTaskList();//模板工作流中的读出的任务集合
				//System.out.println(readWorkflow.getWorkflowName());
				
				/**遍历工作流readWorkflow中的每个任务，并复制出来*/
				for(TempWTask task: tempWTaskList)
				{
					String taskId = task.getTaskId(); //从模板中读出的任务ID
					int workflowId = -1; //初始化任务所在工作流的ID
					
					int baseExecutionTime =  (int)(task.getTaskRunTime()); //任务的基准执行时间，等于工作流模板中的任务执行时间
					if(baseExecutionTime < 1)
					{
						baseExecutionTime = 1;
//						System.out.println("task exeuction time is less than one second");
					}
					
					//设置父任务集合
					List<Constraint> parentConstraintList = new ArrayList<Constraint>();//设置受约束的父任务，用任务ID记录
					for(Constraint pc: task.getParentTaskList())//遍历一个任务的父节点约束
					{
						String parentTaskId = pc.getTaskId();//获得一个任务父节点的ID
						int dataSize = pc.getDataSize();//获得数据传输量
						//用模板中读出的父节点ID和数据传输量设置复制的父节点约束类
						Constraint tempParentConstraint = new Constraint(parentTaskId, dataSize);
						parentConstraintList.add(tempParentConstraint);//把生成的父节点约束加入父节点列表末尾
					}
					
					//子任务集合
					List<Constraint> successorConstraintList = new ArrayList<Constraint>();//设置受约束的子任务，用任务ID记录
					for(Constraint cc: task.getSuccessorTaskList())//遍历一个任务的子节点约束
					{
						String parentTaskId = cc.getTaskId();
						int dataSize = cc.getDataSize();
						Constraint tempParentConstraint = new Constraint(parentTaskId, dataSize);
						successorConstraintList.add(tempParentConstraint);//把生成的子节点约束加入子节点列表末尾
					}
					
					//新建的任务配置初始参数，taskID和baseExecutionTime从模板读出，workflowId设成初始的-1
					WTask wTask = new WTask(taskId, workflowId, baseExecutionTime);
					wTask.getParentIDList().addAll(parentConstraintList);//加入该任务的所有父节点
					wTask.getSuccessorIDList().addAll(successorConstraintList);//加入该任务的所有子节点
					
					taskList.add(wTask); //把新复制得到的任务加入列表
				}/*end： for(TempWTask task: tempWTaskList)	
				      模板中一个特定的工作流的所有任务都已放入taskList*/
				
				String name = readWorkflow.getWorkflowName();//读出模板中工作流名称
				Workflow workflow = new Workflow(-1, name, -1, -1, -1);//给生成的工作流实例赋对应的名字和初值
				workflow.setTaskList(taskList);//把复制的任务加入到工作流任务列表
				templateWList.add(workflow);
				
				/*System.out.println("WorkflowId: "+workflow.getWorkflowId()+
						" | workflowName:"+workflow.getWorkflowName()+
						" | arrival:"+workflow.getArrivalTime()+
						" | MakeSpan:"+workflow.getMakespan()+
						" | deadline:"+workflow.getDeadline());*/
				
//				System.out.println(name+"数据读取配置完毕");
				
			}/*end: for(int i=0; i<15; i++) 循环结束后将读完15个工作流模板*/
			si.close();
		}catch(IOException e)
		{
			System.out.println(e.getMessage());
		}
		
		if(templateWList.size() != StaticfinalTags.workflowTemplateNum)
		{
			
			System.out.println("Error: the template workflow is not right");
		}
		
/*		int f=7;
		System.out.println(templateWList.get(f).getWorkflowName());
		int makespan = CalculateMakespan(templateWList.get(f));
		templateWList.get(f).setMakespan(makespan);
*/		
		//根据最初的基本执行时间计算每个模板工作流的makespan
		for(Workflow workflowMakespan: templateWList)
		{
			int makespan = CalculateMakespan(workflowMakespan);
			workflowMakespan.setMakespan(makespan);
		}
		
		//生成一个工作流列表，用来存放拷贝的工作流
		List<Workflow> wList = new ArrayList<Workflow>();
		int workflowId = 0;
		int arrivalTime = 0;
		
		int RotationID =0; //用来记录循环产生整个工作流的模板
		
		/*配置n个工作流，按到达时间从小到达排列存在列表中*/
		while(wList.size() < workflowNum) // 工作流数目是workflowNum
		{//根据泊松分布求出随机的工作流同时到达的个数
			int temNum = PoissValue(StaticfinalTags.arrivalLamda); //某时刻到达的工作流数量
			if(temNum == 0)//如果到达工作流数量为0
			{
				arrivalTime++; //到下一个时刻
				//System.out.println("到达的工作流数："+temNum+" | 到达时间："+arrivalTime);
				continue;
			}
			else
			{
				if(temNum > 1)
					System.out.println("下面"+temNum+"个流同时到达！");
				
				/*制作同时到达的工作流的副本拷贝,同时到达的工作流在列表中相邻存放*/
				for(int i=0; i<temNum; i++)//
				{
//					System.out.println("Workflow Num: "+wList.size());
					
					int templateNum; //记录选择的工作流模板
					StaticfinalTags.workflowSelectOption SelectStyle = StaticfinalTags.OperationStyle;
					switch(SelectStyle)
					{
						case Special:
							templateNum = StaticfinalTags.selectedNum;
							break;
						case Random:
							templateNum = (int)(Math.random()*StaticfinalTags.workflowTemplateNum);
							break;
						default:
							templateNum = RotationID%StaticfinalTags.workflowTemplateNum;
							
					}
/*					
					if(specialWorkflow)
					{
						templateNum = StaticfinalTags.selectedNum;//指定模板中的哪个工作流
					}
					else
					{
						templateNum = (int)(Math.random()*15);//从1-15中随机选一个工作流模板
					}
*/					
					Workflow findWorkflow = templateWList.get(templateNum);//返回指定位置的工作流
					RotationID++;
					
					//找出该工作流模板的参数,根据参数创建动态到达的工作流
					List<WTask> tempTaskList = new ArrayList<WTask>();//把存放工作流的任务的副本
					
					/**遍历指定工作流中的任务，为任务找出相应的父约束和子约束*/
					for(WTask task: findWorkflow.getTaskList())
					{
						//用遍历中当前任务的id，工作流id和基准执行时间生成任务拷贝的实例
						WTask copyTask = new WTask(task.getTaskId(), workflowId, task.getBaseExecutionTime());
						copyTask.setBaseStartTime(task.getBaseStartTime());
						copyTask.setBaseFinishTime(task.getBaseFinishTime());
						
						//将父任务ID进行关联,将父任务的ID关联约束找出来
						List<Constraint> parentConstraintList = new ArrayList<Constraint>();
						//对找到的工作流中的一个任务，遍历它的父节点ID约束
						for(Constraint pc: task.getParentIDList())
						{
							String parentTaskId = pc.getTaskId();//获得任务的id
							int dataSize = pc.getDataSize();//获得数据量
							Constraint tempParentConstraint = new Constraint(parentTaskId, dataSize);
							parentConstraintList.add(tempParentConstraint);//把生成的约束加入父约束列表
						}
						
						//将子任务ID进行关联，将子任务的ID关联约束找出来
						List<Constraint> successorConstraintList = new ArrayList<Constraint>();
						//对找到的工作流中的一个任务，遍历它的子节点ID约束
						for(Constraint cc: task.getSuccessorIDList())
						{
							String parentTaskId = cc.getTaskId();
							int dataSize = cc.getDataSize();
							Constraint tempSuccessorConstraint = new Constraint(parentTaskId, dataSize);
							successorConstraintList.add(tempSuccessorConstraint);
						}
						
						//给任务的拷贝实例增加父节点约束和子节点约束
						copyTask.getParentIDList().addAll(parentConstraintList);
						copyTask.getSuccessorIDList().addAll(successorConstraintList);
						
						tempTaskList.add(copyTask);//柳佳刚——把拷贝任务加入临时任务读取列表
						
					}/*end: for(WTask task: findWorkflow.getTaskList())*/
					/* 任务副本的相关父约束和子约束添加完毕
					 * */
					
					/**将任务进行关联,通过任务的ID关联约束找出任务实例的关联约束*/
					for(WTask connectedTask: tempTaskList)//遍历每个任务
					{
						for(Constraint parentCon: connectedTask.getParentIDList())//遍历每个任务的父节点
						{//关联任务connectedTask的所有父任务
							String parentID = parentCon.getTaskId();
							int dataSize = parentCon.getDataSize();
							for(WTask parentTask: tempTaskList)
							{
								if(parentID.equals(parentTask.getTaskId()))
								{//找出任务connectedTask的父任务
									ConstraintWTask parent = new ConstraintWTask(parentTask, dataSize);
									connectedTask.getParentTaskList().add(parent);
									break;
								}
							}
						}//关联任务connectedTask父任务结束
						
						for(Constraint successorCon: connectedTask.getSuccessorIDList())
						{//关联任务connectedTask的所有子任务
							String successorID = successorCon.getTaskId();
							int dataSize = successorCon.getDataSize();
							for(WTask successorTask: tempTaskList)
							{
								if(successorID.equals(successorTask.getTaskId()))
								{//找出任务connectedTask的子任务
									ConstraintWTask successor = new ConstraintWTask(successorTask, dataSize);
									connectedTask.getSuccessorTaskList().add(successor);								
									break;
								}
							}
						}//关联任务connectedTask子任务结束
					}//关联任务结束
					
					String name = findWorkflow.getWorkflowName();					
					int deadline = (int)(arrivalTime + findWorkflow.getMakespan()*StaticfinalTags.deadlineBase);
					
					//使用从工作流模板中得到的参数，加计算的arrivalTime、Makespan和deadline一起设置新产生的工作流
					Workflow newWorkflow = new Workflow(workflowId, name, arrivalTime, findWorkflow.getMakespan(), deadline);
					newWorkflow.setTaskList(tempTaskList);
					
					wList.add(newWorkflow); //把新生成的具有动态到达特性的工作流加入到拷贝列表
					
					System.out.println("WorkflowId: "+newWorkflow.getWorkflowId()+
										" | workflowName:"+newWorkflow.getWorkflowName()+
										" | arrival:"+newWorkflow.getArrivalTime()+
										" | MakeSpan:"+newWorkflow.getMakespan()+
										" | deadline:"+newWorkflow.getDeadline());
					
					workflowId++; //递增工作流的Id
					
				}/*end: for(int i=0; i<temNum; i++)*/
				
				arrivalTime++; //更新下一个工作流到达时刻
			}/*end:  if(temNum == 0)...else...结束*/
			
		}/*end: while(wList.size() < workflowNum)*/
		
/*			//这段代码用作测试读取对象使用
		int f=0;
		Workflow wf = wList.get(f);
		System.out.println(wf.getWorkflowName());
		for(WTask task: wf.getTaskList())
		{
			String taskId = task.getTaskId(); //从模板中读出的任务ID
			System.out.println("输出"+taskId+":");
			System.out.println("基准执行时间"+task.getBaseExecutionTime()+" | "+
								"真实基准执行时间"+task.getRealBaseExecutionTime()+" | "+
								"近似基准执行时间"+task.getExecutionTimeWithConfidency());
				
			List<Constraint> tempParentList = new ArrayList<Constraint>();
			List<Constraint> tempChildList = new ArrayList<Constraint>();
			tempParentList = task.getParentIDList();
			tempChildList = task.getSuccessorIDList();
			
			System.out.println("输出"+taskId+"的父任务约束：");
			for(Constraint tp: tempParentList)
			{
				System.out.println(tp.getTaskId()+" "+tp.getDataSize());
			}
			System.out.println("输出"+taskId+"的子任务约束：");
			for(Constraint tc: tempChildList)
			{
				System.out.println(tc.getTaskId()+" "+tc.getDataSize());
			}
			
		}
*/		
		return wList;
	}/* end:  produceWorkflow(int workflowNum)*/
	//产生工作流测试集结束
	
	/*===============================================================================================================*/
	
	/**计算从模板中读出的工作流的makespan*/
	public static int CalculateMakespan(Workflow cWorkflow)
	{
		
		List<WTask> calculatedTaskList = new ArrayList<WTask>(); //已经计算过的任务
		while(true)
		{
			for(WTask task: cWorkflow.getTaskList())
			{//对所有任务进行遍历一次
				if(task.getBaseFinishTime() > -1) 
				{//如果任务已经计算过，则跳过
					continue;
				}
				
				int executionTime = task.getBaseExecutionTime(); //任务的执行时间
				if(task.getParentIDList().size() == 0)//计算开始任务的开始时间和结束时间
				{//没有前驱的任务
					task.setBaseStartTime(0);
					task.setBaseFinishTime(executionTime);
					calculatedTaskList.add(task);
				}
				else 
				{//非开始任务,也就是有父任务的任务	
					int maxStartTime = -1; //最晚开始时间
					boolean unCalculatedParent = false; //是否存在未计算的父任务
					
					for(Constraint con: task.getParentIDList())
					{//判断是否每个父任务都计算过，如果是，那么找出任务task的最早开始时间
						unCalculatedParent = true;
						
						String parentId = con.getTaskId(); //父任务的Id
						int dataSize = con.getDataSize();  //读取数据的大小
						
						for(WTask joinTask: calculatedTaskList)
						{
							if(parentId.equals(joinTask.getTaskId()))
							{
								unCalculatedParent = false;
								int startTime = joinTask.getBaseFinishTime();
				
								if(startTime > maxStartTime)
								{
									maxStartTime = startTime;
								}
								break;
							}
						}
						
						if(unCalculatedParent == true)
						{
							break;
						}	
						
					}
					
					if(unCalculatedParent == false)
					{//如果该任务所有父任务都完成了，那么计算它的开始和结束时间
						task.setBaseStartTime(maxStartTime);				
						task.setBaseFinishTime(maxStartTime + executionTime);
						calculatedTaskList.add(task);
					}										
				}//end else								
			}//end for(Task task: cWorkflow.getTaskList()) 
			
			if(calculatedTaskList.size() == cWorkflow.getTaskList().size())
			{//计算完的条件
				break;
			}
		}//end while

/*		//这段代码测试任务的执行时间
		for(WTask task: cWorkflow.getTaskList())
		{
			System.out.println("taskID:"+task.getTaskId()+" | "+
								"BaseStartTime:"+task.getBaseStartTime()+" | "+
								"BaseExecuteTime:"+task.getBaseExecutionTime()+" | "+
								"BaseFinishTime:"+task.getBaseFinishTime());
		}
*/
		
		int makespan = 0; //任务的最大完成时间就是makespan
		for(WTask cTask: cWorkflow.getTaskList())
		{
			if(cTask.getBaseFinishTime() > makespan)
			{
				makespan = cTask.getBaseFinishTime();
			}
		}
		if(makespan <= 0)
		{
			throw new IllegalArgumentException("Makespan of a workflow is less than zero!");
		}
		return makespan;
	
	}
	
	
	/*================================================================================================================*/
	
	/**
	 * 设置任务真正的基准执行时间
	 * 根据模板中的基本执行时长和标准差系数产生正态分布的随机执行时间作为任务的真正基准执行时间
	 * */ 
	public static void calculateRealTaskBaseExecutionTime(List<Workflow> list)
	{
		//遍历工作流列表
		for(Workflow tempWorkflow: list)
		{
/*			System.out.println("workflowID: "+tempWorkflow.getWorkflowId()+" | "+
								"name: "+tempWorkflow.getWorkflowName()+" | "+
								"arrival: "+tempWorkflow.getArrivalTime()+" | "+
								"deadline: "+tempWorkflow.getDeadline());
*/								
			for(WTask tempTask: tempWorkflow.getTaskList())
			{
				//此处getBaseExecutionTime是模板中直接读出来的执行时间，即基准执行时间
				//标准差=基准执行时间*方差系数
				double standardDeviation = tempTask.getBaseExecutionTime()*StaticfinalTags.standardDeviation;
				double variance = standardDeviation * standardDeviation;
				
				//使用基本执行时长和标准差来生成正态分布的真实基准执行时长，此时和VM因子无关
				int realBaseExecutionTime = (int)NormalDistributionCos(tempTask.getBaseExecutionTime(), variance);
				if(realBaseExecutionTime < 0)
				{
					realBaseExecutionTime = - realBaseExecutionTime;
				}
				
				if(realBaseExecutionTime == 0)
				{
					realBaseExecutionTime = 1;
				}
				
				tempTask.setRealBaseExecutionTime(realBaseExecutionTime);

/*					//这段代码用来测试任务执行时间
				System.out.println("taskID:"+tempTask.getTaskId()+" | "+
						"BaseExeTime:"+tempTask.getBaseExecutionTime()+" | "+
						"RealBaseExeTime:"+tempTask.getRealBaseExecutionTime()+" | "+
						"ExeTimeWithConfid:"+tempTask.getExecutionTimeWithConfidency()+" | "+
						"Average:"+tempTask.getBaseExecutionTime()+" | "+
						"Dev:"+(int)standardDeviation);
*/
				
			}/*end: for(WTask tempTask: tempWorkflow.getTaskList())*/
		}/*end: for(Workflow tempWorkflow: list)*/
	}/*end: calculateRealTaskBaseExecutionTime(List<Workflow> list)*/
	
	/*=================================================================================================================*/
	
	/**
	 * 泊松分布数值的产生
	 * @param Lamda: 泊松分布函数lamda参数(单位时间到达的工作流数量)
	 * @return 指定单位时间内到达的工作流数量
	 * */
	public static int PoissValue(double Lamda)
	{//产生的数值value是符合泊松分布的，均值和方差都是Lamda
		 int value=0;
		 double b=1;
		 double c=0;
		 c=Math.exp(-Lamda); 
		 double u=0;
		 do 
		 {
			 u=Math.random();
			 b*=u;
			 if(b>=c)
				 value++;
		  }while(b>=c);
		 return value;
	}
	
	/*================================================================================================================*/
	
	/**
	 * 正态分布数值生成器
	 * @param average 均值
	 * @param deviance 方差
	 * @return 
	 */
	public static double NormalDistributionCos(double average,double deviance)
	{
		double Pi=3.1415926535;
		double r1=Math.random();
		Math.random();Math.random();Math.random();Math.random();Math.random();
		Math.random();Math.random();
		double r2=Math.random();
		double u=Math.sqrt((-2)*Math.log(r1))*Math.cos(2*Pi*r2);
		double z=average+u*Math.sqrt(deviance);
		return z;
	}
	
}