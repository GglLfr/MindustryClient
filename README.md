# `MindustryClient`

Gradle plugin to conveniently detect and run Mindustry. The plugin defines

- the `MindustryClientService` build service, which detects a Mindustry installation (namely Steam, explicitly-provided
  path, or locally cached);
- a `installClient` task, which downloads a client JAR if there's no Steam installation nor explicitly-provided path;
  and
- a `run` task, which runs Mindustry.

Additionally, you may define some optional properties in either project-scoped `gradle.properties` or through global
`~/.gradle/gradle.properties` (or even through the `-P` option if you're that kind of person), namely

- `mindustryIgnoreSteam`, to ignore Steam installations (defaults to `false`); and
- `mindustryPath`, to explicitly give the path to your Mindustry JAR or executable file.

## Contributing

This project is licensed under [GNU GPL v3](/LICENSE).