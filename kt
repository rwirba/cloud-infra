CI/CD Pipeline Documentation
GitHub Actions + Docker + Helm on AKS
(Using JFrog Artifactory as Container Registry)
Last Updated: February 19, 2026
Purpose: This document captures the full knowledge transfer session explaining how the team builds, containers, and deploys multiple services (including the Order Experience application) using GitHub Actions. Services can be either Java (Maven-based) or Node.js applications. The pipeline supports both types with the same overall structure.
1. Overview
Main tools used:

GitHub Actions (for automation)
JFrog Artifactory (private container registry for storing Docker images)
Docker (to build and push container images)
Helm charts (to deploy to Kubernetes)
Azure Kubernetes Service (AKS)
Self-hosted runners (specific runner group)
Build tools: Maven for Java applications, npm/yarn for Node.js applications

Environments and triggers:

























EnvironmentHow it gets deployedApproval needed?DevAutomatically when code is merged to develop branchNoStageManually triggeredYesProdManually triggeredYes
Four services are already running using this pattern. New services follow the same process.
2. Workflow Steps – Build Phase (CI)

Checkout the source code from the repository
Read configuration files (application.properties for Java, .env or similar for Node.js)
Run code and security scanning tools
Build the application
For Java applications: run Maven clean and package to create the JAR/WAR file
For Node.js applications: install dependencies and run the build command (if applicable)

Apply conditional rules (some steps are skipped on production branch)
Set corporate HTTP proxy (required for downloads during build)
Log in to JFrog Artifactory container registry
Build the Docker image using the Dockerfile in the project root
The application listens on port 3000 inside the container (same for both Java and Node.js)

Tag and push the Docker image to JFrog Artifactory
Images are stored with tags that include commit SHA or branch name


3. Workflow Steps – Deploy Phase (CD)

Log in to Azure using service principal credentials
Set context to the target AKS cluster
Read the Helm chart for the service
Apply environment-specific configuration values (different files for dev, stage, prod)
Perform Helm upgrade or install to deploy or update the application in Kubernetes
After deployment: environment variables are injected, services, ingress rules, secrets, and volumes are applied

4. Helm Chart Configuration (Key Settings)
Each service uses its own Helm chart containing:

Number of replicas
Docker image repository and tag (points to JFrog Artifactory)
Service type and port (usually ClusterIP on port 3000)
CPU and memory requests & limits
Volume definitions and mounts
Secret references (database credentials, API keys, etc.)
Ingress configuration (DNS name and TLS certificate)

5. Branching and Deployment Rules

When a developer creates a feature branch from develop and merges it back to develop → the pipeline automatically builds the image, pushes it to JFrog, and deploys it to the Dev environment.
For Stage or Production:
Go to GitHub Actions
Select the workflow
Click “Run workflow”
Choose the branch and target environment
Wait for approval (required for Stage and Prod)


6. Runner Setup

All jobs run on self-hosted runners
Runners are enrolled in a specific runner group
The same runner group is used across all services

7. Required Secrets (Stored in GitHub)

JFrog Artifactory username and access token
Azure credentials (client ID, tenant ID, subscription ID, client secret)
Application-specific secrets (different values per environment)

8. Typical Repository Folder Structure
Java application:

Source code folder
pom.xml (Maven file)
settings.xml (proxy and repository settings)
Dockerfile
Helm chart folder with environment-specific value files

Node.js application:

Source code folder
package.json
Dockerfile
Helm chart folder with environment-specific value files
