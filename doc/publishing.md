# Publishing to Artifactory

A Publish job uploads with `porelo-release artifactory`, the one helper every repository takes. No repository keeps
a script of its own for it.

## The call

The tools come through the parent's `ci-tools` profile, at the version the parent names:

```yaml
- name: The publishing tools
  run: ./mvnw -B -q -Pci-tools -s settings.xml dependency:build-classpath -Dmdep.outputFile="$RUNNER_TEMP/tools-cp.txt"
- name: Publish
  env:
      JF_URL: ${{ vars.JF_URL }}
      JF_ACCESS_TOKEN: ${{ secrets.JF_ACCESS_TOKEN }}
  run: |
      set -eu
      art() { java -cp "$(cat "$RUNNER_TEMP/tools-cp.txt")" org.fuin.porelo.release.Main artifactory "$@"; }
      art put target/porelo-x.rpm porelo-dist-rpm/snapshots/
      art put target/porelo-x_1.0.0_amd64.deb porelo-dist-deb/pool/main/p/porelo-x/ \
          "deb.distribution=snapshots;deb.component=main;deb.architecture=amd64"
```

A job without the pinned GraalVM runs it with the runner's Java, `"$JAVA_HOME_25_X64/bin/java"`.

## The commands

- `artifactory put FILE REPOSITORY/PATH[/] [KEY=VALUE[;KEY=VALUE...]]`:
  - uploads the file, its SHA-1 and SHA-256 with it, so Artifactory refuses bytes that do not match them;
  - a target ending in `/` takes the file's own name;
  - the properties are set with the upload. A Debian package needs `deb.distribution`, `deb.component` and
    `deb.architecture`, or it is stored and never indexed.
  - A link in place of the file is refused, and so is a file that changes while it is sent.
  - A target holds letters, digits and `._+~@,=-` in its segments, a property `KEY=VALUE` the same less `=`. Nothing
    is escaped or taken apart.
- `artifactory api METHOD /PATH`: a call to the REST interface under `/artifactory`, a query included. It prints the
  answer. A 404 fails. A path with a `.` or `..` segment, escaped or not, is refused, so a call never leaves
  `/artifactory`.
- `artifactory ping`: fails unless Artifactory answers `OK` with the token.

## The token

- `JF_URL` is the platform's https address, without `/artifactory`.
- `JF_ACCESS_TOKEN` comes from the step's environment into the Java process, and goes only into the `Authorization`
  header of calls to that address. It never goes on a command line, into a file or into a log. A redirect to another
  host does not carry it.

## Asked again

- What did not answer, or answered a 5xx, is asked again by the rule of [pinned downloads](pinned-downloads.md): the
  same pauses, and no attempt later than 15 minutes after the first. An upload sends the same bytes each time.
- A 4xx fails at once, as do a refused certificate and a file that could not be read.

**Exit codes:**
- 0: done;
- 1: Artifactory refused, or a file is missing;
- 2: unanswered within the rule, stopped from outside, or `JF_URL` or `JF_ACCESS_TOKEN` is missing or not of its
  form.
