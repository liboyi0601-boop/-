package ScheduleAgorithm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConstraintAwareSampleWeighter
{
	public static final String COMPONENT_SLACK_RISK = "slackRisk";
	public static final String COMPONENT_VIOLATION_RISK = "violationRisk";
	public static final String COMPONENT_CRITICAL_PATH_RISK = "criticalPathRisk";
	public static final String COMPONENT_LATENESS_RISK = "latenessRisk";
	public static final String COMPONENT_INFEASIBLE_RISK = "infeasibleRisk";
	public static final String COMPONENT_COMBINED_RISK_SCORE = "combinedRiskScore";

	private final ConstraintAwareWeightingConfig config;

	public ConstraintAwareSampleWeighter(ConstraintAwareWeightingConfig config)
	{
		this.config = config == null ? ConstraintAwareWeightingConfig.disabled() : config;
	}

	public WeightResult weightTask(MaskedDecisionExample example)
	{
		if(!config.isWeightedLossEnabled())
		{
			return WeightResult.unweighted();
		}
		double[] features = chosenFeatures(example);
		if(features == null)
		{
			return WeightResult.fallback("missing-or-invalid-chosen-task-features");
		}

		if(ConstraintAwareWeightingConfig.MODE_LOW_SLACK.equals(config.getRiskWeightMode()))
		{
			return weightFromRisk(maxRisk(
					lowSlackRisk(features, TaskFeatureExtractor.IDX_WORKFLOW_NORMALIZED_SLACK),
					violationRisk(features, TaskFeatureExtractor.IDX_VIOLATION_RISK),
					lowSlackRisk(features, TaskFeatureExtractor.IDX_CRITICAL_PATH_SLACK)));
		}
		if(ConstraintAwareWeightingConfig.MODE_DEADLINE_RISK.equals(config.getRiskWeightMode()))
		{
			return weightFromRisk(maxRisk(
					lowSlackRisk(features, TaskFeatureExtractor.IDX_WORKFLOW_NORMALIZED_SLACK),
					violationRisk(features, TaskFeatureExtractor.IDX_VIOLATION_RISK),
					lowSlackRisk(features, TaskFeatureExtractor.IDX_CRITICAL_PATH_SLACK),
					remainingCriticalPathRisk(features, TaskFeatureExtractor.IDX_REMAINING_CRITICAL_PATH)));
		}
		if(ConstraintAwareWeightingConfig.MODE_SEVERITY_AWARE.equals(config.getRiskWeightMode()))
		{
			return weightTaskSeverityAware(features);
		}
		return WeightResult.fallback("unsupported-task-risk-weight-mode");
	}

	public WeightResult weightVm(MaskedDecisionExample example)
	{
		if(!config.isWeightedLossEnabled())
		{
			return WeightResult.unweighted();
		}
		double[] features = chosenFeatures(example);
		if(features == null)
		{
			return WeightResult.fallback("missing-or-invalid-chosen-vm-features");
		}

		if(ConstraintAwareWeightingConfig.MODE_LOW_SLACK.equals(config.getRiskWeightMode())
				|| ConstraintAwareWeightingConfig.MODE_DEADLINE_RISK.equals(config.getRiskWeightMode()))
		{
			return weightFromRisk(maxRisk(
					infeasibleRisk(features, VmFeatureExtractor.IDX_FEASIBLE_UNDER_SUB_DEADLINE),
					lowSlackRisk(features, VmFeatureExtractor.IDX_WORKFLOW_NORMALIZED_SLACK),
					violationRisk(features, VmFeatureExtractor.IDX_VIOLATION_RISK),
					lowSlackRisk(features, VmFeatureExtractor.IDX_CRITICAL_PATH_SLACK)));
		}
		if(ConstraintAwareWeightingConfig.MODE_SEVERITY_AWARE.equals(config.getRiskWeightMode()))
		{
			return weightVmSeverityAware(features);
		}
		return WeightResult.fallback("unsupported-vm-risk-weight-mode");
	}

	private WeightResult weightTaskSeverityAware(double[] features)
	{
		double slackRisk = lowSlackRisk(features, TaskFeatureExtractor.IDX_WORKFLOW_NORMALIZED_SLACK);
		double violationRisk = violationRisk(features, TaskFeatureExtractor.IDX_VIOLATION_RISK);
		double criticalPathRisk = lowSlackRisk(features, TaskFeatureExtractor.IDX_CRITICAL_PATH_SLACK);
		double latenessRisk = latenessRisk(features, TaskFeatureExtractor.IDX_EARLIEST_FINISH,
				TaskFeatureExtractor.IDX_SUB_DEADLINE);
		if(!allFinite(slackRisk, violationRisk, criticalPathRisk, latenessRisk))
		{
			return WeightResult.fallback("non-finite-severity-aware-task-components");
		}

		double riskScore = clamp01(
				ConstraintAwareWeightingConfig.SEVERITY_TASK_SLACK_WEIGHT * clamp01(slackRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_TASK_VIOLATION_WEIGHT * clamp01(violationRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_TASK_CRITICAL_PATH_WEIGHT * clamp01(criticalPathRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_TASK_LATENESS_WEIGHT * clamp01(latenessRisk));
		Map<String, Double> components = new LinkedHashMap<String, Double>();
		components.put(COMPONENT_SLACK_RISK, Double.valueOf(clamp01(slackRisk)));
		components.put(COMPONENT_VIOLATION_RISK, Double.valueOf(clamp01(violationRisk)));
		components.put(COMPONENT_CRITICAL_PATH_RISK, Double.valueOf(clamp01(criticalPathRisk)));
		components.put(COMPONENT_LATENESS_RISK, Double.valueOf(clamp01(latenessRisk)));
		components.put(COMPONENT_COMBINED_RISK_SCORE, Double.valueOf(riskScore));
		return weightFromRisk(riskScore, components);
	}

	private WeightResult weightVmSeverityAware(double[] features)
	{
		double infeasibleRisk = infeasibleRisk(features, VmFeatureExtractor.IDX_FEASIBLE_UNDER_SUB_DEADLINE);
		double slackRisk = lowSlackRisk(features, VmFeatureExtractor.IDX_WORKFLOW_NORMALIZED_SLACK);
		double violationRisk = violationRisk(features, VmFeatureExtractor.IDX_VIOLATION_RISK);
		double criticalPathRisk = lowSlackRisk(features, VmFeatureExtractor.IDX_CRITICAL_PATH_SLACK);
		double latenessRisk = latenessRisk(features, VmFeatureExtractor.IDX_ESTIMATED_FINISH_IF_ASSIGNED,
				VmFeatureExtractor.IDX_SELECTED_TASK_SUB_DEADLINE);
		if(!allFinite(infeasibleRisk, slackRisk, violationRisk, criticalPathRisk, latenessRisk))
		{
			return WeightResult.fallback("non-finite-severity-aware-vm-components");
		}

		double riskScore = clamp01(
				ConstraintAwareWeightingConfig.SEVERITY_VM_INFEASIBLE_WEIGHT * clamp01(infeasibleRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_VM_SLACK_WEIGHT * clamp01(slackRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_VM_VIOLATION_WEIGHT * clamp01(violationRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_VM_CRITICAL_PATH_WEIGHT * clamp01(criticalPathRisk)
				+ ConstraintAwareWeightingConfig.SEVERITY_VM_LATENESS_WEIGHT * clamp01(latenessRisk));
		Map<String, Double> components = new LinkedHashMap<String, Double>();
		components.put(COMPONENT_INFEASIBLE_RISK, Double.valueOf(clamp01(infeasibleRisk)));
		components.put(COMPONENT_SLACK_RISK, Double.valueOf(clamp01(slackRisk)));
		components.put(COMPONENT_VIOLATION_RISK, Double.valueOf(clamp01(violationRisk)));
		components.put(COMPONENT_CRITICAL_PATH_RISK, Double.valueOf(clamp01(criticalPathRisk)));
		components.put(COMPONENT_LATENESS_RISK, Double.valueOf(clamp01(latenessRisk)));
		components.put(COMPONENT_COMBINED_RISK_SCORE, Double.valueOf(riskScore));
		return weightFromRisk(riskScore, components);
	}

	private double[] chosenFeatures(MaskedDecisionExample example)
	{
		if(example == null || example.size() == 0 || !example.isValid(example.getChosenIndex()))
		{
			return null;
		}
		return example.getCandidateFeatures(example.getChosenIndex());
	}

	private WeightResult weightFromRisk(double riskScore)
	{
		return weightFromRisk(riskScore, Collections.<String, Double>emptyMap());
	}

	private WeightResult weightFromRisk(double riskScore, Map<String, Double> riskComponents)
	{
		if(!Double.isFinite(riskScore))
		{
			return WeightResult.fallback("non-finite-risk-score");
		}
		double boundedRisk = clamp01(riskScore);
		double weight = 1.0 + config.getRiskWeightScale() * boundedRisk;
		weight = Math.max(1.0, Math.min(config.getMaxSampleWeight(), weight));
		if(!Double.isFinite(weight))
		{
			return WeightResult.fallback("non-finite-sample-weight");
		}
		return new WeightResult(weight, boundedRisk, false, null, riskComponents);
	}

	private double maxRisk(double first, double second, double third)
	{
		return maxRisk(new double[] {first, second, third});
	}

	private double maxRisk(double first, double second, double third, double fourth)
	{
		return maxRisk(new double[] {first, second, third, fourth});
	}

	private double maxRisk(double first, double second, double third, double fourth, double fifth)
	{
		return maxRisk(new double[] {first, second, third, fourth, fifth});
	}

	private double maxRisk(double[] values)
	{
		double max = 0.0;
		for(double value: values)
		{
			if(!Double.isFinite(value))
			{
				return Double.NaN;
			}
			max = Math.max(max, clamp01(value));
		}
		return max;
	}

	private double lowSlackRisk(double[] features, int index)
	{
		Double value = featureValue(features, index);
		return value == null ? Double.NaN : 1.0 - clamp01(value.doubleValue());
	}

	private double violationRisk(double[] features, int index)
	{
		Double value = featureValue(features, index);
		return value == null ? Double.NaN : clamp01(value.doubleValue());
	}

	private double remainingCriticalPathRisk(double[] features, int index)
	{
		Double value = featureValue(features, index);
		return value == null ? Double.NaN : clamp01(value.doubleValue());
	}

	private double infeasibleRisk(double[] features, int index)
	{
		Double value = featureValue(features, index);
		if(value == null)
		{
			return Double.NaN;
		}
		return value.doubleValue() >= 0.5 ? 0.0 : 1.0;
	}

	private double latenessRisk(double[] features, int finishIndex, int deadlineIndex)
	{
		Double finishValue = featureValue(features, finishIndex);
		Double deadlineValue = featureValue(features, deadlineIndex);
		if(finishValue == null || deadlineValue == null)
		{
			return Double.NaN;
		}
		return clamp01(Math.max(0.0, finishValue.doubleValue() - deadlineValue.doubleValue()));
	}

	private boolean allFinite(double... values)
	{
		for(double value: values)
		{
			if(!Double.isFinite(value))
			{
				return false;
			}
		}
		return true;
	}

	private Double featureValue(double[] features, int index)
	{
		if(features == null || index < 0 || index >= features.length || !Double.isFinite(features[index]))
		{
			return null;
		}
		return Double.valueOf(features[index]);
	}

	private double clamp01(double value)
	{
		if(value < 0.0)
		{
			return 0.0;
		}
		if(value > 1.0)
		{
			return 1.0;
		}
		return value;
	}

	public static final class WeightResult
	{
		private final double sampleWeight;
		private final double riskScore;
		private final boolean fallback;
		private final String fallbackReason;
		private final Map<String, Double> riskComponents;

		private WeightResult(double sampleWeight, double riskScore, boolean fallback, String fallbackReason,
				Map<String, Double> riskComponents)
		{
			this.sampleWeight = sampleWeight;
			this.riskScore = riskScore;
			this.fallback = fallback;
			this.fallbackReason = fallbackReason;
			this.riskComponents = riskComponents == null || riskComponents.isEmpty()
					? Collections.<String, Double>emptyMap()
					: Collections.unmodifiableMap(new LinkedHashMap<String, Double>(riskComponents));
		}

		private static WeightResult unweighted()
		{
			return new WeightResult(1.0, 0.0, false, null, Collections.<String, Double>emptyMap());
		}

		private static WeightResult fallback(String fallbackReason)
		{
			return new WeightResult(1.0, 0.0, true, fallbackReason, Collections.<String, Double>emptyMap());
		}

		public double getSampleWeight()
		{
			return sampleWeight;
		}

		public double getRiskScore()
		{
			return riskScore;
		}

		public boolean isFallback()
		{
			return fallback;
		}

		public String getFallbackReason()
		{
			return fallbackReason;
		}

		public Map<String, Double> getRiskComponents()
		{
			return riskComponents;
		}
	}
}
