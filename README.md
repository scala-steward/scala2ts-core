# scala2ts-core

> Transform your Scala classes, objects and traits into Typescript interfaces and classes

This repository contains the parser, transpiler and renderer for the scala2ts
project. It's primary purpose is to act as a [Scala compiler plugin](https://docs.scala-lang.org/overviews/plugins/index.html) that, on compile, will
produce equivalent Typescript typings for your desired Scala classes, objects and traits.

## SBT

It is highly recommended that if you use SBT for your project, to use the `scala2ts-sbt` SBT plugin
instead of this project directly. It streamlines adding the compiler plugin and configuring it for you.

You can find the project [here](https://github.com/scala2ts/scala2ts-sbt). If you dont use SBT or have a 
more complex build configuration, proceed below.

## Support

![Maven Central](https://img.shields.io/maven-central/v/com.github.scala2ts/scala2ts-core_2.13)

|**Version**|**Scala**|
|---|---|
|1.1.x|2.13<br />2.12<br />2.11|
|1.0.x|2.13<br />2.12<br />2.11|

## Usage

```sbt
// build.sbt

lazy val yourProject = (project in file("."))
  .settings(
    autoCompilerPlugins := true,
    addCompilerPlugin("com.github.scala2ts" % "scala2ts-core" % "latestVersion"),
    scalacOptions := Seq(
      // See below for configuration options
    ) 
  )
```

Adding `autoCompilerPlugins` tells SBT to automatically include the `-Xplugin` scalac flag with the
appropriate path to found compiler plugins.

## Configuration

There are many configuration options in how and what Typescript is emitted from your Scala code. The defaults
of which can be found in the [`com.github.scala2ts.configuration.Configuration`](https://github.com/scala2ts/scala2ts-core/blob/master/src/main/scala/com/github/scala2ts/configuration/Configuration.scala#L3) case class.
For ease of use, here is the current list of configuration options and their defaults:

|**Option**|**Flag**|**Multi**|**Type**|**Default**|**Description**|
|---|---|---|---|---|---|
|Debug|-P:scala2ts:debug|no|Boolean|false|Enable debug logging|
|Include File|-P:scala2ts:file:includes|yes|Regex| |File path to include in compilation|
|Exclude File|-P:scala2ts:file:excludes|yes|Regex| |File path to exclude in compilation|
|Include Type|-P:scala2ts:type:includes|yes|Regex| |Type name to include in compilation|
|Exclude Type|-P:scala2ts:type:excludes|yes|Regex| |Type name to exclude in compilation|
|Prefix|-P:scala2ts:type:prefix|no|String| |A prefix to use in your Typescript names (e.g. I for IInterface)|
|Suffix|-P:scala2ts:type:suffix|no|String| |A suffix to use in your Typescript names (e.g. Data InterfaceData)|
|Date Mapping|-P:scala2ts:date|no|String|AsDate|How to emit Date types (options: AsDate, AsString, AsNumber)|
|Long & Double Mapping|-P:scala2ts:longDouble|no|String|AsString|How to emit Long(s) or Double(s) (options: AsString, AsNumber)|
|Sealed Trait Type Mapping|-P:scala2ts:sealedTypes|no|SealedTypesMapping|None|Emit an enum or type union for a sealed trait/class's members (options: None, AsEnum, AsUnion, AsUnionString)|
|Render As|-P:scala2ts:renderAs|no|RenderAs|Interface|How to render interfaces/classes|
|Class Discriminator|-P:scala2ts:includeDiscriminator|no|Boolean|false|Include a class property that acts as a discriminator|
|Discriminator Name|-P:scala2ts:discriminatorName|no|String|"type"|The name of the class discriminator property|
|Output Directory|-P:scala2ts:outDir|no|String| |What directory to emit files to|
|Output File Name|-P:scala2ts:outFileName|no|String|index.d.ts|The name of the outputted Typescript file|
|package.json Name|-P:scala2ts:pj:name|no|String| |The name to use in package.json. Note: This is the only required field to enable package.json to emit|
|package.json Version|-P:scala2ts:pj:name|no|String| |The version to use in package.json|
|package.json Types|-P:scala2ts:pj:types|no|String| |The path to use in the types field of package.json|
|package.json publishConfig Registry|-P:scala2ts:pj:registry|no|String| |The url to use for an external NPM registry|

## Motivations

Most current Scala -> TS solutions rely on SBT, where they couple the transpile code within an SBT plugin. The way
SBT operates, it has its own Scala version separate from the project its operating on. This means that these plugins
can only operate on 2.10 (SBT 0.x) and 2.12 (SBT 1.x) codebases. 

Why is this the case? Scala's reflection library can only operate on Scala code produced by the specific Scala version
being used at the time. If I compile a 2.13 project, I cannot use the 2.12 reflection library against that code. This 
is where the limitations of the current SBT plugins comes to light.

This library tries to eliminate that by operating at the compiler level, where we're guaranteed binary compatibility.
The SBT plugin (scala2ts-sbt) merely acts as a configuration layer for the compiler plugin.

## Acknowledgments

Big thanks to the maintainers of `scala-ts` and `Typescript-Generator` projects, specifically [@cchantep](https://github.com/cchantep) [PR](https://github.com/scala-ts/scala-ts/pull/20) 
with the `scala-ts`library which was the inspiration for this project.
