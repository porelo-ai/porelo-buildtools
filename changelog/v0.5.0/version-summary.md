<!-- @formatter:off -->
<!-- noinspection -->
<!-- Prevents auto format, for JetBrains IDE File > Settings > Editor > Code Style (Formatter Tab) > Turn formatter on/off with markers in code comments  -->

<!-- This file is automatically generate by logchange tool 🌳 🪓 => 🪵 -->
<!-- Visit https://github.com/logchange/logchange and leave a star 🌟 -->
<!-- !!! ⚠️ DO NOT MODIFY THIS FILE, YOUR CHANGES WILL BE LOST ⚠️ !!! -->


[0.5.0] - 2026-10-17
--------------------

### Added (5 changes)

- check-actions reads each workflow's permissions - read or nothing at its top, a write only in a job its list names, write-all nowhere 
- check-citations holds every issue number under issues/ to a link to its index row, every row to its anchor, and every such link to an anchor that is there, here or in another repository's index 
- check-linkage --declared-only holds only the declared libraries to their floor and ceiling, for a package that derives its dependencies from its binaries 
- porelo-release check-linkage holds native binaries to the C library and zlib their packages declare, and the declared floor to the oldest system supported 
- porelo-release check-package-version reads the built .deb and .rpm and holds both to the project's version as the packaging maps it 

### Changed (3 changes)

- An agent leg installs from Artifactory's porelo-dist-deb and porelo-dist-rpm and checks them with the key porelo-packages 
- The tools are Porelo's - org.fuin.porelo porelo-json, porelo-machines, porelo-release, porelo-ffm-check and porelo-cpu-check, on porelo-parent - and what they rent, label and install is named porelo 
- Every workflow run is titled by its workflow, its branch or tag and its commit's subject, and the workflows are named Shared rules check and Machine images 

### Fixed (1 change)

- A command run over ssh that writes much to standard error before it ends is read to its end instead of waiting for ever 


