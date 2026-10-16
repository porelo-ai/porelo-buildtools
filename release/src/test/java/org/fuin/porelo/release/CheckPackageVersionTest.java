package org.fuin.porelo.release;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CheckPackageVersionTest {

    @Test
    void aSnapshotSortsBelowItsReleaseAndAboveTheRunBefore() {
        assertThat(CheckPackageVersion.packageVersion("0.4.1-SNAPSHOT", "7")).isEqualTo("0.4.1~snapshot.7");
        assertThat(CheckPackageVersion.packageVersion("0.4.1", "7")).isEqualTo("0.4.1");
    }

    @Test
    void acceptsPackagesThatCarryTheVersionAndTheRelease() {
        assertThat(CheckPackageVersion.problems("porelo-build-github", "0.4.2~snapshot.7", "porelo-build-github",
                "0.4.2~snapshot.7", "porelo-build-github 0.4.2~snapshot.7 1")).isEmpty();
    }

    @Test
    void refusesADebWhoseVersionCameFromAnotherProperty() {
        // A plugin taking its version from a property that means something else: the raw Maven version sorts above the
        // release, and apt then refuses the upgrade to it.
        assertThat(CheckPackageVersion.problems("porelo-build-github", "0.4.2~snapshot.7", "porelo-build-github",
                "0.4.2-SNAPSHOT", "porelo-build-github 0.4.2~snapshot.7 1"))
                .containsExactly("the deb is version 0.4.2-SNAPSHOT, not 0.4.2~snapshot.7");
    }

    @Test
    void refusesAnRpmWithATimestampRelease() {
        // What the rpm plugin does to a snapshot unless told not to: the rpm and the deb then disagree.
        assertThat(CheckPackageVersion.problems("porelo-build-github", "0.4.2~snapshot.7", "porelo-build-github",
                "0.4.2~snapshot.7", "porelo-build-github 0.4.2~snapshot.7 0.20261009120000"))
                .hasSize(1).first().asString().startsWith("the rpm is");
    }
}
