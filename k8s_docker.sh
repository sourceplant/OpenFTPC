#!/bin/bash
# Set up a docker (Container Runtime) on worker node.
# Use: bash <(curl -s https://raw.githubusercontent.com/sourceplant/sourceleaf/master/k8s_docker.sh)
GREEN=$'\e[0;32m' ; RED=$'\e[0;31m' ; NC=$'\e[0m'
^ () {
if [[ "$BASH_COMMAND" =~ ^echo.*  ]]; then
echo "${GREEN}"
else
echo -e "${NC}# ${RED}${BASH_COMMAND}${NC}\c"
read -r -p "" -t 120 -n 1 -s ; echo
# If you wanted to see output in a clear screen, keep +v -v section disabled
#clear
#echo -e "# ${RED}${BASH_COMMAND}${NC}"
fi
}
# If you wanted to enable comments on screen
#set +v
trap '^' DEBUG
#set -v

echo '
# (Install Docker CE)
## Set up the repository
'
echo '### Install required packages'
yum install -y yum-utils device-mapper-persistent-data lvm2

echo '## Add the Docker repository'
curl -s https://download.docker.com/linux/centos/docker-ce.repo > /etc/yum.repos.d/docker-ce.repo

echo '# Install Docker CE'
yum install -y \
  containerd.io-1.2.13 \
  docker-ce-19.03.8 \
  docker-ce-cli-19.03.8
  
echo '## Create /etc/docker'
mkdir /etc/docker

echo '# Set up the Docker daemon'
cat > /etc/docker/daemon.json <<EOF
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m"
  },
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOF
mkdir -p /etc/systemd/system/docker.service.d
# Restart Docker
systemctl daemon-reload
systemctl restart docker


