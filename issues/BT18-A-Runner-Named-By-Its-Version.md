# BT18 — A Runner Named By Its Version

**Status:** Idea.

**What must be true.** `check-actions` refuses a job whose `runs-on` names a moving runner label such as
`ubuntu-latest`, `macos-latest` or `windows-latest`, naming the file and the job, so the system under a Build does not
change unseen. A runner named by its version (`ubuntu-24.04`) passes, as does an expression or a self-hosted label.

On 2026-10-18, `ubuntu-latest` stood in 26 workflows across eleven repositories while the shared habit was to pin it; each
repository fixed its own by hand. A check holds it from then on.
