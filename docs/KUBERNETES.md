# Kubernetes Deployment

This project uses Kubernetes as the CD target and Amazon ECR as the container registry.

## Flow

```text
CI succeeds on main/master
  -> CD assumes an AWS IAM role through GitHub OIDC
  -> Docker image is built
  -> image is pushed to Amazon ECR
  -> kubectl updates the EKS Deployment image
  -> Kubernetes performs a rolling rollout
```

## Required GitHub Variables

Configure these under repository settings:

- `AWS_REGION`
- `AWS_ROLE_TO_ASSUME`
- `ECR_REPOSITORY`
- `EKS_CLUSTER_NAME`

Use GitHub OIDC with an AWS IAM role instead of long-lived AWS access keys.

## Kubernetes Secrets

Do not commit real secrets.

Use `k8s/secret.example.yml` only as a template. Create the real secret in the cluster:

```bash
kubectl create namespace notification-service
kubectl create secret generic notification-service-secrets \
  -n notification-service \
  --from-literal=DB_USERNAME='...' \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=AWS_ACCESS_KEY='...' \
  --from-literal=AWS_SECRET_KEY='...' \
  --from-literal=AWS_VERIFIED_EMAIL='...' \
  --from-literal=TWILIO_ACCOUNT_SID='...' \
  --from-literal=TWILIO_AUTH_TOKEN='...' \
  --from-literal=TWILIO_FROM_NUMBER='...' \
  --from-literal=JWT_SECRET='...'
```

For production, prefer AWS Secrets Manager with External Secrets Operator.

## Config

`k8s/configmap.yml` stores non-secret config such as database host, Redis host, and Kafka bootstrap servers.

For production, point these at managed services:

- PostgreSQL: Amazon RDS
- Redis: Amazon ElastiCache
- Kafka: Amazon MSK or Confluent Cloud

## Rollback

```bash
kubectl rollout undo deployment/notification-service -n notification-service
```
