# BT30 — Check Actions Reads Workflows As YAML

**Status:** Accepted.

**What must be true.** `porelo-release check-actions` judges every workflow as GitHub reads it, as parsed YAML, so
a write granted in quotes (`contents: 'write'`) or in flow style, and a step written in flow style
(`- {uses: actions/checkout@main}`) or under a quoted key, meet the same refusals as the plain form: the list of
writing jobs, the pin to a commit and `persist-credentials: false`. Today those three checks read the file line by
line, and a reading in another form passes them; the guard of a writing job that runs no fetched code already reads
the parsed file.
