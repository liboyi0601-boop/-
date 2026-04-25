package ScheduleAgorithm;

public final class ConstraintAwareSampleWeighter
{
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
		return WeightResult.fallback("unsupported-vm-risk-weight-mode");
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
		return new WeightResult(weight, boundedRisk, false, null);
	}

	private double maxRisk(double first, double second, double third)
	{
		return maxRisk(new double[] {first, second, third});
	}

	private double maxRisk(double first, double second, double third, double fourth)
	{
		return maxRisk(new double[] {first, second, third, fourth});
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

		private WeightResult(double sampleWeight, double riskScore, boolean fallback, String fallbackReason)
		{
			this.sampleWeight = sampleWeight;
			this.riskScore = riskScore;
			this.fallback = fallback;
			this.fallbackReason = fallbackReason;
		}

		private static WeightResult unweighted()
		{
			return new WeightResult(1.0, 0.0, false, null);
		}

		private static WeightResult fallback(String fallbackReason)
		{
			return new WeightResult(1.0, 0.0, true, fallbackReason);
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
	}
}
