# Porelo Machines

Renting a test machine, and talking to one.

Four repositories rent servers from one Hetzner project to test against: this one and the three
agents. Until now each carried its own copy of a 483-line Python module, and the copies had already
drifted — the agents' copy grew a `key_material()` that normalises a key's line endings, and this
repository's did not, so the same secret was cleaned in three places and passed through raw in the
fourth. That is what this replaces.

## The one thing worth reading first

**A server that is not destroyed costs 81 EUR a month**, against 3 cents for the fifteen minutes it
is meant to live. So destruction is structural rather than a step at the end:

```java
try (Hetzner hetzner = Hetzner.with(token, runId);
     Lease lease = hetzner.acquire(Spec.of("porelo-ci-1", "ubuntu", "build", credential))) {
    lease.awaitSsh();
    lease.ssh().run("./mvnw -B verify");
}
```

`Lease.close()` deletes, and it is not conditional on success — the expensive mistake is a server
that outlives a script which failed on line three, not one that is deleted twice. `sweep` is the net
under that, because a process killed between two statements cannot clean up after itself.

Everything created here is labelled `porelo=ci` plus the run that made it, so the sweep finds it
without a list of names to keep in step with reality.

## Sweeping

```
java -jar porelo-machines.jar sweep [--mine | --from <file>] [--now] [--including-in-use]
```

Every server a lease creates is labelled `porelo-expires` with the end of the time it was made for: its creation, plus
the job's time limit (`--for <minutes>` on `snapshot`, `acceptance` and `lease`, 60 by default, at most 360), plus 15
minutes for the clean-up after the step and the clocks. The value is the UTC minute as `20261018T0528Z`, since a label
value cannot hold an ISO instant's `:`.

With no option `sweep` lists what would go and deletes nothing; `--now` deletes it. It deletes a server whose
`porelo-expires` has passed, and a `porelo=ci` server with no such label, which nobody can attribute; it keeps one
still within its time, however old, and says so with its expiry. Only `--including-in-use` deletes those as well. A
label it cannot read keeps the server and fails the sweep, naming it. `--older-than` is refused: the expiry decides,
not the age ([the decision](../doc/decisions.md#a-sweep-deletes-by-expiry-not-by-age)).

`--mine` deletes what this run created, and `--from <file>` what the run a `lease --write` file names, whatever their
expiry: a job clearing up after itself does so while its servers are still within their time.

`HETZNER_API` carries the API token and is read from the environment, never from an argument —
`/proc/<pid>/cmdline` is world readable and neither supported distribution mounts `/proc` with
`hidepid`.

## What is here and what is not

Seven endpoints, not a client library: create a server, wait for it, delete it, list what was left
behind, and find an image, a location and a key. Paging is followed to the end rather than assumed
away — the API returns 25 entries a page, and a sweep that reads one page finds fewer leaked servers
than exist and then reports that it cleaned up.

`Ssh` is the other half, and the acceptance kit's `Machine` is built on it. Connecting,
authenticating and running a command are the same problem whether the caller is renting the machine
or testing on it; the kit adds what only a scenario needs.
Every caller says which host key `Ssh` accepts: the one a rented machine was created with, or any
key with a reason, for a local virtual machine ([the decision](../doc/decisions.md)).

## Tests

`StubApi` stands up a real HTTP server on a real socket, because what is being checked is paging,
status codes and headers — the layer a mocked client would replace with an assumption. The
fingerprint is checked against what `ssh-keygen -E md5` says, since the API agrees with that rather
than with us.
