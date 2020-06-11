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
set -o functrace
trap '^' DEBUG
#set -v
function setup (){
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

echo '# List files'
rpm -ql containerd.io | grep -v '/share'
rpm -ql docker-ce| grep -v '/share'
rpm -ql docker-ce-cli | grep -v '/share'

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
#mkdir -p /etc/systemd/system/docker.service.d

echo '# Reload systemctl and restart Docker'
systemctl daemon-reload ; sleep 3
systemctl restart docker

echo '# Check status of Docker'
systemctl status docker
journalctl -eu docker

# Running your first container
docker container run hello-world
}
function lab1 (){
echo '## In this Lab, we will see the isolation concept of containers ####'

echo '# Pull an alpine image'
docker image pull alpine

echo '# List images on the system'
docker image ls

echo '# Run the container'
docker container run alpine ls -l

echo 'Note: When you call run, the Docker client finds the image (alpine in this case), creates the container and then runs a command in that container. When you run docker container run alpine, you provided a command (ls -l), so Docker executed this command inside the container for which you saw the directory listing. After the ls command finished, the container shut down.'

echo '# Try to run the container again'
docker container run alpine /bin/sh

echo 'Note: You did not supply any additional commands to /bin/sh so it just launched the shell, exited the shell, and then stopped the container.'

echo '# Run the container in interactive terminal'
docker container run -it alpine /bin/sh

echo 'Note: Ok, we said that we had run each of our commands above in a separate container instance. We can see these instances using the docker container ls command.'

echo '# List the containers that are running'
docker container ls

echo '# List all the containers that you run'
docker container ls -a
echo 'Note: Notice that the STATUS column shows that these containers exited some time ago.'

echo '# Question: Why are there so many containers listed if they are all from the alpine image?
# Ans: This is a critical security concept in the world of Docker containers! Even though each docker container run command used the same alpine image, each execution was a separate, isolated container. Each container has a separate filesystem and runs in a different namespace; by default a container has no way of interacting with other containers, even those from the same image'
echo '# Run a container in interactive mode and create some file and run uname -n which normally shows container id'
docker container run -it alpine /bin/ash

echo '# Run a container and list the previously created file'
docker container run alpine /bin/ash -c 'ls -l hello.txt'
echo 'Note: did you notice that your “hello.txt” file is missing? That’s isolation! Your command ran in a new and separate instance, even though it is based on the same image. The 2nd instance has no way of interacting with the 1st instance because the Docker Engine keeps them separated and we have not setup any extra parameters that would enable these two instances to interact.'

echo '# Start the container where the file was created, check container id'
docker container ls -a
read -p "Enter the container id: " container_id
docker container start ${container_id}

echo 'Note: Notice this time that our container instance is still running as it was initially creaed with interactive /bin/ash'

echo '# Send a command in to the running container to run by using the exec'
docker container exec ${container_id} ls
echo 'Note: This time we get a directory listing and it shows our “hello.txt” file because we used the container instance where we created that file.'

}
setup
lab1
