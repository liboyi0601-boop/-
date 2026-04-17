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
import vmInfo.VmResource;
import workflow.ConstraintWTask;
import workflow.WTask;
import workflow.Workflow;

/**在线多工作流调度算法NOSF
 * @author ljg  ChangSha
 * 创建2017.4
 * * */

public class NOSF_Algorithms 
{

	private List<Workflow> workflowList; //工作流集合,所有提交来的工作流集合
	private VmResource VmRes; //VM资源
	private final NosfPreprocessor preprocessor;

	public NOSF_Algorithms() throws Exception
	{
		this.VmRes = new VmResource(); //初始化VM资源
		this.workflowList = new ArrayList<Workflow>(); //初始化工作流列表
		this.preprocessor = new NosfPreprocessor();
	}
	
	/*=========================================================================================================*/
	
	/**在线动态调度工作流到VM*/
	public void ScheduleWorkflow_By_NOSF() 
	{
		List<Workflow> workflowList = getWorkflowList();

		preprocessor.initializeExecutionTimeEstimate(workflowList); //计算出每个任务在调度时使用的近似基准执行时间
		
		int VmId = VmRes.getActiveVmList().size(); //获取数据中心虚拟机list的大小
		List<SaaSVm> ActiveVmList =VmRes.getActiveVmList(); //数据中心活动VM列表，初始为0
		List<SaaSVm> OffVmList = VmRes.getOffVmList(); //数据中心已关闭的VM列表
		long totalScheduleTime = 0;
		
		int TotalTask = 0;
		for(Workflow flow: workflowList)
		{
			TotalTask = TotalTask + flow.getTaskList().size();
		}
		System.out.println("总的workflow数："+workflowList.size()+"  总任务数："+TotalTask);
		
		List<WTask> GlobalTaskPool = new ArrayList<WTask>(); //任务池，放置全局等待任务
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
			VmId = ActiveVmList.size()+OffVmList.size();
			
			for(Workflow addWorkflow: workflowsArriveSynchronously)
			{//同时到达的工作流还没有分配到VM上的任务加入全局任务池GlobalTaskPool
				for(WTask addTask: addWorkflow.getTaskList())
				{
					if(!addTask.getAllocatedFlag())//找出还没有调度的任务
					{
						GlobalTaskPool.add(addTask);//此时全局等待任务池放入的是同时到达的工作流中还未调度的任务
						                      //多次循环后这个任务池会有很多不同时间到达的工作流（其中还有为调度的任务）
					}
				}
			}
			
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
					List<WTask> FinishedTaskSet = new ArrayList<WTask>();
										
					for(SaaSVm nextFinishVm: nextFinishVmSet)
					{
						WTask finishedTask = nextFinishVm.getExecutingWTask();
					
						//对VM上有等待任务和无等待任务分别处理，目的让VM把已有的等待任务执行完
						if(nextFinishVm.getWaitingWTask().getBaseExecutionTime() != -1)
						{//虚拟机上有正在等待任务，要让正在执行的任务状态设成完成，正在等待的任务立即变为执行状态
							nextFinishVm.getExecutingWTask().setFinishFlag(true); //设置当前VM上的任务的执行完成标致
							nextFinishVm.setExecutingWTask(nextFinishVm.getWaitingWTask());//把VM上的等待任务设为执行状态
							nextFinishVm.setWaitingWTask(new WTask());//设置后续的等待任务实例，此时为空实例没有内容					
						}
						else
						{//虚拟机上没有正在等待任务，要让正在执行的任务状态设成完成
							nextFinishVm.getExecutingWTask().setFinishFlag(true); //设置当前VM上的任务的执行完成标致
							nextFinishVm.setExecutingWTask(new WTask()); //设置正执行的任务实例，此时为空实例没有内容
						}/*经过该if语句后这个特定的VM上正执行的任务已经执行完毕，若有等待的任务也已立即变成执行状态*/
					
						FinishedTaskSet.add(finishedTask);
						sum++;
						
//						if(finishedTask.getTaskId().equals("ID00046"))
//							System.out.println("here");
					
//						System.out.println("工作流:"+finishedTask.getTaskWorkFlowId()+"的任务："+finishedTask.getTaskId()+"完成");
						
					}
					
//					long starTime03 = getCpuTime();
					long starTime03 = System.currentTimeMillis();
					//更新该任务后继任务权值
					preprocessor.updateSuccessorReadyTasks(FinishedTaskSet);
					
						/*从全局任务池中获得候选就绪任务（没有父任务或父任务都已执行完）
						 * 此时的父任务有分配和完成标记，具体完成时间也已计算
						 * 前面找出的紧急任务也在这个候选就绪队列中，此时的任务池中没有下一个到达的任务*/					
					List<WTask> candidateReadyTaskList = preprocessor.collectReadyTasksFromGlobalPool(GlobalTaskPool); //获取就绪任务
					
					/*因为刚才这个VM已完成了其上正在执行的任务，前面选的符合立即分配到该VM上的任务也分配完毕。
					 * 恰恰由于这个一系列动作完成，可能使候选就绪队列中的其他任务也有分配到VM上的机会，
					 * 所以再次给这些候选就绪队列调度一次VM*/
					scheduleReadyTaskToVM(candidateReadyTaskList, ActiveVmList); 
					
//					long endTime03 = getCpuTime();
					long endTime03 = System.currentTimeMillis();
					totalScheduleTime = totalScheduleTime + (endTime03-starTime03);
					//VmId = ActiveVmList.size() + OffVmList.size();
					
					for(WTask allocatedWtask: candidateReadyTaskList)
					{//将已经调度的任务从GlobalTaskPool中移除
						if(allocatedWtask.getAllocatedFlag())
						{
							if(!GlobalTaskPool.remove(allocatedWtask))
							{
								System.out.println("Error: the allocatedWTask cannot be found in GlobalTaskPool");
							}
						}
					}//end for(WTask allocatedWtask: candidateWTaskList)									
				
				}/*end: if(nextFinishTime <= nextUrgentTime && nextFinishTime <= turnOffVmTime)*/
				//任务完成后的相关更新完毕
				
				
				
				//如果VM关机时间比紧急任务时间早，并且VM关机时间比下一个要完成的任务早
				if(turnOffVmTime < nextFinishTime)
				{//关闭虚拟机, turnOffVmTime为当前的时间
					
					//System.out.println("有VM关机");
					StaticfinalTags.currentTime = turnOffVmTime; //更新系统当前的时间
					for(SaaSVm turnOffVm: turnOffVmSet)
					{
						int workTime = turnOffVmTime - turnOffVm.getVmStartWorkTime(); //计算VM活动时长
						
						//在前面求最早turnOffVmTime中已经把不足1小时的时间填充为一个整时间槽长度
						double cost = (workTime * turnOffVm.getVmPrice()) / StaticfinalTags.VmSlot; //计算该VM成本
						
						//System.out.println("VM:"+turnOffVm.getVmID()+" 类型："+turnOffVm.getVmType()+" 关闭");
						
						//设置VM的关闭信息，并计算VM的成本
						turnOffVm.setEndWorkTime(turnOffVmTime); //设置VM结束时间
						turnOffVm.setTotalCost(cost); //设置VM工作成本
						turnOffVm.setVmStatus(false); //设置VM状态
						ActiveVmList.remove(turnOffVm);
						OffVmList.add(turnOffVm);
					}
																														
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
		
		workflowList.clear();
		OffVmList.clear();
		ActiveVmList.clear();
		
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
			double minCost = Double.MAX_VALUE;
			SaaSVm MapTargetVm = null;
			int RealDataArrivalList[] = new int [vmList.size()];//存储任务在各个VM上比较的数据到达时间
			
			for(SaaSVm vm: vmList) //寻找虚拟机,虚拟机为空闲,或者虚拟机的等待任务为空
			{
				int realDataArrival = Integer.MIN_VALUE;
				//找出当前任务若调度到该VM上，自身父任务最大数据到达的时间
				for(ConstraintWTask parentCon: scheduleTask.getParentTaskList()) 
				{//找出任务task的父任务的最大真实数据到达和近似到达时间										
					int DataArrival = 0;
					//int DataArrivalWithConfidency = 0;
					
					//判断是否VM上的任务就是该任务的父任务
					if(vm.getWTaskList().contains(parentCon.getWTask()))
					{
						DataArrival = parentCon.getWTask().getRealFinishTime();
					}
					else
					{
						DataArrival = parentCon.getWTask().getRealFinishTime() 
								+ parentCon.getDataSize()/StaticfinalTags.bandwidth;
					}
					
					if(DataArrival > realDataArrival)
					{
						realDataArrival = DataArrival;
						RealDataArrivalList[vmList.indexOf(vm)]=realDataArrival;
					}
									
				}//end: for(ConstraintWTask parentCon: readyTask.getParentTaskList())
								
				
				if(vm.getWaitingWTask().getTaskId().equals("initial"))
				{//虚拟机的正在等待任务为空
					if(vm.getExecutingWTask().getTaskId().equals("initial"))
					{//虚拟机的正在执行任务为空
						int vmReadyTime = StaticfinalTags.currentTime; //开始时间等于当前时间
						//int readyStartTime = scheduleTask.getEarliestStartTime(); //就绪任务的数据就绪时间
						int readyStartTime = RealDataArrivalList[vmList.indexOf(vm)]; //就绪任务的数据就绪时间
						
						//找出VM就绪和任务数据就绪之间的最大者作为任务的可开始时间
						if (vmReadyTime > readyStartTime)
						{
							readyStartTime = vmReadyTime;
						}
						
						//用当前需要调度任务的近似执行时间*VM因子，计算估计执行时间
						int executionTimeWithConfidency = (int)(scheduleTask.getExecutionTimeWithConfidency()*vm.getVmFactor());
						double cost = executionTimeWithConfidency*vm.getVmPrice(); //计算估计成本
						//由任务可开始执行时间+近似执行时间作近似完成时间
						int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency; //计算估计的完成时间
						
						//选择使得在subdeadline之前能估计完成的VM。
						if(finishTimeWithConfidency <= scheduleTask.getSubDeadLine())
						{
							if(cost<minCost)
							{
								minCost = cost;
					//			MapTargetVm = vm; //找到最小成本的VM
							}
						}						
					}
					else
					{//虚拟机的等待任务为空，而虚拟机上有正在执行的任务
						
						//用VM上正在执行任务的近似执行时长，即正在执行的任务的近似完成时间作为VM的可以时间，即开始时间
						int vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency(); 
						//int readyStartTime = scheduleTask.getEarliestStartTime(); //就绪任务的数据就绪时间
						int readyStartTime = RealDataArrivalList[vmList.indexOf(vm)]; //就绪任务的数据就绪时间
						
						//找出VM就绪和任务数据就绪之间的最大者作为任务的可开始时间
						if(vmReadyTime > readyStartTime)
						{
							readyStartTime = vmReadyTime;
						}
						
						//用当前需要调度任务的近似执行时间*VM因子，计算估计执行时间
						int executionTimeWithConfidency = (int)(scheduleTask.getExecutionTimeWithConfidency()*vm.getVmFactor());
						double cost = executionTimeWithConfidency*vm.getVmPrice(); //计算估计成本
						//由任务可开始执行时间+近似执行时间作近似完成时间
						int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency; //计算估计的完成时间
						
						//选择使得近似完成时间在“当前时刻”+subdeadline之前能估计完成的VM。		
						if(finishTimeWithConfidency <= scheduleTask.getSubDeadLine())
						{
							if(cost<minCost)
							{
								minCost = cost;
					//			MapTargetVm = vm; //找到最小成本的VM
							}
						}	
					}/*end: if(vm.getExecutingWTask().getTaskId().equals("initial"))...else...*/
					
				}//end if(vm.getWaitingWTask().getTaskId().equals("initial"))
				
			}//寻找虚拟机结束	end for(SaaSVm vm: vmList)
			
			
			List<SaaSVm> MapTargetVmSet = new ArrayList<SaaSVm>(); //存储多个具有相同最小成本的VM
			//把多个具有相同最小成本的VM加入集合
			for(SaaSVm vm: vmList)
			{
				if(vm.getWaitingWTask().getTaskId().equals("initial"))
				{
					if(vm.getExecutingWTask().getTaskId().equals("initial"))
					{
						int vmReadyTime = StaticfinalTags.currentTime; //开始时间等于当前时间
						//int readyStartTime = scheduleTask.getEarliestStartTime();
						int readyStartTime = RealDataArrivalList[vmList.indexOf(vm)];
						
						if (vmReadyTime > readyStartTime)
						{
							readyStartTime = vmReadyTime;
						}
						
						//用当前需要调度任务的近似执行时间*VM因子，计算估计执行时间
						int executionTimeWithConfidency = (int)(scheduleTask.getExecutionTimeWithConfidency()*vm.getVmFactor());
						double cost = executionTimeWithConfidency*vm.getVmPrice(); //计算估计成本
						int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency; //计算估计的完成时间
						
						//选择使得在subdeadline之前能估计完成的VM。
						if(finishTimeWithConfidency <= scheduleTask.getSubDeadLine())
						{
							if(cost == minCost)
							{
								MapTargetVmSet.add(vm); //把最小成本的VM加入集合
							}
						}
					}
					else
					{
						//虚拟机的等待任务为空，而虚拟机上有正在执行的任务
						
						//用VM上正在执行任务的近似执行时长，即正在执行的任务的近似完成时间作为VM的可以时间，即开始时间
						int vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency();
						//int readyStartTime = scheduleTask.getEarliestStartTime();
						int readyStartTime = RealDataArrivalList[vmList.indexOf(vm)];
						
						if (vmReadyTime > readyStartTime)
						{
							readyStartTime = vmReadyTime;
						}
						
						//用当前需要调度任务的近似执行时间*VM因子，计算估计执行时间
						int executionTimeWithConfidency = (int)(scheduleTask.getExecutionTimeWithConfidency()*vm.getVmFactor());
						double cost = executionTimeWithConfidency*vm.getVmPrice(); //计算估计成本
						int finishTimeWithConfidency = readyStartTime + executionTimeWithConfidency; //计算估计的完成时间
						
						if(finishTimeWithConfidency <= scheduleTask.getSubDeadLine())
						{
							if(cost == minCost)
							{
								MapTargetVmSet.add(vm); //把最小成本的VM加入集合
							}
						}
					} //end: if(vm.getExecutingWTask().getTaskId().equals("initial"))
				} //end: if(vm.getWaitingWTask().getTaskId().equals("initial"))
			}//遍历相同最小成本VM结束
			
			
			//在相同最小成本的VM中找出空闲时间最小的VM
			int minIdleTime = Integer.MAX_VALUE;
			for(SaaSVm vm: MapTargetVmSet)
			{
				int vmReadyTime = StaticfinalTags.currentTime; //获得VM就绪时间
					
				if(!vm.getExecutingWTask().getTaskId().equals("initial"))
				{//如果VM上有正在执行的任务
					vmReadyTime = vm.getExecutingWTask().getFinishTimeWithConfidency(); //用正在执行的任务的近似完成时间做就绪时间
				}
				//int readyStartTime = scheduleTask.getEarliestStartTime();
				int readyStartTime = RealDataArrivalList[vmList.indexOf(vm)];
				
				//找出VM就绪和任务数据就绪之间的最大者作为任务的可开始时间
				if(vmReadyTime > readyStartTime) 
				{
					readyStartTime = vmReadyTime; //用VM就绪时间作任务可开始时间
				}
					
				int IdleTime = readyStartTime - vmReadyTime; //求出VM空闲时间
				if(IdleTime < minIdleTime)
				{
					minIdleTime = IdleTime; //获得最小的空闲
					MapTargetVm = vm; //获得最小空闲的VM
				}
				
			}
			

			if(MapTargetVm != null)//如果找到这样的合适VM
			{
				allocateReadyWTaskToSaaSVm(scheduleTask, MapTargetVm, RealDataArrivalList[vmList.indexOf(MapTargetVm)]);//将当前任务分配到虚拟机targetVm上
			}
			else
			{//没有找到，则增加一个新的VM
				int readyStartTime = scheduleTask.getEarliestStartTime();
				//找出VM就绪和任务数据就绪之间的最大者作为任务的可开始时间
				if(StaticfinalTags.currentTime > readyStartTime)
				{
					readyStartTime = StaticfinalTags.currentTime;
				}
				
				//确定新增VM类型
				int MapVmType = determineSaaSVmType(scheduleTask, readyStartTime);
				int VmId = vmList.size();//确定需要增加的VM的id
				SaaSVm newVm = VmRes.scaleUpVm(VmId, StaticfinalTags.currentTime, MapVmType);
				if(newVm != null)
				{
					allocateReadyWTaskToNewLeasedVm(scheduleTask, newVm, readyStartTime); //把任务分配到VM上
					vmList.add(newVm); //将新虚拟机加入VM列表
				}

			}

		}//end for(WTask scheduleTask: taskList)
			
	}/*end: scheduleReadyTaskToVM*/
	
	/*======================================================================================================*/
	
	/**把一个任务分配到VM上*/
	public void allocateReadyWTaskToSaaSVm(WTask task, SaaSVm vm, int realDataArrival)
	{
		/*如果VM空闲，则VM的开始时间就是当前时间；
		 *对应外部程序中VM上没有正在执行的任务情况*/
//		int realStartTime = StaticfinalTags.currentTime;
//		int startTimeWithConfidency = StaticfinalTags.currentTime;
		
		int vmRealReadyTime = StaticfinalTags.currentTime; //用当前时间作VM实际就绪时间
		int vmReadyTimeWC = StaticfinalTags.currentTime; //用当前时间作VM近似就绪时间
		//int readyStartTime = task.getEarliestStartTime(); //获取任务数据就绪时间做实际可开始时间
		//int readyStartTimeWC = task.getEarliestStartTime(); //获取任务数据就绪时间做近似可开始时间
		int readyStartTime = realDataArrival; //获取任务数据就绪时间做实际可开始时间
		int readyStartTimeWC = realDataArrival; //获取任务数据就绪时间做近似可开始时间
				
		if(!vm.getExecutingWTask().getTaskId().equals("initial"))
		{
			/*如果虚拟机上有任务执行的情况，这时VM上正在执行的任务是本方法被调用前，已在VM上执行的任务。
			 *对应外部程序中VM上有任务执行的情况,要将正在执行的任务实际完成时间作为VM的开始时间*/
//			realStartTime = vm.getExecutingWTask().getRealFinishTime(); //记录VM上正在执行任务的真实完成时间
//			startTimeWithConfidency = vm.getExecutingWTask().getFinishTimeWithConfidency(); //记录VM上正执行任务的近似完成时间
			
			vmRealReadyTime = vm.getExecutingWTask().getRealFinishTime(); //记录VM上正在执行任务的真实完成时间做真实VM就绪时间
			vmReadyTimeWC = vm.getExecutingWTask().getFinishTimeWithConfidency(); //记录VM上正执行任务的近似完成时间做近似就绪时间
			
		}
		
		//获取VM就绪和任务就绪之间的最大
		if(vmRealReadyTime > readyStartTime) 
		{
			readyStartTime = vmRealReadyTime; //实际可开始时间
		}
		if(vmReadyTimeWC > readyStartTimeWC)
		{
			readyStartTimeWC = vmReadyTimeWC; //近似可开始时间
		}
		
		/*更新任务scheduleTask的状态*/
		
		//当前需要调度的任务的真实执行时间（由正态分布产生的基本执行时长）
		int realExecutionTime = (int)(task.getRealBaseExecutionTime()*vm.getVmFactor());
//		int realFinishTime = realStartTime + realExecutionTime;
		int realFinishTime = readyStartTime + realExecutionTime;
		
		//分配当前需要调度的任务到VM上得到的近似执行时间和近似完成时间
		int executionTimeWithConfidency = (int)(task.getExecutionTimeWithConfidency()*vm.getVmFactor());
//		int finishTimeWithConfidency = startTimeWithConfidency + executionTimeWithConfidency;
		int finishTimeWithConfidency = readyStartTimeWC + executionTimeWithConfidency;
		
		task.setAllocatedFlag(true); //标记已分配
		task.setAllocateVm(vm);  //设定分配的VM
//		task.setRealStartTime(realStartTime); 
		task.setRealStartTime(readyStartTime);//设定真实开始时间
		
		task.setRealExecutionTime(realExecutionTime); //设定真实执行时间(正态分布)
		task.setRealFinishTime(realFinishTime); //设定真实完成时间
		
//		task.setStartTimeWithConfidency(startTimeWithConfidency); 
		task.setStartTimeWithConfidency(readyStartTimeWC); //设定近似开始时间
		task.setFinishTimeWithConfidency(finishTimeWithConfidency); //设定近似完成时间
							
		/*更新虚拟机vm的状态*/
		vm.setFinishTime(finishTimeWithConfidency); //设定近似完成时间
		vm.setRealFinishTime(realFinishTime); //设定真实完成时间(正态分布)
		vm.setReadyTime(finishTimeWithConfidency); //由该VM上任务近似完成时间来表示准备时间
		vm.getWTaskList().add(task); //把当前需要调度的任务加入VM完成任务的列表
		
		if(vm.getExecutingWTask().getTaskId().equals("initial"))
		{
			vm.setExecutingWTask(task);
		}
		else
		{
			vm.setWaitingWTask(task);
			if(task.getRealStartTime() < vm.getExecutingWTask().getRealFinishTime())
				System.out.println("等待任务的开始时间 < 执行任务完成时间！in allocateReadyWTaskToSaaSVm()");
		}
		
	}/*end: allocateReadyWTaskToSaaSVm*/
	
	/*======================================================================================================*/
	
	/**分配任务到新租用的VM上*/
	public void allocateReadyWTaskToNewLeasedVm(WTask task, SaaSVm vm, int realDataArrival)
	{

		int vmRealReadyTime = vm.getReadyTime(); //用当前VM初始化后的时间作VM实际就绪时间
		int vmReadyTimeWC = vm.getReadyTime(); //用当前VM初始化后的时间作VM近似就绪时间
		int readyStartTime = realDataArrival; //获取任务数据就绪时间做实际可开始时间
		int readyStartTimeWC = realDataArrival; //获取任务数据就绪时间做近似可开始时间
				
		
		//获取VM就绪和任务就绪之间的最大
		if(vmRealReadyTime > readyStartTime) 
		{
			readyStartTime = vmRealReadyTime; //实际可开始时间
		}
		if(vmReadyTimeWC > readyStartTimeWC)
		{
			readyStartTimeWC = vmReadyTimeWC; //近似可开始时间
		}
		
		/*更新任务scheduleTask的状态*/
		
		//当前需要调度的任务的真实执行时间（由正态分布产生的基本执行时长）
		int realExecutionTime = (int)(task.getRealBaseExecutionTime()*vm.getVmFactor());
		int realFinishTime = readyStartTime + realExecutionTime;
		
		//分配当前需要调度的任务到VM上得到的近似执行时间和近似完成时间
		int executionTimeWithConfidency = (int)(task.getExecutionTimeWithConfidency()*vm.getVmFactor());
		int finishTimeWithConfidency = readyStartTimeWC + executionTimeWithConfidency;
		
		task.setAllocatedFlag(true); //标记已分配
		task.setAllocateVm(vm);  //设定分配的VM
		
		task.setRealStartTime(readyStartTime);//设定真实开始时间
		task.setRealExecutionTime(realExecutionTime); //设定真实执行时间(正态分布)
		task.setRealFinishTime(realFinishTime); //设定真实完成时间
		
		task.setStartTimeWithConfidency(readyStartTimeWC); //设定近似开始时间
		task.setFinishTimeWithConfidency(finishTimeWithConfidency); //设定近似完成时间
							
		/*更新虚拟机vm的状态*/
		vm.setFinishTime(finishTimeWithConfidency); //设定近似完成时间
		vm.setRealFinishTime(realFinishTime); //设定真实完成时间(正态分布)
		vm.setReadyTime(finishTimeWithConfidency); //由该VM上任务近似完成时间来表示准备时间
		vm.getWTaskList().add(task); //把当前需要调度的任务加入VM完成任务的列表
		
		if(vm.getExecutingWTask().getTaskId().equals("initial"))
		{
			vm.setExecutingWTask(task);
		}
		else
		{
			vm.setWaitingWTask(task);
			if(task.getRealStartTime() < vm.getExecutingWTask().getRealFinishTime())
				System.out.println("等待任务的开始时间 < 执行任务完成时间！in allocateReadyWTaskToSaaSVm()");
		}

	}
	
	/*======================================================================================================*/
	
	/**确定增加虚拟机的类型*/
	public int determineSaaSVmType(WTask task , int startTime)
	{
		int level = 0;
		
		int grade6 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_7.getFactor());
		int grade5 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_6.getFactor());
		int grade4 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_5.getFactor());
		int grade3 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_4.getFactor());
		int grade2 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_3.getFactor());
		int grade1 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_2.getFactor());
		int grade0 = startTime + (int)(task.getExecutionTimeWithConfidency()*VmParameter.Type_1.getFactor());
		
		if(grade6 <= task.getSubDeadLine())
		{
			level = 6;
		}
		else if(grade5 <= task.getSubDeadLine())
		{
			level = 5;
		}
		else if(grade4 <= task.getSubDeadLine())
		{
			level = 4;
		}
		else if(grade3 <= task.getSubDeadLine())
		{
			level = 3;
		}
		else if(grade2 <= task.getSubDeadLine())
		{
			level = 2;
		}
		else if(grade1 <= task.getSubDeadLine())
		{
			level = 1;
		}
		else if(grade0 <= task.getSubDeadLine())
		{
			level = 0;
		}
		else  //if(task.getSubDeadLine()>= task.getLeastFinishTime())
		{
			level = 0;
		}
/*		else
		{
			level = -1;
		}
*/
		return level;
	}
	
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
		getWorkflowList().addAll(list);//把所有的工作流都添加到了workflowList
	}
	
	/**获取工作流，返回一个工作流列表，成员变量workflowList*/
	public List<Workflow> getWorkflowList() {return workflowList; }
	
	/*=====================================================================================================*/
	
	public class AscendingWTaskByPECT implements Comparator<WTask>
	{
		public int compare(WTask task1, WTask task2) 
		{
			return (task1.getEarliestFinishTime() - task2.getEarliestFinishTime());
		}

	}

}
