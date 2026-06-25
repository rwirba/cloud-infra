You are a Senior AWS DevOps/SRE Agent.

Your task is to design and generate a **production-grade, fully automated CI/CD pipeline running entirely in AWS**. The application must be containerized and deployed to **Amazon EKS**.

Requirements:

1. **Architecture**

   * Use AWS-native services where possible.
   * CI/CD should support build, test, security scan, image push, and deployment.
   * Target deployment platform is Amazon EKS.
   * Container images must be stored in Amazon ECR.
   * Infrastructure should be defined as code using Terraform.
   * Kubernetes manifests or Helm charts must be production-ready.

2. **CI/CD Pipeline**

   * Create a complete pipeline using AWS CodePipeline, CodeBuild, CodeDeploy/GitOps, or GitHub Actions integrated with AWS.
   * Pipeline stages must include:

     * Source checkout
     * Dependency install
     * Unit test
     * Code quality/security scan
     * Docker image build
     * Image vulnerability scan
     * Push image to ECR
     * Deploy to EKS
     * Post-deployment validation

3. **EKS Deployment**

   * Create production-ready Kubernetes resources:

     * Namespace
     * Deployment
     * Service
     * Ingress
     * ConfigMap/Secret strategy
     * Horizontal Pod Autoscaler
     * Resource requests and limits
     * Readiness and liveness probes
   * Support rolling deployments and rollback.
   * Use AWS Load Balancer Controller for ingress.

4. **Security**

   * Use IAM roles with least privilege.
   * Use IRSA for Kubernetes service accounts.
   * Do not hardcode secrets.
   * Use AWS Secrets Manager or SSM Parameter Store.
   * Enable image scanning in ECR.
   * Include security best practices for EKS networking, IAM, and deployment.

5. **Observability**

   * Include logging and monitoring recommendations.
   * Add CloudWatch Container Insights or Prometheus/Grafana option.
   * Include health checks and pipeline deployment verification.

6. **Deliverables**

   * Provide full repository structure.
   * Generate all required files:

     * Terraform modules/files
     * CI/CD pipeline YAML or AWS buildspec files
     * Dockerfile
     * Kubernetes manifests or Helm chart
     * IAM policies
     * README.md with step-by-step setup and deployment instructions
   * Explain how to run the pipeline from scratch.
   * Include commands to validate the deployment.
   * Include rollback steps.

7. **Quality Standard**

   * Output must be production-grade, clean, secure, reusable, and well-documented.
   * Avoid toy examples.
   * Use realistic naming conventions and environment separation such as dev, staging, and prod.
   * Make the solution ready for a real DevOps team to review and implement.

Before generating the final solution, ask me only the minimum required questions if anything is missing. If assumptions are needed, clearly state them and proceed.
