# Liferay Workspace Multi-Instance Client Extensions Builds

> [!WARNING] 
> The default mode relies on the `dxp.lxc.liferay.com.virtualInstanceId` property, which is **deprecated**. See [Working with Client Extensions → Configuring Client Extensions](https://learn.liferay.com/w/dxp/development/client-extensions/working-with-client-extensions). It still works in DXP 2026.Q1, but it may be removed in a future release. The one-zip-per-instance mode uses the supported `liferay.virtual.instance.id` Gradle property instead, and the OSGi configuration mode follows option 2 of the KB article linked below.

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

| | One zip, one block per instance (default) | One zip per instance | OSGi configuration files |
|---|---|---|---|
| Enabled with | `build -Pcx.custom.deploy.env=<env>` | `build -Pcx.custom.deploy.env=<env> -Pcx.custom.one-per-instance=true` | `generateCXConfig -Pcx.custom.deploy.env=<env>` |
| Plugin | `com.liferay.custom.cxmultiinstance` | `com.liferay.custom.cxperinstance` | `com.liferay.custom.cxconfig` |
| Output | `dist/<project>.zip` with one block per instance | `dist/<project>.zip` (default instance) and `dist/<project>_<webId>.zip` per instance | `configs/<env>/osgi/configs/…CETConfiguration~<id>--<webId>.config` per instance |
| Instance binding | `dxp.lxc.liferay.com.virtualInstanceId` in the zip | `liferay.virtual.instance.id` | One OSGi `.config` file per instance |
| Static resources | Deployed once, shared by all instances (`/o/<project>/`) | Deployed once per instance (`/o/<project>_<webId>/`) | Deployed once with the standard zip, shared by all instances (`/o/<project>/`) |
| Build time | One build | One Gradle run per instance | One build |
| CX types | All | All | `customElement` only (PoC) |

## What's included

| File | Purpose |
|---|---|
| `buildSrc/build.gradle` | Builds the local plugins (`groovy-gradle-plugin`). |
| `buildSrc/src/main/groovy/com.liferay.custom.cxmultiinstance.gradle` | The `com.liferay.custom.cxmultiinstance` plugin (one zip, one block per instance). |
| `buildSrc/src/main/groovy/com.liferay.custom.cxperinstance.gradle` | The `com.liferay.custom.cxperinstance` plugin (one zip per instance). |
| `buildSrc/src/main/groovy/com.liferay.custom.cxconfig.gradle` | The `com.liferay.custom.cxconfig` plugin (`generateCXConfig` task, PoC). |
| `buildSrc/src/main/resources/cx-config-templates/` | One `.config` template per CX type for `generateCXConfig`. Only `customElement.config` for now. |
| `buildSrc/src/main/groovy/com.liferay.custom.cxinstances.gradle` | The `com.liferay.custom.cxinstances` plugin (`retrieveCXInstances` task). |
| `buildSrc/src/main/groovy/com/liferay/custom/PortalInstancesUtil.groovy` | Reads the web IDs of an installation through the `headless-portal-instances` API. |
| `build.gradle` | Applies `cxinstances` to the root project and the plugins to every project under `client-extensions/`. `cxconfig` is always applied; the build plugins only when `cx.custom.deploy.env` is set. |
| `gradle.properties` | Example portal URL and instance lists per environment. |

## Configuration

Define the instances of each environment in `gradle.properties`, and optionally the URL of its default instance (used by `retrieveCXInstances`):

```properties
cx.build.custom.env.url[dev]=http://localhost:8080
cx.build.custom.env.instances[dev]=default,localhost2,localhost3
cx.build.custom.env.instances[uat]=localhost2,localhost3
```

| Property | Where | Description |
|---|---|---|
| `cx.custom.deploy.env` | Command line (`-P`) | Environment to build or generate the configuration for. If not set, every CX is built from its `client-extension.yaml` as is. |
| `cx.custom.one-per-instance` | Command line (`-P`) | `true` to build one zip per instance. Otherwise, one zip with one block per instance is built. |
| `cx.build.custom.env.instances[<env>]` | `gradle.properties` | Comma-separated list of virtual instance web IDs for `<env>`. Use `default` for the default virtual instance. |
| `cx.build.custom.env.url[<env>]` | `gradle.properties` | URL of the default instance of `<env>`. Only needed for `retrieveCXInstances`. |
| `cx.custom.instances.apply` | Command line (`-P`) | `true` to let `retrieveCXInstances` write the list to `gradle.properties`. Otherwise it only shows it. |

Use the Liferay Workspace environment names (`dev`, `uat`, `prod`, ...), the same as the `configs/<env>` folders, so the generated OSGi configuration lands where `liferay.workspace.environment` expects it.

No `build.gradle` is needed in the CX projects. The root `build.gradle` applies the plugins to every project under `client-extensions/`, at any depth, that has a `client-extension.yaml`.

Without `-Pcx.custom.deploy.env`, the build behaves exactly like a standard Liferay Workspace.

## One zip, one block per instance (default mode)

### Usage

Build every CX for an environment:

```bash
./gradlew build -Pcx.custom.deploy.env=dev
```

Deploy to the bundle configured in `liferay.workspace.home.dir`:

```bash
./gradlew deploy -Pcx.custom.deploy.env=uat
```

Build a single CX:

```bash
./gradlew :client-extensions:liferay-sample-global-js-1:build -Pcx.custom.deploy.env=dev
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
./gradlew build -Pcx.custom.deploy.env=dev -Pcx.custom.one-per-instance=true
```

Deploy all the zips to the bundle configured in `liferay.workspace.home.dir`:

```bash
./gradlew deploy -Pcx.custom.deploy.env=dev -Pcx.custom.one-per-instance=true
```

Build a single CX:

```bash
./gradlew :client-extensions:liferay-sample-global-js-1:build -Pcx.custom.deploy.env=dev -Pcx.custom.one-per-instance=true
```

### How it works

`liferay.virtual.instance.id` is read once when Gradle starts, so it can only take one value per run. The plugin adds a `cxPerInstance` task that runs after the requested tasks and calls `gradlew` again, once per web ID, in sequence:

```bash
./gradlew :client-extensions:<project>:<task> -Pliferay.virtual.instance.id=<webId>
```

- Supported tasks: `assemble`, `build`, `buildClientExtensionZip` and `deploy`.
- Your other `-P` properties and `--offline` are passed to each run. The `cx.custom.*` properties are not, so the runs do not loop.

For example, with `cx.build.custom.env.instances[dev]=default,localhost2,localhost3`, `dist/` contains:

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

## OSGi configuration files (PoC)

> [!NOTE]
> Proof of concept. Only `customElement` client extensions are supported.

Implements option 2 of [How to set up client extension to be used in multiple On-Premise instances](https://learn.liferay.com/kb-article/how-to-set-up-client-extension-to-be-used-in-multiple-on-premise-instances), as described in [Front-end client extension: how to automate deployments for remote apps in on-premises](https://liferay.dev/es/b/-front-end-client-extension-how-to-automate-deployments-for-remote-apps-in-on-premises). The standard zip is deployed once, and one OSGi configuration file per instance registers the client extension in that instance, pointing to the same static resources.

### Usage

Generate the configuration for every CX:

```bash
./gradlew generateCXConfig -Pcx.custom.deploy.env=dev
```

Generate it for a single CX:

```bash
./gradlew :client-extensions:liferay-sample-custom-element-1:generateCXConfig -Pcx.custom.deploy.env=dev
```

Then deploy:

1. The standard zip, without `cx.custom.deploy.env` (`./gradlew deploy`). It publishes the static resources and registers the client extension in the default instance.
2. The generated files, for example with `./gradlew initBundle -Pliferay.workspace.environment=dev`, or by copying `configs/dev/osgi/configs/*.config` to `osgi/configs` in the bundle.

### What it generates

For each `customElement` block and each web ID other than `default`, one file in `configs/<env>/osgi/configs/`:

```
com.liferay.client.extension.type.configuration.CETConfiguration~liferay-sample-custom-element-1--localhost2.config
```

```properties
baseURL="${portalURL}/o/liferay-sample-custom-element-1"
dxp.lxc.liferay.com.virtualInstanceId="localhost2"
name="Liferay Sample Custom Element 1"
type="customElement"
typeSettings=[ \
  "friendlyURLMapping\=vanilla-counter", \
  "instanceable\=false", \
  "urls\=index.58829bb7a75441944031f8527403cf45456d7360.js", \
  "useESM\=false", \
  "htmlElementName\=vanilla-counter", \
  "cssURLs\=style.29ce553a40c0647dcaaa1b4e07c42ba2032a9d51.css", \
  "portletCategoryName\=category.client-extensions" \
]
```

### How it works

- `generateCXConfig` runs `createClientExtensionConfig` and reads the JSON that Liferay Workspace generates for the zip, with wildcards already resolved (`index.<hash>.js`) and the same `baseURL`.
- The CX type is detected from the `type` field, and the template `cx-config-templates/<type>.config` is used. Every `@key@` placeholder is replaced with the value of that key, from the block or its `typeSettings`. `@virtualInstanceId@` is the target web ID.
- Values are escaped for the Apache Felix configuration format. The `=` inside quoted values must be escaped (`\=`): otherwise the parser silently stops reading the file and drops the remaining properties.

### Rules

- **`default` is skipped:** the standard zip already registers the client extension in the default instance.
- **Unsupported types are skipped** with a warning that names the template to add.
- **Stale files are removed:** before generating, the previous `.config` files of that CX in `configs/<env>/osgi/configs/` are deleted, so instances removed from the list disappear.
- **The build plugins are not applied** when `generateCXConfig` is requested, so the JSON it reads is the standard one.

### Adding a CX type

Add `buildSrc/src/main/resources/cx-config-templates/<type>.config` with the properties of that type and `@key@` placeholders. Keys are taken from the block (`name`, `baseURL`, ...) and from its `typeSettings`. Escape the `=` after each `typeSettings` key (`"url\=@url@"`).

### Limitations

- **Tied to a build:** the files contain the hashed file names of the zip they were generated with. Regenerate and deploy them together with every new zip.
- **External reference code:** each instance gets a different code (`LXC:<id>--<webId>`).
- **Browser cache:** as the KB article warns, cached files may not refresh automatically with this approach.
- **Default instance:** the standard zip always registers the client extension in the default instance, even if `default` is not in the list.

## Updating the instance lists from the portal

`retrieveCXInstances` reads the virtual instances of an environment through the `headless-portal-instances` API of its default instance, and shows or updates `cx.build.custom.env.instances[<env>]`. It runs on its own, at any time, and does not build anything.

### Requirements

- `cx.build.custom.env.url[<env>]` in `gradle.properties`.
- An OAuth 2 application of type **Client Credentials** in the **default instance** of that installation, with a client credentials user that is an administrator of the default instance and the read scope of `Liferay.Headless.Portal.Instances`. The API only answers in the default instance.
- Its credentials in the `LIFERAY_OAUTH2_CLIENT_ID` and `LIFERAY_OAUTH2_CLIENT_SECRET` environment variables, so they never end up in `gradle.properties` or in the command history.

### Usage

```bash
export LIFERAY_OAUTH2_CLIENT_ID=<client-id> LIFERAY_OAUTH2_CLIENT_SECRET=<client-secret>
or
LIFERAY_OAUTH2_CLIENT_ID=<client-id> LIFERAY_OAUTH2_CLIENT_SECRET=<client-secret> ./gradlew retrieveCXInstances ...
```

Show the instances of the portal next to the current value:

```bash
./gradlew retrieveCXInstances -Pcx.custom.deploy.env=dev
```

```
[cxinstances] http://localhost:8080
[cxinstances] Current: cx.build.custom.env.instances[dev]=default,localhost2
[cxinstances] Portal:  cx.build.custom.env.instances[dev]=default,localhost2,localhost3
[cxinstances] Run with -Pcx.custom.instances.apply=true to update gradle.properties
```

Update `gradle.properties` automatically:

```bash
./gradlew retrieveCXInstances -Pcx.custom.deploy.env=dev -Pcx.custom.instances.apply=true
```

### Rules

- **`default`:** the list always starts with `default`, unless the current value does not include it. In that case it is kept out, because deploying to the default instance is your decision, not something the portal can tell.
- **Only one line changes:** `cx.build.custom.env.instances[<env>]` is replaced. If it does not exist, it is added after `cx.build.custom.env.url[<env>]`, or at the end of the file.
- **No changes, no write:** if the list already matches, the file is not touched.
- **The build plugins are not applied** when `retrieveCXInstances` is requested, so it also works for an environment without an instance list yet.

## Common limitations

- **Instances must exist:** every web ID in the list must exist in the target installation. Keep one list per installation or environment.
- **Clusters:** the zips must be deployed to `osgi/client-extensions` on every cluster node.
- **`liferay.virtual.instance.id`:** do not set it in `gradle.properties` together with `cx.custom.deploy.env`. The build fails with a clear message.

The build also fails when `cx.custom.deploy.env` is set but `cx.build.custom.env.instances[<env>]` is not defined.
