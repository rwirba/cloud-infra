# Nexus Artifact Deletion Guide

## Overview

This guide explains how to safely delete artifacts from Sonatype Nexus using the command line. It covers common artifact types such as Docker images, Maven/build artifacts, and raw files.

Use this process when cleaning up old, duplicate, failed, or unused artifacts from Nexus repositories.

## Important Notes

Deleting artifacts from Nexus is permanent unless backups or retention policies are available.

Before deleting anything:

* Confirm the repository name.
* Confirm the artifact name, tag, version, or file path.
* Confirm the artifact is no longer required by any deployment, pipeline, or release process.
* Use a service account or user account with delete permissions.
* Avoid passing passwords directly in commands.

Required Nexus permission:

```text
nx-repository-view-*-*-delete
```

## Authentication

Use `curl` with a username and password prompt instead of putting the password directly in the command.

```bash
curl -u "USERNAME:" <NEXUS_API_URL>
```

Nexus will prompt for the password securely.

## Nexus 3: Find and Delete a Component

Most artifact deletions in Nexus 3 require the component ID.

### Step 1: Search for the Artifact

```bash
curl -u "USERNAME:" -X GET \
"http://NEXUS_URL:8081/service/rest/v1/search?repository=REPOSITORY_NAME&name=ARTIFACT_NAME"
```

Example:

```bash
curl -u "admin:" -X GET \
"http://nexus.example.com:8081/service/rest/v1/search?repository=maven-releases&name=my-app"
```

From the response, copy the component `id`.

### Step 2: Delete the Component

```bash
curl -u "USERNAME:" -X DELETE \
"http://NEXUS_URL:8081/service/rest/v1/components/COMPONENT_ID"
```

Example:

```bash
curl -u "admin:" -X DELETE \
"http://nexus.example.com:8081/service/rest/v1/components/abc123def456"
```

A successful delete normally returns no output.

## Delete Maven or Build Artifacts

Use this for `.jar`, `.war`, `.ear`, `.zip`, or other build artifacts stored in Maven repositories.

### Search by Group ID, Artifact ID, and Version

```bash
curl -u "USERNAME:" -X GET \
"http://NEXUS_URL:8081/service/rest/v1/search?repository=REPOSITORY_NAME&maven.groupId=GROUP_ID&maven.artifactId=ARTIFACT_ID&maven.baseVersion=VERSION"
```

Example:

```bash
curl -u "admin:" -X GET \
"http://nexus.example.com:8081/service/rest/v1/search?repository=maven-releases&maven.groupId=com.company.app&maven.artifactId=my-app&maven.baseVersion=1.0.0"
```

Delete using the returned component ID:

```bash
curl -u "USERNAME:" -X DELETE \
"http://NEXUS_URL:8081/service/rest/v1/components/COMPONENT_ID"
```

## Delete Docker Images

Docker images in Nexus are also deleted by component ID.

### Search for Docker Image

```bash
curl -u "USERNAME:" -X GET \
"http://NEXUS_URL:8081/service/rest/v1/search?repository=DOCKER_REPOSITORY_NAME&name=IMAGE_NAME&version=IMAGE_TAG"
```

Example:

```bash
curl -u "admin:" -X GET \
"http://nexus.example.com:8081/service/rest/v1/search?repository=docker-hosted&name=my-app&version=1.0.0"
```

Delete the returned component ID:

```bash
curl -u "USERNAME:" -X DELETE \
"http://NEXUS_URL:8081/service/rest/v1/components/COMPONENT_ID"
```

Example:

```bash
curl -u "admin:" -X DELETE \
"http://nexus.example.com:8081/service/rest/v1/components/abc123def456"
```

## Delete Raw Files

For raw repositories, search by repository and file name/path.

### Search for Raw File

```bash
curl -u "USERNAME:" -X GET \
"http://NEXUS_URL:8081/service/rest/v1/search?repository=RAW_REPOSITORY_NAME&name=FILE_NAME"
```

Example:

```bash
curl -u "admin:" -X GET \
"http://nexus.example.com:8081/service/rest/v1/search?repository=raw-hosted&name=config.zip"
```

Delete the component:

```bash
curl -u "USERNAME:" -X DELETE \
"http://NEXUS_URL:8081/service/rest/v1/components/COMPONENT_ID"
```

## Verify Deletion

After deleting, run the search command again to confirm the artifact no longer exists.

```bash
curl -u "USERNAME:" -X GET \
"http://NEXUS_URL:8081/service/rest/v1/search?repository=REPOSITORY_NAME&name=ARTIFACT_NAME"
```

If the artifact is deleted, it should no longer appear in the search results.

## Recommended Safe Deletion Process

1. Search for the artifact.
2. Confirm the repository, name, version, and component ID.
3. Validate with the application or release owner.
4. Delete the component.
5. Re-run the search to confirm deletion.
6. Document the deletion in the change ticket or Confluence page.

## Example Deletion Record

```text
Repository: maven-releases
Artifact: my-app
Version/Tag: 1.0.0
Component ID: abc123def456
Deleted By: USERNAME
Date: YYYY-MM-DD
Reason: Old unused artifact cleanup
Validation: Confirmed artifact no longer appears in Nexus search results
```

## Troubleshooting

### 403 Forbidden

The user does not have permission to delete artifacts.

Required permission:

```text
nx-repository-view-*-*-delete
```

### 404 Not Found

The component ID may be incorrect or the artifact may have already been deleted.

### Artifact Still Appears After Delete

Wait a few minutes and refresh the Nexus UI. Also confirm you deleted the correct component from the correct repository.

## Best Practices

* Do not delete production release artifacts without approval.
* Do not use plain-text passwords in commands.
* Use a service account where possible.
* Always verify before and after deletion.
* Keep a record of deleted artifacts.
* Follow company change management requirements.
