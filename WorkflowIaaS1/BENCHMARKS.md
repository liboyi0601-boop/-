# Benchmark Workloads

This project now supports a benchmark-only data path for Pegasus / WorkflowSim-style families without changing the protected baseline assets:

- `CyberShake`
- `Montage`
- `Inspiral`
- `Sipht`

The new benchmark path does **not** modify:

- `ExperimentalWorkflow.dat`
- `WorkflowTemplateSet.dat`
- `workloads/golden-100w.dat`
- `workloads/aux-small-20w-seed11.dat`
- `workloads/aux-medium-50w-seed23.dat`

## Source XML Files

Built-in benchmark XML files are read from:

```text
XML Example/
```

Current family-to-file mapping:

- `cybershake`: `CyberShake_30.xml`, `CyberShake_50.xml`, `CyberShake_100.xml`, `CyberShake_1000.xml`
- `montage`: `Montage_25.xml`, `Montage_50.xml`, `Montage_100.xml`, `Montage_1000.xml`
- `inspiral`: `Inspiral_30.xml`, `Inspiral_50.xml`, `Inspiral_100.xml`, `Inspiral_1000.xml`
- `sipht`: `Sipht_30.xml`, `Sipht_60.xml`, `Sipht_100.xml`, `Sipht_1000.xml`

If you later add larger inputs such as `CyberShake_2000.xml`, place them under `XML Example/` and extend `workflow.BenchmarkFamily`. Do not overwrite the protected baseline `.dat` assets.

## Generated Paths

Benchmark templates are written to:

```text
workloads/benchmarks/templates/
```

Benchmark workload datasets are written to:

```text
workloads/benchmarks/
```

Examples:

- `workloads/benchmarks/templates/cybershake-template-set.dat`
- `workloads/benchmarks/templates/bench-mixed-template-set.dat`
- `workloads/benchmarks/bench-cybershake-small-20w.dat`
- `workloads/benchmarks/bench-mixed-medium-50w.dat`

## Generate Templates

Generate all family template sets plus the mixed template set:

```bash
./scripts/generate_benchmark_templates.sh all
```

Generate one family only:

```bash
./scripts/generate_benchmark_templates.sh cybershake
./scripts/generate_benchmark_templates.sh montage
./scripts/generate_benchmark_templates.sh inspiral
./scripts/generate_benchmark_templates.sh sipht
```

Generate only the mixed template set:

```bash
./scripts/generate_benchmark_templates.sh mixed
```

To replace an existing benchmark template set:

```bash
./scripts/generate_benchmark_templates.sh cybershake --overwrite
```

## Generate Workloads

Available benchmark suites:

- `bench-cybershake-small`
- `bench-cybershake-medium`
- `bench-montage-small`
- `bench-montage-medium`
- `bench-inspiral-small`
- `bench-inspiral-medium`
- `bench-sipht-small`
- `bench-sipht-medium`
- `bench-mixed-small`
- `bench-mixed-medium`

Generate a workload file for one suite:

```bash
./scripts/generate_benchmark_workloads.sh bench-cybershake-small
./scripts/generate_benchmark_workloads.sh bench-mixed-small
```

Overwrite an existing generated benchmark workload:

```bash
./scripts/generate_benchmark_workloads.sh bench-cybershake-small --overwrite
```

## Run Benchmark Baselines

Benchmark baselines run through `RegressionRunner`; the protected `ExperimentPlatform` baseline path is unchanged.

```bash
java -cp "bin:Lib/*:ComparisonAlgorithm" main.RegressionRunner --suite bench-cybershake-small --no-trace
java -cp "bin:Lib/*:ComparisonAlgorithm" main.RegressionRunner --suite bench-mixed-small --no-trace
```

## Run Benchmark Learning

Single benchmark train and eval:

```bash
java -cp "bin:Lib/*:ComparisonAlgorithm" main.LearningRunner --suite bench-cybershake-small
java -cp "bin:Lib/*:ComparisonAlgorithm" main.GraphAttentionLearningRunner --suite bench-cybershake-small
java -cp "bin:Lib/*:ComparisonAlgorithm" main.AblationRunner --suite bench-cybershake-small
```

Mixed benchmark train and single benchmark eval:

```bash
java -cp "bin:Lib/*:ComparisonAlgorithm" main.LearningRunner --train-suite bench-mixed-small --eval-suite bench-cybershake-small
java -cp "bin:Lib/*:ComparisonAlgorithm" main.GraphAttentionLearningRunner --train-suite bench-mixed-small --eval-suite bench-cybershake-small
java -cp "bin:Lib/*:ComparisonAlgorithm" main.AblationRunner --train-suite bench-mixed-small --eval-suite bench-cybershake-small
```

Mixed benchmark train and mixed benchmark eval:

```bash
java -cp "bin:Lib/*:ComparisonAlgorithm" main.LearningRunner --train-suite bench-mixed-small --eval-suite bench-mixed-medium
java -cp "bin:Lib/*:ComparisonAlgorithm" main.GraphAttentionLearningRunner --train-suite bench-mixed-small --eval-suite bench-mixed-medium
java -cp "bin:Lib/*:ComparisonAlgorithm" main.AblationRunner --train-suite bench-mixed-small --eval-suite bench-mixed-medium
```

Each run keeps the existing artifact directory layout and records benchmark family provenance in replay summaries, training summaries, and manifests.
