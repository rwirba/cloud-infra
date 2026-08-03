terraform {
  backend "s3" {
    bucket         = "ezlearn-terra-bucket"  # The S3 bucket name
    key            = "terraform"  # The path within the bucket
    region         = "us-east-1"  # The AWS region
    encrypt        = true  # Encrypt the state file in S3
  }
}


resource "aws_security_group" "jenkins" {
  name        = "jenkins-security-group"

  tags = {
    Name = "jenkins-terraform-sg"
  }
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["216.131.79.229/32", "172.31.24.20/32"]
  }

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    ipv6_cidr_blocks = ["::/0"]
  }

    egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1" 
    cidr_blocks = ["0.0.0.0/0"]      
  }
}

resource "aws_security_group" "nexus" {
  name        = "nexus-security-group"

  tags = {
    Name = "nexus-terraform-sg"
  }  
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["72.41.0.101/32" , "172.31.24.20/32"]
  }

  ingress {
    from_port   = 8081
    to_port     = 8081
    protocol    = "tcp"
    cidr_blocks = ["72.41.0.101/32"]
  }

  ingress {
    from_port   = 8081
    to_port     = 8081
    protocol    = "tcp"
    security_groups = [aws_security_group.jenkins.id]
  }

    egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1" 
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "sonarqube" {
  name        = "sonarqube-security-group"

  tags = {
    Name = "sonarqube-terraform-sg"
  }    

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["72.41.0.101/32" , "172.31.24.20/32"]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["72.41.0.101/32"]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    security_groups = [aws_security_group.jenkins.id]
  }

    egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1" 
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ansible" {
  name        = "ansible-security-group"

  tags = {
    Name = "ansible-runner-sg"
  }  
  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1" 
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1" 
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group_rule" "jenkins_from_sonarqube" {
  type              = "ingress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  security_group_id = aws_security_group.jenkins.id
  source_security_group_id = aws_security_group.sonarqube.id
}

resource "aws_instance" "jenkins" {
  ami = "ami-0fc5d935ebf8bc3bc"
  instance_type = "t2.small"
  key_name = "my-key"

  vpc_security_group_ids = [aws_security_group.jenkins.id]

  tags = {
    Name = "Jenkins-terraform"
  }
}

resource "aws_instance" "nexus" {
  ami = "ami-0fa1ca9559f1892ec"
  instance_type = "t2.medium"
  key_name = "my-key"

  vpc_security_group_ids = [aws_security_group.nexus.id]

  tags = {
    Name = "nexus-terraform"
  }
}

resource "aws_instance" "sonarqube" {
  ami = "ami-06aa3f7caf3a30282"
  instance_type = "t2.medium"
  key_name = "my-key"

  vpc_security_group_ids = [aws_security_group.sonarqube.id]

  tags = {
    Name = "sonar-terraform"
  }
}

resource "aws_instance" "ansible-runner" {
  ami = "ami-0fc5d935ebf8bc3bc"
  instance_type = "t2.small"
  key_name = "my-key"

  vpc_security_group_ids = [aws_security_group.ansible.id]

  tags = {
    Name = "Ansible-runner"
  }
}
