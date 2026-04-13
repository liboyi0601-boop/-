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

## Notes

- Main entry: `main.ExperimentPlatform`
- Template builder: `workflow.ReadWorkflowFile`
- Workflow generator: `workflow.WorkflowProducer`
- If you change `share.StaticfinalTags`, rerun the generator before running the experiment.
