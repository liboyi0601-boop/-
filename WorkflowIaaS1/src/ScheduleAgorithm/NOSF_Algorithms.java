package ScheduleAgorithm;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import share.PerformanceValue;
import share.StaticfinalTags;
import vmInfo.SaaSVm;
import vmInfo.VmResource.VmParameter;
import workflow.WTask;
import workflow.Workflow;

/**在线多工作流调度算法NOSF
 * @author ljg  ChangSha
 * 创建2017.4
 * * */

public class NOSF_Algorithms 
{

	private final WorkflowSchedulingEnv env;
	private final NosfPreprocessor preprocessor;
	private final SchedulingPolicy baselinePolicy;
	private final StateBuilder stateBuilder;
	private final SchedulingTraceRecorder traceRecorder;
	private int decisionCount;

	public NOSF_Algorithms() throws Exception
	{
		this(new NoOpSchedulingTraceRecorder());
	}

	public NOSF_Algorithms(SchedulingTraceRecorder traceRecorder) throws Exception
	{
		this.traceRecorder = traceRecorder;
		this.env = new WorkflowSchedulingEnv(traceRecorder); //初始化运行环境
		this.preprocessor = new NosfPreprocessor();
		this.baselinePolicy = new NosfBaselinePolicy();
		this.stateBuilder = new StateBuilder();
		this.decisionCount = 0;
	}
	
	/*=========================================================================================================*/
	
	/**在线动态调度工作流到VM*/
	public void ScheduleWorkflow_By_NOSF() 
	{
		decisionCount = 0;
		List<Workflow> workflowList = getWorkflowList();

		preprocessor.initializeExecutionTimeEstimate(workflowList); //计算出每个任务在调度时使用的近似基准执行时间
		
		List<SaaSVm> ActiveVmList = env.getActiveVmList(); //数据中心活动VM列表，初始为0
		List<SaaSVm> OffVmList = env.getOffVmList(); //数据中心已关闭的VM列表
		List<WTask> GlobalTaskPool = env.getGlobalTaskPool(); //任务池，放置全局等待任务
		long totalScheduleTime = 0;
		
		int TotalTask = 0;
		for(Workflow flow: workflowList)
		{
			TotalTask = TotalTask + flow.getTaskList().size();
		}
		System.out.println("总的workflow数："+workflowList.size()+"  总任务数："+TotalTask);
		
		int sum = 0;
		
		/*对所有工作流进行处理*/
		for(int i=0; i<workflowList.size(); i++)
		{
			//workflowsArriveSynchronously用来存放当前到达的工作流，同时有多个工作流到达时，形成当前同时到达的工作流列表
			List<Workflow>  workflowsArriveSynchronously = new ArrayList<Workflow>();
			workflowsArriveSynchronously.add(workflowList.get(i));//将当前到达的工作流存入
			
			//设置工作流的到达时间为“当前静态标记时间”
			StaticfinalTags.currentTime = workflowList.get(i).getArrivalTime(); //设置系统的当前时刻,同时到达的工作流的时间

/*			//测试代码			
			System.out.println("当前flow ID:"+workflowList.get(i).getWorkflowId()+" | "+
					"name: "+workflowList.get(i).getWorkflowName()+" | "+
					"taskNum:"+workflowList.get(i).getTaskList().size()+" | "+
					"arrival: "+workflowList.get(i).getArrivalTime()+" | "+
					"makespan: "+workflowList.get(i).getMakespan()+" | "+
					"deadline: "+workflowList.get(i).getDeadline()+" | "+
					"当前时间:"+StaticfinalTags.currentTime);
*/			
			/*寻找同时到达的工作流*/
			for(int j=i+1; j<workflowList.size(); j++)
			{
				//如果到达时间相同，则把工作流加入到workflowsArriveSynchronously
				if(workflowList.get(j).getArrivalTime() == workflowList.get(i).getArrivalTime())
				{
					workflowsArriveSynchronously.add(workflowList.get(j));
					i++;
				}
				else
				{
					break;
				}
			}//已经找出同时到达的工作流
			
			/*遍历当前同时到达的工作流，给每个工作流进行预处理*/
			for(int k=0; k<workflowsArriveSynchronously.size(); k++)
			{
//				long startTime01 = getCpuTime( );
				long startTime01 = System.currentTimeMillis();
				preprocessor.preprocessWorkflow(workflowsArriveSynchronously.get(k));
				
//				long endTime01 = getCpuTime();
				long endTime01 = System.currentTimeMillis();
				
				totalScheduleTime = totalScheduleTime + (endTime01-startTime01);
			}
			
			List<WTask> readyWTaskList = new ArrayList<WTask>();//存放同时到达的工作流中的就绪任务
			//对同时到达的工作流处理获得就绪任务list 
			
//			long startTime02 = getCpuTime();
			long startTime02 = System.currentTimeMillis();
			
			readyWTaskList = preprocessor.collectReadyTasksForArrivals(workflowsArriveSynchronously);
						
			scheduleReadyTaskToVM(readyWTaskList, ActiveVmList);
			
//			long endTime02 = getCpuTime();
			long endTime02 = System.currentTimeMillis();
			totalScheduleTime = totalScheduleTime + (endTime02-startTime02);
		
//			System.out.println("当前VM的ID"+VmId+" activeVmNum:"+VmRes.getActiveVmList().size()+" offVmNum:"+VmRes.getOffVmList().size());
//			System.out.println("就绪任务数：" + readyWTaskList.size());
			env.addUnallocatedTasksToGlobalPool(workflowsArriveSynchronously);
			
			int nextArrivalTime = Integer.MAX_VALUE;//用来记录下一个工作流到达的时刻
			if(i != workflowList.size()-1) //如果i不是最后一个工作流
			{
				nextArrivalTime = workflowList.get(i+1).getArrivalTime();//取得下一个工作流的到达时间
			}
			
			//对虚拟机进行遍历  给turnOffVmTime 和 nextFinishTime赋值
			int nextFinishTime = Integer.MAX_VALUE; //下一个紧接着要完成其上任务的VM的完成时间
			List<SaaSVm> nextFinishVmSet = new ArrayList<SaaSVm>();//下一个紧接着要完成其上任务的VM(可能有多个)
			int turnOffVmTime = Integer.MAX_VALUE; //紧接着的一个完成其上任务后会关机的VM时间
			List<SaaSVm> turnOffVmSet = new ArrayList<SaaSVm>();//紧接着的一个完成其上任务后会关机的VM(可能有多个)
			
			/*遍历所有活动VM，对有任务执行和空闲的VM分别考虑，需要求VM的完成时间，开始时间
			 * 这里会遍历出所有活动VM上正执行任务的真实完成时间。
			 * 注意：这里找到的具有早完成任务的VM上面可能还排有正等待着执行的任务，而且可能会有多个VM同时完成*/
			for(SaaSVm initiatedVm: ActiveVmList)
			{//对每个虚拟机进行遍历
				int tempFinishTime = initiatedVm.getExecutingWTask().getRealFinishTime();//获得正在执行的任务的真实完成时间
				if(tempFinishTime != -1)//虚拟机上有正在执行的任务
				{
					if (tempFinishTime < nextFinishTime)//找出虚拟机上正在执行任务的最早完成时刻
					{
						nextFinishTime = tempFinishTime; //找出正在执行任务的真实完成时间，即紧接着的一个完成时间
					}
				}
				else //虚拟机空闲的情况，找出具有最早关机时间的VM
				{
					//该虚拟机可能关闭的时刻，求出VM可能关机的时刻
					int tempTurnOffTime = Integer.MAX_VALUE;
					//判断当前时间-实际的关机时间是否正好是一个slot
					if((StaticfinalTags.currentTime - initiatedVm.getVmStartWorkTime())%StaticfinalTags.VmSlot == 0)
					{
						//如果VM工作时间刚好是一个整的时间槽，则认为当前时刻就是VM该关闭的时间
						tempTurnOffTime = StaticfinalTags.currentTime;
					}
					else
					{
						//如果VM工作时间不足一个整的时间槽，则认为VM在填充完一个整时间槽后是关闭的
						int round = (StaticfinalTags.currentTime - initiatedVm.getVmStartWorkTime())/StaticfinalTags.VmSlot; //向下取整
						tempTurnOffTime = initiatedVm.getVmStartWorkTime()+StaticfinalTags.VmSlot*(round+1);//填充满一个slot后的时间为关机时间
					}
					
					if (tempTurnOffTime < turnOffVmTime)//找出有最早关闭时刻的虚拟机
					{
						turnOffVmTime = tempTurnOffTime;

					}														
				}
				
			}/*end: for(SaaSVm initiatedVm: ActiveVmList)*/
			
			/*把相同最早关机的VM加入集合，最早同时完成任务的VM加入集合*/
			for(SaaSVm initiatedVm: ActiveVmList)
			{
				if(initiatedVm.getExecutingWTask().getRealFinishTime() != -1)
				{
					if(nextFinishTime == initiatedVm.getExecutingWTask().getRealFinishTime())
					{//最早同时完成任务的VM集合
						nextFinishVmSet.add(initiatedVm);
					}
				}
				else
				{
					if((turnOffVmTime - initiatedVm.getVmStartWorkTime())%StaticfinalTags.VmSlot == 0)
					{//当前时刻同时关机的VM集合
						turnOffVmSet.add(initiatedVm);
					}
					else 
					{//填充完一个整时间槽后同时关机的VM集合
						int round = (StaticfinalTags.currentTime - initiatedVm.getVmStartWorkTime())/StaticfinalTags.VmSlot; //向下取整
						if(turnOffVmTime == (initiatedVm.getVmStartWorkTime()+StaticfinalTags.VmSlot*(round+1)))
						{//若干个slot后同时关机的VM集合
							turnOffVmSet.add(initiatedVm);
						}
					}
				}
			}//同时最早关机的VM集合，同时最早完成任务的VM集合添加完毕


			/*到此为止，以上代码完成了如下工作：
			 * 1、调度了一个时间段内同时到达的工作流集合中的就绪任务（没有父任务或父任务都已执行完）
			 * 2、记录下一次要到达的工作流的时间
			 * 3、找出有任务在执行的VM中最早完成的VM或者空闲VM最早关机的VM*/
			
			/*###########################################################################################
			  #		开始循环检测程序：对两个相邻到达工作流之间的所有事件进行更新，                                                               #
			  #	        包括：（1）虚拟机上正在执行任务的完成，（2） VM空闲且到整个钟，关闭该虚拟机。                                     #
			  ###########################################################################################*/
			
			/*前一个工作流到达后被调度执行时引发的后续事件：
			 *虚拟机上正在执行任务的完成、VM空闲且到整个钟，关闭该虚拟机的情况，
			 *这些事件应该在后续新的工作流到达之前进行处理。
			 *此时新工作流还未到达。根据三种事件的产生分别处理能立即调度的任务
			 *更重要的：只有下一个新的工作流正式到达时，该while循环才会跳出！！
			 *所以在下一个新工作流正式到达前，循环检测系统中事件的产生，并对应作出及时处理*/
			
			while(nextArrivalTime >= nextFinishTime || nextArrivalTime > turnOffVmTime)
			{
				/*如果正在执行任务的VM要完成其上任务时间（在一个特定VM上）比VM关机时间早
				 *应该根据该VM上面任务的时间完成时间来更新对应后继任务的最早完成时间，并选择一个在这个时刻能立即执行的任务
				 *放到其上执行*/
				if(nextFinishTime <= turnOffVmTime)
				{
//					System.out.println("VM完成任务！");
				
					//更新下一个完成任务(有多个任务同时完成的情况)
					//更新当前的时间，可由虚拟机上正在执行任务的真正完成时间确定，让当前时间为虚拟机执行完当前任务的实际时间
					StaticfinalTags.currentTime = nextFinishTime;
					List<WTask> FinishedTaskSet = env.completeExecutingTasks(nextFinishVmSet);
					sum = sum + FinishedTaskSet.size();
					
//					long starTime03 = getCpuTime();
					long starTime03 = System.currentTimeMillis();
					//更新该任务后继任务权值
					preprocessor.updateSuccessorReadyTasks(FinishedTaskSet);
					
						/*从全局任务池中获得候选就绪任务（没有父任务或父任务都已执行完）
						 * 此时的父任务有分配和完成标记，具体完成时间也已计算
						 * 前面找出的紧急任务也在这个候选就绪队列中，此时的任务池中没有下一个到达的任务*/					
					List<WTask> candidateReadyTaskList = preprocessor.collectReadyTasksFromGlobalPool(GlobalTaskPool); //获取就绪任务
					recordTaskFinish(FinishedTaskSet, candidateReadyTaskList);
					
					/*因为刚才这个VM已完成了其上正在执行的任务，前面选的符合立即分配到该VM上的任务也分配完毕。
					 * 恰恰由于这个一系列动作完成，可能使候选就绪队列中的其他任务也有分配到VM上的机会，
					 * 所以再次给这些候选就绪队列调度一次VM*/
					scheduleReadyTaskToVM(candidateReadyTaskList, ActiveVmList); 
					
//					long endTime03 = getCpuTime();
					long endTime03 = System.currentTimeMillis();
					totalScheduleTime = totalScheduleTime + (endTime03-starTime03);
					//VmId = ActiveVmList.size() + OffVmList.size();
					
					env.removeAllocatedTasksFromGlobalPool(candidateReadyTaskList);
				
				}/*end: if(nextFinishTime <= nextUrgentTime && nextFinishTime <= turnOffVmTime)*/
				//任务完成后的相关更新完毕
				
				
				
				//如果VM关机时间比紧急任务时间早，并且VM关机时间比下一个要完成的任务早
				if(turnOffVmTime < nextFinishTime)
				{//关闭虚拟机, turnOffVmTime为当前的时间
					
					//System.out.println("有VM关机");
					StaticfinalTags.currentTime = turnOffVmTime; //更新系统当前的时间
					env.turnOffVmSet(turnOffVmSet, turnOffVmTime);
																														
				}//关闭虚拟机结束
				
				
				//对虚拟机进行遍历  给turnOffVmTime 和 nextFinishTime赋值
				nextFinishTime = Integer.MAX_VALUE; //每次需要更新前都要进行这样的赋值
				nextFinishVmSet = new ArrayList<SaaSVm>(); //需要更新正在执行任务状态的虚拟机
				turnOffVmTime = Integer.MAX_VALUE; //每次需要更新前都要进行这样的赋值
				turnOffVmSet = new ArrayList<SaaSVm>();//需要删除的虚拟机
				
				for(SaaSVm initiatedVm: ActiveVmList)
				{//对每个虚拟机进行遍历
					int tempFinishTime = initiatedVm.getExecutingWTask().getRealFinishTime();
					if(tempFinishTime != -1)//虚拟机上有正在执行的任务
					{
						if (tempFinishTime < nextFinishTime)//找出  虚拟机上正在执行任务的最早完成时刻
						{
							nextFinishTime = tempFinishTime;

						}
					}
					else //虚拟机空闲的情况
					{
						//该虚拟机可能关闭的时刻
						int tempTurnOffTime = Integer.MAX_VALUE;
						if((StaticfinalTags.currentTime - initiatedVm.getVmStartWorkTime())%StaticfinalTags.VmSlot == 0)
						{
							tempTurnOffTime = StaticfinalTags.currentTime;
						}
						else
						{
							int round = (StaticfinalTags.currentTime - initiatedVm.getVmStartWorkTime())/StaticfinalTags.VmSlot;
							tempTurnOffTime = initiatedVm.getVmStartWorkTime()+StaticfinalTags.VmSlot*(round+1);
						}
						
						if (tempTurnOffTime < turnOffVmTime)//找出 有最早关闭时刻的虚拟机
						{
							turnOffVmTime = tempTurnOffTime;
						}														
					}
				}/*end: for(SaaSVm initiatedVm: ActiveVmList)*/
				
				//把相同最早关机的VM加入集合，最早同时完成任务的VM加入集合
				for(SaaSVm initiatedVm: ActiveVmList)
				{
					if(initiatedVm.getExecutingWTask().getRealFinishTime() != -1)
					{
						if(nextFinishTime == initiatedVm.getExecutingWTask().getRealFinishTime())
						{//最早同时完成任务的VM集合
							nextFinishVmSet.add(initiatedVm);
						}
					}
					else
					{
						if((turnOffVmTime - initiatedVm.getVmStartWorkTime())%StaticfinalTags.VmSlot == 0)
						{//当前时刻同时关机的VM集合
							turnOffVmSet.add(initiatedVm);
						}
						else 
						{//填充完一个整时间槽后同时关机的VM集合
							int round = (StaticfinalTags.currentTime - initiatedVm.getVmStartWorkTime())/StaticfinalTags.VmSlot; //向下取整
							if(turnOffVmTime == (initiatedVm.getVmStartWorkTime()+StaticfinalTags.VmSlot*(round+1)))
							{//若干个slot后同时关机的VM集合
								turnOffVmSet.add(initiatedVm);
							}
						}
						
					}
				}//同时最早关机的VM集合，同时最早完成任务的VM集合添加完毕
				
				
				if(nextArrivalTime==Integer.MAX_VALUE 
						&& nextFinishTime==Integer.MAX_VALUE && turnOffVmTime==Integer.MAX_VALUE)
				{
					break;
				}
				
			}/*end while, 对两个相邻到达工作流之间的所有事件进行更新  一个轮回结束*/
			
			
			/*##################################################################################
			 *#                                  循环检测程序结束                                                                 #
			 *##################################################################################*/

		}/*end: for(int i=0; i<workflowList.size(); i++)
		   对所有工作流调度结束*/
		
		System.out.println("完成任务数: "+sum);
		
		if(ActiveVmList.size() != 0)
		{//实验最后应该没有活跃虚拟机
			System.out.println("Error: there exists active VMs at last!");
		}
		
		OutputExperimentResult(OffVmList, workflowList, totalScheduleTime);
		
		env.clearRuntimeState();
		
	}/*end: ScheduleWorkflow_By_OSDS()*/
	
	
/*======================================================================================================*/	
	/** Get CPU time in nanoseconds. 
	 * 获得CPU的线程时间（纳秒）*/
	public static long getCpuTime( ) {
	    ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
	    return bean.isCurrentThreadCpuTimeSupported( )? bean.getCurrentThreadUserTime( ) : 0L; //返回CUP线程时间
	}
	
/*======================================================================================================*/

	/**在活动VM列表中寻找适合调度就绪队列的VM，VM上只允许执行一个任务，也只允许一个等待任务*/
	public void scheduleReadyTaskToVM(List<WTask> taskList, List<SaaSVm> vmList)
	{
		//对就绪任务按照权重（最早完成时间）进行升序排序
		Collections.sort(taskList, new AscendingWTaskByPECT());
		
		for(WTask scheduleTask: taskList) //对每个任务进行调度
		{
			recordDecisionCandidate(taskList, vmList);
			SchedulingAction action = baselinePolicy.selectAction(scheduleTask, vmList);
			recordDecisionChosen(scheduleTask, action);
			env.applyAction(action);
		}//end for(WTask scheduleTask: taskList)
			
	}/*end: scheduleReadyTaskToVM*/
	
	/*======================================================================================================*/
	
	/**输出实验结果*/
	public void OutputExperimentResult(List<SaaSVm> OffVmList, List<Workflow> workflowList, long totalScheduleTime)
	{
		java.text.DecimalFormat fd = new java.text.DecimalFormat("0.0000");//设定输出结果小数点的位数
		
		//统计调度指标：(1)费用；(2)资源利用率；(3)偏差率；(4)deadline违背量
		double totalCost = 0;
		int totalExecutionTime = 0; //VM总的真实执行时间
		int totalActiveTime = 0; //VM总的活动时间 
		double taskDeviation = 0; //任务的偏差
		int taskNum = 0; //记录所有任务总数
		
		for(SaaSVm offVm: OffVmList) //遍历所有关闭的VM
		{
			totalCost = totalCost + offVm.getTotalCost(); //累加VM成本
			
			int idleTime = 0;
			int TerminalIdle = 0; //Vm关机时的末端空置时间:  VM关机时间-其上最后一个任务的实际完成时间
			
//			System.out.println("VMID: "+offVm.getVmID());
			TerminalIdle = offVm.getEndWorkTime() - offVm.getWTaskList().get(offVm.getWTaskList().size()-1).getRealFinishTime();
			for(int i=1; i<offVm.getWTaskList().size(); i++)
			{   //求出所有关闭的VM上计算任务时的空闲时间，后一个任务的真实开始时间-前一个任务的真实完成时间
				idleTime = idleTime + offVm.getWTaskList().get(i).getRealStartTime()-offVm.getWTaskList().get(i-1).getRealFinishTime();
			}
			
			//VM总的活动时间
			int VmActiveTime = offVm.getEndWorkTime() - offVm.getVmStartWorkTime();
			double idleRatio = (double)idleTime/VmActiveTime; //当前这个VM的空闲率
			
			//当前VM的末端空闲率
//			double TerminalIdleRatio = (double)TerminalIdle/VmActiveTime;
//			System.out.println("空闲时间: "+idleTime+" 空闲率:"+fd.format(idleRatio)+" 末端空闲时间:"+TerminalIdle+" 末端空闲率："+fd.format(TerminalIdleRatio));
						
			int workTime = 0; //记录任务的执行时间
			//统计当前这个VM的实际工作时间，偏差率
			for(WTask task: offVm.getWTaskList())
			{
				workTime = workTime + task.getRealExecutionTime(); //真实执行时间
				taskNum++; //任务的个数
				//统计总偏差      |真实开始时间-近似开始时间|/真实执行时间
				taskDeviation = taskDeviation + (double)Math.abs((task.getRealStartTime()-task.getStartTimeWithConfidency()))/task.getRealExecutionTime();
			}
			
			totalExecutionTime = totalExecutionTime + workTime; //累加VM的真实执行时间
			totalActiveTime = totalActiveTime + VmActiveTime; //累加VM的活动时间
			
			//计算当前VM的利用率：VM真实执行时间/VM活动时间
//			double reU = (double)workTime/VmActiveTime;
//			System.out.println("当前VM利用率: "+fd.format(reU)+" "+fd.format(reU+idleRatio+TerminalIdleRatio));
			
		}//end: for(SaaSVm offVm: OffVmList)
		
		//所有VM的总利用率：总的真实执行时间/总的活动时间
		double reUtilization = (double)totalExecutionTime/totalActiveTime;
		taskDeviation = taskDeviation/taskNum; //任务偏差率
		
		double workflowDeviation = 0;
		
		int ExceedSum = 0; //记录超过deadline的工作流数目
		double ExceedTimeRateSum = 0; //记录超出率的累加和
		
//		int MeanAheadOfTime = 0;
		for(Workflow tempWorkflow: workflowList) //遍历所有工作流
		{
			int maxRealFinishTime = 0;
			int maxFinishTimeWC = 0;
			int workflowExecutionTime = 0;
//			int aheadOfTime = 0;
			
			double EXceedTimeRate; //记录一个流的时间超出率
			
			for(WTask task: tempWorkflow.getTaskList())
			{
				if(task.getRealFinishTime() > maxRealFinishTime) //找到工作流的真实完成时间(最迟的任务真实结束时间)
				{
					maxRealFinishTime = task.getRealFinishTime();
				}
				if(task.getFinishTimeWithConfidency() > maxFinishTimeWC) //找到工作流的近似完成时间(最迟的任务近似结束时间)
				{
					maxFinishTimeWC = task.getFinishTimeWithConfidency();
				}
			}
			
			//测试  工作流不满足deadline完成
			if(maxRealFinishTime > tempWorkflow.getDeadline())
			{
/*				System.out.println("工作流Id:"+tempWorkflow.getWorkflowId()+
						" 实际完成时间："+maxRealFinishTime+
						" 近似完成时间："+maxFinishTimeWC+
						" deadline:"+tempWorkflow.getDeadline()+
						" 误差："+ (maxRealFinishTime - tempWorkflow.getDeadline()));
*/
				//deadline超出时间占指定deadline的比率
//				EXceedTimeRate = (double)(maxRealFinishTime - tempWorkflow.getDeadline())/(tempWorkflow.getDeadline()-tempWorkflow.getArrivalTime());
//				ExceedTimeRateSum = ExceedTimeRateSum + EXceedTimeRate;
				ExceedSum++;
			}
			
			EXceedTimeRate = (double)(maxRealFinishTime - tempWorkflow.getDeadline())/(tempWorkflow.getDeadline()-tempWorkflow.getArrivalTime());
			ExceedTimeRateSum = ExceedTimeRateSum + EXceedTimeRate;
			
//			ExceedTimeRateSum = ExceedTimeRateSum + EXceedTimeRate;
			
			
//			aheadOfTime = tempWorkflow.getDeadline() - maxRealFinishTime;
//			MeanAheadOfTime = MeanAheadOfTime + aheadOfTime;
//			System.out.println("提前时间：" + aheadOfTime);
			
			workflowExecutionTime = maxRealFinishTime - tempWorkflow.getArrivalTime();
			//工作流的偏差率：|工作流的近似完成时间-工作流的真实完成时间|/工作流的真实执行时间
			workflowDeviation = workflowDeviation + (double)Math.abs((maxFinishTimeWC - maxRealFinishTime))/workflowExecutionTime;								
		
		}//遍历所有工作流结束
		
//		MeanAheadOfTime = MeanAheadOfTime / workflowList.size();
		workflowDeviation = workflowDeviation/workflowList.size();
		
		//工作流超出deadline的数目与总工作流数目的比率
		double ExceedCountRate = (double)ExceedSum / workflowList.size();
		//工作流平均超过deadline的时间占总deadline执行时长的比率
		double meanExceedTimeRate = ExceedTimeRateSum / workflowList.size();
		
//		System.out.println("MeanAheadOfTime: "+MeanAheadOfTime);
		System.out.println("使用的虚拟机数: "+OffVmList.size()+" 完成的任务数："+taskNum);
		System.out.println("WorkflowNum: "+workflowList.size()+"  "+
							" StandardDeviation: "+StaticfinalTags.standardDeviation+"  "+
							" DeadlineBase: "+StaticfinalTags.deadlineBase+"  "+
							" VmSlot: "+StaticfinalTags.VmSlot);
		
		//实验结果的输出	
		PerformanceValue.TotalCost = totalCost;
		PerformanceValue.ResourceUtilization = reUtilization;
		PerformanceValue.taskDeviation = taskDeviation;
		PerformanceValue.workflowDeviation = workflowDeviation;
		PerformanceValue.ViolationCount = ExceedCountRate;
		PerformanceValue.ViolationTime = meanExceedTimeRate;
		PerformanceValue.ScheduleTime = totalScheduleTime;
		PerformanceValue.totalVmCount = OffVmList.size();
		
		System.out.println("总成本: "+fd.format(totalCost)+" VM利用率: "+fd.format(reUtilization)+
				" 任务偏差: "+fd.format(taskDeviation)+" 工作流偏差: "+fd.format(workflowDeviation)+
				" 超出数量比: "+fd.format(ExceedCountRate)+" 超出时间比: "+fd.format(meanExceedTimeRate)+
				" 算法执行时间: "+fd.format(totalScheduleTime)+" 租用VM数: "+fd.format(OffVmList.size()));		
	}
	
	/*======================================================================================================*/
	
	/**提交工作流集合
       @param list: 所有的工作流，把所有的工作流list都添加到了workflowList*/
	public void submitWorkflowList(List<Workflow> list)
	{
		env.submitWorkflowList(list);
	}
	
	/**获取工作流，返回一个工作流列表，成员变量workflowList*/
	public List<Workflow> getWorkflowList() {return env.getWorkflowList(); }

	public SchedulingState buildSchedulingState()
	{
		return stateBuilder.build(env);
	}

	private void recordDecisionCandidate(List<WTask> taskList, List<SaaSVm> vmList)
	{
		if(!traceRecorder.isEnabled())
		{
			return;
		}

		try
		{
			traceRecorder.recordDecisionCandidate(
					StaticfinalTags.currentTime,
					buildPendingTaskSet(taskList),
					vmList,
					env.getWorkflowList().size(),
					env.getActiveVmList().size(),
					env.getOffVmList().size(),
					env.getGlobalTaskPool().size());
		}
		catch(java.io.IOException exception)
		{
			throw new IllegalStateException("Failed to record decision_candidate trace event", exception);
		}
	}

	private void recordDecisionChosen(WTask scheduleTask, SchedulingAction action)
	{
		if(!traceRecorder.isEnabled())
		{
			return;
		}

		SchedulingState snapshot = null;
		if(traceRecorder.shouldCaptureStateSnapshot(decisionCount))
		{
			snapshot = buildSchedulingState();
		}

		try
		{
			traceRecorder.recordDecisionChosen(
					StaticfinalTags.currentTime,
					scheduleTask,
					action,
					estimateCostIncrement(action),
					snapshot);
			decisionCount++;
		}
		catch(java.io.IOException exception)
		{
			throw new IllegalStateException("Failed to record decision_chosen trace event", exception);
		}
	}

	private void recordTaskFinish(List<WTask> finishedTasks, List<WTask> readyTasksAfterFinish)
	{
		if(!traceRecorder.isEnabled())
		{
			return;
		}

		try
		{
			traceRecorder.recordTaskFinish(StaticfinalTags.currentTime, finishedTasks, readyTasksAfterFinish);
		}
		catch(java.io.IOException exception)
		{
			throw new IllegalStateException("Failed to record task_finish trace event", exception);
		}
	}

	private List<WTask> buildPendingTaskSet(List<WTask> taskList)
	{
		List<WTask> pendingTasks = new ArrayList<WTask>();
		for(WTask task: taskList)
		{
			if(!task.getAllocatedFlag())
			{
				pendingTasks.add(task);
			}
		}
		return pendingTasks;
	}

	private double estimateCostIncrement(SchedulingAction action)
	{
		if(action.getActionType() == SchedulingActionType.ALLOCATE_TO_EXISTING_VM)
		{
			SaaSVm targetVm = action.getTargetVm();
			int executionTime = (int)(action.getTask().getExecutionTimeWithConfidency() * targetVm.getVmFactor());
			return executionTime * targetVm.getVmPrice();
		}

		VmParameter vmParameter = VmParameter.valueOf(action.getNewVmType());
		int executionTime = (int)(action.getTask().getExecutionTimeWithConfidency() * vmParameter.getFactor());
		return executionTime * vmParameter.getPrice();
	}
	
	/*=====================================================================================================*/
	
	public class AscendingWTaskByPECT implements Comparator<WTask>
	{
		public int compare(WTask task1, WTask task2) 
		{
			return (task1.getEarliestFinishTime() - task2.getEarliestFinishTime());
		}

	}

}
