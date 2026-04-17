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
 * @author ljg�޸Ĳ���
 * ExperimentPlatform��ʵ�ֽ�ʵ�鹤���������ύ�������㷨�ĵ�������
 * */

public class ExperimentPlatform 
{
	private static List<Workflow> workflowList; //����������
	private static int workflowNum = 0; //ʵ���й�����������
	
	public static void main(String[] args) throws Exception 
	{
		workflowNum = StaticfinalTags.workflowNum; //��ȡ��������Ŀ
		workflowList = getWorkflowListFromFile("ExperimentalWorkflow.dat");//��ȡ���������Լ�
		
/*		���Դ���		
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
			algorithm.submitWorkflowList(workflowList); //�ύ����������������
			algorithm.ScheduleWorkflow_By_NOSF(); //�����㷨��ʵ��	
			
			workflowList.clear();
			Runtime.getRuntime().gc();
		}
		else if(StaticfinalTags.choose == 1)
		{
			ROSA_Algorithms algorithm = new ROSA_Algorithms();
			algorithm.submitWorkflowList(workflowList); //�ύ����������������
			algorithm.ScheduleWorkflow_By_ROSA(); //�����㷨��ʵ��
			
			workflowList.clear();
			Runtime.getRuntime().gc();
		}else if(StaticfinalTags.choose == 2)
		{
			IC_PCPD2_Algorithms algorithm = new IC_PCPD2_Algorithms();
			algorithm.submitWorkflowList(workflowList); //�ύ����������������
			algorithm.ScheduleWorkflow_By_IC_PCPD2(); //�����㷨��ʵ��	
			
			workflowList.clear();
			Runtime.getRuntime().gc();
		}										
	}
	
	/*=========================================================================================*/
	
	/**���Թ������ĵ���ʱ��������󵽴�ʱ�䣬���Makespan*/
	private static void ArrivalTimeTest(List<Workflow> wfList)
	{
		int maxArrivalTime = 0;
		int maxMakespan = 0;
		String workflowName = null;
		int k = 0;
		List<Integer> numList = new ArrayList<Integer>();
		for(Workflow workflow: wfList)
		{
			if(workflow.getArrivalTime() > maxArrivalTime) //������ĵ���ʱ��
			{
				maxArrivalTime = workflow.getArrivalTime();
			}
			if(workflow.getMakespan() > maxMakespan) //�������MakeSpan�Ͷ�Ӧ�Ĺ�������
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
			{	//����������ڵ���Ĺ������ĵ���ʱ���			
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
			totalNum = totalNum + num; //�ѵ����ʱ����ۼ�
		}
		System.out.println("������֮���ƽ������ʱ���: "+totalNum/numList.size()); //������֮���ƽ������ʱ���
		System.out.println("maxArrivalTime: "+maxArrivalTime+" maxMakespan: "+maxMakespan+" workflowName: "+workflowName);

	}
	
	/*=======================================================================================*/
	
	/**��ȡ���������Լ�
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
