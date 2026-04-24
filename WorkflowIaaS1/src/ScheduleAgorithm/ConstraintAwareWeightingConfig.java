package ScheduleAgorithm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConstraintAwareWeightingConfig
{
	public static final String MODE_NONE = "none";
	public static final String MODE_LOW_SLACK = "low-slack";
	public static final String MODE_DEADLINE_RISK = "deadline-risk";

	private final boolean enabled;
	private final String riskWeightMode;
	private final double riskWeightScale;
	private final double maxSampleWeight;

	private ConstraintAwareWeightingConfig(boolean enabled, String riskWeightMode,
			double riskWeightScale, double maxSampleWeight)
	{
		this.enabled = enabled;
		this.riskWeightMode = riskWeightMode;
		this.riskWeightScale = riskWeightScale;
		this.maxSampleWeight = maxSampleWeight;
	}

	public static ConstraintAwareWeightingConfig disabled()
	{
		return new ConstraintAwareWeightingConfig(false, MODE_NONE, 0.0, 1.0);
	}

	public static ConstraintAwareWeightingConfig create(boolean enabled, String riskWeightMode,
			double riskWeightScale, double maxSampleWeight)
	{
		String normalizedMode = riskWeightMode == null ? MODE_NONE : riskWeightMode;
		if(!isSupportedMode(normalizedMode))
		{
			throw new IllegalArgumentException("Unsupported risk weight mode: " + normalizedMode);
		}
		if(!Double.isFinite(riskWeightScale) || riskWeightScale < 0.0)
		{
			throw new IllegalArgumentException("riskWeightScale must be finite and non-negative: " + riskWeightScale);
		}
		if(!Double.isFinite(maxSampleWeight) || maxSampleWeight < 1.0)
		{
			throw new IllegalArgumentException("maxSampleWeight must be finite and >= 1.0: " + maxSampleWeight);
		}
		if(!enabled)
		{
			return disabled();
		}
		return new ConstraintAwareWeightingConfig(true, normalizedMode, riskWeightScale, maxSampleWeight);
	}

	public static boolean isSupportedMode(String mode)
	{
		return MODE_NONE.equals(mode) || MODE_LOW_SLACK.equals(mode) || MODE_DEADLINE_RISK.equals(mode);
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public boolean isWeightedLossEnabled()
	{
		return enabled && !MODE_NONE.equals(riskWeightMode)
				&& riskWeightScale > 0.0 && maxSampleWeight > 1.0;
	}

	public String getRiskWeightMode()
	{
		return riskWeightMode;
	}

	public double getRiskWeightScale()
	{
		return riskWeightScale;
	}

	public double getMaxSampleWeight()
	{
		return maxSampleWeight;
	}

	public Map<String, Object> toSummary()
	{
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("enabled", enabled);
		summary.put("riskWeightMode", riskWeightMode);
		summary.put("riskWeightScale", riskWeightScale);
		summary.put("maxSampleWeight", maxSampleWeight);
		summary.put("weightedLossEnabled", isWeightedLossEnabled());
		summary.put("weightFormula", "sampleWeight = clamp(1.0 + riskWeightScale * riskScore, 1.0, maxSampleWeight)");
		summary.put("riskScoreRange", "[0, 1]");
		return summary;
	}
}
