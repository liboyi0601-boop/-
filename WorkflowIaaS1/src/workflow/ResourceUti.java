package workflow;


public class ResourceUti 
{
	public ResourceUti(){}
	
	public double getResourceUtilization(String taskType)
	{
		if(taskType.equals("mProjectPP")){return 0.8696;}//Montage
		else if(taskType.equals("mDiffFit")){return 0.2839;}
		else if(taskType.equals("mConcatFit")){return 0.5317;}
		else if(taskType.equals("mBgModel")){return 0.9989;}
		else if(taskType.equals("mBackground")){return 0.0846;}
		else if(taskType.equals("mImgTbl")){return 0.0348;}
		else if(taskType.equals("mAdd")){return 0.0848;}
		else if(taskType.equals("mShrink")){return 0.0230;}
		else if(taskType.equals("mJPEG")){return 0.7714;}//Montage
		//================================================
		else if(taskType.equals("ExtractSGT")){return 0.6582;}//CyberShake
		else if(taskType.equals("SeismogramSynthesis")){return 0.9201;}
		else if(taskType.equals("ZipSeis")){return 0.0683;}
		else if(taskType.equals("PeakValCalcOkaya")){return 0.1689;}
		else if(taskType.equals("ZipPSA")){return 0.0289;}//CyberShake
		//================================================
		else if(taskType.equals("fastq2bfq_chr21")){return 0.8842;} //epigenomics
		else if(taskType.equals("pileup_chr21")){return 0.5343;}
		else if(taskType.equals("mapMerge_chr21")){return 0.9508;}
		else if(taskType.equals("map_chr21")){return 0.9669;}
		else if(taskType.equals("sol2sanger_chr21")){return 0.6517;}
		else if(taskType.equals("filterContams_chr21")){return 0.8854;}
		else if(taskType.equals("maqindex_chr21")){return 0.9950;}
		else if(taskType.equals("fastqSplit_chr21")){return 0.2241;}//epigenomics
		//=============================================================
		else if(taskType.equals("TmpltBank")){return 0.9894;} //LIGO Inspiral
		else if(taskType.equals("Inspiral")){return 0.8996;}
		else if(taskType.equals("Thinca")){return 0.4390;}
		else if(taskType.equals("Inca")){return 0.3793;}
		else if(taskType.equals("Data_Find")){return 0.5555;}
		else if(taskType.equals("Inspinj")){return 0.0832;}
		else if(taskType.equals("TrigBank")){return 0.1744;}
		else if(taskType.equals("Sire")){return 0.1415;}
		else if(taskType.equals("Coire")){return 0.080;}  //LIGO Inspiral
		//=============================================================
		else if(taskType.equals("Patser")){return 0.8348;}  //SIPHT
		else if(taskType.equals("Patser_concate")){return 0.1889;}
		else if(taskType.equals("Transterm")){return 0.9479;}
		else if(taskType.equals("Findterm")){return 0.9520;}
		else if(taskType.equals("RNAMotif")){return 0.9505;}
		else if(taskType.equals("Blast")){return 0.9387;}
		else if(taskType.equals("SRNA")){return 0.9348;}
		else if(taskType.equals("FFN_Parse")){return 0.8109;}
		else if(taskType.equals("Blast_synteny")){return 0.6101;}
		else if(taskType.equals("Blast_candidate")){return 0.4361;}
		else if(taskType.equals("Blast_QRNA")){return 0.8780;}
		else if(taskType.equals("Blast_paralogues")){return 0.4430;}
		else if(taskType.equals("SRNA_annotate")){return 0.5596;}//SIPHT
		//==============================================================		
		else
		{//找不到对应的任务类型
			throw new IllegalArgumentException("Task type (i.e., "+taskType+") is not initiated!");
		}
	}
}