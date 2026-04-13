package main;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import share.StaticfinalTags;
import workflow.Workflow;
import ScheduleAgorithm.ROSA_Algorithms;
import ScheduleAgorithm.NOSF_Algorithms;
import ScheduleAgorithm.IC_PCPD2_Algorithms;





/**
 * @author ljg修改补充
 * ExperimentPlatform类实现将实验工作流集合提交到各个算法的调度器中
 * */

public class ExperimentPlatform 
{
	private static List<Workflow> workflowList; //工作流队列
	private static int workflowNum = 0; //实验中工作流的数量
	
	public static void main(String[] args) throws Exception 
	{
		workflowNum = StaticfinalTags.workflowNum; //获取工作流数目
		workflowList = getWorkflowListFromFile("ExperimentalWorkflow.dat");//获取工作流测试集
		
/*		测试代码		
 		for(Workflow flow: workflowList)
		{
			System.out.println("WorkflowId: "+flow.getWorkflowId()+
					" | workflowName:"+flow.getWorkflowName()+
					" | arrival:"+flow.getArrivalTime()+
					" | MakeSpan:"+flow.getMakespan()+
					" | deadline:"+flow.getDeadline());
		}
*/
		//ArrivalTimeTest(workflowList);
		
		if(StaticfinalTags.choose == 0)
		{
			NOSF_Algorithms algorithm = new NOSF_Algorithms();
			algorithm.submitWorkflowList(workflowList); //提交工作流到调度中心
			algorithm.ScheduleWorkflow_By_NOSF(); //调度算法的实现	
			
			workflowList.clear();
			Runtime.getRuntime().gc();
		}
		else if(StaticfinalTags.choose == 1)
		{
			ROSA_Algorithms algorithm = new ROSA_Algorithms();
			algorithm.submitWorkflowList(workflowList); //提交工作流到调度中心
			algorithm.ScheduleWorkflow_By_ROSA(); //调度算法的实现
			
			workflowList.clear();
			Runtime.getRuntime().gc();
		}else if(StaticfinalTags.choose == 2)
		{
			IC_PCPD2_Algorithms algorithm = new IC_PCPD2_Algorithms();
			algorithm.submitWorkflowList(workflowList); //提交工作流到调度中心
			algorithm.ScheduleWorkflow_By_IC_PCPD2(); //调度算法的实现	
			
			workflowList.clear();
			Runtime.getRuntime().gc();
		}										
	}
	
	/*=========================================================================================*/
	
	/**测试工作流的到达时间间隔，最大到达时间，最大Makespan*/
	private static void ArrivalTimeTest(List<Workflow> wfList)
	{
		int maxArrivalTime = 0;
		int maxMakespan = 0;
		String workflowName = null;
		int k = 0;
		List<Integer> numList = new ArrayList<Integer>();
		for(Workflow workflow: wfList)
		{
			if(workflow.getArrivalTime() > maxArrivalTime) //获得最大的到达时间
			{
				maxArrivalTime = workflow.getArrivalTime();
			}
			if(workflow.getMakespan() > maxMakespan) //获得最大的MakeSpan和对应的工作流名
			{
				maxMakespan = workflow.getMakespan();
				workflowName = workflow.getWorkflowName();
			}
			
			if(k == 0)
			{
				k++;
				continue;
			}
			else
			{	//获得两个相邻到达的工作流的到达时间差			
				System.out.print((wfList.get(k).getArrivalTime()-wfList.get(k-1).getArrivalTime())+" ");
				numList.add(wfList.get(k).getArrivalTime()-wfList.get(k-1).getArrivalTime());
				k++;
				if(k%20==0) System.out.println();
			}
		}
		
		System.out.println();
		int totalNum = 0;
		for(int num: numList)
		{
			totalNum = totalNum + num; //把到达的时间差累加
		}
		System.out.println("工作流之间的平均到达时间差: "+totalNum/numList.size()); //工作流之间的平均到达时间差
		System.out.println("maxArrivalTime: "+maxArrivalTime+" maxMakespan: "+maxMakespan+" workflowName: "+workflowName);

	}
	
	/*=======================================================================================*/
	
	/**获取工作流测试集
	 * @throws IOException 
	 * @throws ClassNotFoundException */
	public static List<Workflow> getWorkflowListFromFile(String filename) throws IOException, ClassNotFoundException
	{
		List<Workflow> w_List = new ArrayList<Workflow>();
		Workflow w = null;
		FileInputStream fi = new FileInputStream(filename);
		ObjectInputStream si = new ObjectInputStream(fi);
		try
		{
			for(int i=0; i<workflowNum; i++)
			{
				w = (Workflow)si.readObject();
				w_List.add(w);
			}			
			si.close();
		}catch(IOException e){System.out.println(e.getMessage());}		
		return w_List;
	}

}
