package workflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import share.StaticfinalTags;

/**
 * @author ljg 修改补充完毕，2017.2.10
 * 
 * 工作流的XML文件中:
 * job元素记录节点信息，任务执行时长(s)，uses子元素记录与该job节点相关联的input和output文件传输数据量(k)
 *    input表示由一个文件系统传入该job节点的数据量
 *    output表示由该job节点传输到一个文件系统的数据量
 * child元素记录带有父任务的节点信息，ref属性表示带有父任务的节点ID
 *     parent元素记录父任务，ref属性表示父任务的节点ID
 * 注意：一个父任务与一个子任务之间可能存在多个文件系统传输的link，因此计算任务间传输数据量时要累加所有传输量
 * */

public class ReadWorkflowFile {

	private static List<TempWorkflow> workflowList;
	
	public static void main(String[] args) throws DocumentException, IOException, ClassNotFoundException
	{
		workflowList = new ArrayList<TempWorkflow>();
/*		String wfFile[] = {"CyberShake_30.xml", "CyberShake_50.xml", "CyberShake_100.xml", "CyberShake_1000.xml",
				"Epigenomics_24.xml", "Epigenomics_46.xml", "Epigenomics_100.xml", "Epigenomics_997.xml", 
				"Inspiral_30.xml", "Inspiral_50.xml", "Inspiral_100.xml", "Inspiral_1000.xml",
				"Montage_25.xml", "Montage_50.xml", "Montage_100.xml", "Montage_1000.xml", 
				"Sipht_30.xml", "Sipht_60.xml", "Sipht_100.xml", "Sipht_1000.xml"};
*/

/*		String wfFile[] = {"CyberShake_1000.xml", "Epigenomics_997.xml", "Inspiral_1000.xml",
				"Montage_1000.xml", "Sipht_1000.xml"};
*/		
		
		String wfFile[] = {"CyberShake_30.xml", "CyberShake_50.xml", "CyberShake_100.xml",
				"Epigenomics_24.xml", "Epigenomics_46.xml", "Epigenomics_100.xml",
				"Inspiral_30.xml", "Inspiral_50.xml", "Inspiral_100.xml", 
				"Montage_25.xml", "Montage_50.xml", "Montage_100.xml",  
				"Sipht_30.xml", "Sipht_60.xml", "Sipht_100.xml"};

		
		StaticfinalTags.workflowTemplateNum = wfFile.length;
		
		for(int aa=0; aa < StaticfinalTags.workflowTemplateNum; aa++)
		{
			String workFlowName = wfFile[aa];
		
			System.out.println("Starting read "+workFlowName);
		
			SAXReader reader = new SAXReader();		
			Document document = reader.read(new File("XML Example/"+workFlowName));		
			Element root = document.getRootElement();		
			List taskList = root.elements("job");//获得元素job列表
		
			List<TempWTask> tempTaskList = new ArrayList<TempWTask>();			
			ResourceUti readUtiByTaskType = new ResourceUti(); //不同类型任务的资源利用率写在该类中
		
			for(int i=0; i<taskList.size(); i++) 		
			{//读取每个任务的Id、执行时间、资源利用率			
				Element task = (Element) taskList.get(i);
				String taskID = task.attributeValue("id");
				String stringTime = task.attributeValue("runtime");			
				double runTime = Double.parseDouble(stringTime); //将任务执行时间转化为double
				String type = task.attributeValue("name"); //获取任务的类型
				//System.out.println(taskID+" "+runTime+" "+type);
				double utilization = readUtiByTaskType.getResourceUtilization(type);						
				TempWTask tempTask = new TempWTask(taskID, -1, runTime, utilization);			
				tempTaskList.add(tempTask);		
			}//已经对工作流中所有任务进行读取
			System.out.println("工作流："+workFlowName+"的任务读取完毕");
		
			/**元素名为"child"的列表给出带有父节点的任务信息，ref表示带有父节点的任务ID*/
			List parentList = root.elements("child"); //获得元素child列表
		
			/**确定child元素的每个parent元素的ID和link为input的数据传输量*/
			for(int i=0; i<parentList.size(); i++)
			{//确定每个任务的前驱任务
				Element parents = (Element)parentList.get(i);	//获得当前元素child		
				String taskId = parents.attributeValue("ref"); //带有父节点的任务的ID，获得当前元素child的任务id	
				//System.out.println("查找节点："+taskId+"的input文件：");					
			
				//用来记录与当前child元素相关联的输入文件项值
				List<String> inputFileList = new ArrayList<String>(); //记录读取数据的文件的名字
			
				/**遍历元素名为job的列表，找出对应child节点的所有输入link的文件系统file值*/
				for(int ii=0; ii<taskList.size(); ii++)			
				{//在任务列表找出这个带有父节点的任务				
					Element tempTask = (Element) taskList.get(ii);	//获得元素job			
					String tempTaskID = tempTask.attributeValue("id");	//获得元素job的id
				
					if(tempTaskID.equals(taskId)) //当job中的任务ID等于Child中的任务ID时，job中的这个任务就是带有父节点的任务				
					{//在job中找到Id为taskId的任务					
					
						/**使用迭代器遍历job的子元素uses列表，uses中属性link为input的项记录了所有与job节点相关联的输入边*/
						for(Iterator iter = tempTask.elementIterator(); iter.hasNext();)					
						{	//寻找对应的全部输入文件系统名				
							Element data = (Element) iter.next();//获得元素uses						
							String in = data.attributeValue("link");						
							if(in.equals("input"))	//寻找link值等于input的file名					
							{							
								String parentId = data.attributeValue("file"); //获得输入文件系统名
								//System.out.println(parentId);
								inputFileList.add(parentId); //保存输入文件系统的file值						
							}					
						}
						//System.out.println("节点"+taskId+"的input文件名搜索完毕");
						break; //找到该任务，则不再往后找			
					}											
				}//找出child元素节点所有输入数据的文件系统名称
			
				/**使用迭代器遍历当前第i个child元素的子元素parent列表，parent列表记录了child节点的所有父节点ID*/
				for(Iterator<Element> iter = parents.elementIterator(); iter.hasNext();)
				{//遍历ID为taskId的任务的所有父节点
					Element parent = (Element) iter.next();	//获得第i个child元素下面的当前子元素parent			
					String parentId = parent.attributeValue("ref");	//获得当前的子元素parent的ref							
					int dataSize = 0; //从parentId输入的数据大小，用来记录当前child元素的从一个给定父节点（parentID）输入数据的大小
					//System.out.println("搜索节点"+taskId+"的父节点"+parentId+"的output文件:");
				
					/**根据第i个child元素的当前的"parent"，遍历job列表找对应"parent"的id*/
					for(int ii=0; ii<taskList.size(); ii++) 
					{//找出父节点传入的数据大小
						Element tempTask = (Element) taskList.get(ii);//获得元素job					
						String tempTaskID = tempTask.attributeValue("id");//获得元素job的id
					
						/**当job中的id等于child的当前子列表parent中ref记录的id时，job中找到的id就是child中ref节点的一个父任务*/
						if(tempTaskID.equals(parentId)) 
						{//在job中找到id为taskId的任务的父任务
						
							/**遍历这一个父节点元素job的子元素uses列表找出link项为output的文件系统file值*/
							for(Iterator tempIter = tempTask.elementIterator(); tempIter.hasNext();)
							{
								Element data = (Element) tempIter.next();//获得元素uses							
								String out = data.attributeValue("link");
								if(out.equals("output"))
								{
									String outputFile = data.attributeValue("file");
	
									/**遍历输入文件列表，查找和输出文件值相等的项
									 * 注意：一定要把同一父节点对同一子节点的多个output的数据累加！*/
									for(String inputFile: inputFileList)								
									{									
										if(inputFile.equals(outputFile))
										{	
											int addData = (int)Double.parseDouble(data.attributeValue("size"));
											dataSize = dataSize + addData;
											//System.out.println(parentId+"的output传输数据是"+dataSize);
										
										}								
									}
								}
							}
							break; //处理完该任务，则跳到下一个任务
						}
					} //确定了数据传输量的大小
				
					if(dataSize == 0)
					{
						throw new IllegalArgumentException("Data size is zero!");
					}
				
					//给对应节点增加一个约束				
					Constraint parentConstraint = new Constraint(parentId, dataSize);
					//System.out.println("任务:"+taskId+"的父任务"+parentId+"|数据传输量"+dataSize);
					for(TempWTask childTask: tempTaskList)				
					{					
						if(childTask.getTaskId().equals(taskId))					
						{						
							childTask.getParentTaskList().add(parentConstraint);
							break;
						}								
					}
				}/*end: for(Iterator<Element> iter = parents.elementIterator(); iter.hasNext();)*/
			}/*end: for(int i=0; i<parentList.size(); i++)
		   		所有父任务的约束确定完毕*/

			for(TempWTask childtask: tempTaskList)//对每个任务进行遍历		
			{//确定每个任务的子任务			
				for(Constraint tempConstrain: childtask.getParentTaskList())			
				{//遍历一个任务的所有父任务约束				
					for(TempWTask tempChildtask: tempTaskList)				
					{//找出任务childtask的父任务			
						if(tempChildtask.getTaskId().equals(tempConstrain.getTaskId()))					
						{						
							Constraint childConstraint = new Constraint(childtask.getTaskId(),tempConstrain.getDataSize());						
							tempChildtask.getSuccessorTaskList().add(childConstraint);						
							break;//找到，则跳出					
						}									
					}			
				}					
			}/*每个任务的子任务确定完毕*/
			System.out.println("工作流"+workFlowName+"的数据依赖关系处理完毕");
			
			//创建该工作流		
			TempWorkflow tempWorkflow = new TempWorkflow(workFlowName);		
			tempWorkflow.setTaskList(tempTaskList);				
			workflowList.add(tempWorkflow);

/*			//这段代码用作测试读取对象使用
			List<TempWTask> tempWTaskList = tempWorkflow.getTaskList();//模板工作流中的读出的任务集合
			for(TempWTask task: tempWTaskList)
			{
				String taskId = task.getTaskId(); //从模板中读出的任务ID
				int workflowId = -1; //初始化任务所在工作流的ID
				
				List<Constraint> tempParentList = new ArrayList<Constraint>();
				List<Constraint> tempChildList = new ArrayList<Constraint>();
				tempParentList = task.getParentTaskList();
				tempChildList = task.getSuccessorTaskList();
				
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
			
		}/*end: for(int aa=0; aa<15; aa++), 所有工作流文件读完*/
		
		
		//将工作流 写入文件workflow.txt中作为制作工作流的模板
		FileOutputStream fos = new FileOutputStream("WorkflowTemplateSet.dat"); 
		ObjectOutputStream os = new ObjectOutputStream(fos);
		try
		{
			for(TempWorkflow tempWf: workflowList)
			{
				os.writeObject(tempWf);
			}
			os.close();	
			System.out.println("工作流模板制作完毕，共"+StaticfinalTags.workflowTemplateNum+"个模板！");
		}catch(IOException e)
		{
			System.out.println("!!Error");
			System.out.println(e.getMessage()+" a");
		}

		
		//从模板文件Workflow.txt中读出模板工作流
		List<TempWorkflow> fworkflowList = new ArrayList<TempWorkflow>();
		TempWorkflow w = null;
		FileInputStream fi = new FileInputStream("WorkflowTemplateSet.dat");
		ObjectInputStream si = new ObjectInputStream(fi);
		try
		{
			for(int i=0; i<15; i++)
			{
				w = (TempWorkflow)si.readObject();
				fworkflowList.add(w);
			}			
			si.close();
		}
		catch(IOException e)
		{
			System.out.println(e.getMessage());
		}
											
		System.out.println();
				
	}/*end: void main(String[] args)*/

}
