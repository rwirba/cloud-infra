DevOps Vulnerability Dashboard — Demo Description

The DevOps Vulnerability Dashboard is a self-hosted, lightweight container security tool that continuously scans Docker images for known CVEs and surfaces the results in a clean, real-time web UI — no external SaaS, no agents, just a single Alpine-based container running Trivy, Nginx, and a small Python reporting layer.

In this demo, I walk through the dashboard end-to-end:

1. The stack in one container
Built on Alpine Linux with Trivy for vulnerability scanning, supervisord to manage the scan and web processes, and Nginx to serve the dashboard — all packaged into a single Docker image and deployed via docker compose up. No cluster, no complex infra — just a Compose file and a port.

2. Live vulnerability data
The dashboard currently tracks 100+ images pulled from a Docker Hub namespace, each scanned by Trivy and broken down by severity — Critical, High, Medium, and Low. Every image row is clickable, expanding into a full CVE breakdown with package name, installed vs. fixed version, and a direct link to the CVE record.

3. Redesigned UI
I recently gave the dashboard a full visual overhaul — a proper dark-mode design system with consistent severity color-coding, KPI stat tiles summarizing total findings across the fleet, and a redesigned data table with better readability and hover states.

4. New: vulnerability trend chart
The headline feature of this demo is a brand-new trend chart that tracks total vulnerabilities by severity over time. Every scan cycle now appends a snapshot to a rolling history file, and the dashboard renders it as a live multi-line chart — hover anywhere on the timeline and a tooltip shows the exact Critical/High/Medium/Low counts for that scan, with a crosshair that snaps to the nearest data point. This turns the dashboard from a point-in-time snapshot into something you can actually use to answer "are we getting better or worse over time?"

5. How it fits into a DevOps workflow
I'll show how new images get added to the scan list, how a scan cycle runs, and how the report and history files are generated and served — the same mechanism that would sit behind a CI pipeline gate or a nightly security review process for a team's container registry.
