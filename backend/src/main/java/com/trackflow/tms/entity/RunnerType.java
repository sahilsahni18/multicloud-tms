package com.trackflow.tms.entity;

/** Who executes OpenTofu for a deployment. */
public enum RunnerType {
    /** workflow_dispatch to GitHub Actions; steps reported back via signed callback. */
    GITHUB_ACTIONS,
    /** tofu on the machine running the backend (laptop fallback). */
    LOCAL,
    /** No cloud calls; walks through the steps on a timer (local demo, tests). */
    SIMULATED
}
