# PeSIT Wizard Staging Environment - Variables

# General
variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "pesitwizard"
}

variable "environment" {
  description = "Environment name (staging, production)"
  type        = string
  default     = "staging"
}

# AWS Configuration
variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "eu-west-1"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b", "eu-west-1c"]
}

# VPC Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "private_subnets" {
  description = "Private subnet CIDR blocks"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
}

variable "public_subnets" {
  description = "Public subnet CIDR blocks"
  type        = list(string)
  default     = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
}

# EKS Configuration
variable "kubernetes_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

variable "node_instance_types" {
  description = "EC2 instance types for EKS node group"
  type        = list(string)
  default     = ["m5.large", "m5.xlarge"]
}

variable "min_nodes" {
  description = "Minimum number of nodes in node group"
  type        = number
  default     = 3
}

variable "max_nodes" {
  description = "Maximum number of nodes in node group"
  type        = number
  default     = 10
}

variable "desired_nodes" {
  description = "Desired number of nodes in node group"
  type        = number
  default     = 3
}

variable "enable_fargate" {
  description = "Enable Fargate profiles for burst workloads"
  type        = bool
  default     = false
}

# Database Configuration
variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.r6g.large"
}

variable "db_allocated_storage" {
  description = "Allocated storage for RDS (GB)"
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "Enable Multi-AZ for RDS"
  type        = bool
  default     = true
}

variable "db_backup_retention_period" {
  description = "Days to retain automated backups"
  type        = number
  default     = 7
}

# Redis Configuration
variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.r6g.large"
}

variable "redis_num_cache_clusters" {
  description = "Number of cache clusters (nodes)"
  type        = number
  default     = 2
}

# Monitoring
variable "enable_monitoring" {
  description = "Enable Prometheus/Grafana monitoring"
  type        = bool
  default     = true
}

variable "enable_logging" {
  description = "Enable centralized logging (CloudWatch/ELK)"
  type        = bool
  default     = true
}

# Tags
variable "additional_tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
