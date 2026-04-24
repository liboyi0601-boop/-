# Running WorkflowIaaS1

This project must be compiled with `GBK` source encoding.
The runtime also expects to be started from the project root because
it reads files like `ExperimentalWorkflow.dat`, `WorkflowTemplateSet.dat`,
and `XML Example/...` via relative paths.

## Prerequisites

- Java and `javac` available on `PATH`
- The bundled jars under `Lib/`

## Commands

Build:

```bash
./scripts/build.sh
```

Generate workflow templates from the XML files:

```bash
./scripts/generate_templates.sh
```

Generate an experimental workflow set:

```bash
./scripts/generate_workflows.sh
```

Run the main experiment entry:

```bash
./scripts/run_experiment.sh
```

Generate benchmark-only template sets without touching `WorkflowTemplateSet.dat`:

```bash
./scripts/generate_benchmark_templates.sh all
```

Generate benchmark-only workload files without touching `ExperimentalWorkflow.dat`:

```bash
./scripts/generate_benchmark_workloads.sh bench-cybershake-small
```

## Notes

- Main entry: `main.ExperimentPlatform`
- Template builder: `workflow.ReadWorkflowFile`
- Workflow generator: `workflow.WorkflowProducer`
- Benchmark template builder: `workflow.BenchmarkTemplateBuilder`
- Benchmark workload generator: `workflow.BenchmarkWorkflowGenerator`
- If you change `share.StaticfinalTags`, rerun the generator before running the experiment.
- Benchmark setup and benchmark runner commands are documented in `BENCHMARKS.md`.
- Phase 11-prep examples for `--balanced-families`, `--balance-strategy min-quota`, and `--normalized-comparison`
  are documented in `BENCHMARKS.md`.
- Phase 11C deadline violation metric hygiene notes are documented in `BENCHMARKS.md`; baseline default output remains unchanged.
