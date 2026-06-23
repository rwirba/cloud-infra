# Safely Deleting Nexus Artifacts via Command Line

This guide provides standard operating procedures for securely deleting artifacts from Sonatype Nexus using the command line.

> **Security Notice**
>
> Passing plain-text passwords directly in command-line arguments exposes credentials through:
>
> * Shell history files (`~/.bash_history`)
> * Process listings (`ps`, `top`)
> * Audit logs
>
> Use one of the secure authentication methods described below instead.

---

# Prerequisites

Before proceeding, ensure you have:

* `curl` installed on your local machine
* The target Component ID (Nexus 3) or file path (Nexus 2)
* A Nexus account with appropriate delete permissions

Required permission:

```text
nx-repository-view-*-*-delete
```

---

# Method 1: Interactive Password Prompt (Recommended for Manual Use)

This method prevents your password from being stored in shell history.

By specifying only the username followed by a colon (`:`), `curl` prompts securely for the password.

---

## Nexus 3 – Delete by Component ID

### Step 1: Find the Component ID

Run the search query below:

```bash
curl -u "your_username:" \
-X GET \
"http://<NEXUS_URL>:<PORT>/service/rest/v1/search?repository=<REPO_NAME>&maven.groupId=<GROUP_ID>&maven.artifactId=<ARTIFACT_ID>&maven.baseVersion=<VERSION>"
```

You will be prompted to enter your password securely.

### Step 2: Locate the Component ID

In the JSON response, locate the `"id"` field.

Example:

```json
{
  "id": "Y29tcG9uZW50OjEyMzQ1"
}
```

Copy the value of the ID.

### Step 3: Delete the Component

```bash
curl -u "your_username:" \
-X DELETE \
"http://<NEXUS_URL>:<PORT>/service/rest/v1/components/<COMPONENT_ID>"
```

Example:

```bash
curl -u "jdoe:" \
-X DELETE \
"http://nexus.company.com:8081/service/rest/v1/components/Y29tcG9uZW50OjEyMzQ1"
```

---

## Nexus 2 – Delete by Repository Path

Delete artifacts directly using the repository path:

```bash
curl --request DELETE \
-u "your_username:" \
"http://<NEXUS_URL>:<PORT>/nexus/service/local/repositories/<REPO_NAME>/content/<GROUP_ID_PATH>/<ARTIFACT_ID>/<VERSION>/"
```

Example:

```bash
curl --request DELETE \
-u "jdoe:" \
"http://nexus.company.com:8081/nexus/service/local/repositories/releases/content/com/company/app/my-app/1.0.0/"
```

---

# Method 2: Nexus User Token (Recommended for Automation)

For scripts, CI/CD pipelines, and automation, use a Nexus User Token instead of your account password.

Benefits:

* No need to expose primary account credentials
* Can be revoked independently
* Ideal for Jenkins, GitHub Actions, GitLab CI, and other automation platforms

---

## Step 1: Generate a User Token

1. Log in to Nexus Repository Manager.
2. Click your username in the upper-right corner.
3. Open **User Token**.
4. Click **Access User Token**.
5. Copy the following values:

   * Token Name
   * Token Code

---

## Step 2: Encode the Token

Combine the Token Name and Token Code separated by a colon and Base64 encode them.

```bash
echo -n "token_name:token_code" | base64
```

Example:

```bash
echo -n "john.token:AbCdEf123456" | base64
```

Output:

```text
ZE9rM25OYW1lOnRva2VuQ29kZVN0cmluZw==
```

Copy the generated Base64 string.

---

## Step 3: Delete Using the Authorization Header

Replace `<BASE64_TOKEN>` with the encoded value.

---

### Nexus 3 – Component Deletion

```bash
curl \
-H "Authorization: Basic <BASE64_TOKEN>" \
-X DELETE \
"http://<NEXUS_URL>:<PORT>/service/rest/v1/components/<COMPONENT_ID>"
```

Example:

```bash
curl \
-H "Authorization: Basic ZE9rM25OYW1lOnRva2VuQ29kZVN0cmluZw==" \
-X DELETE \
"http://nexus.company.com:8081/service/rest/v1/components/Y29tcG9uZW50OjEyMzQ1"
```

---

### Nexus 2 – Path Deletion

```bash
curl \
--request DELETE \
-H "Authorization: Basic <BASE64_TOKEN>" \
"http://<NEXUS_URL>:<PORT>/nexus/service/local/repositories/<REPO_NAME>/content/<GROUP_ID_PATH>/<ARTIFACT_ID>/<VERSION>/"
```

Example:

```bash
curl \
--request DELETE \
-H "Authorization: Basic ZE9rM25OYW1lOnRva2VuQ29kZVN0cmluZw==" \
"http://nexus.company.com:8081/nexus/service/local/repositories/releases/content/com/company/app/my-app/1.0.0/"
```

---

# Best Practices

✅ Use User Tokens for automation.

✅ Use interactive password prompts for manual operations.

✅ Verify the component ID before deleting.

✅ Restrict delete permissions to authorized users.

✅ Test commands in a non-production environment first.

❌ Do not hardcode passwords in scripts.

❌ Do not store passwords in shell history.

❌ Do not grant delete permissions broadly.

---

# Troubleshooting

### 401 Unauthorized

Verify:

* Username/password or token is correct
* Account is active
* User token has not been revoked

### 403 Forbidden

Verify the account has:

```text
nx-repository-view-*-*-delete
```

permissions.

### 404 Not Found

Verify:

* Repository name is correct
* Component ID exists
* Artifact path is accurate

### Nexus 3 Search Returns No Results

Verify:

* Repository name
* Maven groupId
* Maven artifactId
* Maven version

---

# References

* Sonatype Nexus Repository Manager Documentation
* Nexus REST API Documentation
* Nexus User Token Documentation
