# Liferay Workspace Multi-Instance Client Extensions Builds

> [!WARNING] 
> The default mode relies on the `dxp.lxc.liferay.com.virtualInstanceId` property, which is **deprecated**. See [Working with Client Extensions → Configuring Client Extensions](https://learn.liferay.com/w/dxp/development/client-extensions/working-with-client-extensions). It still works in DXP 2026.Q1, but it may be removed in a future release. The one-zip-per-instance mode uses the supported `liferay.virtual.instance.id` Gradle property instead.

Liferay Workspace customization that builds Client Extensions (CX) for several virtual instances, driven by a per-environment configuration.
> This is not an official Liferay product. Review it before using it in your own projects.

## Why

A Client Extension built with Liferay Workspace is bound to one virtual instance. To reuse the same extension in several instances you need one of these:

- One build per instance (`-Pliferay.virtual.instance.id=<webId>`), or
- One block per instance in `client-extension.yaml`, each with its own `dxp.lxc.liferay.com.virtualInstanceId`.

When you run several Liferay installations (for example, one per cluster), each with a different set of virtual instances, doing either by hand does not scale. This customization automates both from a list of instances per environment. `client-extension.yaml` stays untouched.

See also:

- [Deploying Client Extensions to multiple Virtual Instances](https://learn.liferay.com/kb-article/deploying-client-extensions-to-multiple-virtual-instances)
- [How to set up client extension to be used in multiple On-Premise instances](https://learn.liferay.com/kb-article/how-to-set-up-client-extension-to-be-used-in-multiple-on-premise-instances)

## Modes

| | One zip, one block per instance (default) | One zip per instance |
|---|---|---|
| Enabled with | `-Pcx.custom.deploy.env=<env>` | `-Pcx.custom.deploy.env=<env> -Pcx.custom.one-per-instance=true` |
| Plugin | `com.liferay.custom.cxmultiinstance` | `com.liferay.custom.cxperinstance` |
| Output | `dist/<project>.zip` with one block per instance | `dist/<project>.zip` (default instance) and `dist/<project>_<webId>.zip` per instance |
| Instance binding | `dxp.lxc.liferay.com.virtualInstanceId` | `liferay.virtual.instance.id` |
| Static resources | Deployed once, shared by all instances (`/o/<project>/`) | Deployed once per instance (`/o/<project>_<webId>/`) |
| Build time | One build | One Gradle run per instance |

## What's included

| File | Purpose |
|---|---|
| `buildSrc/build.gradle` | Builds the local plugins (`groovy-gradle-plugin`). |
| `buildSrc/src/main/groovy/com.liferay.custom.cxmultiinstance.gradle` | The `com.liferay.custom.cxmultiinstance` plugin (one zip, one block per instance). |
| `buildSrc/src/main/groovy/com.liferay.custom.cxperinstance.gradle` | The `com.liferay.custom.cxperinstance` plugin (one zip per instance). |
| `build.gradle` | Applies one of the plugins to every project under `client-extensions/` when `cx.custom.deploy.env` is set. |
| `gradle.properties` | Example instance lists per environment. |

## Configuration

Define the instances of each environment in `gradle.properties`:

```properties
cx.build.custom.env.instances[sample]=default,localhost2,localhost3
cx.build.custom.env.instances[sample-other]=localhost2,localhost3
```

| Property | Where | Description |
|---|---|---|
| `cx.custom.deploy.env` | Command line (`-P`) | Environment to build for. If not set, no plugin is applied and every CX is built from its `client-extension.yaml` as is. |
| `cx.custom.one-per-instance` | Command line (`-P`) | `true` to build one zip per instance. Otherwise, one zip with one block per instance is built. |
| `cx.build.custom.env.instances[<env>]` | `gradle.properties` | Comma-separated list of virtual instance web IDs for `<env>`. Use `default` for the default virtual instance. |

The environment name is free text (`cluster1-dev`, `cluster2-prod`, ...).

No `build.gradle` is needed in the CX projects. The root `build.gradle` applies the plugin to every project under `client-extensions/`, at any depth, that has a `client-extension.yaml`.

Without `-Pcx.custom.deploy.env`, the build behaves exactly like a standard Liferay Workspace.

## One zip, one block per instance (default mode)

### Usage

Build every CX for an environment:

```bash
./gradlew build -Pcx.custom.deploy.env=sample
```

Deploy to the bundle configured in `liferay.workspace.home.dir`:

```bash
./gradlew deploy -Pcx.custom.deploy.env=sample-other
```

Build a single CX:

```bash
./gradlew :client-extensions:liferay-sample-global-js-1:build -Pcx.custom.deploy.env=sample
```

You can also apply the plugin explicitly in a CX `build.gradle`:

```groovy
apply plugin: "com.liferay.custom.cxmultiinstance"
```

### What it generates

For each CX block in `client-extension.yaml` and each web ID other than `default`, the plugin adds a copy of the block:

- **ID:** `<id>--<webId>`, where every `.` in the web ID becomes `-`.
- **Instance:** the copy sets `dxp.lxc.liferay.com.virtualInstanceId: <webId>`.

For example, with `cx.build.custom.env.instances[env]=default,sample.com`:

```yaml
liferay-sample-global-js-1:
    name: Liferay Sample Global JS 1
    type: globalJS
    url: global.*.js
liferay-sample-global-js-1--sample-com:
    name: Liferay Sample Global JS 1
    type: globalJS
    url: global.*.js
    dxp.lxc.liferay.com.virtualInstanceId: sample.com
```

The effective yaml is written to `build/cx-multi-instance/client-extension.<env>.yaml` in each CX project, so you can see exactly what went into the zip.

### Rules

- **`default` included:** the original block is kept and deployed to the default virtual instance.
- **`default` missing:** the original block is removed and only the per-instance copies are deployed.
- **Blocks that already set `dxp.lxc.liferay.com.virtualInstanceId`** are left as they are. The plugin logs a warning and does not copy them.
- **`assemble` and `runtime`** are not copied.
- **Static resources are shared.** All copies use the same web context (`/o/<project>/`). The files are deployed once and every instance loads them from there.
- **Rebuilds:** the environment and its instance list are inputs of `createClientExtensionConfig`, so switching environments forces a rebuild.

### Limitations

- **Plugin internals:** the plugin uses internal classes of the Liferay Workspace Gradle plugin (`ClientExtension`, `CreateClientExtensionConfigTask`). When `default` is not in the list, it also reads a private field of `CreateClientExtensionConfigTask` by reflection. Check it again after upgrading the Workspace plugin.
- **Deprecated property:** the build warns that `dxp.lxc.liferay.com.virtualInstanceId` is deprecated (once per generated copy). The warning is expected.
- **One zip per environment:** each environment produces a different zip, because the generated blocks differ.

## One zip per instance

### Usage

Build every CX, one zip per instance:

```bash
./gradlew build -Pcx.custom.deploy.env=sample -Pcx.custom.one-per-instance=true
```

Deploy all the zips to the bundle configured in `liferay.workspace.home.dir`:

```bash
./gradlew deploy -Pcx.custom.deploy.env=sample -Pcx.custom.one-per-instance=true
```

Build a single CX:

```bash
./gradlew :client-extensions:liferay-sample-global-js-1:build -Pcx.custom.deploy.env=sample -Pcx.custom.one-per-instance=true
```

### How it works

`liferay.virtual.instance.id` is read once when Gradle starts, so it can only take one value per run. The plugin adds a `cxPerInstance` task that runs after the requested tasks and calls `gradlew` again, once per web ID, in sequence:

```bash
./gradlew :client-extensions:<project>:<task> -Pliferay.virtual.instance.id=<webId>
```

- Supported tasks: `assemble`, `build`, `buildClientExtensionZip` and `deploy`.
- Your other `-P` properties and `--offline` are passed to each run. The `cx.custom.*` properties are not, so the runs do not loop.

For example, with `cx.build.custom.env.instances[sample]=default,localhost2,localhost3`, `dist/` contains:

```
liferay-sample-global-js-1.zip              (default instance)
liferay-sample-global-js-1_localhost2.zip   (localhost2)
liferay-sample-global-js-1_localhost3.zip   (localhost3)
```

Each zip is a separate bundle with its own web context (`/o/<project>_<webId>/`), so they can be deployed side by side in `osgi/client-extensions`.

### Rules

- **`default` included:** the main run builds and deploys the standard zip for the default instance.
- **`default` missing:** the main run does not build or deploy the default zip, and removes a previous `dist/<project>.zip` of that CX.
- **Only the requested CX are processed.** Running the command from a CX folder, or with a project path, builds only that CX.

### Limitations

- **Build time:** every instance is a full Gradle run. From the workspace root, the number of runs is CX × instances.
- **`dist/` keeps old zips:** zips for instances removed from the list, or from other environments, are not deleted. Clean `dist/` when switching environments.
- **Static resources are duplicated:** each instance loads its own copy (`/o/<project>_<webId>/`).

## Common limitations

- **Instances must exist:** every web ID in the list must exist in the target installation. Keep one list per installation or environment.
- **Clusters:** the zips must be deployed to `osgi/client-extensions` on every cluster node.
- **`liferay.virtual.instance.id`:** do not set it in `gradle.properties` together with `cx.custom.deploy.env`. The build fails with a clear message.

The build also fails when `cx.custom.deploy.env` is set but `cx.build.custom.env.instances[<env>]` is not defined.
