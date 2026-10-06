# lrws-multicx

> This customization relies on the `dxp.lxc.liferay.com.virtualInstanceId` property, which is **deprecated**. See [Working with Client Extensions → Configuring Client Extensions](https://learn.liferay.com/w/dxp/development/client-extensions/working-with-client-extensions). It still works in DXP 2026.Q1, but it may be removed in a future release.

Liferay Workspace customization that builds a single Client Extension (CX) zip targeting several virtual instances, driven by a per-environment configuration.

> This is not an official Liferay product. It relies on internal classes of the Liferay Workspace Gradle plugin and on the deprecated `dxp.lxc.liferay.com.virtualInstanceId` property. Review it before using it in your own projects.

## Why

A Client Extension built with Liferay Workspace is bound to one virtual instance. To reuse the same extension in several instances you need one of these:

- One build per instance (`-Pliferay.virtual.instance.id=<webId>`), or
- One block per instance in `client-extension.yaml`, each with its own `dxp.lxc.liferay.com.virtualInstanceId`.

When you run several Liferay installations (for example, one per cluster), each with a different set of virtual instances, keeping those yaml blocks by hand does not scale. This customization generates them at build time from a list of instances per environment. `client-extension.yaml` stays untouched.

See also:

- [Deploying Client Extensions to multiple Virtual Instances](https://learn.liferay.com/kb-article/deploying-client-extensions-to-multiple-virtual-instances)
- [How to set up client extension to be used in multiple On-Premise instances](https://learn.liferay.com/kb-article/how-to-set-up-client-extension-to-be-used-in-multiple-on-premise-instances)

## What's included

| File | Purpose |
|---|---|
| `buildSrc/build.gradle` | Builds the local plugin (`groovy-gradle-plugin`). |
| `buildSrc/src/main/groovy/com.liferay.custom.cxmultiinstance.gradle` | The `com.liferay.custom.cxmultiinstance` plugin. |
| `build.gradle` | Applies the plugin to every project under `client-extensions/` when `cx.custom.deploy.env` is set. |
| `gradle.properties` | Example instance lists per environment. |

## Configuration

Define the instances of each environment in `gradle.properties`:

```properties
cx.build.custom.env.instances[sample]=default,localhost2,localhost3
cx.build.custom.env.instances[sample-2]=localhost2,localhost3
```

| Property | Where | Description |
|---|---|---|
| `cx.custom.deploy.env` | Command line (`-P`) | Environment to build for. If not set, the plugin is not applied and every CX is built from its `client-extension.yaml` as is. |
| `cx.build.custom.env.instances[<env>]` | `gradle.properties` | Comma-separated list of virtual instance web IDs for `<env>`. Use `default` for the default virtual instance. |

The environment name is free text (`cluster1-dev`, `cluster2-prod`, ...).

## Usage

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

Without `-Pcx.custom.deploy.env`, the build behaves exactly like a standard Liferay Workspace.

No `build.gradle` is needed in the CX projects. The root `build.gradle` applies the plugin to every project under `client-extensions/`, at any depth, that has a `client-extension.yaml`. You can also apply it explicitly in a CX `build.gradle`:

```groovy
apply plugin: "com.liferay.custom.cxmultiinstance"
```

## What it generates

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
    dxp.lxc.liferay.com.virtualInstanceId: localhost4.com
```

The effective yaml is written to `build/cx-multi-instance/client-extension.<env>.yaml` in each CX project, so you can see exactly what went into the zip.

## Rules

- **`default` included:** the original block is kept and deployed to the default virtual instance.
- **`default` missing:** the original block is removed and only the per-instance copies are deployed.
- **Blocks that already set `dxp.lxc.liferay.com.virtualInstanceId`** are left as they are. The plugin logs a warning and does not copy them.
- **`assemble` and `runtime`** are not copied.
- **Static resources are shared.** All copies use the same web context (`/o/<project>/`). The files are deployed once and every instance loads them from there.
- **Rebuilds:** the environment and its instance list are inputs of `createClientExtensionConfig`, so switching environments forces a rebuild.

The build fails with a clear message when:

- `cx.custom.deploy.env` is set but `cx.build.custom.env.instances[<env>]` is not defined;
- `liferay.virtual.instance.id` is also set, because the two approaches are incompatible.

## Limitations

- **Plugin internals:** the plugin uses internal classes of the Liferay Workspace Gradle plugin (`ClientExtension`, `CreateClientExtensionConfigTask`). When `default` is not in the list, it also reads a private field of `CreateClientExtensionConfigTask` by reflection. Check it again after upgrading the Workspace plugin.
- **Deprecated property:** the build warns that `dxp.lxc.liferay.com.virtualInstanceId` is deprecated (once per generated copy). The warning is expected.
- **Instances must exist:** every web ID in the list must exist in the target installation. Keep one list per installation or environment.
- **Clusters:** the zip must be deployed to `osgi/client-extensions` on every cluster node.
- **One zip per environment:** each environment produces a different zip, because the generated blocks differ.
